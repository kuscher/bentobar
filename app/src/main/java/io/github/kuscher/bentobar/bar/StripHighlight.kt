package io.github.kuscher.bentobar.bar

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One item's box in the strip, in dp along the strip's row from its left end: what the pointer is matched against and
 * what the highlight sits on. [key] is the item's id, or "chevron" for ‹. The box is the item's own, its 6 dp of
 * padding at each end included.
 */
data class Span(val key: String, val left: Float, val right: Float)

/**
 * The strip's one highlight (docs/design/1.3: motion.md §4 and §5, visual.md §3). No Android types, so every rule here
 * is unit-tested; `Strip` hands it the pointer and where the items are drawn, runs its frames and draws what it says.
 *
 * One pill for the whole strip, where each item used to light a box of its own that dropped out in the gaps. The
 * pointer carries it from item to item like Booklight's rubber-band rows ([Band]): the edge that leads sets off at
 * once, the old edge holds on for a few frames and then gathers. It fades in on the item first hovered, with no slide,
 * and fades where it stands a moment after the pointer leaves. While an item's popup is open it stays on that item
 * ([hold]), and while a slider is held on its slider's item ([pin]); while an item is dragged it isn't there ([lift]).
 *
 * Only the pointer moves it. An item that moves or resizes carries the pill on its box with no spring of its own
 * ([layout]), and a change of layout under a pointer that stands still waits for the pointer's next move.
 *
 * Times are nanoseconds on one clock (the frames' and [System.nanoTime] on Android); places are dp.
 */
class StripHighlight {
    /** Frames a second of the screen it is drawn on: the old edge's hold is counted in frames. */
    var refresh = 120f
    /**
     * The system's animator duration scale. 0 ("Remove animations") cuts, shows and hides at once; any other value
     * stretches every fade, hold and spring alike. The grace after the pointer leaves is a tolerance, not motion: it stays.
     */
    var scale = 1f

    private var spans: List<Span> = emptyList()
    private val band = Band(0f, 0f)
    private val presence = Fade()
    /** The item the pill is on or on its way to, and its box when it was sent there (an item that moves carries it). */
    var on: String? = null
        private set
    private var aimed: Span? = null
    /** The item under the pointer, as [target] reads it: a click in a gap goes to it. */
    var under: String? = null
        private set
    private var inside = false
    /** When the pointer left the strip; the pill stays for [GRACE_MS] (a pointer slipping below the bar while scrubbing). */
    private var leftAt: Long? = null
    private var pressed = false
    private var held: String? = null
    private var pinned: String? = null
    private var lifted = false
    /**
     * The pill was left where it is until the pointer moves: a popup closed with the pointer over the strip (it stays
     * on the popup's item), or an item was dropped (it stays away).
     */
    private var waiting = false

    /** Where the pill's two ends are drawn, in dp from the row's left end. */
    val left: Float get() = band.first
    val right: Float get() = band.last
    /** How much of it shows, 0 to 1. */
    val alpha: Float get() = presence.value

    /**
     * How strongly the bar's text colour fills it: firmer on the item whose popup is open, firmer still while the item
     * under it is pressed. A held slider keeps the hover strength: the slider's own handle says it's held.
     */
    val fill: Float get() = when {
        pinned != null -> HOVER
        pressed && on != null && on == under -> PRESSED
        held != null && on == held -> HELD
        else -> HOVER
    }

    /** Something moves or fades: frames are wanted. */
    val moving: Boolean get() = band.moving || presence.running

    /** With nothing moving, the time of the next step that changes anything (the grace's end), or null: nothing to wait for. */
    val wakeAt: Long? get() {
        val at = leftAt ?: return null
        return if (!inside && !lifted && !waiting && anchor() == null && presence.to == 1f) at + GRACE_MS * MS else null
    }

