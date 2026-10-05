package io.github.kuscher.bentobar.items

import io.github.kuscher.bentobar.items.FlightRules.Badge
import io.github.kuscher.bentobar.items.FlightRules.EndSays
import io.github.kuscher.bentobar.items.FlightRules.Heading
import io.github.kuscher.bentobar.items.FlightRules.Headline
import io.github.kuscher.bentobar.items.FlightRules.Kind
import io.github.kuscher.bentobar.items.FlightRules.Phase
import io.github.kuscher.bentobar.items.FlightRules.Stage
import io.github.kuscher.bentobar.items.FlightRules.Standing
import io.github.kuscher.bentobar.items.FlightRules.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * What is said of a flight in each of its phases: the headline, the one badge, where the plane stands
 * on the line, and what is under the line's two ends. Every saved reply of `resources/airlabs` is read
 * here as it was asked for, on 2 October 2026 at 07:29 UTC.
 */
class FlightRowTest {
    private fun reply(name: String): String = javaClass.getResource("/airlabs/$name.json")!!.readText()
    private fun flight(name: String): Flight = AirLabs.flight(reply(name)).value!!
    private fun at(text: String): Instant = Instant.parse(text)
    private fun time(text: String): LocalDateTime = LocalDateTime.parse(text)
    /** When the saved replies were asked for. */
    private val asked = at("2026-10-02T07:29:00Z")
    private fun row(name: String, now: Instant = asked) = FlightRules.row(flight(name), now)

    // ---- every saved reply, as its row

    @Test fun leavingWithinThreeHoursWithOnlyThePlanTheCountdownAndPlanned() {
        val r = row("flight-LH454-planned")                                      // leaves 08:25 UTC
        assertEquals(Phase.SOON, r.phase)
        assertEquals(Headline(Heading.LEAVES_IN, 56), r.headline)
        assertEquals(Badge(Verdict.PLANNED), r.badge)
        // The plane rests at the start: nothing creeps towards the departure.
        assertEquals(0.0, r.share!!, 0.0)
        // Where to go stands at the start; the far end has its time and nothing else, though the service names a terminal there.
        assertEquals(EndSays("FRA", time("2026-10-02T10:25"), terminal = "1", gate = "Z58"), r.from)
        assertEquals(EndSays("SFO", time("2026-10-02T12:40")), r.to)
    }

    @Test fun moreThanThreeHoursOffWhenItLeaves() {
        val r = row("flight-LH454-planned", at("2026-10-02T05:24:00Z"))          // three hours and a minute before
        assertEquals(Phase.AHEAD, r.phase)
        assertEquals(Headline(Heading.LEAVES_AT), r.headline)
        assertEquals(Badge(Verdict.PLANNED), r.badge)
        assertEquals(0.0, r.share!!, 0.0)
        // Exactly three hours before, the countdown begins.
        assertEquals(Headline(Heading.LEAVES_IN, 180), row("flight-LH454-planned", at("2026-10-02T05:25:00Z")).headline)
    }

    @Test fun lateBeforeItLeavesTheCountdownRunsToTheExpectedTimeAndTheBadgeSaysHowLate() {
        val r = row("flight-LH152-delayed")                                      // planned 06:50 UTC, expected 07:35
        assertEquals(Phase.SOON, r.phase)
        assertEquals(Headline(Heading.LEAVES_IN, 6), r.headline)
        assertEquals(Badge(Verdict.DELAYED, 45), r.badge)
        assertEquals(Kind.LATE, r.badge!!.kind)
        assertEquals(0.0, r.share!!, 0.0)
        assertEquals(EndSays("FRA", time("2026-10-02T09:35"), terminal = "1", gate = "A2"), r.from)
        assertEquals(EndSays("LEJ", time("2026-10-02T10:35")), r.to)
        // Its expected time has passed and the service still calls it planned: nobody said that it left, so the countdown
        // stays at its last minute. It never says "leaves 9:35" about a time that is over. How late it runs is still true.
        val past = row("flight-LH152-delayed", at("2026-10-02T07:40:00Z"))
        assertEquals(Phase.SOON, past.phase)
        assertEquals(Headline(Heading.LEAVES_IN, 1), past.headline)
        assertEquals(Badge(Verdict.DELAYED, 45), past.badge)
        assertEquals(0.0, past.share!!, 0.0)
    }

