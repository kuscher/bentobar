package io.github.kuscher.bentobar.items

/** When the ticker computes an item again. No Android types, so the arithmetic is tested. */
object TickRules {
    /**
     * How much earlier than its type's interval an item may be computed again. Ticks aim at the start
     * of each second, the main thread permitting, so two of them can be 990 ms apart: without slack,
     * "a second has not passed" cost a one-second item that tick, and it showed its news a second late
     * (the volume slider after a volume key).
     */
    const val SLACK_MS = 250L

    /** Whether an item last computed at [last] has its turn at [now], when its type asks for every [everyMs] milliseconds. */
    fun due(now: Long, last: Long, everyMs: Long): Boolean = now - last >= everyMs - SLACK_MS
}