    /** The items as they are drawn now, ‹ included, in any order. True when the pill must be drawn again. */
    fun layout(spans: List<Span>, now: Long): Boolean = changes {
        this.spans = spans.sortedBy { it.left }
        // The pill rides on its item's box: whatever moved that box moves the pill by as much, at once.
        val was = aimed
        val box = on?.let { has(it) }
        if (was != null && box != null && box != was) {
            val (l0, r0) = extent(was)
            val (l1, r1) = extent(box)
            band.carry(l1 - l0, r1 - r0)
            aimed = box
        }
        settle(now)
    }

    /** The pointer is over the strip at [x] (dp): it entered, moved or pressed there. */
    fun move(x: Float, now: Long): Boolean = changes {
        under = target(x, spans, under)
        inside = true
        leftAt = null
        waiting = false
        settle(now)
    }

    /** The pointer left the strip (or a finger lifted). */
    fun leave(now: Long): Boolean = changes {
        if (inside) leftAt = now
        inside = false
        waiting = false
        settle(now)
    }

    fun press(down: Boolean, now: Long): Boolean = changes { pressed = down }

    /** [key]'s popup is open ("chevron" for the ‹ menu), or none is (null). */
    fun hold(key: String?, now: Long): Boolean = changes {
        if (key == held) return@changes
        val was = held
        held = key
        // Closed under a pointer that is over the strip: the pill stays on the popup's item until the pointer moves.
        if (key == null && inside && was != null && on == was) waiting = true
        settle(now)
    }

    /** [key]'s slider is held by the pointer (null: let go). The pill stays with it, wherever the pointer goes. */
    fun pin(key: String?, now: Long): Boolean = changes {
        pinned = key
        settle(now)
    }

    /** An item was lifted to be dragged ([up]), or dropped. */
    fun lift(up: Boolean, now: Long): Boolean = changes {
        if (up == lifted) return@changes
        lifted = up
        // After the drop the pill waits for the pointer to move, and shows where it is then.
        if (!up) waiting = true
        settle(now)
    }

    /** One frame at [now]. True while there is more to do: frames ([moving]) or a wait ([wakeAt]). */
    fun step(now: Long): Boolean {
        settle(now)
        if (band.moving) band.frame(now, scale)
        presence.frame(now)
        return moving || wakeAt != null
    }

    private fun has(key: String) = spans.firstOrNull { it.key == key }

    /** The item the pill stays on whatever the pointer does: a held slider's, else an open popup's, if it is in the strip. */
    private fun anchor(): String? = pinned?.takeIf { has(it) != null } ?: held?.takeIf { has(it) != null }

    /** Where the pill should be and whether it should show, from everything it has been told; called on every change and frame. */
    private fun settle(now: Long) {
        val anchor = anchor()
        val grace = leftAt?.let { now - it < GRACE_MS * MS } ?: false
        when {
            lifted -> hide(LIFT_MS)
            anchor != null -> { aim(anchor); show() }
            waiting -> {}
            // An item that left from under a still pointer: the pill stays where it is until the pointer moves.
            inside -> under?.let { has(it) }?.let { aim(it.key); show() }
            grace -> {}
            else -> hide(FADE_MS)
        }
    }

    private fun aim(key: String) {
        val box = has(key) ?: return
        val (l, r) = extent(box)
        on = key
        aimed = box
        // Coming from nothing it is simply there; from anywhere it shows, even half faded, it glides.
        if (presence.value == 0f) band.cut(l, r - l) else band.go(l, r - l, refresh, scale)
    }

    private fun show() = presence.go(1f, FADE_MS, scale)

    private fun hide(ms: Int) {
        if (presence.to == 0f) return
        // It fades where it stands: whatever it was on its way to, and wherever it is sent next, it sets off from rest.
        band.stop()
        presence.go(0f, ms, scale)
    }

    /** What the strip draws and schedules, to tell whether a call changed any of it (most pointer moves don't). */
    private data class Seen(val on: String?, val left: Float, val right: Float, val alpha: Float, val to: Float, val fill: Float,
                            val moving: Boolean, val wakeAt: Long?, val waiting: Boolean)

