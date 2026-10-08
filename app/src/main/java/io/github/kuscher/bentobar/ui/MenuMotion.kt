package io.github.kuscher.bentobar.ui

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * How a popup moves (docs/design/1.3/motion.md): it unfolds down from a lip under its item, its
 * contents drop into place one block after another, and it folds back into the item when it goes.
 * Everything is a function of the time since the popup's first drawn frame, so one clock drives the
 * glass, its blur and shadow, and every block, and the arithmetic is tested on its own
 * (`MenuMotionTest`). No Android types.
 */
object MenuMotion {
    /**
     * The opening: a slow start, a fast middle and a slow landing, with no overshoot. v2 (motion.md §7): Booklight's
     * approved shape with a shorter wait, (0.45, 0, 0.1, 1) rather than (0.55, 0, 0.1, 1).
     */
    val OPENS = Curve(0.45f, 0f, 0.1f, 1f)
    /** The glass coming into presence, early in the slow start so it doesn't pop. */
    val PRESENTS = Curve(0.2f, 0f, 0f, 1f)
    /** Booklight's fold, back into the item. */
    val FOLDS = Curve(0.45f, 0f, 0.4f, 1f)

    /** The height a popup opens from and folds back to: something shows within two frames. */
    const val LIP_DP = 20f
    /** The glass's corner radius (visual.md); a capsule while the glass is shorter than twice this. */
    const val RADIUS_DP = 24f
    const val PRESENCE_MS = 80f
    /** How far above its place a block starts. */
    const val DROP_DP = 6f
    /** A popup left for another one dissolves in place in this time. */
    const val DISSOLVE_MS = 75f

    private const val CONTENTS_OUT_MS = 40f
    private const val PRESENCE_OUT_MS = 50f
    /** Presence starts to fall this long before the fold ends, and is gone this long after. */
    private const val FADE_LEAD_MS = 25f
    private const val CONTENTS_BACK_MS = 100f

    /** How long a popup [heightDp] tall takes to open: a fixed time would move a tall popup's edge too fast, a fixed speed take too long. */
    fun openMs(heightDp: Float): Float = (160f + 0.16f * heightDp).coerceIn(190f, 270f)

    /** How long a popup [fromDp] tall of [heightDp] takes to fold away and fade: the fold, and the fade's last 30 ms. */
    fun closeMs(fromDp: Float, heightDp: Float): Float = fold(fromDp, heightDp) + FADE_LEAD_MS

    private fun fold(fromDp: Float, heightDp: Float): Float = maxOf(50f, 0.45f * openMs(heightDp) * openness(fromDp, heightDp))

    private fun openness(dp: Float, heightDp: Float): Float =
        if (heightDp <= LIP_DP) 1f else ((dp - LIP_DP) / (heightDp - LIP_DP)).coerceIn(0f, 1f)

    /**
     * The glass at a moment: how tall it shows, how present it is (glass, veil, outline and blur
     * together), how strong its shadow, and how visible its contents are as a whole.
     */
    data class Glass(val heightDp: Float, val presence: Float, val shadow: Float, val contents: Float, val done: Boolean) {
        val cornerDp: Float get() = min(RADIUS_DP, heightDp / 2f)
    }

    /** [ms] after the first frame of a popup [heightDp] tall. */
    fun opening(ms: Float, heightDp: Float): Glass {
        if (heightDp <= LIP_DP) return Glass(heightDp, 1f, 1f, 1f, done = true)
        val t = openMs(heightDp)
        val h = LIP_DP + (heightDp - LIP_DP) * OPENS.at(ms / t)
        val presence = PRESENTS.at(ms / PRESENCE_MS)
        return Glass(h, presence, shadow(presence, h, heightDp), 1f, done = ms >= t && ms >= PRESENCE_MS)
    }

    /**
     * [ms] after a popup [fromDp] tall of [heightDp] started to close with [presence]: its contents fade
     * together in place, the glass folds back to the lip, and it fades as it lands.
     */
    fun closing(ms: Float, fromDp: Float, heightDp: Float, presence: Float): Glass {
        val f = fold(fromDp, heightDp)
        val h = fromDp - (fromDp - LIP_DP).coerceAtLeast(0f) * FOLDS.at(ms / f)
        val p = presence * (1f - ((ms - (f - FADE_LEAD_MS)) / PRESENCE_OUT_MS).coerceIn(0f, 1f))
        val contents = 1f - (ms / CONTENTS_OUT_MS).coerceIn(0f, 1f)
        return Glass(h, p, shadow(p, h, heightDp), contents, done = ms >= f + FADE_LEAD_MS)
    }

