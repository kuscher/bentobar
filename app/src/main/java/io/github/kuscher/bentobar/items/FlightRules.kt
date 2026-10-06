package io.github.kuscher.bentobar.items

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** An airport's own wall-clock time, kept as text: "2026-10-01T14:40". */
internal object FlightTime : KSerializer<LocalDateTime> {
    override val descriptor = PrimitiveSerialDescriptor("FlightTime", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDateTime) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDateTime = LocalDateTime.parse(decoder.decodeString())
}

/**
 * One end of a flight: the airport and what is known of the time there. The times are the airport's
 * own wall clock; [offset] is how many minutes that clock is ahead of UTC (−420 for San Francisco in
 * summer), so each of them is a moment too.
 */
@Serializable
data class FlightEnd(
    /** The airport's three letters: SFO. */
    val code: String,
    /** "San Francisco"; empty when the service did not say. */
    val city: String = "",
    @Serializable(with = FlightTime::class) val planned: LocalDateTime? = null,
    @Serializable(with = FlightTime::class) val expected: LocalDateTime? = null,
    @Serializable(with = FlightTime::class) val actual: LocalDateTime? = null,
    val offset: Int = 0,
    val terminal: String? = null,
    val gate: String? = null,
    /** The baggage belt, at the arrival's end. */
    val belt: String? = null,
) {
    /** The time to show: what happened, else what is expected, else the plan. */
    val time: LocalDateTime? get() = actual ?: expected ?: planned
    /** How many minutes after the plan that is; negative when it is before; null when only the plan is known. */
    val late: Int? get() = (actual ?: expected)?.let { t -> planned?.let { Duration.between(it, t).toMinutes().toInt() } }
    fun moment(t: LocalDateTime): Instant = t.toInstant(ZoneOffset.ofTotalSeconds(offset * 60))
    /** City or, where there is none, the three letters. */
    val place: String get() = city.ifEmpty { code }
}

@Serializable
enum class FlightState { PLANNED, IN_AIR, LANDED, CANCELED, DIVERTED }

/**
 * One flight on one day, as the service said it. [timetable]: made from the airline's timetable, not
 * from that day's operations: a plan, with no gate, nothing known of delays and no state of its own
 * (whether it has left is what the clock says against its plan). [loose]: its times are right at
 * their airports, but the moments they stand for may be out, so nothing is counted from them. That is
 * a timetable's flight on a day after a clock change at one of its airports (an hour out), and a
 * flight of which the service gave a time without its UTC twin (nobody knows by how much).
 */
@Serializable
data class Flight(
    /** As a ticket says it: LH455. */
    val number: String,
    /** "Lufthansa"; empty when the service did not say. */
    val airline: String,
    val from: FlightEnd,
    val to: FlightEnd,
    val state: FlightState,
    /** The number it is flown under when this one is sold by another airline (a codeshare): UA945. */
    val flownAs: String? = null,
    val timetable: Boolean = false,
    val loose: Boolean = false,
    /** The aircraft's type, where the service names it: "Boeing 747-8". It comes with the position, so mostly once it has left. */
    val aircraft: String? = null,
    /** The number as air traffic control says it, where the service names it: DLH455. A flight's page on the web is found by this form. */
    val callsign: String? = null,
) {
    /** It has left the ground (or will not). */
    val left: Boolean get() = state == FlightState.IN_AIR || state == FlightState.LANDED || state == FlightState.DIVERTED || from.actual != null
}

/**
 * The rules of a flight: what phase it is in at a moment, what is said of it, where its plane stands,
 * and when the service is worth asking again. No Android, no words: the words are `FlightText`'s.
 * Time comes in as an argument, and durations are always worked out from moments (an airport's time
 * and its clock's distance from UTC), never from two clocks' readings: a flight across the date line
 * that lands "before" it left counts right, and the device's own time zone has no part in any of it.
 */
object FlightRules {
    /** What is said of a flight in two or three words: which words, and how many minutes where they have a number. */
    enum class Saying { PLANNED, TIMETABLE, ON_TIME, DELAYED, IN_AIR, AIR_ON_TIME, AIR_LATE, AIR_EARLY, LANDED, LANDED_LATE, LANDED_EARLY, DIVERTED, CANCELED }

    /** [minutes]: how late or early, where the saying has a number. */
    data class Said(val saying: Saying, val minutes: Int = 0)

    /** Where to go: the terminal, and the gate or the belt. Any of them may be unknown; an unknown one is not said. */
    data class Where(val terminal: String?, val gate: String?, val belt: String?)

    /** Where a followed flight stands by the clock, whatever was last heard of it. */
    enum class Stage { BEFORE, IN_AIR, LANDED, CANCELED }

