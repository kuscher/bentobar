package io.github.kuscher.bentobar.items

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
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
}