    @Test fun anEndWhoseClockCannotBeReadAgainstUtcMakesNoCountdown() {
        fun without(name: String, end: String) = AirLabs.flight(reply(name).replace(Regex("\"${end}_(time|estimated|actual)_utc\": [^,]*,"), "")).value!!
        // The same replies with no UTC twin at one end: the airport's times are right, the moments they stand for are anybody's guess.
        val plan = without("flight-LH454-planned", "dep")
        assertEquals(time("2026-10-02T10:25"), plan.from.planned)
        assertTrue(plan.loose)
        assertTrue(!flight("flight-LH454-planned").loose)
        // It leaves in under an hour by the reply that has its clock; without it nothing is counted: when it leaves, as its airport says.
        assertEquals(Phase.AHEAD, FlightRules.phase(plan, asked))
        assertEquals(Headline(Heading.LEAVES_AT), FlightRules.headline(plan, asked))
        // In the air: no minutes to landing, and no place for the plane. Landed: no "ago".
        val air = without("flight-LH455-in-the-air", "arr")
        assertTrue(air.loose)
        assertEquals(Headline(Heading.IN_AIR), FlightRules.headline(air, asked))
        assertNull(FlightRules.share(air, asked))
        val down = without("flight-LH96-landed", "arr")
        assertTrue(down.loose)
        assertEquals(Headline(Heading.LANDED), FlightRules.headline(down, asked))
        // How late it runs is one clock's two times: that stays.
        assertEquals(Badge(Verdict.EARLY, 24), FlightRules.badge(air, asked))
    }

    @Test fun pastItsTimeAndStillCalledPlannedNobodySaysOnTime() {
        // Leaves 08:25 UTC. A minute after, with no word that it left: one minute to go, and neither "Planned" nor "On time".
        val plan = flight("flight-LH454-planned")
        assertTrue(FlightRules.overdue(plan, at("2026-10-02T08:26:00Z")))
        val r = FlightRules.row(plan, at("2026-10-02T08:26:00Z"))
        assertEquals(Phase.SOON, r.phase)
        assertEquals(Headline(Heading.LEAVES_IN, 1), r.headline)
        assertNull(r.badge)
        val onTime = plan.copy(from = plan.from.copy(expected = plan.from.planned))
        assertEquals(Badge(Verdict.ON_TIME), FlightRules.badge(onTime, at("2026-10-02T08:24:00Z")))
        assertNull(FlightRules.badge(onTime, at("2026-10-02T08:25:00Z")))
        // Hours after, it is the same: the clock alone never makes a plan of a time that has passed.
        assertEquals(Headline(Heading.LEAVES_IN, 1), FlightRules.headline(plan, at("2026-10-02T11:00:00Z")))
        // Before its time nothing is overdue, and a timetable's flight has its own phase for a time that passed.
        assertTrue(!FlightRules.overdue(plan, at("2026-10-02T08:24:00Z")))
        assertTrue(!FlightRules.overdue(plan.copy(timetable = true), at("2026-10-02T08:26:00Z")))
    }

    @Test fun inTheAirMinutesToLandingEarlyAndThePlaneNearlyThere() {
        val r = row("flight-LH455-in-the-air")                                   // left 21:47 UTC, expected 08:01
        assertEquals(Phase.IN_AIR, r.phase)
        assertEquals(Headline(Heading.LANDS_IN, 32), r.headline)
        assertEquals(Badge(Verdict.EARLY, 24), r.badge)
        assertEquals(Kind.GOOD, r.badge!!.kind)
        // 582 of 614 minutes (the service said 95).
        assertEquals(0.948, r.share!!, 0.001)
        // Once it has left: the aircraft at the start, and at the far end the terminal, with the gate while no belt is named.
        assertEquals(EndSays("SFO", time("2026-10-01T14:47"), aircraft = "Boeing 747-8"), r.from)
        assertEquals(EndSays("FRA", time("2026-10-02T10:01"), terminal = "1", gate = "Z69"), r.to)
    }

    @Test fun inTheAirThirteenMinutesBehindItsPlanOnTime() {
        val r = row("flight-LH9152-codeshare")                                   // left 06:59 UTC, expected 16:13
        assertEquals(Phase.IN_AIR, r.phase)
        assertEquals(Headline(Heading.LANDS_IN, 524), r.headline)
        assertEquals(Badge(Verdict.ON_TIME), r.badge)
        assertEquals(0.054, r.share!!, 0.001)                                    // 30 of 554 minutes (the service said 5)
        // No aircraft and no gate were named: their words are simply not there.
        assertEquals(EndSays("FRA", time("2026-10-02T08:59")), r.from)
        assertEquals(EndSays("ORD", time("2026-10-02T11:13"), terminal = "5"), r.to)
    }

