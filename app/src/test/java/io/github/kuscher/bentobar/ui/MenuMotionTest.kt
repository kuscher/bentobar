package io.github.kuscher.bentobar.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The numbers of docs/design/1.3/motion.md: how a popup opens, settles its contents, closes and turns round. */
class MenuMotionTest {
    private val lip = MenuMotion.LIP_DP

    @Test fun theCurvesAreCssCubicBeziersThatStartAndEndExactly() {
        for (c in listOf(MenuMotion.OPENS, MenuMotion.PRESENTS, MenuMotion.FOLDS)) {
            assertEquals(0f, c.at(0f), 0f); assertEquals(1f, c.at(1f), 0f)
            var last = 0f
            for (i in 1..100) { val v = c.at(i / 100f); assertTrue("monotonic", v >= last - 1e-4f); last = v }
        }
        // Slow, fast, slow: the opening barely moves in its first and last tenth.
        assertTrue(MenuMotion.OPENS.at(0.1f) < 0.05f)
        assertTrue(MenuMotion.OPENS.at(0.9f) > 0.97f)
        // cubic-bezier(0.55, 0, 0.1, 1) at its middle, as a browser computes it.
        assertEquals(0.79f, MenuMotion.OPENS.at(0.5f), 0.02f)
    }

    @Test fun aTallerPopupTakesALittleLongerToOpenUpToAThirdOfASecond() {
        assertEquals(240f, MenuMotion.openMs(120f), 0f)
        assertEquals(240f, MenuMotion.openMs(200f), 0f)
        assertEquals(260f, MenuMotion.openMs(300f), 0.01f)
        assertEquals(340f, MenuMotion.openMs(700f), 0.01f)
        assertEquals(340f, MenuMotion.openMs(1400f), 0f)
    }

    @Test fun itUnfoldsDownFromALipAndRestsAtItsFullHeight() {
        val h = 300f
        val first = MenuMotion.opening(0f, h)
        assertEquals(lip, first.heightDp, 0f)
        assertEquals(0f, first.presence, 0f)
        assertEquals(0f, first.shadow, 0f)
        assertEquals(10f, first.cornerDp, 0f)                    // the lip is a capsule
        // The timeline in motion.md, H = 300 dp: 50 dp at 60 ms, 175 dp at 100 ms, at rest at 260 ms.
        assertEquals(50f, MenuMotion.opening(60f, h).heightDp, 10f)
        assertEquals(175f, MenuMotion.opening(100f, h).heightDp, 15f)
        assertEquals(h, MenuMotion.opening(260f, h).heightDp, 0f)
        assertTrue(MenuMotion.opening(260f, h).done)
        assertFalse(MenuMotion.opening(200f, h).done)
        var last = 0f
        for (ms in 0..400 step 4) {
            val g = MenuMotion.opening(ms.toFloat(), h)
            assertTrue(g.heightDp >= last && g.heightDp <= h); last = g.heightDp
        }
        assertEquals(MenuMotion.RADIUS_DP, MenuMotion.opening(260f, h).cornerDp, 0f)
    }

    @Test fun theGlassArrivesDuringTheSlowStartAndItsShadowOnceThereIsSomethingToCastIt() {
        assertEquals(0.42f, MenuMotion.opening(17f, 300f).presence, 0.1f)
        assertEquals(1f, MenuMotion.opening(100f, 300f).presence, 0f)
        // No dark line under the lip: the shadow comes with the opening, full by 60 % of the way.
        assertTrue(MenuMotion.opening(30f, 300f).shadow < 0.05f)
        assertEquals(1f, MenuMotion.opening(160f, 300f).shadow, 0f)
    }

    @Test fun aPopupNoTallerThanItsLipIsSimplyThere() {
        val g = MenuMotion.opening(0f, 16f)
        assertEquals(16f, g.heightDp, 0f)
        assertTrue(MenuMotion.opening(100f, 16f).done)
    }

    @Test fun eachBlockDropsIntoPlaceInTurnWithoutShrinkingBack() {
        // In a popup of five blocks, block i starts at 30 + 20 × i ms, 8 dp above its place.
        assertEquals(-8f, MenuMotion.block(0, 29f, 5).dy, 0f)
        assertEquals(0f, MenuMotion.block(0, 29f, 5).alpha, 0f)
        assertEquals(-8f, MenuMotion.block(3, 89f, 5).dy, 0f)
        assertTrue(MenuMotion.block(3, 100f, 5).dy > -8f)
        var most = -8f
        for (ms in 30..700) { val b = MenuMotion.block(0, ms.toFloat(), 5); most = maxOf(most, b.dy) }
        assertTrue("overshoot ${most} dp", most < 0.1f)
        // Readable about 65 ms in; within half a dp by about 180 ms on that spring, so the last block is still by ~370 ms.
        assertTrue(MenuMotion.block(0, 30f + 65f, 5).alpha > 0.85f)
        assertEquals(0f, MenuMotion.block(8, 190f + 180f, 9).dy, 0.5f)
        assertTrue(MenuMotion.block(8, 190f + 400f, 9).done)
        assertEquals(0f, MenuMotion.block(8, 190f + 400f, 9).dy, 0f)
        assertEquals(1f, MenuMotion.block(8, 190f + 400f, 9).alpha, 0f)
    }

