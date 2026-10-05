package io.github.kuscher.bentobar.items

import io.github.kuscher.bentobar.items.AirLabs.Failure
import io.github.kuscher.bentobar.items.FlightRules.Tracked
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Flights for looking at the item in each of its states without waiting for a real one to be in it
 * (debug builds: `./bento debug flight show NAME`). Each is made for the moment it is staged at, so
 * that it reads as its name says then; the staged clock (`./bento debug now +30m`) moves it on from
 * there. Nothing of a sample is asked of the flight service, and none needs a key.
 *
 * The flight is the examples' LH 455, San Francisco to Frankfurt, with made-up times.
 */
object FlightSamples {
    /** What the first Flight item shows in place of the real thing. */
    sealed interface Staged {
        /** A flight that is followed, as it was last heard. */
        class Following(val tracked: Tracked) : Staged
        /** A press of Track that came to nothing: the number that was entered, as it is shown, and why. */
        class Failed(val number: String, val failure: Failure) : Staged
        /** A lookup that is on its way. */
        class Looking(val number: String) : Staged
        /** Nothing followed: the field, the day chips, the note. */
        data object Empty : Staged
        /** No key saved: the words, and the way to the item's settings. */
        data object NoKey : Staged
    }

    private const val NUMBER = "LH455"
    private const val SHOWN = "LH 455"
    /** San Francisco in summer, seven hours behind UTC; Frankfurt, two ahead. */
    private const val SFO = -420
    private const val FRA = 120
    /** How long it flies, by its timetable. */
    private const val FLIGHT = 645L

    /** The flights, by name: each from the moment it is looked at. */
    private val flights: Map<String, (Long) -> Flight> = linkedMapOf(
        // The day after tomorrow at 2:40 PM in San Francisco: "LH 455 · Fri 2:40 PM", Planned.
        "ahead" to { now ->
            val leaves = local(now, 0, SFO).toLocalDate().plusDays(2).atTime(14, 40)
            lh455(sfo(leaves, gate = null), fra(leaves.plusMinutes(FLIGHT + (FRA - SFO))), FlightState.PLANNED)
        },
        // "1h 37m · Gate G13", On time.
        "soon" to { now -> lh455(sfo(local(now, 97, SFO), expected = local(now, 97, SFO)), fra(local(now, 97 + FLIGHT, FRA)), FlightState.PLANNED) },
        // "1h 37m · +25m · G13", Delayed 25 min.
        "soon-late" to { now -> lh455(sfo(local(now, 72, SFO), expected = local(now, 97, SFO)), fra(local(now, 72 + FLIGHT, FRA), expected = local(now, 97 + FLIGHT, FRA)), FlightState.PLANNED) },
        // "2h 05m" and when it lands, On time.
        "air" to { now -> lh455(left(now, 520), fra(local(now, 125, FRA), expected = local(now, 125, FRA), gate = "Z69"), FlightState.IN_AIR) },
        // "2h 05m · +20m", Delayed 20 min.
        "air-late" to { now -> lh455(left(now, 540), fra(local(now, 105, FRA), expected = local(now, 125, FRA), gate = "Z69"), FlightState.IN_AIR) },
        // "2h 05m · −18m", 18 min early.
        "air-early" to { now -> lh455(left(now, 502), fra(local(now, 143, FRA), expected = local(now, 125, FRA), gate = "Z69"), FlightState.IN_AIR) },
        // "Landed · Belt 21", Landed 20 min ago, On time.
        "landed" to { now -> lh455(left(now, 665), fra(local(now, -23, FRA), actual = local(now, -20, FRA), gate = "Z69", belt = "21"), FlightState.LANDED) },
        // "LH 455 canceled": the crossed-out plane, an alert for an hour.
        "canceled" to { now -> lh455(sfo(local(now, 120, SFO)), fra(local(now, 120 + FLIGHT, FRA)), FlightState.CANCELED) },
        // "LH 455 diverted": an alert for an hour.
        "diverted" to { now -> lh455(left(now, 300), fra(local(now, 345, FRA), expected = local(now, 345, FRA)), FlightState.DIVERTED) },
        // A plan from the timetable whose time passed an hour ago: "LH 455 · no update", From the timetable.
        "timetable" to { now -> lh455(sfo(local(now, -60, SFO), gate = null, terminal = null), fra(local(now, -60 + FLIGHT, FRA)), FlightState.PLANNED, timetable = true) },
        // A plan from the timetable for a day after a clock change: no countdown though it leaves in under three hours, and the note that its times may be an hour off.
        "loose" to { now -> lh455(sfo(local(now, 100, SFO), gate = null, terminal = null), fra(local(now, 100 + FLIGHT, FRA)), FlightState.PLANNED, timetable = true).copy(loose = true) },
        // Sold as LH 9152 and flown by another airline: "Operated as UA 945".
        "codeshare" to { now -> lh455(sfo(local(now, 97, SFO), expected = local(now, 97, SFO)), fra(local(now, 97 + FLIGHT, FRA)), FlightState.PLANNED).copy(number = "LH9152", flownAs = "UA945") },
    )

