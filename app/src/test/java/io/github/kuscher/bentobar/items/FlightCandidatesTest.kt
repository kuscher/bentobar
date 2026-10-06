package io.github.kuscher.bentobar.items

import io.github.kuscher.bentobar.items.AirLabs.Failure
import io.github.kuscher.bentobar.net.Reply
import io.github.kuscher.bentobar.net.Request
import io.github.kuscher.bentobar.net.Why
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * A number that flies more than once a day. UA 1227 goes from Orlando to Newark in the morning, from
 * Newark to San Francisco after it and from San Francisco to Portland in the evening, every day, and
 * from Raleigh to Newark on Mondays. The three replies for it in `resources/airlabs` are what the
 * service answered on 6 October 2026 at 05:48 UTC, which is 10:48 PM on Monday the 5th in Los
 * Angeles, without their `request` object (it repeats the key and names the caller's address).
 */
class FlightCandidatesTest {
    private fun reply(name: String): String = javaClass.getResource("/airlabs/$name.json")!!.readText()
    private fun at(text: String): Instant = Instant.parse(text)
    private fun time(text: String): LocalDateTime = LocalDateTime.parse(text)
    private val flight = reply("flight-UA1227-first-leg-planned")
    private val table = reply("routes-UA1227-three-legs-a-day")
    private val soon = reply("schedules-UA1227")
    /** When the replies were asked for. */
    private val evening = at("2026-10-06T05:48:00Z")
    private val ua1227 = FlightNumber("UA", 1227)
    private val monday = LocalDate.of(2026, 10, 5)
    private val tuesday = LocalDate.of(2026, 10, 6)

    /** A service that answers each of its three questions with the text given (none: no connection), and remembers what it was asked. */
    private class Service(private val replies: Map<String, String>) {
        val asked = ArrayList<String>()
        fun get(request: Request): Reply {
            val what = request.path.substringAfterLast('/')
            asked.add(what)
            return replies[what]?.let { Reply.Ok(it) } ?: Reply.Failed(Why.OFFLINE)
        }
    }

    private fun service(vararg replies: Pair<String, String>) = Service(mapOf(*replies))
    /** The service as it answered that evening: the morning's flight, whatever is asked about the number, and its timetable. */
    private fun thatEvening() = service("flight" to flight, "routes" to table, "schedules" to soon)
    private val unknown = reply("error-not-found")
    private val none = """{"response":[]}"""
    private val la = ZoneId.of("America/Los_Angeles")
    private val tokyo = ZoneId.of("Asia/Tokyo")

    /** What a press of Track comes to: for a day chip, a day of the device's in [zone]; with no day, the next flight. */
    private fun press(s: Service, day: LocalDate? = null, zone: ZoneId = la, now: Instant = evening, n: FlightNumber = ua1227): AirLabs.Candidates =
        AirLabs.candidates(n, day, "k", now, zone.takeIf { day != null }, s::get)!!

    /** Each flight as its two airports and the time it leaves, at its own airport. */
    private fun legs(c: AirLabs.Candidates) = c.flights.map { "${it.from.code}-${it.to.code} ${it.from.time}" }

    /** The evening's flight from San Francisco, which had landed, as the answer to the one-flight question: the coming hours' row for it, which has no names. */
    private val landed = "{\"response\":" + Json.parseToJsonElement(soon).jsonObject.getValue("response").jsonArray[1] + "}"

    // ---- the replies, read

    @Test fun theTimetableHasALineForEachFlightOfTheNumber() {
        val lines = AirLabs.routes(table).value!!
        assertEquals(listOf("EWR" to "SFO", "MCO" to "EWR", "RDU" to "EWR", "SFO" to "PDX"), lines.map { it.from to it.to })
        assertEquals(listOf(LocalTime.of(13, 20), LocalTime.of(8, 45), LocalTime.of(9, 10), LocalTime.of(19, 5)), lines.map { it.leaves })
        assertEquals(listOf(359, 161, 157, 115), lines.map { it.minutes })
        // Three of them every day, the one from Raleigh on Mondays.
        val daily = DayOfWeek.entries.toSet()
        assertEquals(listOf(daily, daily, setOf(DayOfWeek.MONDAY), daily), lines.map { it.days })
        // The East Coast four hours behind UTC, the West Coast seven: the evening flight's UTC twin is on the day after, which is read right.
        assertEquals(listOf(-240, -240, -240, -420), lines.map { it.fromOffset })
        assertEquals(listOf(-420, -240, -240, -420), lines.map { it.toOffset })
        // A terminal is said only where one is named, not two.
        assertEquals(listOf(null, "B", "2", null), lines.map { it.fromTerminal })
        assertTrue(lines.all { it.toTerminal == null && it.callsign == "UAL1227" })
    }

    @Test fun theServicesNearestFlightIsTheMorningsWhichHasNotLeftYet() {
        val f = AirLabs.flight(flight).value!!
        assertEquals("UA1227", f.number)
        assertEquals("United Airlines", f.airline)
        assertEquals(FlightState.PLANNED, f.state)
        assertEquals(FlightEnd("MCO", "Orlando", time("2026-10-06T08:45"), offset = -240, terminal = "B", gate = "48"), f.from)
        assertEquals(FlightEnd("EWR", "Newark", time("2026-10-06T11:26"), offset = -240, terminal = "A", gate = "20", belt = "3"), f.to)
        // Seven hours off when it was asked for.
        assertEquals(at("2026-10-06T12:45:00Z"), f.from.moment(f.from.planned!!))
        assertTrue(!f.loose && !f.timetable)
        assertNull(f.flownAs)
    }

    @Test fun theComingHoursHaveThatFlightAndTheEveningsThatLandedTwoHoursAgo() {
        val rows = AirLabs.schedules(soon).value!!
        assertEquals(listOf("MCO" to FlightState.PLANNED, "SFO" to FlightState.LANDED), rows.map { it.from.code to it.state })
        // No cities and no airline in this reply; the gate and the belt are there.
        assertEquals(listOf("", ""), rows.map { it.from.city })
        assertEquals("48", rows[0].from.gate)
        assertEquals(time("2026-10-05T20:50"), rows[1].to.actual)
        assertEquals("9", rows[1].to.belt)
        // The one that landed is still "the next" for an hour more, and over after that.
        assertTrue(!FlightRules.over(rows[1], evening))
        assertTrue(FlightRules.over(rows[1], at("2026-10-06T06:51:00Z")))
        assertTrue(rows.none { it.loose })
    }

    // ---- a flight that is followed is looked up again by its airport

    @Test fun withAnAirportNamedTheLookupLandsOnTheFlightThatLeavesFromIt() {
        // San Francisco on the 6th. The service answers with the morning's flight from Orlando: not that one, so the
        // timetable's line from San Francisco, and no other.
        val s = thatEvening()
        val third = AirLabs.lookup(ua1227, tuesday, "SFO", "k", evening, s::get)!!.flight!!
        assertEquals(listOf("flight", "routes"), s.asked)
        assertEquals("SFO" to "PDX", third.from.code to third.to.code)
        assertEquals(time("2026-10-06T19:05"), third.from.planned)
        assertEquals(time("2026-10-06T21:00"), third.to.planned)
        assertTrue(third.timetable)
        assertEquals("United Airlines", third.airline)                 // the names are the ones the service gave for the number
        // Newark: the second, whose airport the service named as the first one's arrival.
        val second = AirLabs.lookup(ua1227, tuesday, "EWR", "k", evening, thatEvening()::get)!!.flight!!
        assertEquals("EWR" to "SFO", second.from.code to second.to.code)
        assertEquals(time("2026-10-06T13:20"), second.from.planned)
        assertEquals(time("2026-10-06T16:19"), second.to.planned)      // 359 minutes later, on San Francisco's clock
        assertEquals("Newark" to "SFO", second.from.place to second.to.place)
        // Orlando: the service's own flight, with its gate, and one request.
        val o = thatEvening()
        val first = AirLabs.lookup(ua1227, tuesday, "MCO", "k", evening, o::get)!!.flight!!
        assertEquals(listOf("flight"), o.asked)
        assertFalse(first.timetable)
        assertEquals("48", first.from.gate)
        // With no airport named it is as it was before there was a choice: the service's flight, if that is its day.
        assertEquals("MCO", AirLabs.lookup(ua1227, tuesday, null, "k", evening, thatEvening()::get)!!.flight!!.from.code)
        assertEquals("MCO", AirLabs.lookup(ua1227, tuesday, "k", evening, get = thatEvening()::get)!!.flight!!.from.code)
    }

    @Test fun anAirportTheNumberDoesNotLeaveOnThatDayIsNoFlight() {
        // Raleigh on a Tuesday: its line flies on Mondays. And an airport the number never leaves.
        assertEquals(Failure.NOT_THAT_DAY, AirLabs.lookup(ua1227, tuesday, "RDU", "k", evening, thatEvening()::get)!!.failure)
        assertEquals(Failure.NOT_THAT_DAY, AirLabs.lookup(ua1227, tuesday, "LAX", "k", evening, thatEvening()::get)!!.failure)
        val raleigh = AirLabs.lookup(ua1227, monday, "RDU", "k", evening, thatEvening()::get)!!.flight!!
        assertEquals(time("2026-10-05T09:10"), raleigh.from.planned)
        assertEquals("RDU" to "Newark", raleigh.from.place to raleigh.to.place)
        // The timetable is not to be had: that is what went wrong, and the service's flight from another airport is no answer.
        val cut = service("flight" to flight)
        val a = AirLabs.lookup(ua1227, tuesday, "SFO", "k", evening, cut::get)!!
        assertNull(a.flight)
        assertEquals(Failure.OFFLINE, a.failure)
        assertEquals(listOf("flight", "routes"), cut.asked)
        // A number the service's one-flight question does not know: the timetable's line from that airport, with no names.
        val bare = AirLabs.lookup(ua1227, tuesday, "SFO", "k", evening, service("flight" to unknown, "routes" to table)::get)!!.flight!!
        assertEquals("SFO" to "PDX", bare.from.place to bare.to.place)
        assertEquals("", bare.airline)
        assertEquals(Failure.NOT_FOUND, AirLabs.lookup(ua1227, tuesday, "LAX", "k", evening, service("flight" to unknown, "routes" to table)::get)!!.failure)
    }

    @Test fun withAnAirportAndNoDayItIsThatAirportsNextFlight() {
        // (What is kept of a flight always has its day, unless the service named no time for it to leave.)
        // From Orlando: the service's flight, which has not left. One request.
        val o = thatEvening()
        assertEquals("48", AirLabs.lookup(ua1227, null, "MCO", "k", evening, o::get)!!.flight!!.from.gate)
        assertEquals(listOf("flight"), o.asked)
        // From San Francisco: not that flight. The coming hours have the one that landed there two hours ago, which is still "the next".
        val s = thatEvening()
        val landed = AirLabs.lookup(ua1227, null, "SFO", "k", evening, s::get)!!.flight!!
        assertEquals(listOf("flight", "schedules"), s.asked)
        assertEquals(FlightState.LANDED, landed.state)
        assertEquals("SFO" to "PDX", landed.from.code to landed.to.code)
        // An hour later that one is over: the timetable's next from San Francisco, this evening's.
        val later = thatEvening()
        val next = AirLabs.lookup(ua1227, null, "SFO", "k", at("2026-10-06T07:00:00Z"), later::get)!!.flight!!
        assertEquals(listOf("flight", "schedules", "routes"), later.asked)
        assertTrue(next.timetable)
        assertEquals(time("2026-10-06T19:05"), next.from.planned)
        // From Newark, where nothing of the coming hours leaves: the timetable's too.
        assertEquals(time("2026-10-06T13:20"), AirLabs.lookup(ua1227, null, "EWR", "k", evening, thatEvening()::get)!!.flight!!.from.planned)
        // An airport it never leaves: nothing, and not the flight from Orlando "as it was".
        val none = AirLabs.lookup(ua1227, null, "LAX", "k", evening, thatEvening()::get)!!
        assertNull(none.flight)
        assertEquals(Failure.NOT_FOUND, none.failure)
    }

    // ---- the flights a press of Track can mean

    @Test fun tomorrowSeenFromLosAngelesIsItsThreeFlightsInTheOrderTheyLeave() {
        val s = thatEvening()
        val c = press(s, tuesday)
        assertEquals(listOf("flight", "routes"), s.asked)
        assertNull(c.failure)
        // Not the one from Raleigh, which flies on Mondays.
        assertEquals(listOf("MCO-EWR 2026-10-06T08:45", "EWR-SFO 2026-10-06T13:20", "SFO-PDX 2026-10-06T19:05"), legs(c))
        assertEquals(listOf("2026-10-06T11:26", "2026-10-06T16:19", "2026-10-06T21:00"), c.flights.map { it.to.time.toString() })
        // The first is the service's own flight, as it said it; the two after it are the timetable's, a plan each.
        assertEquals(AirLabs.flight(flight).value, c.flights[0])
        assertEquals(listOf(false, true, true), c.flights.map { it.timetable })
        assertTrue(c.flights.drop(1).all { it.state == FlightState.PLANNED && it.from.gate == null && !it.loose })
        // They are said with the names the service gave: the airline for all of them, a city where it named that airport.
        assertEquals(listOf("Orlando" to "Newark", "Newark" to "SFO", "SFO" to "PDX"), c.flights.map { it.from.place to it.to.place })
        assertTrue(c.flights.all { it.number == "UA1227" && it.airline == "United Airlines" && it.callsign == "UAL1227" })
    }

    @Test fun onAMondayThereAreFour() {
        // "Today", that Monday evening in Los Angeles: all four have left, and all four are that day's.
        val c = press(thatEvening(), monday)
        assertEquals(listOf("MCO-EWR 2026-10-05T08:45", "RDU-EWR 2026-10-05T09:10", "EWR-SFO 2026-10-05T13:20", "SFO-PDX 2026-10-05T19:05"), legs(c))
        // The service's flight is Tuesday's: none of these is it.
        assertTrue(c.flights.all { it.timetable })
        assertEquals(listOf("Orlando" to "Newark", "RDU" to "Newark", "Newark" to "SFO", "SFO" to "PDX"), c.flights.map { it.from.place to it.to.place })
    }

    @Test fun theNextFlightIsAskedAboutAmongTheOnesThatLeaveWithinADay() {
        val s = thatEvening()
        val c = press(s)
        assertEquals(listOf("flight", "routes"), s.asked)
        // Seven, eleven and a half and twenty hours off. The one from Raleigh leaves in six days.
        assertEquals(listOf("MCO-EWR 2026-10-06T08:45", "EWR-SFO 2026-10-06T13:20", "SFO-PDX 2026-10-06T19:05"), legs(c))
        // The first is the flight the service answered with, as itself: it has not left yet.
        assertEquals(AirLabs.flight(flight).value, c.flights[0])
        assertEquals(listOf(false, true, true), c.flights.map { it.timetable })
        assertEquals(Duration.ofHours(24), AirLabs.DAY_AHEAD)
    }

    @Test fun aLineThatLeavesInExactlyADayIsOfferedAndOneAMinuteLaterIsNot() {
        // Only the timetable knows the number here. The Monday flight from Raleigh leaves on the 12th at 13:10 UTC.
        fun count(now: String) = press(service("flight" to unknown, "routes" to table), now = at(now)).flights.size
        assertEquals(4, count("2026-10-11T13:10:00Z"))
        assertEquals(3, count("2026-10-11T13:09:00Z"))
        // A flight that leaves this very minute is not one that is still to leave.
        assertEquals(listOf("EWR-SFO 2026-10-11T13:20", "SFO-PDX 2026-10-11T19:05", "MCO-EWR 2026-10-12T08:45"),
            legs(press(service("flight" to unknown, "routes" to table), now = at("2026-10-11T12:45:00Z"))))
    }

    @Test fun fromADeviceInTokyoTheDayIsTokyosAndTheFlightsAreTheOnesThatLeaveOnIt() {
        // 2:48 PM on Tuesday there. Its Tuesday began at 15:00 UTC on Monday: two of the flights that leave on it
        // leave on Monday by their own airports' clocks, and the morning's from Orlando is the service's own.
        val today = press(thatEvening(), tuesday, tokyo)
        assertEquals(listOf("EWR-SFO 2026-10-05T13:20", "SFO-PDX 2026-10-05T19:05", "MCO-EWR 2026-10-06T08:45"), legs(today))
        assertEquals(listOf(true, true, false), today.flights.map { it.timetable })
        for (f in today.flights) assertEquals(tuesday, LocalDate.ofInstant(f.from.moment(f.from.time!!), tokyo))
        // Its Wednesday: the two later ones of the airports' Tuesday, and Wednesday's first.
        val tomorrow = press(thatEvening(), tuesday.plusDays(1), tokyo)
        assertEquals(listOf("EWR-SFO 2026-10-06T13:20", "SFO-PDX 2026-10-06T19:05", "MCO-EWR 2026-10-07T08:45"), legs(tomorrow))
        assertTrue(tomorrow.flights.all { it.timetable })
        // The same Tuesday asked for in Los Angeles is other flights.
        assertEquals(listOf("MCO-EWR 2026-10-06T08:45", "EWR-SFO 2026-10-06T13:20", "SFO-PDX 2026-10-06T19:05"), legs(press(thatEvening(), tuesday, la)))
    }

    @Test fun theServicesOwnFlightStandsInItsLinesPlaceAndTakesFromThePlanOnlyWhatItLacks() {
        // The service's flight as it was said has everything: it is offered as it is, and the timetable's plan for it is not.
        val whole = press(thatEvening(), tuesday).flights
        assertEquals(1, whole.count { it.from.code == "MCO" })
        assertEquals("48", whole[0].from.gate)
        // The same flight with no time of landing and no terminal to leave from: the plan has both. What the service said stays.
        val bare = flight.replace("\"dep_terminal\": \"B\"", "\"dep_terminal\": null")
            .replace(Regex("\"arr_time(_utc)?\": \"[^\"]*\""), "\"arr_time$1\": null")
        val said = AirLabs.flight(bare).value!!
        assertNull(said.from.terminal); assertNull(said.to.time)
        val first = press(service("flight" to bare, "routes" to table), tuesday).flights[0]
        assertFalse(first.timetable)
        assertEquals("B", first.from.terminal)
        assertEquals(time("2026-10-06T11:26"), first.to.planned)
        assertEquals(at("2026-10-06T15:26:00Z"), first.to.moment(first.to.planned!!))
        assertEquals(said.copy(from = said.from.copy(terminal = "B"), to = said.to.copy(planned = time("2026-10-06T11:26"), offset = -240)), first)
        assertEquals(listOf("48", "A", "20", "3"), listOf(first.from.gate, first.to.terminal, first.to.gate, first.to.belt))
    }

    @Test fun aFlightTheTimetableHasNoLineForIsAmongThemAllTheSame() {
        // The timetable without its line from Orlando: the service's flight from there leaves on that day, and is one of the day's.
        val rows = Json.parseToJsonElement(table).jsonObject.getValue("response").jsonArray
        val without = """{"response":[${rows[0]},${rows[2]},${rows[3]}]}"""
        assertEquals(3, AirLabs.routes(without).value!!.size)
        for (day in listOf(tuesday, null)) {
            val c = press(service("flight" to flight, "routes" to without), day)
            assertEquals("$day", listOf("MCO-EWR 2026-10-06T08:45", "EWR-SFO 2026-10-06T13:20", "SFO-PDX 2026-10-06T19:05"), legs(c))
            assertEquals("$day", AirLabs.flight(flight).value, c.flights[0])
        }
    }

    @Test fun hoursAfterAFlightLandedTheComingHoursFlightIsFirstAsItself() {
        // An hour later the one-flight question answers with the evening's flight, which landed over three hours ago:
        // the next is among the coming hours' flights, and the timetable is asked for besides. Three requests, the most there are.
        val s = service("flight" to landed, "schedules" to soon, "routes" to table)
        val c = press(s, now = at("2026-10-06T07:00:00Z"))
        assertEquals(listOf("flight", "schedules", "routes"), s.asked)
        assertEquals(listOf("MCO-EWR 2026-10-06T08:45", "EWR-SFO 2026-10-06T13:20", "SFO-PDX 2026-10-06T19:05"), legs(c))
        assertEquals(listOf(false, true, true), c.flights.map { it.timetable })
        assertEquals(listOf("48", "3"), listOf(c.flights[0].from.gate, c.flights[0].to.belt))
        // The flight that landed is none of them: the one from San Francisco is this evening's.
        assertTrue(c.flights.none { it.state == FlightState.LANDED })
        // A coming hours' flight stands in its line's place too where it is not the first: here the one from Newark has a gate already.
        val both = soon.replaceFirst("\"response\": [", """"response": [{"flight_iata":"UA1227","flight_icao":"UAL1227","dep_iata":"EWR","arr_iata":"SFO","status":"scheduled",
            "dep_gate":"C92","dep_time":"2026-10-06 13:20","dep_time_utc":"2026-10-06 17:20","arr_time":"2026-10-06 16:19","arr_time_utc":"2026-10-06 23:19"},""")
        assertEquals(3, AirLabs.schedules(both).value!!.size)
        val d = press(service("flight" to landed, "schedules" to both, "routes" to table), now = at("2026-10-06T07:00:00Z"))
        assertEquals(legs(c), legs(d))
        assertEquals(listOf(false, false, true), d.flights.map { it.timetable })
        assertEquals("C92", d.flights[1].from.gate)
    }

    @Test fun aFlightThatLandedIsNotOneOfTheNextButStaysTheAnswerWhereThereIsNothingToChoose() {
        // Two hours after the evening's flight landed the service still answers with it: "the next" for three hours, where
        // nothing is asked. Here there are three that leave within a day, and it is not among them.
        val s = service("flight" to landed, "routes" to table)
        val c = press(s)
        assertEquals(listOf("flight", "routes"), s.asked)
        assertEquals(listOf("MCO-EWR 2026-10-06T08:45", "EWR-SFO 2026-10-06T13:20", "SFO-PDX 2026-10-06T19:05"), legs(c))
        assertTrue(c.flights.all { it.timetable })
        // A number that flies once a day: twenty minutes after it landed the one flight of the next day is no choice, and the answer is the one that landed.
        val lh455 = service("flight" to reply("flight-LH455-landed"), "routes" to reply("routes-LH455"))
        val one = press(lh455, now = at("2026-10-02T08:16:00Z"), n = FlightNumber("LH", 455)).flights.single()
        assertEquals(FlightState.LANDED, one.state)
        assertEquals(listOf("flight", "routes"), lh455.asked)
    }

    @Test fun aFlightThatWasCanceledIsFirstAmongTheNextAsItHasNotLeft() {
        val canceled = flight.replace("\"scheduled\"", "\"cancelled\"")
        val c = press(service("flight" to canceled, "routes" to table))
        assertEquals(listOf("MCO-EWR 2026-10-06T08:45", "EWR-SFO 2026-10-06T13:20", "SFO-PDX 2026-10-06T19:05"), legs(c))
        assertEquals(FlightState.CANCELED, c.flights[0].state)
        // An hour after it was to leave it is still the service's answer, and still first: whoever asks should hear of it.
        // The timetable would offer tomorrow's from Orlando in its place, and call it planned.
        val after = press(service("flight" to canceled, "routes" to table), now = at("2026-10-06T13:45:00Z"))
        assertEquals(legs(c), legs(after))
        assertEquals(FlightState.CANCELED, after.flights[0].state)
    }

    // ---- one flight is no question

    @Test fun oneFlightThatDayIsTheAnswerAsItAlwaysWas() {
        val lh455 = FlightNumber("LH", 455)
        val asked = at("2026-10-02T07:29:00Z")
        fun both() = service("flight" to reply("flight-LH455-in-the-air"), "routes" to reply("routes-LH455"))
        // The next flight, a day chip that is its day, and a day chip that is the timetable's: one each, and the one the lookup finds.
        for (day in listOf(null, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3))) {
            val s = both()
            val c = press(s, day, la, asked, lh455)
            val one = AirLabs.lookup(lh455, day, "k", asked, la.takeIf { day != null }, both()::get)!!.flight
            assertEquals("$day", listOf(one), c.flights)
            assertNull(c.failure)
            // The timetable is asked for in every case: it is what says that there is nothing to choose.
            assertEquals("$day", listOf("flight", "routes"), s.asked)
        }
        assertEquals(FlightState.IN_AIR, press(both(), null, la, asked, lh455).flights.single().state)
        assertTrue(press(both(), LocalDate.of(2026, 10, 3), la, asked, lh455).flights.single().timetable)
    }

    @Test fun aNumberThatFliesOnceADayAsksNothingWhileItsFlightIsInTheAirOrLate() {
        // LH 455 is in the air, and tomorrow's leaves in fourteen hours: within a day, and still no second flight to choose.
        // The flight the service answers with stands for its line.
        val air = press(service("flight" to reply("flight-LH455-in-the-air"), "routes" to reply("routes-LH455")), now = at("2026-10-02T07:29:00Z"), n = FlightNumber("LH", 455))
        assertEquals(listOf("SFO-FRA 2026-10-01T14:47"), legs(air))
        // LH 454 was to leave Frankfurt a quarter of an hour ago and has not: the timetable's next is tomorrow's, and it is no choice either.
        val daily = """{"response":[{"flight_iata":"LH454","flight_icao":"DLH454","dep_iata":"FRA","arr_iata":"SFO","dep_time":"10:25","dep_time_utc":"08:25",
            "arr_time":"12:40","arr_time_utc":"19:40","duration":675,"days":["mon","tue","wed","thu","fri","sat","sun"]}]}"""
        val late = press(service("flight" to reply("flight-LH454-planned"), "routes" to daily), now = at("2026-10-02T08:40:00Z"), n = FlightNumber("LH", 454))
        assertEquals(listOf("FRA-SFO 2026-10-02T10:25"), legs(late))
        assertFalse(late.flights.single().timetable)
        // With a second line it is a question, and the flight in the air is first.
        val two = daily.replace("\"dep_iata\":\"FRA\",\"arr_iata\":\"SFO\"", "\"dep_iata\":\"FRA\",\"arr_iata\":\"MUC\"").replace("DLH454", "DLH455")
        val c = press(service("flight" to reply("flight-LH455-in-the-air"), "routes" to reply("routes-LH455").replaceFirst("\"response\": [", "\"response\": [" + two.substringAfter("[").substringBeforeLast("]") + ",")),
            now = at("2026-10-02T07:29:00Z"), n = FlightNumber("LH", 455))
        assertEquals(listOf("SFO-FRA 2026-10-01T14:47", "FRA-MUC 2026-10-02T10:25"), legs(c))
    }

    @Test fun twoFlightsThatAreTheSameFlightAreOfferedOnce() {
        // A number that leaves one airport twice on one day is one flight to the rule that follows it (the airport and the
        // planned day), so the second could not be followed apart from the first. With another airport's flight: two to choose from.
        val twice = """{"response":[
            {"flight_iata":"XX12","dep_iata":"AAA","arr_iata":"BBB","dep_time":"18:00","dep_time_utc":"18:00","arr_time":"19:00","arr_time_utc":"19:00","duration":60},
            {"flight_iata":"XX12","dep_iata":"AAA","arr_iata":"BBB","dep_time":"08:00","dep_time_utc":"08:00","arr_time":"09:00","arr_time_utc":"09:00","duration":60},
            {"flight_iata":"XX12","dep_iata":"CCC","arr_iata":"AAA","dep_time":"12:00","dep_time_utc":"12:00","arr_time":"13:00","arr_time_utc":"13:00","duration":60}]}"""
        val c = press(service("flight" to unknown, "routes" to twice), tuesday, ZoneId.of("UTC"), n = FlightNumber("XX", 12))
        assertEquals(listOf("AAA-BBB 2026-10-06T08:00", "CCC-AAA 2026-10-06T12:00"), legs(c))
        for (a in c.flights) for (b in c.flights) assertEquals(a === b, FlightRules.same(a, b))
    }

    // ---- what went wrong, and what a press costs

    @Test fun whenTheTimetableCannotBeHadTheFlightThatWasFoundIsTheAnswer() {
        val found = AirLabs.flight(flight).value
        // No connection for the second request, told to slow down, a reply nobody can read, a month's lookups used up on the first.
        val spent = """{"error":{"message":"x","code":"month_limit_exceeded"}}"""
        val tries: List<(Request) -> Reply> = listOf(
            { r -> if (r.path == AirLabs.FLIGHT) Reply.Ok(flight) else Reply.Failed(Why.OFFLINE) },
            { r -> if (r.path == AirLabs.FLIGHT) Reply.Ok(flight) else Reply.Failed(Why.STATUS, 429) },
            { r -> Reply.Ok(if (r.path == AirLabs.FLIGHT) flight else "<html>502</html>") },
            { r -> Reply.Ok(if (r.path == AirLabs.FLIGHT) flight else spent) },
            { r -> Reply.Ok(if (r.path == AirLabs.FLIGHT) flight else none) },
        )
        for ((i, get) in tries.withIndex()) for (day in listOf(null, tuesday)) {
            val c = AirLabs.candidates(ua1227, day, "k", evening, la.takeIf { day != null }, get)!!
            assertEquals("$i $day", listOf(found), c.flights)
            assertNull("$i $day", c.failure)
        }
        // A flight that is not the day's is no answer, and why the timetable was not to be had is said, as it always was.
        val cut = service("flight" to flight)
        val c = press(cut, tuesday.plusDays(1))
        assertEquals(emptyList<Flight>(), c.flights)
        assertEquals(Failure.OFFLINE, c.failure)
        assertEquals(listOf("flight", "routes"), cut.asked)
        // A request that was not sent at all is nothing to say: also when the flight was found before it.
        assertNull(AirLabs.candidates(ua1227, null, "k", evening) { r -> if (r.path == AirLabs.FLIGHT) Reply.Ok(flight) else Reply.Failed(Why.OFF) })
        assertNull(AirLabs.candidates(ua1227, null, "k", evening) { Reply.Failed(Why.OFF) })
    }

    @Test fun aNumberOnlyTheTimetableKnowsIsAQuestionToo() {
        val s = service("flight" to unknown, "routes" to table)
        val c = press(s, tuesday)
        assertEquals(listOf("flight", "routes"), s.asked)
        assertEquals(listOf("MCO-EWR 2026-10-06T08:45", "EWR-SFO 2026-10-06T13:20", "SFO-PDX 2026-10-06T19:05"), legs(c))
        // Nobody named an airline or a city: the letters. The timetable has the callsign.
        assertEquals(listOf("MCO" to "EWR", "EWR" to "SFO", "SFO" to "PDX"), c.flights.map { it.from.place to it.to.place })
        assertTrue(c.flights.all { it.timetable && it.airline == "" && it.number == "UA1227" && it.callsign == "UAL1227" })
        assertEquals(legs(c), legs(press(service("flight" to unknown, "routes" to table))))
    }

    @Test fun whatWentWrongIsWhatItAlwaysWas() {
        fun failed(s: Service, day: LocalDate? = null): Failure? = press(s, day).also { assertEquals(emptyList<Flight>(), it.flights) }.failure
        // Nobody knows the number; the timetable that would have said so is not to be had; the key is not accepted; no connection at all.
        assertEquals(Failure.NOT_FOUND, failed(service("flight" to unknown, "routes" to none)))
        assertEquals(Failure.OFFLINE, failed(service("flight" to unknown)))
        val refused = service("flight" to reply("error-unknown-key"), "routes" to table)
        assertEquals(Failure.REFUSED, failed(refused))
        assertEquals(listOf("flight"), refused.asked)
        val offline = service()
        assertEquals(Failure.OFFLINE, failed(offline, tuesday))
        assertEquals(listOf("flight"), offline.asked)
        // A day none of its lines flies on: the Monday flight from Raleigh alone, asked for on a Tuesday.
        val mondays = """{"response":[""" + Json.parseToJsonElement(table).jsonObject.getValue("response").jsonArray[2] + "]}"
        assertEquals(Failure.NOT_THAT_DAY, failed(service("flight" to flight, "routes" to mondays), tuesday.plusDays(1)))
        assertEquals(Failure.NOT_THAT_DAY, failed(service("flight" to unknown, "routes" to mondays), tuesday))
    }

    @Test fun whatAPressOfTrackCosts() {
        fun asked(s: Service, day: LocalDate? = null, now: Instant = evening): List<String> { press(s, day, now = now); return s.asked }
        // One flight found, several found, a day chosen: the one flight and the timetable.
        assertEquals(listOf("flight", "routes"), asked(thatEvening()))
        assertEquals(listOf("flight", "routes"), asked(thatEvening(), tuesday))
        assertEquals(listOf("flight", "routes"), asked(thatEvening(), tuesday.plusDays(3)))
        // Not found: the same two, as before.
        assertEquals(listOf("flight", "routes"), asked(service("flight" to unknown, "routes" to none)))
        // The next flight, hours after the last one landed: the coming hours' too. Never more.
        assertEquals(listOf("flight", "schedules", "routes"), asked(service("flight" to landed, "schedules" to soon, "routes" to table), now = at("2026-10-06T07:00:00Z")))
        assertEquals(listOf("flight", "schedules", "routes"), asked(service("flight" to landed, "schedules" to none, "routes" to table), now = at("2026-10-06T07:00:00Z")))
        // A key that is not accepted, or no connection: one, and nothing after it.
        assertEquals(listOf("flight"), asked(service("flight" to reply("error-unknown-key"))))
        assertEquals(listOf("flight"), asked(service()))
    }

    @Test fun howManyLookupsAreLeftIsWhatTheLastReplySaid() {
        fun counted(text: String, left: Int) = text.replaceFirst("{", """{"request":{"key":{"limits_total":$left}},""")
        assertEquals(939, press(service("flight" to counted(flight, 940), "routes" to counted(table, 939)), tuesday).left)
        assertEquals(940, press(service("flight" to counted(flight, 940), "routes" to table), tuesday).left)
        assertEquals(940, press(service("flight" to counted(flight, 940))).left)
    }

    @Test fun aCallsignIsAskedForAsOneAndTheTimetableByTheTicketsNumber() {
        val asked = ArrayList<Request>()
        val c = AirLabs.candidates(FlightNumber("UAL", 1227), tuesday, "k", evening, la) { r -> asked += r; Reply.Ok(if (r.path == AirLabs.FLIGHT) flight else table) }!!
        assertEquals(3, c.flights.size)
        assertEquals(listOf(AirLabs.FLIGHT to ("flight_icao" to "UAL1227"), AirLabs.ROUTES to ("flight_iata" to "UA1227")), asked.map { it.path to it.query.first() })
        for (r in asked) assertEquals(listOf(r.query.first().first, "api_key"), r.query.map { it.first })
    }
}