    private fun seen() = Seen(on, left, right, alpha, presence.to, fill, moving, wakeAt, waiting)

    private inline fun changes(block: () -> Unit): Boolean {
        val before = seen()
        block()
        return seen() != before
    }

    companion object {
        /** The pointer crosses a gap's middle by this much before the item beyond it takes the pill. */
        const val SLOP = 2f
        /** The pill reaches this far past an item's box at each end, is this tall, and keeps this ring to the strip's own edges. */
        const val OUTSET = 4f
        const val HEIGHT = 26f
        const val RING = 2f
        /** An alert's red capsule over the pill: 4 dp lower, 2 dp past the box, so the pill shows round it as a 2 dp ring. */
        const val ALERT_HEIGHT = 22f
        const val ALERT_OUTSET = 2f
        /** The bar's text colour at these strengths: hovered, its popup open, pressed. */
        const val HOVER = 0.14f
        const val HELD = 0.18f
        const val PRESSED = 0.22f
        const val GRACE_MS = 150
        const val FADE_MS = 120
        /** Out of the way at once when an item is lifted to be dragged. */
        const val LIFT_MS = 80
        private const val MS = 1_000_000L

        /**
         * The item whose share of the strip [x] is in: each gap is split at its middle, the end items own the strip out
         * to its ends, and [current] keeps the pointer until it is [SLOP] past a middle, so a pointer resting on one
         * doesn't flicker between two items. [spans] are sorted by their left ends; null when there are none.
         */
        fun target(x: Float, spans: List<Span>, current: String?): String? {
            if (spans.isEmpty()) return null
            fun middle(i: Int) = (spans[i].right + spans[i + 1].left) / 2
            val c = spans.indexOfFirst { it.key == current }
            if (c >= 0) {
                val from = if (c == 0) Float.NEGATIVE_INFINITY else middle(c - 1) - SLOP
                val to = if (c == spans.lastIndex) Float.POSITIVE_INFINITY else middle(c) + SLOP
                if (x >= from && x <= to) return current
            }
            var i = 0
            while (i < spans.lastIndex && x >= middle(i)) i++
            return spans[i].key
        }

        /** Where the pill lies on [span]: [OUTSET] past each end, and never narrower than it is tall (a narrow ‹). */
        fun extent(span: Span): Pair<Float, Float> {
            val l = span.left - OUTSET
            val r = span.right + OUTSET
            if (r - l >= HEIGHT) return l to r
            val m = (l + r) / 2
            return m - HEIGHT / 2 to m + HEIGHT / 2
        }

        /** The pill's height in a strip [room] dp tall: [HEIGHT], less where the strip is lower, so the [RING] stays. */
        fun height(room: Float) = minOf(HEIGHT, room - 2 * RING)

        /** The alert capsule's, in the same strip: as much lower than the pill as [ALERT_HEIGHT] is than [HEIGHT]. */
        fun alertHeight(room: Float) = height(room) - (HEIGHT - ALERT_HEIGHT)
    }
}

/**
 * A highlight along the strip, from its left end to its right, travelling like rubber. Booklight's `Band`
 * (overlay/Rows.kt, approved there as variant A) along x, with its numbers from motion.md §4:
 * - the edge that leads goes at once, on [LEAD];
 * - the old edge holds on for five frames at 120 Hz (two at 60), so the pill lies over both items, then gathers on
 *   [TRAIL]; on a long way (the leading edge has more than [NEAR] to go) it holds two frames and follows on [TRAIL_FAR],
 *   as firm as the lead, so the two arrive together;
 * - a band that is already moving doesn't hold, and every edge carries on from where it is at the speed it has;
 * - the stretch is drawn as it is up to [KNEE] past the longer item, then eased so it never exceeds [MOST].
 *
 * Where this differs from Booklight's, and why:
 * - Its springs are solved here ([Spring]) rather than taken from Compose, so it is plain Kotlin and unit-tested.
 * - An edge is at rest within [REST] dp of its place and slower than [STILL] dp a second, then cut onto it (Booklight
 *   rides Compose's spring to 0.01 dp). That ends a step about 220 ms after it began instead of 380, as the motion
 *   page's own simulation counted it; the last half dp is under a pixel at the Googlebooks' densities.
 * - [NEAR] is 110 dp (Booklight 98): every step to a neighbour (36–102 dp) is a near one.
 * - Booklight's row that moves under its pill, and a row that only grows, go there on a spring of their own. Here an
 *   item that moves or resizes carries the pill exactly ([carry]): the pill is the item's hover, not a thing apart.
 *
 * Its edges are not animations of their own: [frame] moves all of them, once a frame, and [go] only says where to, so
 * a new place cancels nothing and the hold is a count of frames.
 */