    /** What a press of Track can come to, by name. */
    private val failures: Map<String, Staged.Failed> = linkedMapOf(
        "offline" to Staged.Failed(SHOWN, Failure.OFFLINE),
        "not-found" to Staged.Failed("LH 9999", Failure.NOT_FOUND),
        "used-up" to Staged.Failed(SHOWN, Failure.USED_UP),
        "refused" to Staged.Failed(SHOWN, Failure.REFUSED),
        "no-answer" to Staged.Failed(SHOWN, Failure.NO_ANSWER),
    )

    /** What can be said after a flight's name: how the last ask about it went. */
    private val turns: Map<String, (Tracked, Long) -> Tracked> = linkedMapOf(
        // The last answer is 61 minutes old: "not live" within three hours of leaving and in the air.
        "old" to { t, now -> t.copy(askedAt = now - 61 * 60_000L, heardAt = now - 61 * 60_000L) },
        "offline" to { t, now -> t.copy(failure = Failure.OFFLINE, failures = 1, heardAt = now - 12 * 60_000L) },
        "no-answer" to { t, now -> t.copy(failure = Failure.NO_ANSWER, failures = 1, heardAt = now - 12 * 60_000L) },
        "refused" to { t, now -> t.copy(failure = Failure.REFUSED, heardAt = now - 12 * 60_000L) },
        "used-up" to { t, now -> t.copy(failure = Failure.USED_UP, left = 0, heardAt = now - 12 * 60_000L) },
        "few" to { t, _ -> t.copy(left = 12) },
    )

    /** Every name `flight show` takes, and what may follow a flight's. */
    val names: String get() = (flights.keys + failures.keys + listOf("looking", "none", "no-key")).joinToString(" ") + " | after a flight: " + turns.keys.joinToString(" ")

    /** The sample called [name] as it stands at [now] (wall clock, milliseconds); [turn]: how its last ask went. Null: no such sample. */
    fun of(name: String, turn: String?, now: Long): Staged? {
        failures[name]?.let { return it.takeIf { turn == null } }
        when (name) {
            "looking" -> return Staged.Looking(SHOWN).takeIf { turn == null }
            "none" -> return Staged.Empty.takeIf { turn == null }
            "no-key" -> return Staged.NoKey.takeIf { turn == null }
        }
        val f = flights[name]?.invoke(now) ?: return null
        val t = Tracked(f.number, (f.from.planned ?: f.from.time)?.toLocalDate()?.toString(), f, askedAt = now, heardAt = now, left = 940, alertSince = FlightRules.alertSince(f, null, now))
        return Staged.Following(if (turn == null) t else (turns[turn] ?: return null)(t, now))
    }

    /**
     * An airport's own time [minutes] after [now], counted from the minute that [now] is in: a countdown
     * then starts on its figure ("1h 37m" for 97) and steps when the clock's minute does.
     */
    private fun local(now: Long, minutes: Long, offset: Int): LocalDateTime =
        LocalDateTime.ofEpochSecond(Math.floorDiv(now, 60_000L) * 60 + minutes * 60, 0, ZoneOffset.ofTotalSeconds(offset * 60))

    private fun sfo(planned: LocalDateTime, expected: LocalDateTime? = null, actual: LocalDateTime? = null, gate: String? = "G13", terminal: String? = "1") =
        FlightEnd("SFO", "San Francisco", planned, expected, actual, SFO, terminal, gate)

    private fun fra(planned: LocalDateTime, expected: LocalDateTime? = null, actual: LocalDateTime? = null, gate: String? = null, belt: String? = null) =
        FlightEnd("FRA", "Frankfurt", planned, expected, actual, FRA, "1", gate, belt)

    /** It left [ago] minutes ago, seven minutes after its plan. */
    private fun left(now: Long, ago: Long) = sfo(local(now, -ago - 7, SFO), local(now, -ago, SFO), local(now, -ago, SFO))

    private fun lh455(from: FlightEnd, to: FlightEnd, state: FlightState, timetable: Boolean = false) =
        Flight(NUMBER, "Lufthansa", from, to, state, timetable = timetable, aircraft = "Boeing 747-8".takeIf { from.actual != null }, callsign = "DLH455")
}