    /** [minutes]: to go until it leaves ([Stage.BEFORE]) or lands ([Stage.IN_AIR]). */
    data class Standing(val stage: Stage, val minutes: Long = 0)

    /** How a time's day is said beside it: not at all (it is the user's today), by its weekday (within six days), or by its date. */
    enum class DayForm { NONE, WEEKDAY, DATE }

    /** Where a flight is in its life for someone who looks at it now: what its headline is about. */
    enum class Phase {
        /** Still to leave, and more than three hours off: when it leaves. */
        AHEAD,
        /** It leaves within three hours: a countdown to that. */
        SOON,
        IN_AIR,
        LANDED,
        CANCELED,
        DIVERTED,
        /** A timetable's flight whose time to leave has passed: nobody knows what became of it. */
        TIMETABLE,
    }

    /** The sentence a flight has as its headline. The words are the app's, in the user's language. */
    enum class Heading { LEAVES_AT, LEAVES_IN, LANDS_IN, IN_AIR, LANDED_AGO, LANDED_NOW, LANDED, CANCELED, DIVERTED, TIMETABLE }

    /** [minutes]: to go ([Heading.LEAVES_IN], [Heading.LANDS_IN]) or since ([Heading.LANDED_AGO]), where the sentence has a number. */
    data class Headline(val heading: Heading, val minutes: Long = 0)

    /** Whether a flight runs to plan: what the one badge under the headline says. */
    enum class Verdict {
        /** Only the plan is known. */
        PLANNED,
        ON_TIME,
        /** It will leave, or land, later than planned. */
        DELAYED,
        /** It landed later than planned. */
        LATE,
        EARLY,
    }

    /** Good news, a delay, or neither: the badge's look. */
    enum class Kind { PLAIN, GOOD, LATE }

    /** [minutes]: how late or early, where the verdict has a number. */
    data class Badge(val verdict: Verdict, val minutes: Int = 0) {
        val kind: Kind get() = when (verdict) {
            Verdict.ON_TIME, Verdict.EARLY -> Kind.GOOD
            Verdict.DELAYED, Verdict.LATE -> Kind.LATE
            Verdict.PLANNED -> Kind.PLAIN
        }
    }

    /**
     * What stands under one end of a flight's line: the airport, its time ([struck]: it will not happen), and
     * what matters at that end now. Before the flight leaves that is where to go at the start; after, where
     * to meet it at the far end, and at the start the aircraft. Whatever the service did not name is null,
     * and is then simply not said.
     */
    data class EndSays(
        val code: String, val time: LocalDateTime?, val struck: Boolean = false,
        val terminal: String? = null, val gate: String? = null, val belt: String? = null, val aircraft: String? = null,
    )

    /**
     * What is shown of a flight at one moment. [share]: how much of the flying time has passed, 0 to 1,
     * which is where the plane stands on the line; null when nobody knows where it is, and the line has
     * no plane.
     */
    data class Row(val phase: Phase, val headline: Headline, val badge: Badge?, val share: Double?, val from: EndSays, val to: EndSays)

    /**
     * A plane that leaves or lands within this many minutes of its plan is on time: what airlines call
     * on time. (The service reports every minute; "Delayed 6 min" in a color would be alarm for nothing,
     * and the exact time stands under the line either way.)
     */
    const val ON_TIME = 15
    /**
     * From this many minutes behind its plan a flight is very late, and its line in the bar says so.
     * A judgment, where [ON_TIME] is the airlines' own line.
     */
    const val VERY_LATE = 45
    /** After this long on the ground a flight is over, and "the next flight" is the one after it. */
    val OVER: Duration = Duration.ofHours(3)

    /**
     * What is said of a flight's punctuality at [now]. "On time" only when the service has given a new
     * time and it is the plan's (within [ON_TIME] minutes); with only the plan it is "Planned". A delay
     * is the departure's until the flight has left, and the landing's from then on. A timetable's flight
     * whose time to leave has passed is not "Planned" any more, and nobody knows what became of it.
     */
    fun said(f: Flight, now: Instant): Said = when (f.state) {
        FlightState.CANCELED -> Said(Saying.CANCELED)
        FlightState.DIVERTED -> Said(Saying.DIVERTED)
        FlightState.LANDED -> against(f.to.late, Saying.LANDED_LATE, Saying.LANDED_EARLY, Saying.LANDED, Saying.LANDED)
        FlightState.IN_AIR -> against(f.to.late, Saying.AIR_LATE, Saying.AIR_EARLY, Saying.AIR_ON_TIME, Saying.IN_AIR)
        FlightState.PLANNED -> f.from.late.let {
            if (f.timetable) Said(if (departed(f, now)) Saying.TIMETABLE else Saying.PLANNED)
            else if (it == null) Said(Saying.PLANNED) else if (it >= ON_TIME) Said(Saying.DELAYED, it) else Said(Saying.ON_TIME)
        }
    }

