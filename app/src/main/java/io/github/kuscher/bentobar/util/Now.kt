package io.github.kuscher.bentobar.util

import android.os.SystemClock

/**
 * The clock for everything that asks "how old is this" or "how long until": readings, countdowns,
 * when to ask a service again. One place, so that a test on a device can move all of it at once:
 * [ahead] is only ever set by the adb test hook of debug builds (`./bento debug now +3h`), and is 0
 * in every release.
 *
 * Rules that depend on time take the time as an argument and stay pure; their callers pass [wall] or
 * [elapsed].
 */
object Now {
    /** Milliseconds this clock runs ahead of the real one. Debug builds' test hook only. */
    @Volatile var ahead: Long = 0

    /** Wall-clock time, in milliseconds since 1970. */
    fun wall(): Long = System.currentTimeMillis() + ahead

    /** Time since boot, counting sleep: for ages and intervals, which a change of the wall clock must not move. */
    fun elapsed(): Long = SystemClock.elapsedRealtime() + ahead
}
