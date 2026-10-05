package io.github.kuscher.bentobar.items

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Made-up readings for testing on a device (debug builds: `./bento debug weather stage rain-soon`).
 * Each stands for a row of the product's table and is built from the moment it is staged at, so its
 * hours and days are the right ones whenever that is. Nothing is asked for a sample, and it names no
 * real place. Pure, so a unit test checks that every sample reads as its row is written.
 */
object WeatherSamples {
    /** Where the samples are: nowhere. Never sent, never kept. */
    const val PLACE = "0.00,0.00"

    val names = listOf("clear", "rain-soon", "rain-later", "raining", "storm", "snow", "old", "error", "slow-down", "offline", "offline-new",
        "no-answer", "loading")

    /** [reading]: what is shown instead of the real one; null is "still loading". */
    class Sample(val name: String, val reading: Reading?)

    /** The sample called [name], as if read at [now] in [zone]; null for a name that is none. */
    fun of(name: String, now: Long, zone: ZoneId): Sample? = when (name) {
        "clear" -> day(now, zone, code = 0)
        // An 80% chance of rain in the hour after next: between one and two hours off, so the rule's two hours reach it and one hour doesn't.
        "rain-soon" -> day(now, zone, wet = 2)
        // The same four to five hours off: the item stays hidden.
        "rain-later" -> day(now, zone, wet = 5)
        "raining" -> day(now, zone, code = 63)
        "storm" -> day(now, zone, code = 95)
        "snow" -> day(now, zone, code = 73, temp = -20.0) // −4 °F
        // Read three hours and a minute ago, and offline since: no number any more.
        "old" -> day(now - WeatherRules.OLD_MS - 60_000, zone)?.copy(failure = Failure.OFFLINE)
        "error" -> Reading(PLACE, failure = Failure.NO_ANSWER, misses = 1)
        "slow-down" -> Reading(PLACE, failure = Failure.SLOW_DOWN, misses = 1)
        // Not live: the numbers stay, and the menu's note says why they are not new.
        "offline" -> day(now, zone)?.copy(failure = Failure.OFFLINE)
        "no-answer" -> day(now, zone)?.copy(failure = Failure.NO_ANSWER, misses = 1)
        "offline-new" -> Reading(PLACE, failure = Failure.OFFLINE)
        "loading" -> null
        else -> return null
    }.let { Sample(name, it) }

    /** The afternoon the design draws: 72 °F, feels like 68, a high of 78 and a low of 61, in metric as the service sends it. */
    private val HOURS = listOf(22.4, 22.2, 21.7, 20.6, 18.9, 17.8, 16.7)
    private val SUNRISE = LocalTime.of(7, 8)
    private val SUNSET = LocalTime.of(18, 42)

    /** The days from tomorrow on, as the design's rows have them: rain on the first, 78 °F and 61 °F. */
    private class Next(val code: Int, val high: Double, val low: Double, val chance: Int)
    private val DAYS = listOf(Next(63, 25.6, 16.1, 60), Next(1, 23.9, 15.0, 0), Next(0, 26.7, 16.7, 5), Next(0, 27.8, 17.2, 10),
        Next(3, 23.3, 15.6, 20), Next(3, 22.0, 15.0, 30))

    /**
     * One day's reading taken at [now]: the sky is [code] and it is [temp] degrees; with [wet], the
     * hour that many hours after the one that is running has an 80% chance of rain.
     */
    private fun day(now: Long, zone: ZoneId, code: Int = 2, temp: Double = 22.2, wet: Int = -1): Reading? {
        val here = Instant.ofEpochMilli(now).atZone(zone)
        val firstHour = here.truncatedTo(ChronoUnit.HOURS)
        val today = here.toLocalDate()
        val shift = temp - 22.2
        fun light(t: ZonedDateTime) = t.toLocalTime().let { it >= SUNRISE && it < SUNSET }
        fun start(date: LocalDate) = date.atStartOfDay(zone).toEpochSecond()
        fun sun(date: LocalDate, time: LocalTime) = date.atTime(time).atZone(zone).toEpochSecond()
        return Reading(
            place = PLACE, fetchedAt = now, zone = zone.id, offsetSec = here.offset.totalSeconds,
            current = Current(now / 1000, temp, temp - 2.2, code, light(here), windKmh = 14.5),
            hours = (0 until 24).map { i ->
                val t = firstHour.plusHours(i.toLong())
                Hour(t.toEpochSecond(), HOURS.getOrElse(i) { 16.0 } + shift, if (i == wet) 80 else 0, if (i == wet) 61 else code, light(t))
            },
            days = listOf(Day(start(today), code, 25.6 + shift, 16.1 + shift, 20, sun(today, SUNRISE), sun(today, SUNSET))) +
                DAYS.mapIndexed { i, d ->
                    val date = today.plusDays(i + 1L)
                    Day(start(date), d.code, d.high + shift, d.low + shift, d.chance, sun(date, SUNRISE), sun(date, SUNSET))
                },
        )
    }
}
