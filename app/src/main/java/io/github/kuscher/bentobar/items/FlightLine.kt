package io.github.kuscher.bentobar.items

import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * The arithmetic of a flight's line in the menu: for a line of a given width, where its dots stand,
 * how far the solid part reaches and where the plane is. In the canvas's own units, and counted from
 * the departure's end: the canvas mirrors it where the language reads from the right. Pure, so the
 * rules are unit-tested and the drawing only draws.
 */
object FlightLine {
    /**
     * [start], [end]: the centers of the first and the last dot the line can have, which stand on its
     * two ends. [pitch]: from one dot's center to the next. [dots]: the centers of the dots that are
     * drawn, the part still to fly. [flownUntil]: where the solid part ends, which begins at [start];
     * null: there is none. [center]: the plane's; null: no plane.
     */
    class Drawn(val start: Float, val end: Float, val pitch: Float, val dots: List<Float>, val flownUntil: Float?, val center: Float?)

    private val NOTHING = Drawn(0f, 0f, 0f, emptyList(), null, null)

    /**
     * The line for [width] and the [share] of the flight that is behind it (null: nobody knows where
     * it is, so there is no plane). Dots of [dot] across stand about [apart] from each other, exactly
     * so far that the first and the last are on the line's ends, and they never move: the plane passes
     * over them. The plane is [plane] long and keeps half of that from either end; the solid part ends
     * [behind] short of its tail, and the first dot drawn is the first that is [ahead] clear of its nose.
     */
    fun of(width: Float, share: Double?, dot: Float, apart: Float, plane: Float, behind: Float, ahead: Float): Drawn {
        val length = width - dot
        // (Written so that a width nobody can draw on, not a number included, comes out as nothing.)
        if (!(length > 0f) || !(apart > 0f)) return NOTHING
        val start = dot / 2
        val steps = (length / apart).roundToInt().coerceAtLeast(1)
        val pitch = length / steps
        val center = share?.let {
            val along = if (it.isNaN()) 0f else it.coerceIn(0.0, 1.0).toFloat()
            // On a line shorter than the plane there is no way to go: it stands in the middle.
            if (width <= plane) width / 2 else plane / 2 + along * (width - plane)
        }
        val flownUntil = center?.let { it - plane / 2 - behind }?.takeIf { it > start }
        val clear = if (center == null) start else center + plane / 2 + ahead + dot / 2
        val first = ceil(((clear - start) / pitch - 1e-4f).coerceAtLeast(0f)).toInt()
        return Drawn(start, start + length, pitch, (first..steps).map { start + it * pitch }, flownUntil, center)
    }
}