    /** It has left: the service says so, or, for a timetable's flight, its time to leave has passed. */
    fun departed(f: Flight, now: Instant): Boolean = f.left || (f.timetable && passed(f.from, now))

    /** It has landed: the service says so, or, for a timetable's flight, its time to land has passed. */
    fun arrived(f: Flight, now: Instant): Boolean = f.state == FlightState.LANDED || (f.timetable && passed(f.to, now))

    private fun passed(e: FlightEnd, now: Instant): Boolean = e.time?.let { !e.moment(it).isAfter(now) } ?: false

    /** A landing against its plan. */
    private fun against(minutes: Int?, late: Saying, early: Saying, onTime: Saying, unknown: Saying): Said = when {
        minutes == null -> Said(unknown)
        minutes >= ON_TIME -> Said(late, minutes)
        minutes <= -ON_TIME -> Said(early, -minutes)
        else -> Said(onTime)
    }

    /** From this long before it leaves, a flight is counted down to. */
    val SOON: Duration = Duration.ofHours(3)
    /** In the air the plane is never nearer an end of its line than this: it reaches the far end only when the service says "landed". */
    const val EDGE = 0.02

    /** Whole minutes from [now] until [then], rounded up: a minute that has begun is still to go. Negative once it has passed. */
    private fun until(now: Instant, then: Instant): Long = Math.floorDiv(Duration.between(now, then).seconds + 59, 60L)

    /**
     * Which phase [f] is in at [now]. What the service says, not the clock, decides whether it has
     * left and landed; only a timetable's flight goes by its plan, and once its time to leave has
     * passed nobody knows more. A flight the service still calls planned after its time to leave
     * ([overdue]) stays in its countdown, at the last minute: it is never a plan for a time that has
     * passed. A plan whose clocks may be out ([Flight.loose]) is never counted down to.
     */
    fun phase(f: Flight, now: Instant): Phase = when (f.state) {
        FlightState.CANCELED -> Phase.CANCELED
        FlightState.DIVERTED -> Phase.DIVERTED
        FlightState.LANDED -> Phase.LANDED
        FlightState.IN_AIR -> Phase.IN_AIR
        FlightState.PLANNED -> {
            val toGo = f.from.time?.let { until(now, f.from.moment(it)) }
            if (f.timetable && departed(f, now)) Phase.TIMETABLE
            else if (toGo != null && toGo <= SOON.toMinutes() && !f.loose) Phase.SOON
            else Phase.AHEAD
        }
    }

    /**
     * The service still calls [f] planned and its time to leave has passed: nobody has said that it
     * left. Every flight is here for some minutes, between its time and the next answer; one with no
     * connection stays. (A timetable's flight has a phase of its own for this, and one whose clocks
     * may be out has no moment to be past.)
     */
    fun overdue(f: Flight, now: Instant): Boolean = f.state == FlightState.PLANNED && !f.timetable && !f.loose && passed(f.from, now)

    /**
     * The headline: the one thing needed in that phase. When it leaves; within three hours of that,
     * how long until it does; in the air, how long until it lands (never less than a minute: it has
     * landed when the service says so); for three hours after, how long ago it landed.
     */
    fun headline(f: Flight, now: Instant): Headline = when (phase(f, now)) {
        Phase.CANCELED -> Headline(Heading.CANCELED)
        Phase.DIVERTED -> Headline(Heading.DIVERTED)
        Phase.TIMETABLE -> Headline(Heading.TIMETABLE)
        Phase.AHEAD -> Headline(Heading.LEAVES_AT)
        Phase.SOON -> f.from.time?.let { Headline(Heading.LEAVES_IN, until(now, f.from.moment(it)).coerceAtLeast(1)) } ?: Headline(Heading.LEAVES_AT)
        Phase.IN_AIR -> f.to.time?.takeUnless { f.loose }?.let { Headline(Heading.LANDS_IN, until(now, f.to.moment(it)).coerceAtLeast(1)) } ?: Headline(Heading.IN_AIR)
        Phase.LANDED -> {
            val ago = f.to.time?.takeUnless { f.loose }?.let { Duration.between(f.to.moment(it), now) }
            when {
                ago == null || ago > OVER -> Headline(Heading.LANDED)
                ago.seconds < 60 -> Headline(Heading.LANDED_NOW)
                else -> Headline(Heading.LANDED_AGO, ago.toMinutes())
            }
        }
    }

