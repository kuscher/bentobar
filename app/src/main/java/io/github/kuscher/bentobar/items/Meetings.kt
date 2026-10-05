package io.github.kuscher.bentobar.items

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * The Next meeting item's rules. Pure Kotlin (no Android), so they're unit-tested on the JVM.
 * Task-app events never get here: [Calendar] leaves them out when it loads.
 */
object Meetings {
    /** The horizon's time of day, tomorrow: today, plus 3 hours for a midnight or 1 a.m. meeting. */
    private val CUTOFF: LocalTime = LocalTime.of(3, 0)

    /**
     * The end of the horizon: "today, plus 3 hours". 03:00 after the current evening, so a midnight
     * or 1 a.m. meeting still counts. Between midnight and 03:00 the evening is still "today", so
     * it's 03:00 that same night, not 26 hours away. Nothing after it counts, ever.
     */
    fun horizon(now: Long, zone: ZoneId): Long {
        val t = Instant.ofEpochMilli(now).atZone(zone)
        val day = if (t.toLocalTime() < CUTOFF) t.toLocalDate() else t.toLocalDate().plusDays(1)
        return day.atTime(CUTOFF).atZone(zone).toInstant().toEpochMilli()
    }

    /** On now, or starting before [horizon]; an event that has ended is out. */
    fun inHorizon(begin: Long, end: Long, now: Long, horizon: Long): Boolean = end > now && begin < horizon

    /**
     * A meeting is timed (not all-day), on a calendar the user can edit (contributor access or more:
     * not holidays, birthdays or subscriptions), and has a video-call link or an attendee besides the
     * user. So a flight or a solo appointment is never one.
     */
    fun isMeeting(allDay: Boolean, editable: Boolean, hasCallLink: Boolean, hasOthers: Boolean): Boolean =
        !allDay && editable && (hasCallLink || hasOthers)

    /** What the calendar loads: the [usual] window, then whatever part of the [viewed] month lies before or after it. */
    fun spans(usual: LongRange, viewed: LongRange?): List<LongRange> = buildList {
        add(usual)
        if (viewed == null) return@buildList
        if (viewed.first < usual.first) add(viewed.first..minOf(viewed.last, usual.first))
        if (viewed.last > usual.last) add(maxOf(viewed.first, usual.last)..viewed.last)
    }
}
