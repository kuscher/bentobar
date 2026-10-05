package io.github.kuscher.bentobar.bar

/**
 * The arithmetic of the slider in the bar, apart from its drawing and its pointer events so that it
 * is unit-tested on the JVM (`SliderMathTest`). Pure Kotlin. A level is 0 to 1; a slider with steps
 * (15 for a volume of 0 to 15) only ever stands on k / steps.
 */
object SliderMath {
    /**
     * The level for a pointer at [x] over a track that starts at [left] and is [width] wide (all in
     * the same pixels): how far along it is, kept within the track (a pointer past either end, or
     * past the bar, means that end), counted from the other end when the layout runs right to left,
     * and moved to the nearest of [steps] steps.
     */
    fun level(x: Float, left: Float, width: Float, steps: Int, rtl: Boolean = false): Float {
        if (width <= 0f) return 0f
        val along = ((x - left) / width).coerceIn(0f, 1f)
        return snap(if (rtl) 1f - along else along, steps)
    }

    /** [level] kept within 0 to 1 and, with [steps] > 0, moved to the nearest step. */
    fun snap(level: Float, steps: Int): Float {
        val l = if (level.isNaN()) 0f else level.coerceIn(0f, 1f)
        return if (steps <= 0) l else Math.round(l * steps).toFloat() / steps
    }

    /** Which step [level] stands for, 0 to [steps]: what an item hands to the system (a volume index). */
    fun step(level: Float, steps: Int): Int = if (steps <= 0) 0 else Math.round(snap(level, steps) * steps)

    /**
     * [level], but never nothing: a click or a tap sets one step at the least (a slip at the track's
     * very start must not turn the sound off; a drag may). Without steps the least is a 64th, one dp
     * of the track.
     */
    fun atLeastOneStep(level: Float, steps: Int): Float = maxOf(snap(level, steps), if (steps > 0) 1f / steps else 1f / 64f)

    /**
     * How much of a track [track] wide is drawn filled at [level]: nothing at 0, and otherwise at
     * least [min] (a dot), so that the lowest step can be told from none.
     */
    fun fillWidth(level: Float, track: Float, min: Float): Float {
        val l = if (level.isNaN()) 0f else level.coerceIn(0f, 1f)
        return if (l <= 0f || track <= 0f) 0f else (l * track).coerceIn(minOf(min, track), track)
    }

    /** Where the handle's middle stands along the track: on the level, but [edge] in from either end, so it never hangs over. */
    fun handleCenter(level: Float, track: Float, edge: Float): Float {
        val l = if (level.isNaN()) 0f else level.coerceIn(0f, 1f)
        return if (track <= 2 * edge) track / 2 else (l * track).coerceIn(edge, track - edge)
    }
}