    /**
     * The badge: whether it runs to plan. The departure's delay until the flight has left, the
     * landing's from then on. Late, and early, start [ON_TIME] minutes from the plan. "On time" is a
     * claim, made only when the service has sent a time of its own: before leaving the plan alone is
     * "Planned", in the air and after landing it is no badge at all. None either where the headline
     * has said it all: canceled, diverted, a timetable's flight past its time, and a flight nobody
     * names a time to leave for (its headline can only say "Planned"). And none about a time that has
     * passed: once a flight is [overdue], "Planned" and "On time" are nobody's to say. A delay that
     * was known is still true then.
     */
    fun badge(f: Flight, now: Instant): Badge? = when (phase(f, now)) {
        Phase.CANCELED, Phase.DIVERTED, Phase.TIMETABLE -> null
        Phase.AHEAD, Phase.SOON -> f.from.late.let {
            if (f.from.time == null) null
            else if (it == null || f.timetable) Badge(Verdict.PLANNED).takeUnless { overdue(f, now) }
            else if (it >= ON_TIME) Badge(Verdict.DELAYED, it) else Badge(Verdict.ON_TIME).takeUnless { overdue(f, now) }
        }
        Phase.IN_AIR -> verdict(f.to.late, Verdict.DELAYED)
        Phase.LANDED -> verdict(f.to.late, Verdict.LATE)
    }

    private fun verdict(minutes: Int?, late: Verdict): Badge? = when {
        minutes == null -> null
        minutes >= ON_TIME -> Badge(late, minutes)
        minutes <= -ON_TIME -> Badge(Verdict.EARLY, -minutes)
        else -> Badge(Verdict.ON_TIME)
    }

    /**
     * How [f] stands at [now]: what its line in the bar is colored by. It goes by the [badge], so by
     * the departure until the flight has left and by the landing from then on: late from [ON_TIME]
     * minutes behind the plan, very late from [VERY_LATE]. Good news is the landing's alone: before a
     * flight leaves, "on time" is a plan like any other and claims nothing. Nothing is claimed either
     * where the badge has nothing to say (only the plan is known), nor of an answer that is over an
     * hour old ([stale]: what it said of late and early may be wrong by now), nor of a flight still in
     * the air when the time it was to land has passed: "on time" is nobody's to say then, as before it
     * leaves ([overdue]), while a delay that was known is still true. A flight that is canceled or
     * diverted will not arrive as planned, however old the answer.
     */
    fun stands(f: Flight, now: Instant, stale: Boolean): Stands {
        val phase = phase(f, now)
        if (phase == Phase.CANCELED || phase == Phase.DIVERTED) return Stands.WILL_NOT_ARRIVE
        val badge = badge(f, now).takeUnless { stale } ?: return Stands.NO_CLAIM
        return when (badge.verdict) {
            Verdict.PLANNED -> Stands.NO_CLAIM
            Verdict.ON_TIME, Verdict.EARLY -> when {
                phase == Phase.IN_AIR && passed(f.to, now) -> Stands.NO_CLAIM
                phase == Phase.IN_AIR || phase == Phase.LANDED -> Stands.GOOD
                else -> Stands.NO_CLAIM
            }
            Verdict.DELAYED, Verdict.LATE -> if (badge.minutes >= VERY_LATE) Stands.VERY_LATE else Stands.LATE
        }
    }

    /**
     * How much of the flight is behind it at [now], 0 to 1: where the plane stands on the line. From
     * time alone: the minutes since it left against the minutes from then to its landing (the actual
     * time, else the expected, else the plan), each as a moment, so time zones do not matter. Never
     * from the service's own percentage (it is a share of the planned duration, and starts some way
     * along) nor from distance. 0 until the service says it has left; in the air between [EDGE] and
     * 1 − [EDGE], however the clock stands; 1 once it has landed.
     *
     * Null where a plane on the line would claim what nobody knows: a flight that is canceled or
     * diverted, a timetable's flight past its time to leave, and one in the air with no time of
     * landing to reckon with.
     */
    fun share(f: Flight, now: Instant): Double? = when (phase(f, now)) {
        Phase.CANCELED, Phase.DIVERTED, Phase.TIMETABLE -> null
        Phase.AHEAD, Phase.SOON -> 0.0
        Phase.LANDED -> 1.0
        Phase.IN_AIR -> {
            val left = f.from.time?.let(f.from::moment)
            val lands = f.to.time?.let(f.to::moment)
            if (left == null || lands == null || !lands.isAfter(left) || f.loose) null
            else (Duration.between(left, now).seconds.toDouble() / Duration.between(left, lands).seconds).coerceIn(EDGE, 1 - EDGE)
        }
    }

