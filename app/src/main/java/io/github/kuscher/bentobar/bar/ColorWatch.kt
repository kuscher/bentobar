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
 * Readings stay tied to a cause and end: nothing here reads on a timer (see CLAUDE.md, Play Protect).
 */
class ColorWatch {
    enum class Cause(internal val after: LongArray) {
        /** A window settled against the bar or under it, or left; or a fresh look is wanted: the bar follows within the second. */
        QUICK(longArrayOf(0, 1_000)),
        /**
         * The strip is back on screen (an unlock, a wake, a full-screen app or a panel gone), or the
         * theme, the wallpaper or the display changed: the bar can take seconds, more after a sleep.
         */
        SLOW(longArrayOf(0, 750, 1_750, 3_250, 5_750, 9_750)),
    }

    /** What a reading showed, against the colours the strip had. */
    enum class Result { SAME, CHANGED, NOTHING }

    private val slow = ArrayList<Long>()
    private val quick = ArrayList<Long>()
    private var again = 0
    private var nothing = 0
    private var lastRead: Long? = null

    /**
     * Something that can change the bar happened at [now]; the first reading is wanted [first] ms later.
     * A quick cause takes the place of the readings an earlier one still had to come (in a stream of
     * window changes only the last gets its second reading) and leaves a slow one's late readings
     * alone. A slow cause starts over.
     */
    fun cause(cause: Cause, now: Long, first: Long) {
        // Not on the heels of a reading just taken: Android would refuse the screenshot.
        val start = lastRead?.let { maxOf(now + first, it + MERGE_MS) } ?: (now + first)
        val times = cause.after.map { start + it }
        quick.clear()
        if (cause == Cause.SLOW) { slow.clear(); slow += times } else quick += times
        again = 0
    }

    /** Milliseconds from [now] to the next reading (0 when it is overdue), or null when none is to come. */
    fun due(now: Long): Long? = (slow + quick).minOrNull()?.let { (it - now).coerceAtLeast(0) }

    /**
     * A reading was taken at [now]. True when it was the third in a row that gave nothing: the strip
     * then uses the theme and the wallpaper's hints rather than how the bar looked before, until a
     * reading shows something.
     */
    fun read(now: Long, result: Result): Boolean {
        lastRead = now
        // One reading serves all that were due by now, or would be within the moment.
        slow.removeAll { it <= now + MERGE_MS }
        quick.removeAll { it <= now + MERGE_MS }
        val last = slow.isEmpty() && quick.isEmpty()
        when (result) {
            Result.SAME -> nothing = 0
            Result.CHANGED -> {
                nothing = 0
                // Caught halfway through a fade, perhaps: read again when it has settled.
                if (last && again < MAX_AGAIN) { again++; quick += now + SETTLE_MS }
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
        slow.clear(); quick.clear()
        again = 0
    }

    companion object {
        /** Readings closer together than this are one reading; Android refuses a screenshot within a third of a second of the last. */
        private const val MERGE_MS = 400L
        /** A fade is over this long after a reading that caught it. */
        private const val SETTLE_MS = 800L
        private const val MAX_AGAIN = 3
        /** A reading that gave nothing (the bar caught fading, a screenshot refused) is tried again after this. */
        private const val RETRY_MS = 1_500L
        private const val GIVE_UP = 3
    }
}