    @Test fun justAfterTakeOffThePlaceIsFromTimeNotFromTheServicesPercentage() {
        val r = row("flight-SQ26-second-leg")                                    // left 07:02 UTC, expected 14:57
        assertEquals(Headline(Heading.LANDS_IN, 448), r.headline)
        assertEquals(Badge(Verdict.ON_TIME), r.badge)                            // thirteen minutes before its plan
        assertEquals(0.057, r.share!!, 0.001)                                    // 27 of 475 minutes (the service said 14)
        assertEquals("Boeing 777-300ER", r.from.aircraft)
        assertEquals(EndSays("JFK", time("2026-10-02T10:57"), terminal = "4"), r.to)
    }

    @Test fun landedHowLongAgoAndTheLandingsVerdict() {
        val r = row("flight-LH96-landed")                                        // landed 07:13 UTC, three minutes after its plan
        assertEquals(Phase.LANDED, r.phase)
        assertEquals(Headline(Heading.LANDED_AGO, 16), r.headline)
        assertEquals(Badge(Verdict.ON_TIME), r.badge)
        assertEquals(1.0, r.share!!, 0.0)
        assertEquals(EndSays("FRA", time("2026-10-02T08:18")), r.from)
        assertEquals(EndSays("MUC", time("2026-10-02T09:13"), terminal = "2", gate = "G10"), r.to)
    }

    @Test fun justLandedBeforeTheServiceHasTheMinuteItTouchedDownTheExpectedTimeStandsIn() {
        val f = flight("flight-LH455-landed")                                    // expected 07:56 UTC, no actual time
        val r = FlightRules.row(f, at("2026-10-02T07:58:00Z"))
        assertEquals(Headline(Heading.LANDED_AGO, 2), r.headline)
        assertEquals(Badge(Verdict.EARLY, 29), r.badge)
        assertEquals(1.0, r.share!!, 0.0)                                        // landed, though no actual time is known
        assertEquals(EndSays("FRA", time("2026-10-02T09:56"), terminal = "1", gate = "Z69"), r.to)
        assertEquals("Boeing 747-8", r.from.aircraft)
        // In its first minute, and while the expected time is still ahead of the clock: just now.
        assertEquals(Headline(Heading.LANDED_NOW), FlightRules.headline(f, at("2026-10-02T07:56:30Z")))
        assertEquals(Headline(Heading.LANDED_NOW), FlightRules.headline(f, at("2026-10-02T07:50:00Z")))
        assertEquals(Headline(Heading.LANDED_AGO, 1), FlightRules.headline(f, at("2026-10-02T07:57:00Z")))
        assertEquals(Headline(Heading.LANDED_AGO, 180), FlightRules.headline(f, at("2026-10-02T10:56:00Z")))
    }

    @Test fun landedHoursAgoLandedAndNothingCountsAnyMore() {
        val r = row("flight-JL101-landed-nine-hours-ago")                        // landed 22:34 UTC the day before
        assertEquals(Phase.LANDED, r.phase)
        assertEquals(Headline(Heading.LANDED), r.headline)
        assertEquals(Badge(Verdict.ON_TIME), r.badge)                            // a minute before its plan
        assertEquals(1.0, r.share!!, 0.0)
        assertEquals(EndSays("HND", time("2026-10-02T06:31")), r.from)
        assertEquals(EndSays("ITM", time("2026-10-02T07:34")), r.to)
        // Twelve minutes after it landed.
        assertEquals(Headline(Heading.LANDED_AGO, 12), row("flight-JL101-landed-nine-hours-ago", at("2026-10-01T22:46:00Z")).headline)
    }

    @Test fun canceledTheWordBothTimesStruckNoBadgeAndNoPlane() {
        val r = row("flight-LH1184-cancelled")
        assertEquals(Phase.CANCELED, r.phase)
        assertEquals(Headline(Heading.CANCELED), r.headline)
        assertNull(r.badge)
        assertNull(r.share)
        // The service names a terminal and a gate: for a flight that will not happen they are not said.
        assertEquals(EndSays("FRA", time("2026-10-02T07:45"), struck = true), r.from)
        assertEquals(EndSays("ZRH", time("2026-10-02T08:45"), struck = true), r.to)
    }

    // ---- the phases nobody saved a reply of

