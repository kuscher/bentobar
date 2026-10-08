package io.github.kuscher.bentobar.bar

import io.github.kuscher.bentobar.bar.StripHighlight.Companion.HELD
import io.github.kuscher.bentobar.bar.StripHighlight.Companion.HOVER
import io.github.kuscher.bentobar.bar.StripHighlight.Companion.PRESSED
import io.github.kuscher.bentobar.bar.StripHighlight.Companion.extent
import io.github.kuscher.bentobar.bar.StripHighlight.Companion.target
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The strip's one highlight (docs/design/1.3/motion.md §4, visual.md §3): which item the pointer is over, the
 * rubber band that carries the pill from one item to the next, and when the pill comes and goes.
 */
class StripHighlightTest {
    private val ms = 1_000_000L
    /** Frames start here: a frame time of 0 means "no frame yet" to the band. */
    private val t0 = 1_000 * ms
    private fun frame(hz: Float) = (1e9 / hz).toLong()

    // ‹ and three items as the strip draws them, in dp: each item's box (its 6 dp padding included), 12 dp between
    // boxes, and none between ‹ and the first item (the strip puts no gap there).
    private val chevron = Span("chevron", 0f, 21f)
    private val cpu = Span("cpu", 21f, 79f)
    private val net = Span("net", 91f, 179f)
    private val clock = Span("clock", 191f, 251f)
    private val strip = listOf(chevron, cpu, net, clock)
    /** The middle of each gap: ‹|cpu at 21, cpu|net at 85, net|clock at 185. */
    private val middles = listOf(21f, 85f, 185f)

    // ---- which item -------------------------------------------------------------------------

    @Test fun eachGapIsSplitAtItsMiddle() {
        for ((i, m) in middles.withIndex()) {
            assertEquals(strip[i].key, target(m - 0.1f, strip, null))
            assertEquals(strip[i + 1].key, target(m + 0.1f, strip, null))
        }
        // Inside a box, and in the middle of nowhere a gap has.
        assertEquals("net", target(130f, strip, null))
        assertEquals("cpu", target(84f, strip, null))
    }

    @Test fun theItemTheHighlightIsOnKeepsItTwoDpPastTheMiddle() {
        for ((i, m) in middles.withIndex()) {
            val left = strip[i].key
            val right = strip[i + 1].key
            // Coming from the left: still the left item up to 2 dp past the middle, the right one beyond.
            assertEquals(left, target(m + 1.9f, strip, left))
            assertEquals(right, target(m + 2.1f, strip, left))
            // Coming from the right: the same, the other way.
            assertEquals(right, target(m - 1.9f, strip, right))
            assertEquals(left, target(m - 2.1f, strip, right))
        }
    }

    @Test fun aPointerThatJumpsSeveralItemsGoesToTheLatest() {
        assertEquals("clock", target(220f, strip, "chevron"))
        assertEquals("chevron", target(5f, strip, "clock"))
    }

    @Test fun theEndItemsOwnTheStripOutToItsEnds() {
        assertEquals("chevron", target(-30f, strip, null))
        assertEquals("chevron", target(-30f, strip, "net"))
        assertEquals("clock", target(400f, strip, null))
        assertEquals("clock", target(400f, strip, "cpu"))
    }

    @Test fun theChevronIsOneMoreItem() {
        assertEquals("chevron", target(10f, strip, null))
        assertEquals("chevron", target(22.9f, strip, "chevron"))
        assertEquals("cpu", target(23.1f, strip, "chevron"))
    }

    @Test fun anItemThatLeftIsNoReasonToHoldOn() {
        assertEquals("cpu", target(84f, strip, "gone"))
        assertEquals("net", target(86f, strip, "gone"))
    }

    @Test fun anEmptyStripHasNothingAndOneItemHasEverything() {
        assertNull(target(10f, emptyList(), null))
        assertEquals("net", target(-500f, listOf(net), null))
        assertEquals("net", target(500f, listOf(net), "cpu"))
    }

    @Test fun thePillReachesFourDpPastTheBoxAndIsNeverNarrowerThanTall() {
        assertEquals(17f to 83f, extent(cpu))
        // A narrow ‹ (14 dp box): 22 dp with the outset, widened about its middle to the pill's 26 dp height.
        assertEquals(-6f to 20f, extent(Span("chevron", 0f, 14f)))
    }

    // ---- the band ---------------------------------------------------------------------------

