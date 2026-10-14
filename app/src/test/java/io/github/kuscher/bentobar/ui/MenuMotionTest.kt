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
        // v2's curve, cubic-bezier(0.45, 0, 0.1, 1): the same shape with a shorter wait, half open by a third of the time.
        assertEquals(0.5f, MenuMotion.OPENS.at(0.33f), 0.06f)
    }

    @Test fun aTallerPopupTakesALittleLongerToOpenWithinAQuarterOfASecondOrSo() {
        // v2: 160 + 0.16 × height, between 190 and 270 ms (a fifth quicker than v1).
        assertEquals(190f, MenuMotion.openMs(120f), 0f)
        assertEquals(208f, MenuMotion.openMs(300f), 0.01f)
        assertEquals(258.56f, MenuMotion.openMs(616f), 0.01f)
        assertEquals(270f, MenuMotion.openMs(1400f), 0f)
    }

    @Test fun itUnfoldsDownFromALipAndRestsAtItsFullHeight() {
        val h = 300f
        val first = MenuMotion.opening(0f, h)
        assertEquals(lip, first.heightDp, 0f)
        assertEquals(0f, first.presence, 0f)
        assertEquals(0f, first.shadow, 0f)
        assertEquals(10f, first.cornerDp, 0f)                    // the lip is a capsule
        // motion.md §7, H = 300 dp: half open at 69 ms, at rest at 208 ms.
        assertEquals(160f, MenuMotion.opening(69f, h).heightDp, 15f)
        assertEquals(h, MenuMotion.opening(208f, h).heightDp, 0f)
        assertTrue(MenuMotion.opening(208f, h).done)
        assertFalse(MenuMotion.opening(180f, h).done)
        var last = 0f
        for (ms in 0..400 step 4) {
            val g = MenuMotion.opening(ms.toFloat(), h)
            assertTrue(g.heightDp >= last && g.heightDp <= h); last = g.heightDp
        }
        assertEquals(MenuMotion.RADIUS_DP, MenuMotion.opening(208f, h).cornerDp, 0f)
    }

    @Test fun theGlassArrivesDuringTheSlowStartAndItsShadowOnceThereIsSomethingToCastIt() {
        assertEquals(0.5f, MenuMotion.opening(17f, 300f).presence, 0.12f)
        assertEquals(1f, MenuMotion.opening(80f, 300f).presence, 0f)
        // No dark line under the lip: the shadow comes with the opening, full by 60 % of the way.
        assertTrue(MenuMotion.opening(30f, 300f).shadow < 0.05f)
        assertEquals(1f, MenuMotion.opening(130f, 300f).shadow, 0f)
    }

    @Test fun aPopupNoTallerThanItsLipIsSimplyThere() {
        val g = MenuMotion.opening(0f, 16f)
        assertEquals(16f, g.heightDp, 0f)
        assertTrue(MenuMotion.opening(100f, 16f).done)
    }

    @Test fun eachBlockDropsIntoPlaceInTurnWithoutShrinkingBack() {
        // v2: in a popup of five blocks, block i starts at 24 + 16 × i ms, 6 dp above its place.
        assertEquals(-6f, MenuMotion.block(0, 23f, 5).dy, 0f)
        assertEquals(0f, MenuMotion.block(0, 23f, 5).alpha, 0f)
        assertEquals(-6f, MenuMotion.block(3, 71f, 5).dy, 0f)
        assertTrue(MenuMotion.block(3, 80f, 5).dy > -6f)
        var most = -6f
        for (ms in 24..700) { val b = MenuMotion.block(0, ms.toFloat(), 5); most = maxOf(most, b.dy) }
        assertTrue("overshoot ${most} dp", most < 0.1f)
        // Readable about 57 ms in; within half a dp by about 140 ms on spring(0.86, 700).
        assertTrue(MenuMotion.block(0, 24f + 57f, 5).alpha > 0.85f)
        assertEquals(0f, MenuMotion.block(8, 152f + 140f, 9).dy, 0.5f)
        assertTrue(MenuMotion.block(8, 152f + 400f, 9).done)
        assertEquals(0f, MenuMotion.block(8, 152f + 400f, 9).dy, 0f)
        assertEquals(1f, MenuMotion.block(8, 152f + 400f, 9).alpha, 0f)
    }

    @Test fun aTallPopupsBlocksSpreadOverTheSameTimeRatherThanArrivingInAClump() {
        // Sixteen blocks (the Weather popup): the last starts at 152 ms, and no two start together, so the lower glass is
        // never left empty while a clump waits (the motion review of the built 1.3; v2's numbers).
        assertEquals(152f, MenuMotion.blockStartMs(15, 16), 0.01f)
        for (i in 1 until 16) assertTrue(MenuMotion.blockStartMs(i, 16) > MenuMotion.blockStartMs(i - 1, 16))
        // Up to nine blocks, 16 ms apart.
        assertEquals(24f + 16f * 4, MenuMotion.blockStartMs(4, 5), 0f)
        assertEquals(152f, MenuMotion.blockStartMs(8, 9), 0f)
        assertEquals(24f, MenuMotion.blockStartMs(0, 1), 0f)
        assertEquals(552f, MenuMotion.blocksDoneMs(16), 0.01f)
    }

    @Test fun closingFoldsBackIntoTheItemAndFadesAsItLands() {
        val h = 300f                                          // T 208, so the fold takes 0.45 × 208 = 93.6 ms
        val c = MenuMotion.closing(0f, h, h, 1f)
        assertEquals(h, c.heightDp, 0f); assertEquals(1f, c.presence, 0f); assertEquals(1f, c.contents, 0f)
        assertEquals(0f, MenuMotion.closing(40f, h, h, 1f).contents, 0f)    // the contents go first, in place, in 40 ms
        assertEquals(lip, MenuMotion.closing(93.6f, h, h, 1f).heightDp, 0.01f)
        assertEquals(1f, MenuMotion.closing(68f, h, h, 1f).presence, 0f)    // presence falls from 25 ms before the fold ends
        assertEquals(0.5f, MenuMotion.closing(93.6f, h, h, 1f).presence, 0.01f)
        assertEquals(118.6f, MenuMotion.closeMs(h, h), 0.01f)
        assertTrue(MenuMotion.closing(118.6f, h, h, 1f).done)
        assertEquals(0f, MenuMotion.closing(118.6f, h, h, 1f).presence, 0f)
    }

    @Test fun closedWhileStillOpeningItFoldsFromWhereItIsAndFaster() {
        // Three quarters open: the fold is three quarters as long, but never under 50 ms.
        val f = MenuMotion.closeMs(lip + (300f - lip) * 0.75f, 300f) - 25f
        assertEquals(93.6f * 0.75f, f, 0.5f)
        assertEquals(50f + 25f, MenuMotion.closeMs(lip + 4f, 300f), 0.01f)
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
        // Presence comes back in 60 ms, the contents in 100 ms, both from where they were (unchanged in v2).
        assertEquals(0.6f, start.presence, 0f); assertEquals(1f, MenuMotion.reopening(60f, 150f, -2f, h, 0.6f, 0.2f).presence, 0f)
        assertEquals(0.2f, start.contents, 0f); assertEquals(1f, MenuMotion.reopening(100f, 150f, -2f, h, 0.6f, 0.2f).contents, 0f)
    }

    @Test fun aPopupLeftForAnotherDissolvesInPlace() {
        assertEquals(1f, MenuMotion.dissolve(0f), 0f)
        assertEquals(0.5f, MenuMotion.dissolve(37.5f), 0.01f)
        assertEquals(0f, MenuMotion.dissolve(75f), 0f)
        assertEquals(75f, MenuMotion.DISSOLVE_MS, 0f)
    }

    @Test fun theSystemsAnimationSpeedStretchesEverythingAndRemoveAnimationsSkipsIt() {
        assertEquals(100f, MenuMotion.scaled(100f, 1f), 0f)
        assertEquals(50f, MenuMotion.scaled(100f, 2f), 0f)          // "Animator duration scale 2×": twice as slow
        assertEquals(Float.POSITIVE_INFINITY, MenuMotion.scaled(0f, 0f), 0f)
        assertTrue(MenuMotion.opening(MenuMotion.scaled(0f, 0f), 300f).done)
        assertTrue(MenuMotion.block(0, MenuMotion.scaled(0f, 0f), 3).done)
        assertTrue(MenuMotion.closing(MenuMotion.scaled(0f, 0f), 300f, 300f, 1f).done)
    }

    @Test fun eachPartStartsAfterItsBlockByItsOffsetAndEverythingHasStartedBy180Ms() {
        // A part 20 ms into the third of five blocks: 24 + 2 × 16 + 20.
        assertEquals(76f, MenuMotion.partStartMs(block = 2, blocks = 5, offsetMs = 20f, lastStartMs = 150f), 0.01f)
        // A popup whose last part would start at 240 ms: every start is scaled by 180 ÷ 240, order and proportions kept.
        assertEquals(76f * 0.75f, MenuMotion.partStartMs(2, 5, 20f, lastStartMs = 240f), 0.01f)
        assertEquals(180f, MenuMotion.partStartMs(4, 5, 152f, lastStartMs = 240f), 0.01f)
    }

    @Test fun theVerbsEndExactlyWhereThePartRestsAndNothingPassesItsPlace() {
        // Fade: 100 ms on (0.2, 0, 0, 1).
        assertEquals(0f, MenuMotion.fade(0f), 0f)
        assertEquals(1f, MenuMotion.fade(100f), 0f)
        assertTrue(MenuMotion.fade(50f) > 0.7f)
        // Pop from 0.85: at its place at rest, never more than 0.2 % past it, fully there by about 120 ms.
        assertEquals(0.85f, MenuMotion.pop(0f, 0.85f).scale, 0f)
        assertEquals(0f, MenuMotion.pop(0f, 0.85f).alpha, 0f)
        var most = 0f
        for (ms in 0..400) most = maxOf(most, MenuMotion.pop(ms.toFloat(), 0.85f).scale)
        assertTrue("scale reached $most", most <= 1.002f)
        assertEquals(1f, MenuMotion.pop(120f, 0.85f).alpha, 0.001f)
        assertEquals(1f, MenuMotion.pop(500f, 0.85f).scale, 0f)
        // Draw and fill: 180 ms on (0.35, 0, 0.1, 1), from nothing to all.
        assertEquals(0f, MenuMotion.draw(0f), 0f)
        assertEquals(1f, MenuMotion.draw(180f), 0f)
        assertTrue(MenuMotion.draw(90f) in 0.4f..0.9f)
        // Before its start a part isn't there at all.
        assertEquals(0f, MenuMotion.fade(-10f), 0f)
        assertEquals(0f, MenuMotion.pop(-10f, 0.9f).alpha, 0f)
        assertEquals(0f, MenuMotion.draw(-10f), 0f)
        // "No animations": no time at all, every part at rest.
        assertEquals(1f, MenuMotion.fade(Float.POSITIVE_INFINITY), 0f)
        assertEquals(1f, MenuMotion.pop(Float.POSITIVE_INFINITY, 0.8f).scale, 0f)
        assertEquals(1f, MenuMotion.draw(Float.POSITIVE_INFINITY), 0f)
    }
}