    @Test fun divertedWhenItLeftStandsTheLandingIsStruckAndNobodySaysWhereToGo() {
        val r = FlightRules.row(flight("flight-LH455-in-the-air").copy(state = FlightState.DIVERTED), asked)
        assertEquals(Phase.DIVERTED, r.phase)
        assertEquals(Headline(Heading.DIVERTED), r.headline)
        assertNull(r.badge)
        assertNull(r.share)
        assertEquals(EndSays("SFO", time("2026-10-01T14:47")), r.from)
        assertEquals(EndSays("FRA", time("2026-10-02T10:01"), struck = true), r.to)
    }

    @Test fun aTimetablesFlightAPlanWhileItsTimeIsStillToComeAndNothingKnownOnceItHasPassed() {
        val like = flight("flight-LH455-in-the-air")
        val route = AirLabs.routes(reply("routes-LH455")).value!![0]
        val f = AirLabs.planned(route, like, LocalDate.of(2026, 10, 3))!!        // leaves 21:40 UTC, lands 08:25 UTC the day after
        // A day and a half off: when it leaves, "Planned", and the plane at rest at the start (it has not left: that much is certain).
        val far = FlightRules.row(f, asked)
        assertEquals(Phase.AHEAD, far.phase)
        assertEquals(Headline(Heading.LEAVES_AT), far.headline)
        assertEquals(Badge(Verdict.PLANNED), far.badge)
        assertEquals(0.0, far.share!!, 0.0)
        assertEquals(EndSays("SFO", time("2026-10-03T14:40")), far.from)
        assertEquals(EndSays("FRA", time("2026-10-04T10:25")), far.to)
        // Within three hours: the countdown, and still only the plan.
        val near = FlightRules.row(f, at("2026-10-03T20:00:00Z"))
        assertEquals(Headline(Heading.LEAVES_IN, 100), near.headline)
        assertEquals(Badge(Verdict.PLANNED), near.badge)
        // Past its time to leave, in what would be its flight, and after: from the timetable, no badge, no plane.
        for (now in listOf(at("2026-10-03T21:40:00Z"), at("2026-10-04T00:00:00Z"), at("2026-10-04T09:00:00Z"))) {
            val past = FlightRules.row(f, now)
            assertEquals(Phase.TIMETABLE, past.phase)
            assertEquals(Headline(Heading.TIMETABLE), past.headline)
            assertNull(past.badge)
            assertNull(past.share)
            assertEquals(far.from, past.from); assertEquals(far.to, past.to)
        }
        // A plan whose clocks may be an hour out is never counted down to.
        assertEquals(Headline(Heading.LEAVES_AT), FlightRules.headline(f.copy(loose = true), at("2026-10-03T20:00:00Z")))
    }

    @Test fun inTheAirWithNoTimeOfLandingToReckonWithInTheAirAndNoPlane() {
        val air = flight("flight-LH455-in-the-air")
        val blind = air.copy(to = air.to.copy(planned = null, expected = null))
        assertEquals(Headline(Heading.IN_AIR), FlightRules.headline(blind, asked))
        assertNull(FlightRules.share(blind, asked))
        assertNull(FlightRules.badge(blind, asked))
        // With only the plan of the landing: the countdown runs to the plan, the plane flies, and there is no badge.
        val planned = air.copy(to = air.to.copy(expected = null))                // planned 08:25 UTC
        assertEquals(Headline(Heading.LANDS_IN, 56), FlightRules.headline(planned, asked))
        assertEquals(0.912, FlightRules.share(planned, asked)!!, 0.001)
        assertNull(FlightRules.badge(planned, asked))
        // A landing that would be before the take-off is nobody's time.
        assertNull(FlightRules.share(air.copy(to = air.to.copy(expected = time("2026-10-01T20:00"))), asked))
    }

    @Test fun withNoTimeToLeaveAtAllThereIsTheHeadlineAloneAndInTheAirWithoutOneNoPlane() {
        val planned = flight("flight-LH454-planned")
        val bare = planned.copy(from = planned.from.copy(planned = null))
        val r = FlightRules.row(bare, asked)
        assertEquals(Phase.AHEAD, r.phase)
        assertEquals(Headline(Heading.LEAVES_AT), r.headline)      // the app can only say "Planned" for it
        assertNull(r.badge)                                        // so no badge says it again
        assertEquals(0.0, r.share!!, 0.0)
        assertNull(r.from.time)
        // In the air, and nobody says when it left: the minutes to landing still count, but there is no share to work out.
        val air = flight("flight-LH455-in-the-air")
        val blind = air.copy(from = air.from.copy(planned = null, expected = null, actual = null))
        assertEquals(Headline(Heading.LANDS_IN, 32), FlightRules.headline(blind, asked))
        assertNull(FlightRules.share(blind, asked))
    }

