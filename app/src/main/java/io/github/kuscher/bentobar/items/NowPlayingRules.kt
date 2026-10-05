package io.github.kuscher.bentobar.items

/**
 * The rules of the now-playing source that need no Android: which player comes first, where a track
 * stands by the clock, and a title made fit to show. Pure Kotlin, unit-tested (`NowPlayingRulesTest`).
 */
object NowPlayingRules {
    /** What the order of players is decided by. */
    data class Standing(val key: String, val playing: Boolean, val startedAt: Long, val lastPlayedAt: Long)

    /**
     * The players in the order they are shown: the ones playing first, the one that started last at
     * the top (the bar follows it); then the others, the one that played most recently first. Ties
     * keep the order the system gave.
     */
    fun order(players: List<Standing>): List<String> =
        players.withIndex().sortedWith(
            compareByDescending<IndexedValue<Standing>> { it.value.playing }
                .thenByDescending { if (it.value.playing) it.value.startedAt else it.value.lastPlayedAt }
                .thenBy { it.index },
        ).map { it.value.key }

    /**
     * Where a track stands at [now]: [positionMs] was true at [positionAt] (the same clock as [now]),
     * and while it plays it moves on at [speed]. Never before the start, never past [durationMs]
     * where that is known (above 0).
     */
    fun position(positionMs: Long, positionAt: Long, speed: Float, playing: Boolean, durationMs: Long, now: Long): Long {
        val moved = if (playing && speed != 0f && positionAt > 0) ((now - positionAt).coerceAtLeast(0) * speed).toLong() else 0L
        val p = (positionMs + moved).coerceAtLeast(0)
        return if (durationMs > 0) p.coerceAtMost(durationMs) else p
    }

    /**
     * [text] as one line: line breaks, tabs and other control characters become spaces, runs of
     * spaces one, none at the ends; at most [max] characters, cut between whole characters (never
     * through the two halves of an emoji). Null is the empty string.
     */
    fun oneLine(text: CharSequence?, max: Int = 200): String {
        if (text == null) return ""
        val out = StringBuilder(minOf(text.length, max))
        var space = true // true at the start, so that leading spaces are dropped
        var i = 0
        while (i < text.length) {
            val cp = Character.codePointAt(text, i)
            i += Character.charCount(cp)
            val blank = Character.isWhitespace(cp) || Character.isISOControl(cp) || cp == 0x00A0 || cp == 0x2028 || cp == 0x2029
            if (blank) {
                if (!space) out.append(' ')
                space = true
            } else {
                if (out.length + Character.charCount(cp) > max) break
                out.appendCodePoint(cp)
                space = false
            }
        }
        return out.toString().trimEnd()
    }
}