    @Test fun aTallPopupsBlocksSpreadOverTheSameTimeRatherThanArrivingInAClump() {
        // Sixteen blocks (the Weather popup): the last still starts at 190 ms, and no two start together, so the lower
        // glass is never left empty while a clump waits (the motion review of the built 1.3).
        assertEquals(190f, MenuMotion.blockStartMs(15, 16), 0.01f)
        for (i in 1 until 16) assertTrue(MenuMotion.blockStartMs(i, 16) > MenuMotion.blockStartMs(i - 1, 16))
        // Up to nine blocks, 20 ms apart as before.
        assertEquals(30f + 20f * 4, MenuMotion.blockStartMs(4, 5), 0f)
        assertEquals(190f, MenuMotion.blockStartMs(8, 9), 0f)
        assertEquals(30f, MenuMotion.blockStartMs(0, 1), 0f)
        assertEquals(590f, MenuMotion.blocksDoneMs(16), 0.01f)
    }

    @Test fun closingFoldsBackIntoTheItemAndFadesAsItLands() {
        val h = 300f                                          // T 260, so the fold takes 0.45 × 260 = 117 ms
        val c = MenuMotion.closing(0f, h, h, 1f)
        assertEquals(h, c.heightDp, 0f); assertEquals(1f, c.presence, 0f); assertEquals(1f, c.contents, 0f)
        assertEquals(0f, MenuMotion.closing(50f, h, h, 1f).contents, 0f)    // the contents go first, in place
        assertEquals(170f, MenuMotion.closing(50f, h, h, 1f).heightDp, 25f)
        assertEquals(lip, MenuMotion.closing(117f, h, h, 1f).heightDp, 0.01f)
        assertEquals(1f, MenuMotion.closing(87f, h, h, 1f).presence, 0f)    // presence falls from 30 ms before the fold ends
        assertEquals(0.5f, MenuMotion.closing(117f, h, h, 1f).presence, 0.01f)
        assertEquals(147f, MenuMotion.closeMs(h, h), 0.01f)
        assertTrue(MenuMotion.closing(147f, h, h, 1f).done)
        assertEquals(0f, MenuMotion.closing(147f, h, h, 1f).presence, 0f)
    }

    @Test fun closedWhileStillOpeningItFoldsFromWhereItIsAndFaster() {
        // Half open: the fold is half as long, but never under 50 ms.
        val f = MenuMotion.closeMs(lip + (300f - lip) / 2, 300f) - 30f
        assertEquals(117f / 2, f, 0.5f)
        assertEquals(50f + 30f, MenuMotion.closeMs(lip + 4f, 300f), 0.01f)
        // It starts from the presence it had.
        assertEquals(0.4f, MenuMotion.closing(0f, 100f, 300f, 0.4f).presence, 0f)
    }

    @Test fun clickedAgainWhileClosingItReopensFromWhereItIsKeepingItsSpeed() {
        val h = 300f
        // Folding up at 2 dp/ms from 150 dp: it carries on up a little, turns, and rests at full height without passing it.
        val start = MenuMotion.reopening(0f, 150f, -2f, h, 0.6f, 0.2f)
        assertEquals(150f, start.heightDp, 0f)
        assertTrue(MenuMotion.reopening(8f, 150f, -2f, h, 0.6f, 0.2f).heightDp < 150f)
        var most = 0f
        for (ms in 0..800 step 2) most = maxOf(most, MenuMotion.reopening(ms.toFloat(), 150f, -2f, h, 0.6f, 0.2f).heightDp)
        assertTrue(most <= h + 0.01f)
        val end = MenuMotion.reopening(800f, 150f, -2f, h, 0.6f, 0.2f)
        assertEquals(h, end.heightDp, 0f); assertTrue(end.done)
        // Presence comes back in 60 ms, the contents in 100 ms, both from where they were.
        assertEquals(0.6f, start.presence, 0f); assertEquals(1f, MenuMotion.reopening(60f, 150f, -2f, h, 0.6f, 0.2f).presence, 0f)
        assertEquals(0.2f, start.contents, 0f); assertEquals(1f, MenuMotion.reopening(100f, 150f, -2f, h, 0.6f, 0.2f).contents, 0f)
    }

    @Test fun aPopupLeftForAnotherDissolvesInPlace() {
        assertEquals(1f, MenuMotion.dissolve(0f), 0f)
        assertEquals(0.5f, MenuMotion.dissolve(45f), 0.01f)
        assertEquals(0f, MenuMotion.dissolve(90f), 0f)
        assertEquals(90f, MenuMotion.DISSOLVE_MS, 0f)
    }

    @Test fun theSystemsAnimationSpeedStretchesEverythingAndRemoveAnimationsSkipsIt() {
        assertEquals(100f, MenuMotion.scaled(100f, 1f), 0f)
        assertEquals(50f, MenuMotion.scaled(100f, 2f), 0f)          // "Animator duration scale 2×": twice as slow
        assertEquals(Float.POSITIVE_INFINITY, MenuMotion.scaled(0f, 0f), 0f)
        assertTrue(MenuMotion.opening(MenuMotion.scaled(0f, 0f), 300f).done)
        assertTrue(MenuMotion.block(0, MenuMotion.scaled(0f, 0f), 3).done)
        assertTrue(MenuMotion.closing(MenuMotion.scaled(0f, 0f), 300f, 300f, 1f).done)
    }
}