    @Test fun aBeltTakesTheGatesPlaceAtTheFarEnd() {
        val landed = flight("flight-LH96-landed")
        val r = FlightRules.row(landed.copy(to = landed.to.copy(belt = "4")), asked)
        assertEquals(EndSays("MUC", time("2026-10-02T09:13"), terminal = "2", belt = "4"), r.to)
    }

    // ---- the badge

    @Test fun lateStartsFifteenMinutesAfterThePlan() {
        val planned = flight("flight-LH454-planned")
        fun leaving(minutes: Long) = FlightRules.badge(planned.copy(from = planned.from.copy(expected = planned.from.planned!!.plusMinutes(minutes))), asked)
        assertEquals(Badge(Verdict.ON_TIME), leaving(0))
        assertEquals(Badge(Verdict.ON_TIME), leaving(14))
        assertEquals(Badge(Verdict.DELAYED, 15), leaving(15))
        assertEquals(Badge(Verdict.DELAYED, 75), leaving(75))
        // Leaving early is on time: nobody is told to hurry by a badge.
        assertEquals(Badge(Verdict.ON_TIME), leaving(-20))
        val air = flight("flight-LH455-in-the-air")
        fun landing(minutes: Long) = FlightRules.badge(air.copy(to = air.to.copy(expected = air.to.planned!!.plusMinutes(minutes))), asked)
        assertEquals(Badge(Verdict.ON_TIME), landing(14))
        assertEquals(Badge(Verdict.DELAYED, 15), landing(15))
        assertEquals(Badge(Verdict.ON_TIME), landing(-14))
        assertEquals(Badge(Verdict.EARLY, 15), landing(-15))
        // After landing the same minutes read "late", not "delayed".
        val down = flight("flight-LH96-landed")
        fun landed(minutes: Long) = FlightRules.badge(down.copy(to = down.to.copy(actual = down.to.planned!!.plusMinutes(minutes))), asked)
        assertEquals(Badge(Verdict.ON_TIME), landed(14))
        assertEquals(Badge(Verdict.LATE, 27), landed(27))
        assertEquals(Badge(Verdict.EARLY, 29), landed(-29))
        assertEquals(Kind.LATE, landed(27)!!.kind)
        assertEquals(Kind.GOOD, landed(-29)!!.kind)
        assertEquals(Kind.PLAIN, Badge(Verdict.PLANNED).kind)
    }

    @Test fun onTimeIsAClaimWithOnlyThePlanThereIsPlannedBeforeLeavingAndNoBadgeAfter() {
        assertEquals(Badge(Verdict.PLANNED), FlightRules.badge(flight("flight-LH454-planned"), asked))
        val air = flight("flight-LH455-in-the-air")
        assertNull(FlightRules.badge(air.copy(to = air.to.copy(expected = null)), asked))
        val down = flight("flight-LH96-landed")
        assertNull(FlightRules.badge(down.copy(to = down.to.copy(expected = null, actual = null)), asked))
        // The departure's delay counts until it has left, the landing's after: LH 455 left seven minutes late and is early.
        assertEquals(Badge(Verdict.EARLY, 24), FlightRules.badge(air, asked))
    }

    // ---- the plane's place

    @Test fun thePlaneMovesWithTheClockAndReachesTheEndOnlyWhenTheServiceSaysLanded() {
        val air = flight("flight-LH455-in-the-air")                              // left 21:47 UTC, expected 08:01: 614 minutes
        fun share(now: String) = FlightRules.share(air, at(now))!!
        assertEquals(0.5, share("2026-10-02T02:54:00Z"), 0.001)
        // A minute is a 614th of the line.
        assertEquals(1.0 / 614, share("2026-10-02T02:55:00Z") - share("2026-10-02T02:54:00Z"), 1e-9)
        // Never nearer an end than two per cent: just after take-off, in the last minutes, and past its expected time.
        assertEquals(0.02, share("2026-10-01T21:48:00Z"), 0.0)
        assertEquals(0.98, share("2026-10-02T07:55:00Z"), 0.0)
        assertEquals(0.98, share("2026-10-02T09:30:00Z"), 0.0)
        // The headline never says less than a minute while it is in the air.
        assertEquals(Headline(Heading.LANDS_IN, 1), FlightRules.headline(air, at("2026-10-02T08:00:30Z")))
        assertEquals(Headline(Heading.LANDS_IN, 1), FlightRules.headline(air, at("2026-10-02T09:30:00Z")))
        // Landed: the end, with or without an actual time. Before it leaves: the start, however late it is.
        assertEquals(1.0, FlightRules.share(flight("flight-LH455-landed"), asked)!!, 0.0)
        assertEquals(0.0, FlightRules.share(flight("flight-LH152-delayed"), at("2026-10-02T09:00:00Z"))!!, 0.0)
        // Step by step through the whole flight it only ever goes forward.
        var last = 0.0
        var t = at("2026-10-01T21:00:00Z")
        while (t.isBefore(at("2026-10-02T10:00:00Z"))) {
            val s = FlightRules.share(air, t)!!
            assertTrue(s >= last && s in 0.02..0.98)
            last = s; t = t.plusSeconds(60)
        }
    }