    /** Runs [band] at [hz] until it rests and returns how long that took, in ms; [each] sees every frame. */
    private fun settle(band: Band, hz: Float, scale: Float = 1f, start: Long = t0, each: (Int) -> Unit = {}): Long {
        var t = start
        var i = 0
        while (band.frame(t, scale)) {
            each(i++)
            t += frame(hz)
            assertTrue("never rests", i < 2_000)
        }
        return (t - start) / ms
    }

    @Test fun aStepToTheNextItemStretchesOverBothAndGathersOnTheNew() {
        // cpu (66 dp pill) to net (96 dp pill), as the strip draws them: 4 dp between the two pills.
        val (l0, r0) = extent(cpu)
        val (l1, r1) = extent(net)
        val band = Band(l0, r0 - l0)
        band.go(l1, r1 - l1, refresh = 120f, scale = 1f)
        var most = 0f
        val took = settle(band, 120f) { most = maxOf(most, band.last - band.first - (r1 - l1)) }
        assertTrue("stretch $most", most > 20f && most <= Band.MOST)
        assertTrue("at rest after $took ms", took <= 260)
        assertEquals(l1, band.first, 0f)
        assertEquals(r1, band.last, 0f)
        assertTrue(band.resting)
    }

    @Test fun theStepsTheMotionPageSimulatedComeOutTheSame() {
        // motion.md §4 measured boxes with no outset, 12 dp apart: 58 → 88 dp stretches 35 dp, 90 → 90 stretches 52.
        for ((from, to, stretch) in listOf(Triple(58f, 88f, 35f), Triple(90f, 90f, 52f))) {
            val band = Band(0f, from)
            band.go(from + 12f, to, 120f, 1f)
            var most = 0f
            val took = settle(band, 120f) { most = maxOf(most, band.last - band.first - maxOf(from, to)) }
            assertEquals("$from → $to", stretch, most, 1f)
            assertTrue("$from → $to at rest after $took ms", took <= 260)
        }
    }

    @Test fun theOldEdgeHoldsFiveFramesAt120HzAndTwoAt60() {
        for ((hz, hold) in listOf(120f to 5, 60f to 2)) {
            val band = Band(0f, 58f)
            band.go(62f, 88f, hz, 1f)
            var leadOff = -1
            var oldOff = -1
            settle(band, hz) { i ->
                if (leadOff < 0 && band.last != 58f) leadOff = i
                if (oldOff < 0 && band.first != 0f) oldOff = i
            }
            assertEquals("$hz Hz", hold, oldOff - leadOff)
        }
    }

    @Test fun holdsAreFramesOfTheScreenStretchedByTheAnimationScale() {
        assertEquals(5, Band.holdFrames(far = false, refresh = 120f, scale = 1f))
        assertEquals(2, Band.holdFrames(far = false, refresh = 60f, scale = 1f))
        assertEquals(2, Band.holdFrames(far = true, refresh = 120f, scale = 1f))
        assertEquals(1, Band.holdFrames(far = true, refresh = 60f, scale = 1f))
        assertEquals(10, Band.holdFrames(far = false, refresh = 120f, scale = 2f))
        assertEquals(0, Band.holdFrames(far = false, refresh = 120f, scale = 0f))
    }

    @Test fun aLongWayIsNeverDrawnLongerThanTheLimit() {
        // From ‹ to an item 300 dp away: the leading edge has far more than 110 dp to go.
        val band = Band(0f, 40f)
        band.go(300f, 40f, 120f, 1f)
        var most = 0f
        val took = settle(band, 120f) { most = maxOf(most, band.last - band.first - 40f) }
        assertTrue("stretch $most", most <= Band.MOST + 0.01f)
        // It still stretches: the limit eases towards 56 dp, it does not cut at the knee.
        assertTrue("stretch $most", most > Band.KNEE)
        assertTrue("at rest after $took ms", took <= 300)
        assertEquals(300f, band.first, 0f)
        assertEquals(340f, band.last, 0f)
    }

    @Test fun aBandThatIsMovingTurnsRoundWithoutHoldingAndKeepsItsSpeed() {
        val f = frame(120f)
        val band = Band(0f, 58f)
        band.go(70f, 88f, 120f, 1f)
        var t = t0
        // Ten frames in, the old edge has let go (frame 6) and is on its way to the right.
        repeat(10) { band.frame(t, 1f); t += f }
        val before = band.first
        band.frame(t, 1f); t += f
        val pace = band.first - before
        assertTrue("old edge moving right: $pace", pace > 1f)
        // Back to the item it came from.
        band.go(0f, 58f, 120f, 1f)
        val atTurn = band.first
        val endAtTurn = band.last
        band.frame(t, 1f); t += f
        // The first edge carries on to the right for a moment, at about the pace it had: it doesn't start from rest.
        val after = band.first - atTurn
        assertTrue("kept its speed: $after after $pace", after > 0.3f * pace)
        // And the edge that is now the old one doesn't hold: it is on its way back within two frames.
        band.frame(t, 1f); t += f
        assertTrue("no hold", band.last < endAtTurn)
        settle(band, 120f, start = t)
        assertEquals(0f, band.first, 0f)
        assertEquals(58f, band.last, 0f)
    }