internal class Band(first: Float, size: Float) {
    private val start = Edge(first)
    private val end = Edge(first + size)
    /** What the stretch is measured against: the longer of the item it left and the one it goes to. */
    private val body = Edge(size)
    /** Towards [end], to the right. The limit draws the old edge nearer; the edge that leads is where it is. */
    private var forward = true
    /** The place and the length it was last sent to. */
    private var asked = first
    private var length = size
    // The old edge holding on: which, how many frames more, and where it goes on what when it lets go.
    private var held: Edge? = null
    private var wait = -1
    private var heldTo = 0f
    private var heldOn: Spring? = null
    /** The last frame's time, and whether frames are running: what sets off between two frames is one frame on in the next. */
    private var clock = 0L
    private var awake = false

    /** Neither edge is moving and none is holding on: only then does the next move hold. */
    val resting get() = !start.running && !end.running && wait < 0
    val moving get() = !resting || body.running

    /** How far the limit draws the old edge from where it is: nothing up to [KNEE] of stretch. */
    private val pull: Float get() {
        val extra = end.at - start.at - body.at
        return if (extra > KNEE) extra - stretch(extra) else 0f
    }

    /** Where its two edges are drawn. */
    val first get() = if (forward) start.at + pull else start.at
    val last get() = if (forward) end.at else end.at - pull

    /** It goes to [first], [size] long. [calm]: nothing of it was moving, so the old edge holds on. */
    fun go(first: Float, size: Float, refresh: Float, scale: Float, calm: Boolean = resting) {
        if (first == asked && size == length) return
        if (scale <= 0f) { cut(first, size); return }
        val s = this.first
        val e = last
        // Already there (it stopped on this item and is sent back to it): nothing to move.
        if (calm && s == first && e == first + size) { asked = first; length = size; return }
        val now = if (awake) clock else 0L
        // Every edge goes on from where it is drawn, with the speed it has. If the limit was drawing one nearer, the
        // limit starts again from this length, so that nothing jumps when the direction turns round.
        val pulled = pull > 0f
        held = null; wait = -1
        val most = maxOf(length, size)
        if (calm) body.cut(maxOf(most, e - s))
        if (!calm || body.at != most) body.go(most, PLACE, now, if (pulled) e - s - KNEE else body.at)
        asked = first; length = size
        forward = first + size / 2 > (start.to + end.to) / 2
        val lead = if (forward) end else start
        val old = if (forward) start else end
        val leadTo = if (forward) first + size else first
        val oldTo = if (forward) first else first + size
        // A long way for either edge: from a wide item to a narrow one beside it the leading edge barely moves, but the old
        // edge crosses the whole wide item, and on the soft spring that read as a slow wipe.
        val far = maxOf(abs(leadTo - (if (forward) e else s)), abs(oldTo - (if (forward) s else e))) > NEAR
        lead.go(leadTo, LEAD, now, if (forward) e else s)
        // (An edge that had not let go yet when the direction turned stays where it is: it is the one that leads now.)
        val frames = if (calm) holdFrames(far, refresh, scale) else 0
        val trail = if (far) TRAIL_FAR else TRAIL
        if (frames == 0) { old.go(oldTo, trail, now, if (forward) s else e); return }
        // It lets go in the frame that is `frames` after the one the other edge sets off in.
        held = old; heldTo = oldTo; heldOn = trail; wait = if (awake) frames - 1 else frames
    }

