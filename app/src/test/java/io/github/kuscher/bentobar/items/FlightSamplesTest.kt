package io.github.kuscher.bentobar.items

import io.github.kuscher.bentobar.items.AirLabs.Failure
import io.github.kuscher.bentobar.items.FlightSamples.Staged
import io.github.kuscher.bentobar.util.Sym
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * The flights a test on a device stages by name (`flight show soon`): each gives the bar's text of
 * its row in the table, to the character, and the menu's headline and badge that go with it. Looked
 * at on Wednesday 7 October 2026 at 11:20 PM in San Francisco.
 */
class FlightSamplesTest {
    private val us = FlightVoices.us(ZoneId.of("America/Los_Angeles"))
    private val now = Instant.parse("2026-10-08T06:20:00Z")
    private fun staged(name: String, turn: String? = null, at: Instant = now) = FlightSamples.of(name, turn, at.toEpochMilli())
    private fun tracked(name: String, turn: String? = null, at: Instant = now) = (staged(name, turn, at) as Staged.Following).tracked
    private fun bar(name: String, turn: String? = null, later: Long = 0) = FlightText.bar(tracked(name, turn), null, now.plusSeconds(later * 60), 24, us)
    private fun card(name: String, turn: String? = null, later: Long = 0) = FlightText.card(tracked(name, turn), now.plusSeconds(later * 60), us)!!

    /** The text, the glyph and the color of the bar, then the headline and the badge of the menu. */
    private fun shown(name: String) = listOf(bar(name).text, bar(name).icon, bar(name).tone, card(name).headline, card(name).badge)

    @Test fun eachFlightSampleReadsAsItsRowOfTheTable() {
        assertEquals(listOf("LH 455 · Fri 2:40 PM", Sym.FLIGHT_TAKEOFF, Tone.NORMAL, "Leaves Fri 2:40 PM", "Planned"), shown("ahead"))
        assertEquals(listOf("1h 37m · Gate G13", Sym.FLIGHT_TAKEOFF, Tone.NORMAL, "Leaves in 1\u00A0h 37\u00A0min", "On time"), shown("soon"))
        assertEquals(listOf("1h 37m · +25m · G13", Sym.FLIGHT_TAKEOFF, Tone.WARN, "Leaves in 1\u00A0h 37\u00A0min", "Delayed 25\u00A0min"), shown("soon-late"))
        assertEquals(listOf("2h 05m · 10:25 AM", Sym.FLIGHT_LAND, Tone.NORMAL, "Lands in 2\u00A0h 05\u00A0min", "On time"), shown("air"))
        assertEquals(listOf("2h 05m · +20m", Sym.FLIGHT_LAND, Tone.WARN, "Lands in 2\u00A0h 05\u00A0min", "Delayed 20\u00A0min"), shown("air-late"))
        assertEquals(listOf("2h 05m · −18m", Sym.FLIGHT_LAND, Tone.NORMAL, "Lands in 2\u00A0h 05\u00A0min", "18\u00A0min early"), shown("air-early"))
        assertEquals(listOf("Landed · Belt 21", Sym.FLIGHT_LAND, Tone.NORMAL, "Landed 20\u00A0min ago", "On time"), shown("landed"))
        assertEquals(listOf("LH 455 canceled", Sym.AIRPLANEMODE_INACTIVE, Tone.ALERT, "Canceled", null), shown("canceled"))
        assertEquals(listOf("LH 455 diverted", Sym.FLIGHT, Tone.ALERT, "Diverted", null), shown("diverted"))
        assertEquals(listOf("LH 455 · no update", Sym.FLIGHT, Tone.NORMAL, "From the timetable", null), shown("timetable"))
    }

    @Test fun eachOfThemIsOutInTheBarButThePlanDaysAhead() {
        for (name in listOf("soon", "soon-late", "air", "air-late", "landed", "canceled", "diverted", "timetable")) assertTrue(name, bar(name).active)
        assertFalse(bar("ahead").active)                              // about two days off, and the rule's default is 24 hours
        for (name in listOf("ahead", "soon", "air", "landed", "canceled")) assertEquals(name, "LH 455 · San Francisco → Frankfurt", bar(name).tooltip)
    }

    @Test fun theFailureSamplesAreTheFieldWithItsMessageAndThePlaneAlone() {
        fun failed(name: String) = staged(name) as Staged.Failed
        fun words(name: String) = FlightText.error(failed(name).failure, failed(name).number, null, us)
        assertEquals("No connection", words("offline"))
        assertEquals("No flight LH 9999 found. Check the number.", words("not-found"))
        assertEquals("This key's lookups for the month are used up.", words("used-up"))
        assertEquals("AirLabs doesn't accept this key.", words("refused"))
        assertEquals("AirLabs didn't answer. Try again in a moment.", words("no-answer"))
        assertEquals(listOf(Failure.OFFLINE, Failure.NOT_FOUND, Failure.USED_UP, Failure.REFUSED), listOf("offline", "not-found", "used-up", "refused").map { failed(it).failure })
        // In the bar such a press of Track leaves nothing: no flight is followed.
        assertNull(FlightText.bar(null, null, now, 24, us).text)
    }

    @Test fun theOtherStatesCanBeStagedToo() {
        assertEquals("LH 455", (staged("looking") as Staged.Looking).number)
        assertEquals(Staged.Empty, staged("none"))
        assertEquals(Staged.NoKey, staged("no-key"))
        assertNull(staged("no-such-sample"))
        assertNull(staged("soon", "no-such-turn"))
        assertNull(staged("offline", "old"))
        // Every name QA was given is there.
        for (name in listOf("ahead", "soon", "soon-late", "air", "air-late", "landed", "canceled", "diverted", "timetable", "offline", "not-found", "used-up", "refused"))
            assertTrue(name, staged(name) != null && " $name " in " ${FlightSamples.names} ")
    }