    /**
     * The plane only goes forward. [shown]: where it was last drawn for this flight; [share]: where the
     * times put it now. A later estimate lengthens a flight, and its share drops: the plane then waits
     * where it is until the clock has caught up. Null (no plane) is taken as it comes.
     */
    fun forward(shown: Double?, share: Double?): Double? = if (shown == null || share == null) share else maxOf(shown, share)

    /**
     * What stands under the two ends of the line. Each airport with its time (what happened, else what
     * is expected, else the plan). Before it leaves: gate and terminal at the start, nothing at the far
     * end but its time. Once it has left: the aircraft at the start; at the far end the terminal and
     * the belt, or the gate while no belt is named. A flight that will not happen has its times struck
     * (a canceled one both, at their plan; a diverted one the landing's) and nothing else is said.
     */
    private fun ends(f: Flight, phase: Phase): Pair<EndSays, EndSays> = when (phase) {
        Phase.CANCELED -> EndSays(f.from.code, f.from.planned ?: f.from.time, struck = true) to EndSays(f.to.code, f.to.planned ?: f.to.time, struck = true)
        Phase.DIVERTED -> EndSays(f.from.code, f.from.time) to EndSays(f.to.code, f.to.time, struck = true)
        Phase.TIMETABLE -> EndSays(f.from.code, f.from.time) to EndSays(f.to.code, f.to.time)
        Phase.AHEAD, Phase.SOON -> EndSays(f.from.code, f.from.time, terminal = f.from.terminal, gate = f.from.gate) to EndSays(f.to.code, f.to.time)
        Phase.IN_AIR, Phase.LANDED ->
            EndSays(f.from.code, f.from.time, aircraft = f.aircraft) to EndSays(f.to.code, f.to.time, terminal = f.to.terminal, gate = f.to.gate.takeIf { f.to.belt == null }, belt = f.to.belt)
    }

    /** Everything that is shown of a flight at [now]: its phase, the headline, the badge, where the plane is and what stands under the line's ends. */
    fun row(f: Flight, now: Instant): Row {
        val phase = phase(f, now)
        val (from, to) = ends(f, phase)
        return Row(phase, headline(f, now), badge(f, now), share(f, now), from, to)
    }

    /** Where to go: before it leaves, the departure's terminal and gate; after, the arrival's terminal and belt. Nothing for a flight that will not happen. */
    fun where(f: Flight): Where = when {
        f.state == FlightState.CANCELED || f.state == FlightState.DIVERTED -> Where(null, null, null)
        f.left -> Where(f.to.terminal, null, f.to.belt)
        else -> Where(f.from.terminal, f.from.gate, null)
    }

    /**
     * How the day of [t], an airport's own time, is said: not at all on the user's [today]; a weekday
     * only within six days of it, where it can mean one day alone; further off (a flight at
     * Christmas, or a week from today, which has today's weekday) the date.
     */
    fun dayForm(t: LocalDateTime, today: LocalDate): DayForm = when (ChronoUnit.DAYS.between(today, t.toLocalDate())) {
        0L -> DayForm.NONE
        in -6L..6L -> DayForm.WEEKDAY
        else -> DayForm.DATE
    }

    /**
     * True when [f] is over for someone asking at [now]: it landed, or was to land, more than three
     * hours ago (a canceled one: was to leave). Then the next flight of that number is what was
     * asked for.
     */
    fun over(f: Flight, now: Instant): Boolean {
        val end = if (f.state == FlightState.CANCELED) f.from.planned?.let(f.from::moment) else f.to.time?.let(f.to::moment)
        return end != null && (f.state == FlightState.LANDED || f.state == FlightState.CANCELED || f.state == FlightState.DIVERTED) && Duration.between(end, now) > OVER
    }

    /**
     * Which of several flights of one number is "the next": the one in the air now; else one that
     * landed in the last three hours; else the next to leave, or the next that was to leave and is
     * canceled (the timetable would call that one planned). Null when none of them is any of these.
     */
    fun next(flights: List<Flight>, now: Instant): Flight? {
        fun leaves(f: Flight) = f.from.time?.let(f.from::moment)
        flights.filter { it.state == FlightState.IN_AIR }.maxByOrNull { leaves(it) ?: Instant.MIN }?.let { return it }
        flights.filter { it.state == FlightState.LANDED && !over(it, now) }.maxByOrNull { it.to.time?.let(it.to::moment) ?: Instant.MIN }?.let { return it }
        return flights.filter { (it.state == FlightState.PLANNED || it.state == FlightState.CANCELED) && (leaves(it) ?: Instant.MIN) > now.minus(OVER) }.minByOrNull { leaves(it) ?: Instant.MAX }
    }

