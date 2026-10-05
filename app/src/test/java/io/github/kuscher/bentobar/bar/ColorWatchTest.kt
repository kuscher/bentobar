package io.github.kuscher.bentobar.bar

import io.github.kuscher.bentobar.bar.ColorWatch.Cause
import io.github.kuscher.bentobar.bar.ColorWatch.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the status bar's colours are read after something that can change them, and when the readings stop. */
class ColorWatchTest {
    /** A strip that follows a bar: [look] is what the strip shows, [at] when it read the bar. */
    private class Strip(var look: String?, val watch: ColorWatch = ColorWatch()) {
        val at = ArrayList<Long>()
        var now = 0L

        /** Reads whenever a reading is due, up to [until]; [bar] is the bar's look at a moment, null for a reading that gives nothing. */
        fun follow(until: Long = 60_000, bar: (Long) -> String?): Strip {
            while (true) {
                val wait = watch.due(now) ?: break
                if (now + wait > until) break
                now += wait
                at += now
                val seen = bar(now)
                val result = when (seen) { null -> Result.NOTHING; look -> Result.SAME; else -> Result.CHANGED }
                if (seen != null) look = seen
                if (watch.read(now, result)) look = HINTS
            }
            return this
        }
    }

    /**
     * Seen on a device: after an unlock the strip stayed white. The bar keeps the lock screen's light icons for
     * a while after the strip is back, then turns dark with no event. 0.9 read the bar 250 ms after the strip
     * came back (white, not the dark it had: a change) and once more 800 ms later (white again: settled, it
     * thought), and never again.
     */
    @Test fun afterAnUnlockTheBarIsReadUntilItHasSettled() {
        val strip = Strip("dark")
        strip.watch.cause(Cause.SLOW, now = 0, first = 250)
        strip.follow { t -> if (t < 3_000) "white" else "dark" }
        assertEquals(listOf(250L, 1_000L, 2_000L, 3_500L, 6_000L, 10_000L), strip.at)
        assertEquals("dark", strip.look)
    }

    /** The same when the bar takes eight seconds (a wake from a long sleep): the last reading finds the change, so one more confirms it. */
    @Test fun aReturnIsWatchedForTenSeconds() {
        val strip = Strip("dark")
        strip.watch.cause(Cause.SLOW, now = 0, first = 250)
        strip.follow { t -> if (t < 8_000) "white" else "dark" }
        assertEquals(listOf(250L, 1_000L, 2_000L, 3_500L, 6_000L, 10_000L, 10_800L), strip.at)
        assertEquals("dark", strip.look)
        assertNull(strip.watch.due(strip.now))
    }

    /**
     * A window is maximized: its bounds say so at once, the bar turns black when the window has landed. A
     * reading before that shows the old look, which is no proof that nothing will change: the second
     * reading is taken whatever the first one showed (and, having found the change, is confirmed).
     */
    @Test fun aReadingThatMatchesTheLastOneDoesNotEndTheWatch() {
        val strip = Strip("see-through")
        strip.watch.cause(Cause.QUICK, now = 0, first = 300)
        strip.follow { t -> if (t < 600) "see-through" else "black" }
        assertEquals(listOf(300L, 1_300L, 2_100L), strip.at)
        assertEquals("black", strip.look)
    }

    @Test fun aBarThatDidNotChangeCostsTwoReadings() {
        val strip = Strip("black")
        strip.watch.cause(Cause.QUICK, now = 0, first = 300)
        strip.follow { "black" }
        assertEquals(listOf(300L, 1_300L), strip.at)
    }

    /** The bar fades: a look that still differed at the last reading may have been caught halfway. */
    @Test fun aLookStillMovingAtTheLastReadingIsReadOnceMore() {
        val strip = Strip("see-through")
        strip.watch.cause(Cause.QUICK, now = 0, first = 300)
        strip.follow { t -> when { t < 1_000 -> "see-through"; t < 1_500 -> "halfway"; else -> "black" } }
        assertEquals(listOf(300L, 1_300L, 2_100L, 2_900L), strip.at)
        assertEquals("black", strip.look)
    }

    @Test fun aBarThatNeverSettlesIsNotReadForever() {
        val strip = Strip("a")
        strip.watch.cause(Cause.QUICK, now = 0, first = 300)
        strip.follow { t -> "look at $t" }
        assertEquals(listOf(300L, 1_300L, 2_100L, 2_900L, 3_700L), strip.at)
        assertNull(strip.watch.due(strip.now))
    }

    /**
     * Under a full-screen app every screen it opens is another window under the bar. The readings keep two
     * seconds apart as before ([BarNeighbours.wait]): a new change takes the place of the second reading of
     * the one before, and only the last change gets both.
     */
    @Test fun windowChangesInAStreamKeepTheirReadingsApart() {
        val watch = ColorWatch()
        val at = ArrayList<Long>()
        var lastRead = -10_000L
        for (t in 0L..24_000L step 100) {
            if (watch.due(t) == 0L) { at += t; lastRead = t; watch.read(t, Result.SAME) }
            if (t <= 20_000 && t % 400 == 0L) watch.cause(Cause.QUICK, t, BarNeighbours.wait(t - lastRead))
        }
        val during = at.filter { it <= 20_000 }
        assertTrue("$during", during.size in 9..11)
        assertTrue("$during", during.zipWithNext().all { (a, b) -> b - a >= BarNeighbours.APART_MS })
        // After the last change: its reading, and the one a second later.
        assertEquals("$at", 2, at.size - during.size)
    }

