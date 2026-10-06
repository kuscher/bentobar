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

    // ---- the route line in the bar, which every state of its design can be staged for

    /** A sample in the bar of an item that draws the line, [later] minutes after it was staged. */
    private fun lined(name: String, turn: String? = null, later: Long = 0) = FlightText.bar(tracked(name, turn), null, now.plusSeconds(later * 60), 24, us, line = true)
    private fun route(name: String, turn: String? = null, later: Long = 0) = lined(name, turn, later).route

    @Test fun theSamplesThatCameWithTheLineReadAsTheirRows() {
        assertEquals(listOf("1h 37m · +1h 25m", Sym.FLIGHT_TAKEOFF, Tone.WARN, "Leaves in 1 h 37 min", "Delayed 1 h 25 min"), shown("soon-very-late"))
        assertEquals(listOf("2h 05m · +55m", Sym.FLIGHT_LAND, Tone.WARN, "Lands in 2 h 05 min", "Delayed 55 min"), shown("air-very-late"))
        assertEquals(listOf("Landed · Belt 21", Sym.FLIGHT_LAND, Tone.NORMAL, "Landed 20 min ago", "25 min late"), shown("landed-late"))
        // In the air, and the service names no time of landing: nothing to count, and no plane on the menu's line.
        assertEquals(listOf("In the air", Sym.FLIGHT_LAND, Tone.NORMAL, "In the air", null), shown("air-no-landing"))
        assertNull(card("air-no-landing").share)
        for (name in listOf("soon-very-late", "air-very-late", "landed-late", "air-no-landing")) {
            assertTrue(name, staged(name) != null && " $name " in " ${FlightSamples.names} ")
            assertTrue(name, bar(name).active)
        }
        // They start on their figures too, whatever the second.
        for (second in listOf("00", "29", "59")) {
            val at = Instant.parse("2026-10-08T06:20:${second}Z")
            assertEquals(second, "1h 37m · +1h 25m", FlightText.bar(tracked("soon-very-late", at = at), null, at, 24, us).text)
            assertEquals(second, "2h 05m · +55m", FlightText.bar(tracked("air-very-late", at = at), null, at, 24, us).text)
            assertEquals(second, "25 min late", FlightText.card(tracked("landed-late", at = at), at, us)!!.badge)
        }
    }

    @Test fun everyStateThatHasALineCanBeStaged() {
        // Within three hours of leaving the plane waits at the start: in the text's color, late, very late.
        assertEquals(BarRoute(0f, Stands.NO_CLAIM), route("soon"))
        assertEquals(BarRoute(0f, Stands.LATE), route("soon-late"))
        assertEquals(BarRoute(0f, Stands.VERY_LATE), route("soon-very-late"))
        // In the air it stands where the clock puts it: on time and early, late, very late.
        assertEquals(BarRoute((520.0 / 645.0).toFloat(), Stands.GOOD), route("air"))
        assertEquals(BarRoute((502.0 / 627.0).toFloat(), Stands.GOOD), route("air-early"))
        assertEquals(BarRoute((540.0 / 665.0).toFloat(), Stands.LATE), route("air-late"))
        assertEquals(BarRoute((575.0 / 700.0).toFloat(), Stands.VERY_LATE), route("air-very-late"))
        // Nobody has said how it stands: the last answer is over an hour old ("old" after a flight's name).
        assertEquals(BarRoute((520.0 / 645.0).toFloat(), Stands.NO_CLAIM), route("air", "old"))
        assertEquals("2h 05m · not live", lined("air", "old").text)
        assertEquals(BarRoute((575.0 / 700.0).toFloat(), Stands.NO_CLAIM), route("air-very-late", "old"))
        assertEquals(BarRoute(0f, Stands.NO_CLAIM), route("soon-late", "old"))
        // Landed, for its hour: on time, late, and with an answer too old to say.
        assertEquals(BarRoute(1f, Stands.GOOD), route("landed"))
        assertEquals(BarRoute(1f, Stands.LATE), route("landed-late"))
        assertEquals(BarRoute(1f, Stands.NO_CLAIM), route("landed", "old"))
        // Canceled and diverted: in the alert pill for their first hour, and after it (the staged clock, 61 minutes on).
        val canceled = BarRoute(0f, Stands.WILL_NOT_ARRIVE, struck = true, whole = true)
        assertEquals(canceled, route("canceled"))
        assertEquals(Tone.ALERT, lined("canceled").tone)
        assertEquals(canceled, route("canceled", later = 61))
        assertEquals(Tone.NORMAL, lined("canceled", later = 61).tone)
        val diverted = BarRoute(0.5f, Stands.WILL_NOT_ARRIVE, struck = false, whole = true)
        assertEquals(diverted, route("diverted"))
        assertEquals(Tone.ALERT, lined("diverted").tone)
        assertEquals(diverted, route("diverted", later = 61))
        assertEquals(Tone.NORMAL, lined("diverted", later = 61).tone)
        // A number sold by another airline is a countdown like any other.
        assertEquals(BarRoute(0f, Stands.NO_CLAIM), route("codeshare"))
    }

    @Test fun theStatesWithoutALineCanBeStagedAndStayAsTheyWere() {
        // Far off, a plan nobody counts down to, no update, in the air with no time of landing, landed over an hour ago.
        for ((name, later) in listOf("ahead" to 0L, "tomorrow" to 0L, "loose" to 0L, "timetable" to 0L, "air-no-landing" to 0L, "landed" to 41L, "landed-late" to 41L)) {
            assertNull(name, route(name, later = later))
            assertEquals(name, bar(name, later = later), lined(name, later = later))
        }
        // Nothing followed, a lookup on its way: the plane alone, and the number.
        assertNull(FlightText.bar(null, null, now, 24, us, line = true).route)
        assertNull(FlightText.bar(null, (staged("looking") as Staged.Looking).number, now, 24, us, line = true).route)
        // The line comes and goes with the staged clock: "soon" was to leave in 97 minutes, "landed" came down 20 minutes ago.
        assertNull(route("tomorrow", later = 22 * 60 - 1))
        assertEquals(BarRoute(0f, Stands.NO_CLAIM), route("tomorrow", later = 22 * 60))
        assertEquals(BarRoute(1f, Stands.GOOD), route("landed", later = 39))
    }

    @Test fun withTheLineEverySampleSaysWhatItSaysWithoutIt() {
        val flights = FlightSamples.names.substringBefore(" | ").split(" ").filter { staged(it) is Staged.Following }
        assertEquals(17, flights.size)
        for (name in flights) for (turn in listOf(null, "old")) for (later in listOf(0L, 61L)) {
            val plain = bar(name, turn, later)
            val line = lined(name, turn, later)
            val what = "$name ${turn.orEmpty()} +$later"
            assertEquals(what, plain.text, line.text)
            assertEquals(what, plain.icon, line.icon)
            assertEquals(what, plain.active, line.active)
            assertEquals(what, plain.tooltip, line.tooltip)
            assertNull(what, plain.route)
            // The words take the bar's color beside a line, and keep their own where there is none.
            assertEquals(what, if (line.route == null || plain.tone == Tone.ALERT) plain.tone else Tone.NORMAL, line.tone)
            // The spoken sentence is the same but for a green line's "on time".
            val onTime = line.route?.stands == Stands.GOOD && !plain.desc.endsWith(" early")
            assertEquals(what, if (onTime) plain.desc.replaceFirst(Regex("(, belt .*)?$"), ", on time$1") else plain.desc, line.desc)
        }
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