    @Test fun aSampleMovesOnWithTheStagedClock() {
        // Half an hour on: the countdown has counted, by the clock alone.
        assertEquals("1h 07m · Gate G13", bar("soon", later = 30).text)
        assertEquals("Leaves in 1\u00A0h 07\u00A0min", card("soon", later = 30).headline)
        // Sixty-one minutes on without an answer: not live, and no color of its own.
        assertEquals("36m · not live", bar("soon", later = 61).text)
        assertEquals(Tone.NORMAL, bar("soon-late", later = 61).tone)
        // The alert of a cancellation is over after its hour; the words stay.
        assertEquals(Tone.ALERT, bar("canceled", later = 59).tone)
        assertEquals(Tone.NORMAL, bar("canceled", later = 60).tone)
        assertEquals("LH 455 canceled", bar("canceled", later = 60).text)
        // Forty-one minutes on, the landed one has been down for an hour and a minute: the plane alone, and "Landed" in the menu.
        assertEquals("Landed · Belt 21", bar("landed", later = 39).text)
        assertNull(bar("landed", later = 41).text)
        assertFalse(bar("landed", later = 41).active)
        assertEquals("Landed", card("landed", later = 41).headline)
        // A day on, it is cleared.
        assertNull(FlightText.card(tracked("landed"), now.plusSeconds(24 * 3600), us))
    }

    @Test fun theRuleCanBeWalkedThroughWithTheClock() {
        // Hidden 25 hours before it leaves, out at 23.
        assertFalse(bar("tomorrow").active)
        assertFalse(bar("tomorrow", later = 59).active)
        assertTrue(bar("tomorrow", later = 60).active)
        assertTrue(bar("tomorrow", later = 120).active)
        // The landed one came down 20 minutes before it was staged: still out 59 minutes after landing, hidden at 61.
        assertTrue(bar("landed", later = 39).active)
        assertFalse(bar("landed", later = 41).active)
        // And cleared a day after it landed: the menu is back at "No flight tracked".
        assertNull(FlightText.card(tracked("landed"), now.plusSeconds(24 * 3600 - 20 * 60), us))
        assertEquals("No flight tracked", FlightText.bar(tracked("landed"), null, now.plusSeconds(24 * 3600 - 20 * 60), 24, us).desc)
    }

    @Test fun aSampleStartsOnItsFigureWhateverTheSecond() {
        for (second in listOf("00", "01", "30", "59")) {
            val at = Instant.parse("2026-10-08T06:20:${second}Z")
            assertEquals(second, "1h 37m · Gate G13", FlightText.bar(tracked("soon", at = at), null, at, 24, us).text)
            assertEquals(second, "2h 05m · +20m", FlightText.bar(tracked("air-late", at = at), null, at, 24, us).text)
            assertEquals(second, "Landed 20\u00A0min ago", FlightText.card(tracked("landed", at = at), at, us)!!.headline)
        }
    }

    @Test fun howTheLastAskWentCanBeStagedWithAFlight() {
        assertEquals("1h 37m · not live", bar("soon", "old").text)
        assertEquals("2h 05m · not live", bar("air-late", "old").text)
        assertEquals(Tone.NORMAL, bar("air-late", "old").tone)
        assertEquals("Times are each airport's own. Flight data by AirLabs · no connection, updated 11:08 PM", card("air", "offline").note)
        assertEquals("Times are each airport's own. Flight data by AirLabs · no answer, updated 11:08 PM", card("air", "no-answer").note)
        assertEquals("Times are each airport's own. Flight data by AirLabs · key not accepted, updated 11:08 PM", card("air", "refused").note)
        assertTrue(card("air", "refused").changeKey)
        assertEquals("Times are each airport's own. Flight data by AirLabs · no lookups left this month, updated 11:08 PM", card("air", "used-up").note)
        assertEquals("Times are each airport's own. Flight data by AirLabs · few lookups left, refresh by hand; updated 11:20 PM", card("air", "few").note)
        // A refused or used-up key, and few lookups left: nothing is asked by itself.
        for (turn in listOf("refused", "used-up", "few")) assertNull(turn, FlightRules.every(tracked("air", turn), now))
        // The rest of the card stays as it was.
        assertEquals("Lands in 2\u00A0h 05\u00A0min", card("air", "offline").headline)
    }

    @Test fun theOddOnesHaveTheirNotes() {
        assertTrue(card("loose").loose)
        assertEquals("Leaves Thu 1:00 AM", card("loose").headline)    // no countdown, though it is under three hours off
        assertEquals("Operated as UA 945", card("codeshare").operatedAs)
        assertEquals("LH 9152 · Lufthansa", card("codeshare").title)
    }

    @Test fun whatIsUnderTheLineForTheSamples() {
        assertEquals("Gate G13 · Terminal 1", card("soon").from.words)
        assertEquals("Terminal 1", card("soon").to.words)
        assertEquals("Boeing 747-8", card("air").from.words)
        assertEquals("Gate Z69 · Terminal 1", card("air").to.words)
        assertEquals("Terminal 1 · Belt 21", card("landed").to.words)
        assertEquals(0.0, card("soon").share!!, 0.0)
        assertEquals(520.0 / 645.0, card("air").share!!, 1e-9)
        assertEquals(1.0, card("landed").share!!, 0.0)
        assertNull(card("canceled").share)
        assertTrue(card("canceled").from.struck && card("canceled").to.struck)
        assertTrue(card("diverted").to.struck && !card("diverted").from.struck)
    }
}
