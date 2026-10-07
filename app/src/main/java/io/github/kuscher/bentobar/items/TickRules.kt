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

    /**
     * When an item computed by hand at [now] (a click, a refresh, an answer that came in) counts as
     * computed: at the tick before it, [lastTick], so the next tick is still its turn. Stamped [now],
     * a click late in a second made the next tick skip it: a seconds clock stood for 1.7 s and jumped
     * by two. Before the first tick, or with a tick time that lies ahead, [now].
     */
    fun stamp(now: Long, lastTick: Long?): Long = if (lastTick != null && lastTick <= now) lastTick else now

    /** How long an item that had something to say stays out after it has nothing more ("show when" items of [holds] types). */
    const val HOLD_MS = 20_000L

    /**
     * The types whose reading goes up and down around the user's line (speed, load): held, so they don't blink. Not the
     * others: what has ended goes at once (a meeting's next title must not show while presenting), and Now playing and
     * Heat hold on their own.
     */
    fun holds(type: String): Boolean = type in HOLDING

    private val HOLDING = setOf("network", "cpu", "memory")

    /**
     * Whether an item counts as having something to say at [now]: it [active]ly has, or had at
     * [lastActive] less than [HOLD_MS] ago. One reading a second decided it before, so an item at its
     * threshold (a download in bursts, a CPU near its number) blinked in and out and the strip shifted.
     */
    fun held(active: Boolean, lastActive: Long?, now: Long): Boolean =
        active || (lastActive != null && now - lastActive in 0 until HOLD_MS)
}
