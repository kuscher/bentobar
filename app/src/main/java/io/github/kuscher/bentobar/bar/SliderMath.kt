package io.github.kuscher.bentobar.bar

/**
 * The arithmetic of the slider in the bar, apart from its drawing and its pointer events so that it
 * is unit-tested on the JVM (`SliderMathTest`). Pure Kotlin.
 */
object SliderMath {
    /**
     * The level, 0 to 1, for a pointer at [x] over a track that starts at [left] and is [width] wide
     * (all in the same pixels): how far along it is, kept within the track, counted from the other
     * end when the layout runs right to left, and moved to the nearest of [steps] steps.
     */
    fun level(x: Float, left: Float, width: Float, steps: Int, rtl: Boolean = false): Float {
        if (width <= 0f) return 0f
        val along = ((x - left) / width).coerceIn(0f, 1f)
        return snap(if (rtl) 1f - along else along, steps)
    }

    /** [level] kept within 0 to 1 and, with [steps] > 0, moved to the nearest step (step k of n is k / n). */
    fun snap(level: Float, steps: Int): Float {
        val l = if (level.isNaN()) 0f else level.coerceIn(0f, 1f)
        return if (steps <= 0) l else Math.round(l * steps).toFloat() / steps
    }

    /** Which step [level] stands for: 0 to [steps]. What an item hands to the system (a volume index). */
    fun step(level: Float, steps: Int): Int = if (steps <= 0) 0 else Math.round(snap(level, steps) * steps)

    /** How much of the track's [width] is filled at [level], in pixels. */
    fun filled(level: Float, width: Float): Float = width * (if (level.isNaN()) 0f else level.coerceIn(0f, 1f))
}