    /**
     * Where a followed flight stands at [now] by the clock: the time to go until it leaves, then the
     * time to landing, then that it has landed. A flight that was to land more than three hours ago
     * has landed as far as a follower is concerned, whatever was last heard of it: one that slept
     * through the landing must not stand at "0 min". A timetable's flight goes by its plan alone.
     */
    fun standing(f: Flight, now: Instant): Standing {
        fun until(end: FlightEnd): Long = end.time?.let { (Duration.between(now, end.moment(it)).seconds + 59) / 60 }?.coerceAtLeast(0) ?: 0
        val landing = f.to.time?.let(f.to::moment)
        return when {
            f.state == FlightState.CANCELED -> Standing(Stage.CANCELED)
            arrived(f, now) || (landing != null && Duration.between(landing, now) > OVER) -> Standing(Stage.LANDED)
            departed(f, now) -> Standing(Stage.IN_AIR, until(f.to))
            else -> Standing(Stage.BEFORE, until(f.from))
        }
    }

    /** A followed flight is asked for again this often once it is near: from three hours before it leaves until it has landed. */
    val NEAR: Duration = Duration.ofMinutes(30)
    /** And this often while it is further off. */
    val FAR: Duration = Duration.ofHours(3)
    /** The service knows a flight's day about this long before it leaves (its `schedules` reach ten hours ahead): before that a timetable's plan cannot change. */
    val KNOWN: Duration = Duration.ofHours(10)

    /**
     * How long a followed flight waits between two asks at [now]; null when there is nothing to ask:
     * it has landed (or was to, more than three hours ago), it is canceled, or it is a plan from the
     * timetable more than ten hours off, which the service could only repeat.
     */
    fun pace(f: Flight, now: Instant): Duration? {
        val s = standing(f, now)
        return when (s.stage) {
            Stage.LANDED, Stage.CANCELED -> null
            Stage.IN_AIR -> NEAR
            Stage.BEFORE -> when {
                f.timetable && s.minutes > KNOWN.toMinutes() -> null
                s.minutes > FAR.toMinutes() -> FAR
                else -> NEAR
            }
        }
    }

    /** True if [got] is the flight [was] is, heard of again: it starts at the same airport on the same planned day. Another day's flight of the number is another flight. */
    fun same(was: Flight, got: Flight): Boolean =
        was.from.code == got.from.code && was.from.planned != null && was.from.planned.toLocalDate() == got.from.planned?.toLocalDate()

    // ---- what a bar item keeps of a flight, and when it asks again

    /**
     * What a Flight item follows and what was last heard of it: the one thing kept for it between two
     * answers and across a restart. It is the app's own model, written out field by field. A reply's
     * text is never kept (it repeats the key it was asked with), and nothing here has a place for one.
     */
    @Serializable
    data class Tracked(
        /** The number as it was entered, closed up (LH455, or the callsign DLH455); empty: the item follows nothing. */
        val number: String = "",
        /** The day that flight leaves, at its own airport (2026-10-01): an item follows one flight, never the next day's of its number. */
        val day: String? = null,
        /** The flight as last heard; null: asked for and not answered yet. */
        val flight: Flight? = null,
        /** Why the last ask brought nothing new; null: it did. */
        val failure: AirLabs.Failure? = null,
        /** How many asks in a row came to nothing, for the wait before the next. */
        val failures: Int = 0,
        /** When the service was last asked, whatever came of it: wall clock, milliseconds. */
        val askedAt: Long = 0,
        /** When it last answered about this flight: what "updated 9:40 AM" says, and what "not live" is measured from. */
        val heardAt: Long = 0,
        /** Lookups the key has left this month, as last said. */
        val left: Int? = null,
        /** When a cancellation or a diversion was first seen: the alert lasts an hour from then. */
        val alertSince: Long? = null,
        /** The service has gone on to another flight of the number, or knows this one no more: asking has ended. */
        val ended: Boolean = false,
        /** How long a "slow down" asked to be left alone, in seconds. */
        val waitSec: Long? = null,
        /**
         * The airport that flight leaves from (SFO). A number can fly more than once on one day, from
         * one airport after another, and an item follows one of those flights: with [number] and [day]
         * this says which. Null: nothing is followed, or it was kept before there was such a choice.
         */
        val from: String? = null,
    ) {
        val following: Boolean get() = number.isNotEmpty()
        /** Whose flight it is stays out of anything that prints a value. */
        override fun toString(): String = if (following) "Tracked(a flight)" else "Tracked(nothing)"
    }

