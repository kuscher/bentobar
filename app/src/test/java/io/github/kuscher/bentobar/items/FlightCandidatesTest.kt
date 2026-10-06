package io.github.kuscher.bentobar.items

import io.github.kuscher.bentobar.items.AirLabs.Failure
import io.github.kuscher.bentobar.net.Reply
import io.github.kuscher.bentobar.net.Request
import io.github.kuscher.bentobar.net.Why
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

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
}