    @Test fun aLaterEstimateDoesNotTakeThePlaneBack() {
        val air = flight("flight-LH455-in-the-air")
        val shown = FlightRules.share(air, asked)!!                             // 0.948
        // The service now expects it forty minutes later: by the times it is further from its end than it was drawn.
        val slower = air.copy(to = air.to.copy(expected = air.to.expected!!.plusMinutes(40)))
        val behind = FlightRules.share(slower, asked)!!
        assertTrue(behind < shown)
        assertEquals(shown, FlightRules.forward(shown, behind)!!, 0.0)
        // It waits there until the clock has caught up, and goes on from then.
        val later = FlightRules.share(slower, at("2026-10-02T08:25:00Z"))!!
        assertTrue(later > shown)
        assertEquals(later, FlightRules.forward(shown, later)!!, 0.0)
        // Nothing drawn yet: where the times put it. No plane any more: none, whatever was drawn.
        assertEquals(behind, FlightRules.forward(null, behind)!!, 0.0)
        assertNull(FlightRules.forward(shown, null))
    }

    // ---- a flight that is followed while nobody hears of it

    @Test fun thePlaneOfAFlightThatSleptThroughItsLandingStandsAtTheEnd() {
        val air = flight("flight-LH455-in-the-air")
        fun share(f: Flight, now: Instant) = FlightRules.share(FlightRules.shown(f, now), now)
        assertEquals(FlightRules.share(air, asked), share(air, asked))
        // A flight that was to land hours ago is taken for landed: its plane is at the end, though the service never said so.
        assertEquals(Standing(Stage.LANDED), FlightRules.standing(air, at("2026-10-02T16:00:00Z")))
        assertEquals(1.0, share(air, at("2026-10-02T16:00:00Z"))!!, 0.0)
        assertEquals(0.0, share(flight("flight-LH454-planned"), asked)!!, 0.0)
        assertEquals(1.0, share(flight("flight-LH96-landed"), asked)!!, 0.0)
        assertNull(share(flight("flight-LH1184-cancelled"), asked))
        // A timetable's flight counts down by its plan, but nobody knows where it is: no plane.
        val route = AirLabs.routes(reply("routes-LH455")).value!![0]
        val plan = AirLabs.planned(route, air, LocalDate.of(2026, 10, 3))!!
        assertEquals(0.0, share(plan, asked)!!, 0.0)
        assertEquals(Stage.IN_AIR, FlightRules.standing(plan, at("2026-10-04T00:00:00Z")).stage)
        assertNull(share(plan, at("2026-10-04T00:00:00Z")))
        assertNull(share(plan, at("2026-10-04T09:00:00Z")))
    }

    // ---- what is read off the reply for it

    @Test fun theAircraftsTypeIsReadWhereTheServiceNamesIt() {
        assertEquals("Boeing 747-8", flight("flight-LH455-in-the-air").aircraft)
        assertEquals("Boeing 777-300ER", flight("flight-SQ26-second-leg").aircraft)
        assertNull(flight("flight-LH454-planned").aircraft)
        assertNull(flight("flight-LH9152-codeshare").aircraft)
        fun model(m: String) = AirLabs.flight("""{"response":{"flight_iata":"XX12","dep_iata":"AAA","arr_iata":"BBB","status":"en-route","model":"$m"}}""").value!!.aircraft
        assertEquals("Boeing 737-800", model("Boeing 737-800 (winglets) pax"))
        assertEquals("Airbus A350-900", model("Airbus A350-900"))
        assertNull(model(" "))
    }
}