    @Test fun withoutAnimationsTheBandCuts() {
        val band = Band(0f, 58f)
        band.go(62f, 88f, 120f, scale = 0f)
        assertEquals(62f, band.first, 0f)
        assertEquals(150f, band.last, 0f)
        assertFalse(band.moving)
    }

    @Test fun anotherAnimationScaleStretchesTheWholeWay() {
        val once = Band(0f, 58f).let { it.go(62f, 88f, 120f, 1f); settle(it, 120f) }
        val twice = Band(0f, 58f).let { it.go(62f, 88f, 120f, 2f); settle(it, 120f, scale = 2f) }
        assertEquals(2.0, twice.toDouble() / once, 0.15)
    }

    @Test fun anItemThatMovesCarriesTheBandWithoutASpring() {
        val band = Band(10f, 50f)
        band.carry(5f, 12f)
        assertEquals(15f, band.first, 0f)
        assertEquals(72f, band.last, 0f)
        assertFalse(band.moving)
    }

    // ---- the highlight ----------------------------------------------------------------------

    /** A highlight over [strip], and the clock its frames run on. */
    private inner class Hover(hz: Float = 120f, scale: Float = 1f) {
        val h = StripHighlight().apply { refresh = hz; this.scale = scale; layout(strip, t0) }
        val f = frame(hz)
        var now = t0

        /** Runs frames for [forMs] (or until nothing is left to do), [each] after every one. */
        fun run(forMs: Int, each: () -> Unit = {}) {
            val until = now + forMs * ms
            while (now < until) {
                h.step(now)
                each()
                now += f
            }
        }

        /** Runs frames until nothing moves, fades or waits any more; how long that took, in ms. */
        fun rest(): Long {
            val from = now
            while (h.step(now)) {
                now += f
                assertTrue("never rests", now - from < 5_000 * ms)
            }
            return (now - from) / ms
        }

        fun over(span: Span) = (span.left + span.right) / 2
        fun isOn(span: Span) {
            val (l, r) = extent(span)
            assertEquals(l, h.left, 0.001f)
            assertEquals(r, h.right, 0.001f)
        }
    }

    @Test fun enteringFadesInOnTheHoveredItemWithNoSlide() = with(Hover()) {
        assertEquals(0f, h.alpha, 0f)
        h.move(over(net), now)
        isOn(net)
        var last = -1f
        var halfway = -1f
        val start = now
        run(130) {
            isOn(net)
            assertTrue("fades up steadily", h.alpha >= last)
            last = h.alpha
            if (halfway < 0 && now - start >= 60 * ms) halfway = h.alpha
        }
        assertEquals(1f, h.alpha, 0f)
        // cubic-bezier(0.4, 0, 0.2, 1) is well ahead of a straight line halfway.
        assertTrue("halfway $halfway", halfway > 0.6f && halfway < 0.95f)
        assertEquals(HOVER, h.fill, 0f)
        rest()
        assertFalse(h.moving)
        assertNull(h.wakeAt)
    }

    @Test fun movingWithinAnItemChangesNothing() = with(Hover()) {
        h.move(over(net), now)
        rest()
        assertFalse(h.move(over(net) + 5f, now))
        assertFalse(h.move(over(net) - 30f, now))
    }

    @Test fun leavingWaitsThenFadesWhereItStands() = with(Hover()) {
        h.move(over(cpu), now)
        rest()
        val left = now
        h.leave(now)
        // The grace is a wait, not frames: nothing runs during it.
        assertFalse(h.moving)
        assertEquals(left + 150 * ms, h.wakeAt)
        run(149) { assertEquals(1f, h.alpha, 0f) }
        now = left + 150 * ms
        run(10)
        assertTrue("fading", h.alpha < 1f)
        val faded = now
        while (h.step(now)) { isOn(cpu); now += f }
        assertEquals(0f, h.alpha, 0f)
        assertTrue("faded in ${(now - faded) / ms} ms", now - faded <= 130 * ms)
        isOn(cpu)
        assertNull(h.wakeAt)
    }