    /** After an ask that came to nothing the next comes this much later, and twice as late each time after that, up to [RETRY_MOST]. */
    val RETRY: Duration = Duration.ofMinutes(2)
    val RETRY_MOST: Duration = Duration.ofMinutes(30)
    /** With fewer lookups left than this nothing is asked unasked: what is left is the user's to spend. */
    const val FEW = 20
    /** The quicker pace around take-off and landing needs more lookups left than this. */
    const val PLENTY = 100
    /** That pace: in the last hour before it leaves and the last half hour before it lands. */
    val QUICK: Duration = Duration.ofMinutes(10)
    private val QUICK_BEFORE_LEAVING: Duration = Duration.ofHours(1)
    private val QUICK_BEFORE_LANDING: Duration = Duration.ofMinutes(30)
    /** And for this long past either time, while nobody says that it happened. */
    private val QUICK_PAST: Duration = Duration.ofHours(1)
    /** A flight is put away this long after it landed, or was to land. */
    val CLEARED: Duration = Duration.ofHours(24)
    /** For this long after it landed a flight is in the bar; then the plain plane again, while the menu keeps the flight. */
    val LANDED_SHOWN: Duration = Duration.ofHours(1)
    /** How long the alert of a cancellation or a diversion lasts from when it was first seen. */
    val ALERT: Duration = Duration.ofHours(1)
    /** An answer older than this is not live any more: late, early and the gate are what it can have made wrong. */
    val STALE: Duration = Duration.ofHours(1)

    /** How long to wait after [failures] asks in a row that came to nothing: 2 minutes, 4, 8, 16, then 30. */
    fun retry(failures: Int): Duration = minOf(RETRY.multipliedBy(1L shl (failures - 1).coerceIn(0, 4)), RETRY_MOST)

    /**
     * [pace], and quicker around the two moments that matter: every ten minutes in the last hour before
     * the flight leaves and in the last half hour before it lands. And for an hour past such a time
     * that came with no word (still "planned" after its time to leave, still in the air after its
     * time to land): that is when someone watches most, and the bar stands at its last minute until
     * an answer comes. An answer with a new estimate puts the time ahead again; after the hour it is
     * the usual pace. Only for a flight the service knows, with clocks that can be read (a timetable's
     * times are nobody's word), and only while the key has lookups to spare ([left], where the reply
     * said so).
     */
    fun pace(f: Flight, now: Instant, left: Int?): Duration? {
        val usual = pace(f, now) ?: return null
        if (f.timetable || f.loose || usual != NEAR || left == null || left <= PLENTY) return usual
        val (end, window) = when (standing(f, now).stage) {
            Stage.BEFORE -> f.from to QUICK_BEFORE_LEAVING
            Stage.IN_AIR -> f.to to QUICK_BEFORE_LANDING
            else -> return usual
        }
        val toGo = end.time?.let { Duration.between(now, end.moment(it)) } ?: return usual
        return if (toGo <= window && toGo >= QUICK_PAST.negated()) QUICK else usual
    }

    /**
     * How long what is known of a followed flight stays fresh, in milliseconds since the service was
     * last asked; null: it is not asked again unasked. That is: every three hours until three hours
     * before the flight leaves, then every half hour until it has landed ([pace]); nothing for a plan
     * from the timetable more than ten hours off, nothing after it landed or was canceled, nothing once
     * the service has gone on to another flight, nothing while the key is refused or used up, and
     * nothing with fewer than [FEW] lookups left. After an ask that came to nothing: [retry]. A flight
     * that is [cleared] is due at once: the load that follows only puts it away.
     *
     * One ask more than [pace] has: a flight that is taken for landed by the clock alone, and was last
     * heard of before it was to land (the lid was closed through the landing), is asked about once on
     * waking. The service may know what the clock cannot: that it landed late, or was diverted.
     */
    fun every(t: Tracked, now: Instant): Long? {
        if (!t.following) return null
        if (cleared(t, now)) return 0
        val failed = t.failure == AirLabs.Failure.OFFLINE || t.failure == AirLabs.Failure.NO_ANSWER
        if (t.ended || (t.failure != null && !failed)) return null
        if (t.left != null && t.left < FEW) return null
        val usual = when {
            // Asked for and never answered (the switch came back on without a connection): there is only the trying again.
            t.flight == null -> NEAR
            else -> pace(t.flight, now, t.left) ?: if (unheard(t, t.flight, now)) NEAR else return null
        }
        return (if (failed) maxOf(retry(t.failures), Duration.ofSeconds((t.waitSec ?: 0).coerceIn(0, 86_400))) else usual).toMillis()
    }

    /** [f] is landed only by [shown]'s reckoning, and nothing was heard of it since it was to land. */
    private fun unheard(t: Tracked, f: Flight, now: Instant): Boolean {
        val landing = f.to.time?.let(f.to::moment) ?: return false
        return shown(f, now) !== f && Instant.ofEpochMilli(t.heardAt).isBefore(landing)
    }