    /**
     * [ms] after a closing popup was asked to open again, from [fromDp] tall moving at [dpPerMs]
     * (negative while it was folding up): it turns round on a critically damped spring, keeping its
     * speed, so it never passes its full height; presence and contents come back from where they were.
     */
    fun reopening(ms: Float, fromDp: Float, dpPerMs: Float, heightDp: Float, presence: Float, contents: Float): Glass {
        val w = sqrt(1000f) // spring(1.0, 1000), per second
        if (ms.isInfinite()) return Glass(heightDp, 1f, 1f, 1f, done = true)
        val s = ms / 1000f
        val d0 = fromDp - heightDp
        val v0 = dpPerMs * 1000f
        var d = (d0 + (v0 + w * d0) * s) * exp(-w * s)
        val done = abs(d) < 0.05f && ms >= CONTENTS_BACK_MS
        if (done) d = 0f
        val h = (heightDp + d).coerceIn(0f, heightDp)
        val p = presence + (1f - presence) * (ms / PRESENCE_OUT_MS).coerceIn(0f, 1f)
        val c = contents + (1f - contents) * (ms / CONTENTS_BACK_MS).coerceIn(0f, 1f)
        return Glass(h, p, shadow(p, h, heightDp), c, done)
    }

    /** No dark line under a 20 dp lip: the shadow comes with the opening, at full strength by 60 % of the way. */
    private fun shadow(presence: Float, dp: Float, heightDp: Float) = presence * smoothstep(0f, 0.6f, openness(dp, heightDp))

    /** Where a block is and how visible: [dy] dp from its place (negative: above it), [alpha]. */
    data class Block(val dy: Float, val alpha: Float, val done: Boolean)

    /**
     * When block [index] of [count] starts: each 20 ms after the one above, closer together in a tall popup, so the
     * last still starts by 190 ms and none arrive in a clump (a fixed cap left the lower glass empty, then filled it at once).
     */
    fun blockStartMs(index: Int, count: Int): Float = 24f + index * if (count <= 1) 16f else min(16f, 128f / (count - 1))

    /**
     * Block [index] [ms] after the popup's first frame: it drops [DROP_DP] into place on a stiffer
     * Booklight `place` spring (0.86, 700), which overshoots by about 0.03 dp, so nothing shrinks back. It is
     * readable well before the last few dp settle; the glass's edge already hides what it hasn't reached.
     */
    fun block(index: Int, ms: Float, count: Int): Block {
        val s = (ms - blockStartMs(index, count)) / 1000f
        if (s <= 0f) return Block(-DROP_DP, 0f, false)
        if (s > 1f) return Block(0f, 1f, true) // long settled (and "Remove animations": no time at all)
        val x = spring(-DROP_DP, s, 0.86f, 700f)
        if (abs(x) < 0.02f && s > 0.2f) return Block(0f, 1f, true)
        return Block(x, smoothstep(0f, 0.6f, 1f - abs(x) / DROP_DP), false)
    }

    /** The time when every block of a popup with [blocks] blocks is at rest, for the clock to stop. */
    fun blocksDoneMs(blocks: Int): Float = blockStartMs(blocks - 1, blocks) + 400f

    /** A popup's alpha [ms] after it was left for another. */
    fun dissolve(ms: Float): Float = 1f - (ms / DISSOLVE_MS).coerceIn(0f, 1f)

    /**
     * [elapsedMs] of real time as motion time under the system's animator duration [scale]: 2 is
     * twice as slow; 0 ("Remove animations") is every motion at its end at once.
     */
    fun scaled(elapsedMs: Float, scale: Float): Float = if (scale <= 0f) Float.POSITIVE_INFINITY else elapsedMs / scale

    /** A damped spring released still [from] its rest, [s] seconds later (mass 1). */
    private fun spring(from: Float, s: Float, damping: Float, stiffness: Float): Float {
        val w0 = sqrt(stiffness)
        val zw = damping * w0
        if (damping >= 1f) return from * (1f + w0 * s) * exp(-w0 * s)
        val wd = w0 * sqrt(1f - damping * damping)
        return exp(-zw * s) * (from * cos(wd * s) + zw * from / wd * sin(wd * s))
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}

/** A CSS `cubic-bezier(x1, y1, x2, y2)` easing: [at] gives the progress for a time between 0 and 1. */
class Curve(private val x1: Float, private val y1: Float, private val x2: Float, private val y2: Float) {
    fun at(t: Float): Float {
        if (t.isNaN() || t <= 0f) return 0f
        if (t >= 1f) return 1f
        return y(solve(t))
    }

    private fun x(s: Float) = bezier(s, x1, x2)
    private fun y(s: Float) = bezier(s, y1, y2)
    private fun bezier(s: Float, p1: Float, p2: Float): Float {
        val u = 1f - s
        return 3f * u * u * s * p1 + 3f * u * s * s * p2 + s * s * s
    }

    /** The curve's parameter where x is [t]: Newton's method, and halving where it stalls (as browsers do). */
    private fun solve(t: Float): Float {
        var s = t
        for (i in 0 until 8) {
            val e = x(s) - t
            if (abs(e) < 1e-6f) return s
            val d = 3f * (1f - s) * (1f - s) * x1 + 6f * (1f - s) * s * (x2 - x1) + 3f * s * s * (1f - x2)
            if (abs(d) < 1e-6f) break
            s -= e / d
        }
        var lo = 0f
        var hi = 1f
        s = t
        repeat(40) {
            val e = x(s) - t
            if (abs(e) < 1e-6f) return s
            if (e > 0f) hi = s else lo = s
            s = (lo + hi) / 2f
        }
        return s
    }
}