    /** One frame of the screen; false once everything stands still. To be called while it is [moving], in every frame. */
    fun frame(now: Long, scale: Float): Boolean {
        if (wait > 0) wait-- else if (wait == 0) { wait = -1; held?.go(heldTo, heldOn!!, now); held = null }
        start.frame(now, scale); end.frame(now, scale); body.frame(now, scale)
        clock = now
        awake = moving
        return awake
    }

    /** It is there, without a way. */
    fun cut(first: Float, size: Float) {
        start.cut(first); end.cut(first + size); body.cut(size)
        asked = first; length = size; held = null; wait = -1; awake = false
    }

    /** It stays where it is (it is fading away): wherever it is sent next, it sets off from rest. */
    fun stop() {
        val s = first
        val e = last
        start.cut(s); end.cut(e); body.cut(e - s)
        asked = Float.NaN; length = e - s; held = null; wait = -1; awake = false
    }

    /** The item it is on or going to moved its left end by [dl] and its right end by [dr]: everything moves with it, springs and all. */
    fun carry(dl: Float, dr: Float) {
        start.carry(dl); end.carry(dr); body.carry(dr - dl)
        if (held === start) heldTo += dl else if (held === end) heldTo += dr
        asked += dl; length += dr - dl
    }

    companion object {
        /** The edge that leads. */
        val LEAD = Spring(0.85, 1400.0)
        /** The old edge, once it lets go; on a long way as firm as the lead, so it doesn't slow down and set off again at the landing. */
        val TRAIL = Spring(0.86, 900.0)
        val TRAIL_FAR = Spring(0.90, 1400.0)
        /** The length the stretch is measured against, when it changes on the way (Booklight's `place`). */
        val PLACE = Spring(0.86, 520.0)
        /** The leading edge has more than this to go: a long way. */
        const val NEAR = 110f
        /** Up to here a stretch is drawn as it is; beyond, it eases into [MOST]. */
        const val KNEE = 40f
        /** The pill is drawn at most this much longer than the longer of its two items, however far it goes. */
        const val MOST = 56f

        /** How much of a stretch of [extra] dp is drawn. */
        fun stretch(extra: Float): Float {
            if (extra <= KNEE) return extra
            val room = MOST - KNEE
            return KNEE + room * (1f - exp(-((extra - KNEE) / room)))
        }

        /**
         * How long the old edge holds on, in frames of a screen that draws [refresh] a second: five at 120 Hz (42 ms),
         * two at 60, two at 120 on a long way. Frames and not a time: a wait of 40 ms would end four, five or six frames
         * after the other edge set off, and the stretch would differ from one move to the next.
         */
        fun holdFrames(far: Boolean, refresh: Float, scale: Float): Int =
            if (scale <= 0f) 0 else (((if (far) 0.017f else 0.040f) * refresh).roundToInt() * scale).roundToInt()
    }
}

/**
 * A damped spring, x'' = -stiffness·(x - goal) - c·x' with a mass of 1 and c = 2·[ratio]·√stiffness: Compose's
 * `spring(dampingRatio, stiffness)`. Every spring of the strip passes its goal a little ([ratio] under 1). Solved for a
 * time rather than stepped from frame to frame, so a frame that comes late puts it where it would be.
 */
internal class Spring(private val ratio: Double, stiffness: Double) {
    private val w = sqrt(stiffness)
    private val decay = ratio * w
    private val ring = w * sqrt(1 - ratio * ratio)

    init { require(ratio > 0 && ratio < 1) }

    /** How far from its goal it is [t] seconds after it was [x0] away and moving at [v0] a second. */
    fun offset(x0: Double, v0: Double, t: Double) =
        exp(-decay * t) * (x0 * cos(ring * t) + (v0 + decay * x0) / ring * sin(ring * t))