    @Test fun backDuringTheGraceItNeverDipsAndGlides() = with(Hover()) {
        h.move(over(cpu), now)
        rest()
        h.leave(now)
        now += 100 * ms
        h.move(over(net), now)
        var lefts = HashSet<Float>()
        run(300) { assertEquals(1f, h.alpha, 0f); lefts += h.left }
        assertTrue("glided through ${lefts.size} places", lefts.size > 5)
        isOn(net)
    }

    @Test fun backDuringTheFadeItFadesUpFromWhereItIsAndGlides() = with(Hover()) {
        h.move(over(cpu), now)
        rest()
        h.leave(now)
        now += 150 * ms
        run(60)
        val alpha = h.alpha
        assertTrue("half faded: $alpha", alpha > 0.05f && alpha < 0.95f)
        val (l0, _) = extent(cpu)
        h.move(over(clock), now)
        // From where it is: no jump, neither to nothing nor to full, nor to the new item.
        assertEquals(alpha, h.alpha, 0.001f)
        assertEquals(l0, h.left, 0.001f)
        var lefts = HashSet<Float>()
        var last = alpha
        run(400) { assertTrue(h.alpha >= last); last = h.alpha; lefts += h.left }
        assertEquals(1f, h.alpha, 0f)
        assertTrue("glided through ${lefts.size} places", lefts.size > 5)
        isOn(clock)
    }

    @Test fun anOpenPopupHoldsThePillOnItsItem() = with(Hover()) {
        h.move(over(cpu), now)
        h.hold("cpu", now)
        rest()
        assertEquals(HELD, h.fill, 0f)
        // The pointer heads elsewhere along the strip, and into the popup: the pill stays.
        h.move(over(clock), now)
        run(200)
        isOn(cpu)
        h.leave(now)
        run(500)
        isOn(cpu)
        assertEquals(1f, h.alpha, 0f)
        assertEquals(HELD, h.fill, 0f)
    }

    @Test fun clickingAnotherItemGlidesThePillThere() = with(Hover()) {
        h.move(over(cpu), now)
        h.hold("cpu", now)
        rest()
        h.move(over(clock), now)
        h.hold("clock", now)
        var lefts = HashSet<Float>()
        run(400) { lefts += h.left; assertEquals(1f, h.alpha, 0f) }
        assertTrue("glided through ${lefts.size} places", lefts.size > 5)
        isOn(clock)
        assertEquals(HELD, h.fill, 0f)
    }

    @Test fun aClosedPopupLeavesThePillUntilThePointerMoves() = with(Hover()) {
        h.move(over(cpu), now)
        h.hold("cpu", now)
        rest()
        h.move(over(net), now)
        rest()
        h.hold(null, now)
        run(300)
        isOn(cpu)
        assertEquals(1f, h.alpha, 0f)
        assertEquals(HOVER, h.fill, 0f)
        h.move(over(net) + 1f, now)
        run(400)
        isOn(net)
    }

    @Test fun aClosedPopupWithThePointerElsewhereFadesWithTheFold() = with(Hover()) {
        h.move(over(cpu), now)
        h.hold("cpu", now)
        rest()
        h.leave(now)
        run(400)
        assertEquals(1f, h.alpha, 0f)
        h.hold(null, now)
        val closed = now
        rest()
        assertEquals(0f, h.alpha, 0f)
        assertTrue("gone in ${(now - closed) / ms} ms", now - closed <= 140 * ms)
    }

    @Test fun aPopupOfAnItemNotInTheStripHoldsNothing() = with(Hover()) {
        h.move(over(cpu), now)
        h.hold("hidden", now)
        rest()
        h.move(over(net), now)
        run(400)
        isOn(net)
        assertEquals(HOVER, h.fill, 0f)
    }

    @Test fun pressingIsFirmerOnTheItemThePillIsOn() = with(Hover()) {
        h.move(over(cpu), now)
        rest()
        h.press(true, now)
        assertEquals(PRESSED, h.fill, 0f)
        h.press(false, now)
        assertEquals(HOVER, h.fill, 0f)
        // Held on cpu, pressed over net (the press that closes cpu's popup): cpu is not the one pressed.
        h.hold("cpu", now)
        h.move(over(net), now)
        h.press(true, now)
        assertEquals(HELD, h.fill, 0f)
    }