    /** How long after an ask the next one by hand has to wait: two minutes, ten seconds after a try that found no connection, and never less than a "slow down" asked for. */
    fun byHand(t: Tracked): Long = maxOf(AirLabs.keep(t.failure).toMillis(), (t.waitSec ?: 0).coerceIn(0, 86_400) * 1000)

    /**
     * Whether a new key is what [t] waits for. The old one was refused or used up, which the menu says
     * until the service is asked again; or it had so few lookups left that a flight which had its turn
     * was not asked about. Saving a key asks about such a flight at once. One that is followed as
     * planned keeps its turn, new key or not, and one that is over is left alone: a key is no reason
     * to ask.
     */
    fun waitsForKey(t: Tracked, now: Instant): Boolean = when {
        !t.following || t.ended -> false
        t.failure == AirLabs.Failure.REFUSED || t.failure == AirLabs.Failure.USED_UP -> true
        else -> t.left != null && t.left < FEW && every(t.copy(left = null), now) != null
    }

    /** When [f] is put away: [CLEARED] after it landed or was to land; for a flight nobody names a landing for, after it left or was to leave. */
    private fun clearedAt(f: Flight): Instant? = (f.to.time?.let(f.to::moment) ?: f.from.time?.let(f.from::moment))?.plus(CLEARED)

    /** The flight [t] holds is over and done: it is shown no more, and the next load removes what is kept of it. */
    fun cleared(t: Tracked, now: Instant): Boolean {
        val f = t.flight ?: return false
        return !now.isBefore(clearedAt(f) ?: Instant.ofEpochMilli(t.heardAt).plus(CLEARED))
    }

    /**
     * [f] as it is taken at [now]. A flight that was to land more than three hours ago, and of which
     * nobody said that it did, has landed, whatever was last heard of it: a bar that slept through the
     * landing must not count down to nothing. A timetable's flight is left as it is (nobody knows what
     * became of it), and so is one the service called canceled or diverted.
     *
     * [ended]: the asking has ended for it (the service has gone on to another flight of the number),
     * so nobody will ever say that it landed. Its time to land says so then, at once: it must not
     * stand at "1 min" for the three hours a flight that is still asked about is given.
     */
    fun shown(f: Flight, now: Instant, ended: Boolean = false): Flight =
        if (!f.timetable && (f.state == FlightState.PLANNED || f.state == FlightState.IN_AIR) &&
            (standing(f, now).stage == Stage.LANDED || (ended && passed(f.to, now)))) f.copy(state = FlightState.LANDED)
        else f

    /** When a cancellation or a diversion was first seen: [since] if [f] was already known as one, else [now]; null for a flight that goes its way. */
    fun alertSince(f: Flight, since: Long?, now: Long): Long? =
        if (f.state == FlightState.CANCELED || f.state == FlightState.DIVERTED) since ?: now else null

    /** A cancellation or a diversion is news for [ALERT] from when it was first seen; after that the words stay and the alert goes. */
    fun alert(t: Tracked, now: Instant): Boolean {
        val f = t.flight ?: return false
        val since = alertSince(f, t.alertSince, t.heardAt) ?: return false
        return Duration.between(Instant.ofEpochMilli(since), now).let { !it.isNegative && it < ALERT }
    }

    /** The last answer about [t]'s flight is more than [STALE] old. */
    fun stale(t: Tracked, now: Instant): Boolean = t.heardAt > 0 && Duration.between(Instant.ofEpochMilli(t.heardAt), now) > STALE

    /**
     * Nobody knows what became of [t]'s flight: it is [overdue], and the last answer is more than
     * [STALE] old. Until then it is about to leave, for all anyone knows; from then on the item says
     * "no update", as it does for a timetable's flight past its time.
     */
    fun silent(t: Tracked, now: Instant): Boolean = t.flight?.let { overdue(shown(it, now, t.ended), now) } == true && stale(t, now)

    /**
     * True if nothing in [f] can trip the arithmetic above: every time in a century this app can be
     * run in, every clock within a day of UTC. The reader lets nothing else through; this is for what
     * comes back from a file.
     */
    fun sound(f: Flight): Boolean = listOf(f.from, f.to).all { e ->
        e.offset in -MOST_OFFSET..MOST_OFFSET && listOfNotNull(e.planned, e.expected, e.actual).all { it.year in YEARS }
    }

    /** No clock on earth is further from UTC than fourteen hours: anything else is not an airport's time. */
    const val MOST_OFFSET = 14 * 60
    val YEARS = 2000..2100
}
