package io.github.kuscher.bentobar.bar

/**
 * When the status bar's colours are read, after something that can change them. No Android types, so
 * the cases the devices showed are unit-tested.
 *
 * The bar says nothing when its look changes, and it changes late: it fades, a window's bounds say
 * "maximized" before the bar turns black, and after an unlock it can keep the lock screen's light
 * icons for seconds. One reading soon after the cause can show the look from before, and a reading
 * that matches the last one is no proof that the change is over. Reading once, and once more only if
 * that differed, left the strip white after an unlock until the next change of windows.
 *
 * So every cause is followed by readings spread over the time the bar may take, each of them taken
 * whatever the one before showed, and a look that still differed at the last one is read again.
 *
 * A reading is a screenshot, and screenshots must stay few (see CLAUDE.md, Play Protect). Readings are
 * tied to a cause and end; none is taken on a timer. Causes that keep coming are held to a budget:
 * after [BUSY] readings within a minute a cause's first reading waits two seconds behind the last
 * one, and what follows it comes later ([LATER_MS]), where the next cause of a stream takes its
 * place. Nothing is dropped: the last cause of a stream still gets its readings.
 */
class ColorWatch {
    enum class Cause(internal val after: LongArray, internal val busy: LongArray) {
        /** A window settled against the bar or under it, or left; the wallpaper changed; or a fresh look is wanted: the bar follows within the second. */
        QUICK(longArrayOf(0, 1_000), longArrayOf(0, LATER_MS)),
        /**
         * The strip is back on screen (an unlock, a wake, a full-screen app or a panel gone), or the
         * theme or the display changed: the bar can take seconds, more after a sleep.
         */
        SLOW(longArrayOf(0, 750, 1_750, 3_250, 5_750, 9_750), longArrayOf(0, 3_250, 9_750)),
    }

    /** What a reading showed, against the colours the strip had. */
    enum class Result { SAME, CHANGED, NOTHING }

    private val slow = ArrayList<Long>()
    private val quick = ArrayList<Long>()
    private var again = 0
    private var nothing = 0
    /** When screenshots were asked for within the last minute, oldest first. */
    private val asked = ArrayDeque<Long>()
    /** The first reading a change of windows still waits for. */
    private var windowsDue: Long? = null
    /** When a change of windows last got its first reading. */
    private var windowsRead = -BarNeighbours.APART_MS
    /** The series still owed while the strip has nothing to draw ([pause]): SLOW, QUICK or none. */
    private var owed: Cause? = null

    /**
     * Something that can change the bar happened at [now]; the first reading is wanted [first] ms later.
     * A quick cause takes the place of the readings an earlier one still had to come (in a stream of
     * window changes only the last gets its second reading) and leaves a slow one's late readings
     * alone. A slow cause starts over.
     */
    fun cause(cause: Cause, now: Long, first: Long) {
        val busy = busy(now)
        var start = now + first
        // Not on the heels of a screenshot just asked for: Android would refuse this one.
        asked.lastOrNull()?.let { start = maxOf(start, it + if (busy) BarNeighbours.APART_MS else MERGE_MS) }
        val times = (if (busy) cause.busy else cause.after).map { start + it }
        quick.clear(); windowsDue = null
        if (cause == Cause.SLOW) { slow.clear(); slow += times } else quick += times
        again = 0
    }

    /**
     * A window settled against the bar or under it, or left, at [now]. A quick cause whose first
     * readings keep two seconds apart ([BarNeighbours.wait]): under a full-screen app every screen it
     * opens is another window under the bar. Only these first readings count for that: a window
     * maximized right after an unlock is not held back by the readings the unlock asked for.
     */
    fun windows(now: Long) {
        cause(Cause.QUICK, now, BarNeighbours.wait(now - windowsRead))
        windowsDue = quick.first()
    }

    /** Milliseconds from [now] to the next reading (0 when it is overdue), or null when none is to come. */
    fun due(now: Long): Long? = (slow + quick).minOrNull()?.let { (it - now).coerceAtLeast(0) }

    /**
     * A screenshot is asked for at [now]. It serves every reading that was due by now, or would be
     * within the moment, and from here on a cause knows that it is on its way.
     */
    fun taken(now: Long) {
        asked += now
        while (asked.first() <= now - MINUTE_MS) asked.removeFirst()
        slow.removeAll { it <= now + MERGE_MS }
        quick.removeAll { it <= now + MERGE_MS }
        windowsDue?.let { if (it <= now + MERGE_MS) { windowsRead = now; windowsDue = null } }
    }

    /**
     * The screenshot's answer came at [now]. True when it was the third in a row that gave nothing: the
     * strip then uses the theme and the wallpaper's hints rather than how the bar looked before, until
     * a reading shows something.
     */
    fun read(now: Long, result: Result): Boolean {
        val last = slow.isEmpty() && quick.isEmpty()
        when (result) {
            Result.SAME -> nothing = 0
            Result.CHANGED -> {
                nothing = 0
                // Caught halfway through a fade, perhaps: read again when it has settled.
                if (last && again < MAX_AGAIN) { again++; quick += now + if (busy(now)) LATER_MS else SETTLE_MS }
            }
            Result.NOTHING -> {
                nothing++
                if (last && nothing < GIVE_UP) quick += now + RETRY_MS
                return nothing == GIVE_UP
            }
        }
        return false
    }

    /** The strip left the screen: what was to be read no longer matters, and its return asks again. */
    fun clear() {
        slow.clear(); quick.clear(); windowsDue = null
        again = 0
        owed = null
    }

    /**
     * The strip is on screen with nothing to draw: no readings (an invisible window of ours reading the
     * screen is what Play Protect flags). The series still to come is owed until [resume].
     */
    fun pause() {
        owe(if (slow.isNotEmpty()) Cause.SLOW else if (quick.isNotEmpty()) Cause.QUICK else null)
        slow.clear(); quick.clear(); windowsDue = null
        again = 0
    }

    /** A [cause] while the strip has nothing to draw: it waits for [resume]; a slow one isn't replaced by a quick one. */
    fun owe(cause: Cause?) {
        if (cause != null && owed != Cause.SLOW) owed = cause
    }

    /** The strip has something to draw again at [now]: the owed series starts [first] ms later, two quick readings if none. */
    fun resume(now: Long, first: Long) {
        val cause = owed ?: Cause.QUICK
        owed = null
        cause(cause, now, first)
    }

    private fun busy(now: Long) = asked.count { it > now - MINUTE_MS } >= BUSY

    companion object {
        /** What a reading that [seen] the bar (null: it gave nothing) is, against the colours the strip [had]. */
        fun result(had: Any?, seen: Any?): Result = when (seen) { null -> Result.NOTHING; had -> Result.SAME; else -> Result.CHANGED }

        /** Readings closer together than this are one reading; Android refuses a screenshot within a third of a second of the last. */
        private const val MERGE_MS = 400L
        /** A fade is over this long after a reading that caught it. */
        private const val SETTLE_MS = 800L
        private const val MAX_AGAIN = 3
        /** A reading that gave nothing (the bar caught fading, a screenshot refused) is tried again after this. */
        private const val RETRY_MS = 1_500L
        private const val GIVE_UP = 3
        /** This many readings within a minute, and causes are held back. An unlock and a few window changes stay under it. */
        private const val BUSY = 12
        private const val MINUTE_MS = 60_000L
        /** What follows a first reading while busy: long enough for the next cause of a stream to come first. */
        private const val LATER_MS = 4_000L
    }
}
