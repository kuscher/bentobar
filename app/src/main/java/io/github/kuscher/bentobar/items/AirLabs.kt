package io.github.kuscher.bentobar.items

import io.github.kuscher.bentobar.net.Host
import io.github.kuscher.bentobar.net.Reply
import io.github.kuscher.bentobar.net.Request
import io.github.kuscher.bentobar.net.Why
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A flight number as it is entered: `LH455`, `lh 455`, `LH-455`, `LH0455`, or the callsign `DLH455`.
 * Two characters (an airline's designator: two letters, or a letter and a digit) or a callsign's
 * three letters, then one to four digits and at most one letter. There is no table of airlines:
 * whether anyone flies a number is the service's to say, and its reply names the airline.
 */
data class FlightNumber(val designator: String, val number: Int, val suffix: String = "") {
    /** Entered in the callsign's form (DLH455): it is asked for as one. */
    val callsign: Boolean get() = designator.length == 3
    /** Closed up, as it is asked for and kept: LH455. */
    val code: String get() = "$designator$number$suffix"
    /** As it is shown: "LH 455". */
    val shown: String get() = "$designator $number$suffix"

    companion object {
        private val CALLSIGN = Regex("([A-Z]{3})[ -]?0*([1-9][0-9]{0,3})([A-Z]?)")
        private val TICKET = Regex("([A-Z]{2}|[A-Z][0-9]|[0-9][A-Z])[ -]?0*([1-9][0-9]{0,3})([A-Z]?)")

        /** The number [text] is, or null: nothing but a flight number may stand in it. Three letters can only be a callsign's, so that form is tried first. */
        fun read(text: String): FlightNumber? {
            val t = text.trim().uppercase(Locale.ROOT)
            val m = CALLSIGN.matchEntire(t) ?: TICKET.matchEntire(t) ?: return null
            return FlightNumber(m.groupValues[1], m.groupValues[2].toInt(), m.groupValues[3])
        }

        /** [number] as it is shown ("LH455" as "LH 455"); what does not read as a flight number stays as it is. */
        fun shown(number: String): String = read(number)?.shown ?: number
    }
}

/**
 * AirLabs (airlabs.co, API v9), read: what to ask, and what its replies say. What is written here was
 * seen in its replies on 2 October 2026: `flight` answers with the one flight of a number that is
 * nearest to now, `schedules` with the flights of the next ten hours, `routes` with the timetable. An
 * error comes with the status 200 and an `error` object. A reply from the network is never trusted to
 * be well formed, short or one line.
 *
 * Every reply repeats the key it was asked with (and names the caller's address) in a `request`
 * object. Of that object one number is read, how many lookups are left, and nothing else of a reply
 * leaves this file but the model made here: a reply's text is never kept, logged or shown.
 */
object AirLabs {
    /** Where a key is got. */
    const val SIGN_UP = "https://airlabs.co/signup"
    /** The one flight of a number nearest to now. */
    const val FLIGHT = "/api/v9/flight"
    /** The flights of a number in the next ten hours, and the ones just flown. */
    const val SCHEDULES = "/api/v9/schedules"
    /** The timetable for a number. */
    const val ROUTES = "/api/v9/routes"

    /** Why there is no answer, to be said in plain words. */
    @Serializable
    enum class Failure {
        /** The service knows no flight of that number. */
        NOT_FOUND,
        /** The key is unknown to the service, or has run out. */
        REFUSED,
        /** The key's lookups for the month are used up. */
        USED_UP,
        /** The number does not fly on the day that was asked for. */
        NOT_THAT_DAY,
        /** The device reached nobody. */
        OFFLINE,
        /** Anything else: a reply that could not be read, an error of the service's own, a limit for the minute or the hour. */
        NO_ANSWER,
    }

    /** What a reply held: [value], or why not. [left]: how many lookups the key has left this month, where the reply says. */
    class Read<out T>(val value: T?, val failure: Failure? = null, val left: Int? = null)

    /** A line of an airline's timetable: this number flies from here to there at these times on these days. Times are each airport's own. */
    data class Route(
        val from: String, val to: String, val leaves: LocalTime, val minutes: Int, val days: Set<DayOfWeek>,
        /** How many minutes each airport's clock is ahead of UTC. */
        val fromOffset: Int, val toOffset: Int,
        val fromTerminal: String? = null, val toTerminal: String? = null,
        val callsign: String? = null,
    )

    /**
     * One question about [n]: the host, the path, and the two things that are sent, the number and the
     * user's own [key]. Nothing else goes to the service. (The transport encodes them; a request prints
     * as its host and path alone.)
     */
    fun request(path: String, n: FlightNumber, key: String): Request =
        Request(Host.AIRLABS, path, listOf((if (n.callsign) "flight_icao" else "flight_iata") to n.code, "api_key" to key.trim()))

    /** What a lookup came to: the flight, or why there is none; and how many lookups the key has left, where a reply said. */
    class Answer(val flight: Flight?, val failure: Failure?, val left: Int?)

    /**
     * The next flight of [n], or the one on [day], asked for through [get] (which sends a request and
     * says what came of it). `flight` answers with the one nearest to now. If that one landed more than
     * three hours ago, the next is among the coming ten hours' (`schedules`) or, further off, in the
     * timetable (`routes`); a day that is not that flight's is the timetable's too, and so is a number
     * `flight` does not know (one that flies once a week). Up to three requests, and one in the usual
     * case.
     *
     * [zone]: where [day] was chosen, when it is a day of the device's (a day chip: "Today" in Los
     * Angeles is tomorrow's date in Tokyo for a flight that leaves there in an hour). The flight the
     * service answers with is then taken for that day too if it leaves on it by the device's clock.
     * Null: [day] is the departure airport's own date and nothing else (what is kept of a followed flight).
     *
     * Null: a request was not sent at all (the service was switched off or the bar hid under the
     * lookup), so there is nothing to say, neither an answer nor a failure.
     */
    fun lookup(n: FlightNumber, day: LocalDate?, key: String, now: Instant, zone: ZoneId? = null, get: (Request) -> Reply): Answer? {
        var left: Int? = null
        var unsent = false
        // What is asked for: the number as it was entered, until the service has named the ticket's form of a callsign.
        var number = n
        fun <T> ask(path: String, read: (String) -> Read<T>): Read<T> {
            // Once a request was not sent, the lookup is over: nothing more is tried.
            if (unsent) return Read(null, Failure.NO_ANSWER)
            return when (val reply = send(request(path, number, key), get)) {
                is Reply.Ok -> read(reply.text).also { r -> r.left?.let { left = it } }
                is Reply.Failed -> { if (reply.why == Why.OFF) unsent = true; Read(null, failure(reply)) }
            }
        }
        fun found(): Answer {
            val first = ask(FLIGHT) { flight(it) }
            if (first.failure == Failure.NOT_FOUND) {
                val timetable = ask(ROUTES) { routes(it) }
                // The timetable was not to be had (no connection, no lookups left): that is what went wrong, not "nothing found".
                timetable.failure?.takeIf { it != Failure.NOT_FOUND }?.let { return Answer(null, it, left) }
                val lines = timetable.value.orEmpty()
                val like = Flight(n.code, "", FlightEnd(""), FlightEnd(""), FlightState.PLANNED)
                val planned = (if (day != null) on(lines, like, day) else upcoming(lines, like, now))?.let { dated(it, now) }
                return Answer(planned, if (planned != null) null else if (day != null && lines.isNotEmpty()) Failure.NOT_THAT_DAY else Failure.NOT_FOUND, left)
            }
            val f = first.value ?: return Answer(null, first.failure ?: Failure.NO_ANSWER, left)
            // The one-flight question was tried with a callsign; the other two only with a ticket's number, which this reply has.
            if (n.callsign) FlightNumber.read(f.number)?.takeUnless { it.callsign }?.let { number = it }
            if (day != null) {
                fun onDevice(t: LocalDateTime?) = zone != null && t != null && LocalDate.ofInstant(f.from.moment(t), zone) == day
                if (f.from.planned?.toLocalDate() == day || f.from.time?.toLocalDate() == day || onDevice(f.from.planned) || onDevice(f.from.time)) return Answer(f, null, left)
                val timetable = ask(ROUTES) { routes(it) }
                val lines = timetable.value ?: return Answer(null, timetable.failure?.takeIf { it != Failure.NOT_FOUND } ?: Failure.NOT_THAT_DAY, left)
                return on(lines, f, day)?.let { Answer(dated(it, now), null, left) } ?: Answer(null, Failure.NOT_THAT_DAY, left)
            }
            if (!FlightRules.over(f, now)) return Answer(f, null, left)
            val soon = ask(SCHEDULES) { schedules(it) }
            FlightRules.next(soon.value.orEmpty(), now)?.let { return Answer(named(it, f), null, left) }
            val lines = ask(ROUTES) { routes(it) }
            upcoming(lines.value.orEmpty(), f, now)?.let { return Answer(dated(it, now), null, left) }
            // No next flight, and one of the two was not to be had (no connection, told to slow down): that is what went wrong.
            // To follow the one that landed hours ago instead would be to say "Landed" and never ask again.
            listOf(soon.failure, lines.failure).firstOrNull { it != null && it != Failure.NOT_FOUND }?.let { return Answer(null, it, left) }
            // Nothing ahead that anyone knows of: the one that was, as it was.
            return Answer(f, null, left)
        }
        return found().takeIf { !unsent }
    }

    /** What sending came to. Whoever carries requests must not throw; if one does, its message goes nowhere (it could hold the address, and with it the key). */
    private fun send(request: Request, get: (Request) -> Reply): Reply = try { get(request) } catch (_: Exception) { Reply.Failed(Why.UNREADABLE) }

    /** No connection is itself; everything else that is no reply is "no answer". */
    private fun failure(reply: Reply.Failed): Failure = if (reply.why == Why.OFFLINE) Failure.OFFLINE else Failure.NO_ANSWER

    /** What asking again about one flight came to. */
    sealed interface Again {
        /** The same flight, as the service says it now. */
        data class Is(val flight: Flight) : Again
        /** The service has nothing more to say about it: it answers with a later flight of the number, knows none, or will not answer this key. Asking ends. */
        data object Gone : Again
        /** The service does not have its day yet (it still answers with the flight before): the plan stands, and the next ask comes when it is due. */
        data object NotYet : Again
        /** No answer this time (no connection, a reply nobody can read): worth another try soon. */
        data object Failed : Again
    }

    /** [again]; how many lookups the key has left, where the reply said; why nothing came, where nothing did; and how long a "slow down" asked to be left alone. */
    class Asked(val again: Again, val left: Int?, val failure: Failure? = null, val retryAfterSec: Long? = null)

    /**
     * Asks about the flight [was] once more: one request, `flight`, whatever the first lookup took. A
     * bar item follows one flight, not "the next" of its number: when the service has gone on to a
     * later one, that is the end of the asking, never a new flight to count down to. When it still
     * answers with an earlier one, the flight that is followed is still to come and the service is not
     * there yet (a plan from the timetable, or a flight found among the coming hours while the one
     * before it was still the service's answer).
     *
     * Null: the request was not sent at all, as for [lookup].
     */
    fun again(n: FlightNumber, key: String, was: Flight, get: (Request) -> Reply): Asked? {
        val r = when (val reply = send(request(FLIGHT, n, key), get)) {
            is Reply.Ok -> flight(reply.text)
            is Reply.Failed -> return if (reply.why == Why.OFF) null else Asked(Again.Failed, null, failure(reply), reply.retryAfterSec)
        }
        val got = r.value
        val again = when {
            got != null && FlightRules.same(was, got) -> Again.Is(got)
            // A flight with no planned time: nobody can tell which day's it is, so it is no word about this one, and no end of it either.
            got != null && got.from.planned == null -> Again.Failed
            // Another flight of the number: the one before it (the service is not there yet) or one after it.
            got != null -> if (before(got, was)) Again.NotYet else Again.Gone
            r.failure == Failure.OFFLINE || r.failure == Failure.NO_ANSWER -> Again.Failed
            r.failure == Failure.NOT_FOUND && was.timetable -> Again.NotYet
            else -> Again.Gone
        }
        return Asked(again, r.left, r.failure ?: Failure.NO_ANSWER.takeIf { again == Again.Failed })
    }

    private fun before(a: Flight, b: Flight): Boolean {
        val x = a.from.planned?.let(a.from::moment) ?: return false
        val y = b.from.planned?.let(b.from::moment) ?: return false
        return x.isBefore(y)
    }

    /** How long what an ask came to stands before the same thing is asked again: by hand, or for a number that was not found. */
    fun keep(failure: Failure?): Duration = when (failure) {
        // What may be right again in a moment is asked again soon.
        Failure.OFFLINE, Failure.NO_ANSWER -> Duration.ofSeconds(10)
        // A number nobody flies, or not on that day, costs two lookups to find out and stays so: an hour.
        Failure.NOT_FOUND, Failure.NOT_THAT_DAY -> Duration.ofHours(1)
        // An answer, a refused key, a month's lookups used up: two minutes.
        else -> Duration.ofMinutes(2)
    }

    fun flight(body: String): Read<Flight> = read(body) { (it as? JsonObject)?.let(::flight) }

    fun schedules(body: String): Read<List<Flight>> = read(body) { r -> (r as? JsonArray)?.mapNotNull { (it as? JsonObject)?.let(::flight) } }

    fun routes(body: String): Read<List<Route>> = read(body) { r -> (r as? JsonArray)?.mapNotNull { (it as? JsonObject)?.let(::route) } }

    private fun <T> read(body: String, take: (JsonElement?) -> T?): Read<T> {
        val all = try { Json.parseToJsonElement(body) as? JsonObject } catch (_: Exception) { null } ?: return Read(null, Failure.NO_ANSWER)
        // The one thing read of the object that repeats the request: how many lookups are left. The key beside it is never touched.
        val left = ((all["request"] as? JsonObject)?.get("key") as? JsonObject)?.number("limits_total")?.toInt()?.coerceAtLeast(0)
        (all["error"] as? JsonObject)?.let { e ->
            return Read(null, when (e.text("code", 40)) {
                "not_found" -> Failure.NOT_FOUND
                "unknown_api_key", "expired_api_key" -> Failure.REFUSED
                "month_limit_exceeded" -> Failure.USED_UP
                else -> Failure.NO_ANSWER
            }, left)
        }
        val value = try { take(all["response"]) } catch (_: Exception) { null }
        return if (value == null) Read(null, Failure.NO_ANSWER, left) else Read(value, null, left)
    }

    private val TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    /** A text of the reply made fit to show: one line, no longer than [max], and null where there is none. */
    private fun JsonObject.text(key: String, max: Int): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.let { NowPlayingRules.oneLine(it.content, max) }?.takeIf { it.isNotEmpty() }
    private fun JsonObject.number(key: String): Double? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
    /** A time of the reply; one in a year nobody flies in is no time. */
    private fun JsonObject.time(key: String): LocalDateTime? =
        text(key, 16)?.let { runCatching { LocalDateTime.parse(it, TIME) }.getOrNull() }?.takeIf { it.year in FlightRules.YEARS }

    private val CALLSIGN = Regex("[A-Z0-9]{3,8}")

    /** The number as air traffic control says it. It becomes part of an address (the flight's page), so anything but letters and digits is none. */
    private fun JsonObject.callsign(): String? = text("flight_icao", 16)?.takeIf { CALLSIGN.matches(it) }

    private fun flight(m: JsonObject): Flight? {
        val number = m.text("flight_iata", 8) ?: m.text("flight_icao", 8) ?: return null
        val from = end(m, "dep") ?: return null
        val to = end(m, "arr") ?: return null
        val state = when (m.text("status", 20)) {
            "scheduled" -> FlightState.PLANNED
            "en-route", "active" -> FlightState.IN_AIR
            "landed" -> FlightState.LANDED
            "cancelled" -> FlightState.CANCELED
            "diverted" -> FlightState.DIVERTED
            // A word not seen before: what the times say.
            else -> if (to.actual != null) FlightState.LANDED else if (from.actual != null) FlightState.IN_AIR else FlightState.PLANNED
        }
        return Flight(number, m.text("airline_name", 40).orEmpty(), from, to, state, flownAs = m.text("cs_flight_iata", 8),
            aircraft = m.text("model", 60)?.let(::aircraft), callsign = m.callsign())
    }

    private val REMARK = Regex("\\s*\\([^)]*\\)")

    /** The aircraft's type as it is said: the service's "Boeing 747-8 pax" without what it adds to the type (a remark in brackets, and "pax" for one that carries passengers). */
    private fun aircraft(model: String): String? = model.replace(REMARK, "").trim().removeSuffix(" pax").trim().take(40).trim().takeIf { it.isNotEmpty() }

    /** One end, from the fields that start with [p] (`dep`, `arr`). Its clock's distance from UTC is read off a time that is given both ways. */
    private fun end(m: JsonObject, p: String): FlightEnd? {
        val code = m.text("${p}_iata", 4) ?: m.text("${p}_icao", 4) ?: return null
        val offset = listOf("time", "estimated", "actual").firstNotNullOfOrNull { k ->
            val local = m.time("${p}_$k"); val utc = m.time("${p}_${k}_utc")
            // (No clock on earth is further from UTC than fourteen hours: anything else is not an airport's time.)
            if (local != null && utc != null) Duration.between(utc, local).toMinutes().takeIf { it in -FlightRules.MOST_OFFSET..FlightRules.MOST_OFFSET }?.toInt() else null
        } ?: 0
        return FlightEnd(
            code, m.text("${p}_city", 40).orEmpty(),
            m.time("${p}_time"), m.time("${p}_estimated"), m.time("${p}_actual"), offset,
            m.text("${p}_terminal", 12), m.text("${p}_gate", 8), if (p == "arr") m.text("arr_baggage", 8) else null,
        )
    }

    private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")
    private val DAYS = mapOf("mon" to DayOfWeek.MONDAY, "tue" to DayOfWeek.TUESDAY, "wed" to DayOfWeek.WEDNESDAY, "thu" to DayOfWeek.THURSDAY,
        "fri" to DayOfWeek.FRIDAY, "sat" to DayOfWeek.SATURDAY, "sun" to DayOfWeek.SUNDAY)
    /** No flight is two days long: a timetable's line that says so is no line. */
    private const val MOST_MINUTES = 48 * 60

    private fun route(m: JsonObject): Route? {
        fun clock(key: String) = m.text(key, 5)?.let { runCatching { LocalTime.parse(it, CLOCK) }.getOrNull() }
        /**
         * A clock against UTC, from the same moment said both ways without a day. Ten hours behind and
         * fourteen ahead are the same two clocks, and which it is cannot be told from them: taken as
         * between eleven hours behind and thirteen ahead, which is right for Hawaii, New Zealand, Fiji
         * and Tonga and wrong only for American Samoa, Niue and Kiritimati. ([planned] takes an
         * airport's clock from a dated reply where it has one.)
         */
        fun offset(local: LocalTime?, utc: LocalTime?): Int {
            if (local == null || utc == null) return 0
            val d = (local.toSecondOfDay() - utc.toSecondOfDay()) / 60
            return if (d > 13 * 60) d - 24 * 60 else if (d <= -11 * 60) d + 24 * 60 else d
        }
        fun strings(key: String) = (m[key] as? JsonArray)?.mapNotNull { e -> (e as? JsonPrimitive)?.takeIf { it.isString }?.content }
        fun one(key: String) = strings(key)?.singleOrNull()?.let { NowPlayingRules.oneLine(it, 12) }?.takeIf { it.isNotEmpty() }
        val leaves = clock("dep_time") ?: return null
        val minutes = m.number("duration")?.toInt()?.takeIf { it in 1..MOST_MINUTES } ?: return null
        val days = strings("days")?.mapNotNull { DAYS[it] }?.toSet().orEmpty()
        return Route(
            m.text("dep_iata", 4) ?: return null, m.text("arr_iata", 4) ?: return null, leaves, minutes, days,
            offset(leaves, clock("dep_time_utc")), offset(clock("arr_time"), clock("arr_time_utc")), one("dep_terminals"), one("arr_terminals"),
            m.callsign(),
        )
    }

    /**
     * The flight a line of the timetable is on [day], said with the names [like] has (the same number,
     * another day). A plan: no gate, no delay. Null if it does not fly on that day of the week. Where
     * [like] has the same airport with a dated time, that reply's clock is taken for it: read off a
     * date, it is exact, and the timetable's own is a guess for the clocks furthest from UTC.
     */
    fun planned(route: Route, like: Flight, day: LocalDate): Flight? {
        if (route.days.isNotEmpty() && day.dayOfWeek !in route.days) return null
        fun known(code: String) = listOf(like.from, like.to).firstOrNull { it.code == code }
        val fromOffset = known(route.from)?.takeIf { it.time != null }?.offset ?: route.fromOffset
        val toOffset = known(route.to)?.takeIf { it.time != null }?.offset ?: route.toOffset
        val leaves = day.atTime(route.leaves)
        val lands = LocalDateTime.ofEpochSecond(leaves.toEpochSecond(ZoneOffset.ofTotalSeconds(fromOffset * 60)) + route.minutes * 60L, 0, ZoneOffset.ofTotalSeconds(toOffset * 60))
        return Flight(
            like.number, like.airline,
            FlightEnd(route.from, known(route.from)?.city.orEmpty(), planned = leaves, offset = fromOffset, terminal = route.fromTerminal),
            FlightEnd(route.to, known(route.to)?.city.orEmpty(), planned = lands, offset = toOffset, terminal = route.toTerminal),
            FlightState.PLANNED, like.flownAs, timetable = true, callsign = like.callsign ?: route.callsign,
        )
    }

    /**
     * The next flight of the timetable to leave after [now], within a week: the soonest among the
     * lines that start where [like] started (a number with two legs stays on its leg), else the
     * soonest of the others.
     */
    fun upcoming(routes: List<Route>, like: Flight, now: Instant): Flight? {
        fun leaves(f: Flight) = f.from.planned?.let(f.from::moment)
        fun soonest(lines: List<Route>): Flight? = lines.mapNotNull { r ->
            val today = LocalDateTime.ofEpochSecond(now.epochSecond, 0, ZoneOffset.ofTotalSeconds(r.fromOffset * 60)).toLocalDate()
            (-1L..7L).firstNotNullOfOrNull { d -> planned(r, like, today.plusDays(d))?.takeIf { leaves(it)?.isAfter(now) == true } }
        }.minByOrNull { leaves(it) ?: Instant.MAX }
        val (same, other) = routes.partition { it.from == like.from.code }
        return soonest(same) ?: soonest(other)
    }

    /** The timetable's flight on [day], whichever of its lines flies then; the lines that start where [like] started come first. */
    fun on(routes: List<Route>, like: Flight, day: LocalDate): Flight? =
        routes.sortedBy { it.from != like.from.code }.firstNotNullOfOrNull { planned(it, like, day) }

    /** The zones there are, for [steady]: read once. */
    private val ZONES: List<ZoneId> by lazy { ZoneId.getAvailableZoneIds().mapNotNull { runCatching { ZoneId.of(it) }.getOrNull() } }

    /**
     * True if the clocks a timetable's flight was reckoned with still hold on its day. The timetable
     * gives each airport's clock as it is today and no zone, so nobody can say what that clock will
     * be after a change. What can be said: whether any place whose clock stands where this airport's
     * stands today changes it between [now] and the flight. If none does, the moments are right; if
     * one does (Frankfurt's, on a day after the end of summer time), they may be an hour out.
     */
    fun steady(f: Flight, now: Instant): Boolean = listOf(f.from, f.to).all { e ->
        val then = e.time?.let(e::moment) ?: return@all true
        ZONES.none { z -> z.rules.getOffset(now).totalSeconds == e.offset * 60 && z.rules.getOffset(then).totalSeconds != e.offset * 60 }
    }

    /** A timetable's flight with what is known of its clocks: [Flight.loose] where they may not hold on its day. */
    private fun dated(f: Flight, now: Instant): Flight = if (f.timetable && !steady(f, now)) f.copy(loose = true) else f

    /** [f], a flight from a reply that names no airline and no cities (`schedules`), with the names [like] has for them. */
    fun named(f: Flight, like: Flight): Flight {
        fun end(e: FlightEnd) = listOf(like.from, like.to).firstOrNull { it.code == e.code }?.let { e.copy(city = e.city.ifEmpty { it.city }) } ?: e
        return f.copy(airline = f.airline.ifEmpty { like.airline }, from = end(f.from), to = end(f.to), callsign = f.callsign ?: like.callsign)
    }
}