    /** How fast it moves then. */
    fun speed(x0: Double, v0: Double, t: Double) =
        exp(-decay * t) * (v0 * cos(ring * t) - (x0 * ring + decay * (v0 + decay * x0) / ring) * sin(ring * t))
}

/**
 * One edge of the band, in dp. It goes to its place on a spring; given another place or another spring on its way, it
 * goes on from where it is with the speed it has. The spring is asked for the time since the edge set off, not stepped.
 */
internal class Edge(at: Float) {
    var at = at
        private set
    /** Where it is going. */
    var to = at
        private set
    /** How fast it is, in dp a second of the spring's own time (the animation scale stretches the clock, not the spring). */
    private var speed = 0f
    private var spring: Spring? = null
    private var from = at
    private var push = 0f
    private var since = 0L
    val running get() = spring != null

    /** [clock] is the last frame's time: the next frame is one frame on. 0 when no frame is running: the next frame is then its first, and shows it where it stands. */
    fun go(to: Float, on: Spring, clock: Long, start: Float = at) {
        this.to = to; at = start
        from = start; push = speed
        spring = on; since = clock
    }

    fun cut(to: Float) { at = to; this.to = to; speed = 0f; spring = null }

    fun carry(d: Float) { at += d; to += d; from += d }

    fun frame(now: Long, scale: Float) {
        val s = spring ?: return
        if (scale <= 0f) { cut(to); return }
        if (since == 0L) since = now
        val t = (now - since) / 1e9 / scale
        if (t <= 0.0) return
        val x = s.offset((from - to).toDouble(), push.toDouble(), t)
        val v = s.speed((from - to).toDouble(), push.toDouble(), t)
        at = (to + x).toFloat()
        speed = v.toFloat()
        if (abs(x) < REST && abs(v) < STILL) cut(to)
    }

    companion object {
        /** At rest: this near its place (dp) and this slow (dp a second): the last of it is under a pixel. */
        const val REST = 0.5
        const val STILL = 20.0
    }
}

/** How much of the pill shows, 0 to 1: a tween from wherever it is, which starts with the next frame (as Compose's do). */
internal class Fade {
    var value = 0f
        private set
    /** Where it is going. */
    var to = 0f
        private set
    private var from = 0f
    private var since = 0L
    private var lasts = 0L
    var running = false
        private set

    fun go(to: Float, ms: Int, scale: Float) {
        if (to == this.to && (running || value == to)) return
        this.to = to
        if (scale <= 0f) { value = to; running = false; return }
        from = value; since = 0L; lasts = (ms * scale * 1_000_000.0).toLong(); running = true
    }

    fun frame(now: Long) {
        if (!running) return
        if (since == 0L) since = now
        val p = (now - since).toFloat() / lasts
        if (p >= 1f) { value = to; running = false } else value = from + (to - from) * STANDARD.at(p)
    }

    private companion object {
        /** cubic-bezier(0.4, 0, 0.2, 1): Material's standard easing, Compose's FastOutSlowIn. */
        val STANDARD = Curve(0.4, 0.0, 0.2, 1.0)
    }
}

/** A CSS cubic-bezier easing, from (0, 0) to (1, 1) through the control points (x1, y1) and (x2, y2). */
internal class Curve(private val x1: Double, private val y1: Double, private val x2: Double, private val y2: Double) {
    private fun bezier(t: Double, p1: Double, p2: Double) = 3 * (1 - t) * (1 - t) * t * p1 + 3 * (1 - t) * t * t * p2 + t * t * t

    /** The curve's height where it is [x] along: x is monotonic in t, so halving finds t to well under a millionth. */
    fun at(x: Float): Float {
        if (x <= 0f) return 0f
        if (x >= 1f) return 1f
        var lo = 0.0
        var hi = 1.0
        repeat(30) {
            val mid = (lo + hi) / 2
            if (bezier(mid, x1, x2) < x) lo = mid else hi = mid
        }
        return bezier((lo + hi) / 2, y1, y2).toFloat()
    }
}