    /** A window change while a return is still watched adds its readings and takes none of the late ones away. */
    @Test fun aWindowChangeDuringAReturnKeepsTheLateReadings() {
        val strip = Strip("dark")
        strip.watch.cause(Cause.SLOW, now = 0, first = 250)
        strip.follow(until = 400) { "white" }
        assertEquals(listOf(250L), strip.at)
        strip.watch.cause(Cause.QUICK, now = 400, first = BarNeighbours.wait(400 - 250))
        strip.follow { t -> if (t < 4_000) "white" else "dark" }
        // 2 000 serves the window change's 2 250 too, and 3 250 the return's 3 500.
        assertEquals(listOf(250L, 1_000L, 2_000L, 3_250L, 6_000L, 10_000L), strip.at)
        assertEquals("dark", strip.look)
    }

    /** The strip is back again before the first return was through (a panel opened and closed twice). */
    @Test fun aNewReturnStartsOver() {
        val strip = Strip("dark")
        strip.watch.cause(Cause.SLOW, now = 0, first = 250)
        strip.follow(until = 1_200) { "dark" }
        strip.watch.cause(Cause.SLOW, now = 1_200, first = 250)
        strip.follow { "dark" }
        assertEquals(listOf(250L, 1_000L, 1_450L, 2_200L, 3_200L, 4_700L, 7_200L, 11_200L), strip.at)
    }

    /** A reading can give nothing (the bar caught fading, a screenshot refused): tried twice more, then the hints. */
    @Test fun readingsThatGiveNothingAreTriedAgainAndThenTheHintsAreUsed() {
        val strip = Strip("dark")
        strip.watch.cause(Cause.QUICK, now = 0, first = 300)
        strip.follow { null }
        assertEquals(listOf(300L, 1_300L, 2_800L), strip.at)
        assertEquals(HINTS, strip.look)
        assertNull(strip.watch.due(strip.now))
    }

    @Test fun aReadingAfterTwoThatGaveNothingKeepsTheColours() {
        val strip = Strip("dark")
        strip.watch.cause(Cause.QUICK, now = 0, first = 300)
        strip.follow { t -> if (t < 2_000) null else "black" }
        assertEquals("black", strip.look)
        // 2 800: the look differs from the one before, so one more.
        assertEquals(listOf(300L, 1_300L, 2_800L, 3_600L), strip.at)
    }

    /** With late readings still to come the hints are used after three that gave nothing, and a later reading replaces them. */
    @Test fun theHintsAreUsedOnceAndAReadingReplacesThem() {
        val watch = ColorWatch()
        watch.cause(Cause.SLOW, now = 0, first = 250)
        assertFalse(watch.read(250, Result.NOTHING))
        assertFalse(watch.read(1_000, Result.NOTHING))
        assertTrue(watch.read(2_000, Result.NOTHING))
        assertEquals(1_500L, watch.due(2_000))
        assertFalse(watch.read(3_500, Result.NOTHING))
        assertFalse(watch.read(6_000, Result.NOTHING))
        assertFalse(watch.read(10_000, Result.CHANGED))
        // The look changed at the last reading: one more.
        assertEquals(800L, watch.due(10_000))
    }

    @Test fun nothingIsDueWithoutACauseOrAfterClear() {
        val watch = ColorWatch()
        assertNull(watch.due(0))
        watch.cause(Cause.SLOW, now = 0, first = 250)
        assertEquals(250L, watch.due(0))
        assertEquals(0L, watch.due(900))
        watch.clear()
        assertNull(watch.due(900))
    }

    /** The main thread was busy and two readings fell due: one is taken for both. */
    @Test fun oneReadingServesAllThatAreOverdue() {
        val watch = ColorWatch()
        watch.cause(Cause.SLOW, now = 0, first = 250)
        watch.read(1_100, Result.SAME)
        assertEquals(900L, watch.due(1_100))
    }

    /** Android refuses a screenshot within a third of a second of the last one: a cause right after a reading waits that out. */
    @Test fun aCauseRightAfterAReadingKeepsItsDistance() {
        val watch = ColorWatch()
        watch.cause(Cause.SLOW, now = 0, first = 250)
        watch.read(1_000, Result.SAME)
        watch.cause(Cause.QUICK, now = 1_010, first = 300)
        assertEquals(390L, watch.due(1_010))
        // Its second reading keeps its second.
        watch.read(1_400, Result.SAME)
        assertEquals(600L, watch.due(1_400))
    }

    /** A reading nobody asked for (the test hook) that finds another look is confirmed like any other. */
    @Test fun aReadingOutOfTurnThatFindsAChangeIsConfirmed() {
        val watch = ColorWatch()
        assertFalse(watch.read(5_000, Result.SAME))
        assertNull(watch.due(5_000))
        watch.read(6_000, Result.CHANGED)
        assertEquals(800L, watch.due(6_000))
    }

    private companion object { const val HINTS = "hints" }
}