    @Test fun aHeldSliderPinsThePillAtHoverStrength() = with(Hover()) {
        h.move(over(net), now)
        h.press(true, now)
        h.pin("net", now)
        rest()
        assertEquals(HOVER, h.fill, 0f)
        // The pointer drags along the strip and past its end: the pill stays with the slider.
        h.move(over(clock), now)
        h.leave(now)
        run(500)
        isOn(net)
        assertEquals(1f, h.alpha, 0f)
        // Let go outside the strip: it goes as a pointer leaving does.
        h.press(false, now)
        h.pin(null, now)
        rest()
        assertEquals(0f, h.alpha, 0f)
    }

    @Test fun draggingHidesItAndItWaitsForAMoveAfterTheDrop() = with(Hover()) {
        h.move(over(net), now)
        rest()
        h.press(true, now)
        h.lift(true, now)
        val lifted = now
        rest()
        assertEquals(0f, h.alpha, 0f)
        assertTrue("gone in ${(now - lifted) / ms} ms", now - lifted <= 95 * ms)
        // The other items slide aside while it's dragged; the drop puts net at the end.
        val moved = listOf(chevron, cpu.copy(left = 21f, right = 79f), clock.copy(left = 91f, right = 151f), net.copy(left = 163f, right = 251f))
        h.layout(moved, now)
        h.press(false, now)
        h.lift(false, now)
        run(300)
        assertEquals(0f, h.alpha, 0f)
        assertFalse(h.moving)
        // The next move shows it where the pointer is, without sliding in.
        h.move(130f, now)
        isOn(moved[2])
        rest()
        assertEquals(1f, h.alpha, 0f)
    }

    @Test fun thePillSitsOnAnItemThatMovesOrResizesWithoutASpring() = with(Hover()) {
        h.move(over(net), now)
        rest()
        val wider = net.copy(left = 95f, right = 190f)
        assertTrue(h.layout(listOf(chevron, cpu, wider, clock.copy(left = 202f, right = 262f)), now))
        isOn(wider)
        assertFalse(h.moving)
    }

    @Test fun aLayoutChangeUnderAStillPointerWaitsForTheNextMove() = with(Hover()) {
        val x = over(net)
        h.move(x, now)
        rest()
        // A timer pops out before net: net slides right, and the timer lies under the pointer now.
        val popped = listOf(chevron, cpu, Span("timer", 91f, 140f), net.copy(left = 152f, right = 240f), clock.copy(left = 252f, right = 312f))
        h.layout(popped, now)
        run(300)
        isOn(popped[3])
        h.move(x + 1f, now)
        run(400)
        isOn(popped[2])
    }

    @Test fun anItemThatLeavesUnderThePillIsLeftUntilThePointerMoves() = with(Hover()) {
        h.move(over(net), now)
        rest()
        h.layout(listOf(chevron, cpu, clock), now)
        run(300)
        isOn(net)
        h.move(over(clock), now)
        run(400)
        isOn(clock)
    }

    @Test fun withoutAnimationsItCutsAppearsAndGoesAtOnce() = with(Hover(scale = 0f)) {
        h.move(over(cpu), now)
        assertEquals(1f, h.alpha, 0f)
        isOn(cpu)
        assertFalse(h.moving)
        h.move(over(clock), now)
        isOn(clock)
        assertFalse(h.moving)
        // The grace is a tolerance, not motion: it stays.
        val left = now
        h.leave(now)
        assertEquals(1f, h.alpha, 0f)
        assertEquals(left + 150 * ms, h.wakeAt)
        now = left + 150 * ms
        h.step(now)
        assertEquals(0f, h.alpha, 0f)
        assertFalse(h.moving)
        assertNull(h.wakeAt)
    }

    @Test fun anotherAnimationScaleStretchesTheFadesToo() = with(Hover(scale = 2f)) {
        h.move(over(cpu), now)
        val start = now
        var full = -1L
        run(300) { if (full < 0 && h.alpha == 1f) full = (now - start) / ms }
        // 120 ms at scale 1, twice that here (to the frame that reaches it).
        assertTrue("faded in after $full ms", full in 240L..249L)
    }

    @Test fun aTouchShowsThePillWhileTheFingerIsDown() = with(Hover()) {
        h.move(over(net), now)
        h.press(true, now)
        rest()
        assertEquals(PRESSED, h.fill, 0f)
        h.press(false, now)
        h.leave(now)
        rest()
        assertEquals(0f, h.alpha, 0f)
    }

    @Test fun nothingIsScheduledAtRest() = with(Hover()) {
        assertFalse(h.step(now))
        h.move(over(cpu), now)
        assertTrue(h.moving)
        rest()
        assertFalse(h.moving)
        assertNull(h.wakeAt)
        assertFalse(h.step(now))
        assertTrue(abs(h.alpha - 1f) < 1e-6f)
    }
}
