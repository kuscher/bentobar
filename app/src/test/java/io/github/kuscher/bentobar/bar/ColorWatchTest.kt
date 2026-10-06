package io.github.kuscher.bentobar.bar

import io.github.kuscher.bentobar.bar.ColorWatch.Cause
import io.github.kuscher.bentobar.bar.ColorWatch.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the status bar's colours are read after something that can change them, when the readings stop, and how many there can be. */
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
                watch.taken(now)
                val seen = bar(now)
                val result = ColorWatch.result(look, seen)
                if (seen != null) look = seen
                if (watch.read(now, result)) look = HINTS
            }
            return this
        }
    }

    /** A screenshot asked for and answered at [t]. */
    private fun ColorWatch.shot(t: Long, result: Result): Boolean { taken(t); return read(t, result) }

    /** The most screenshots asked for within any one minute. */
    private fun mostInAMinute(asked: List<Long>): Int {
        var most = 0
        var from = 0
        for (to in asked.indices) {
            while (asked[to] - asked[from] >= 60_000) from++
            most = maxOf(most, to - from + 1)
        }
        return most
    }

    // ---- one cause ---------------------------------------------------------------------------

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

    /** Whenever the bar turns within those ten seconds, the strip follows, at the latest four seconds behind. */
    @Test fun whereverTheChangeFallsAfterAReturnItIsRead() {
        for (turn in 0L..9_750L step 250) {
            val strip = Strip("dark")
            strip.watch.cause(Cause.SLOW, now = 0, first = 250)
            strip.follow { t -> if (t < turn) "white" else "dark" }
            assertEquals("the bar turned at $turn", "dark", strip.look)
            val seen = strip.at.first { it >= turn }
            assertTrue("the bar turned at $turn and was read at $seen", seen - turn <= 4_000)
        }
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

    /** And not for longer: a bar that turns after that is read at the next cause, as before. Only a timer would catch it. */
    @Test fun aBarThatTurnsAfterTheWatchIsNotNoticed() {
        val strip = Strip("dark")
        strip.watch.cause(Cause.SLOW, now = 0, first = 250)
        strip.follow { t -> if (t < 12_000) "white" else "dark" }
        assertEquals(10_000L, strip.at.last())
        assertEquals("white", strip.look)
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
        // The next cause gets its own readings again, and its own three more.
        strip.watch.cause(Cause.QUICK, now = 5_000, first = 300)
        strip.follow { t -> "look at $t" }
        assertEquals(listOf(5_300L, 6_300L, 7_100L, 7_900L, 8_700L), strip.at.drop(5))
    }

    // ---- causes that meet ---------------------------------------------------------------------

    /**
     * A window is maximized right after an unlock. Its readings come at once (it is the first window change
     * in a while) and stand in for the unlock's readings that fall beside them; the late ones stay.
     */
    @Test fun aWindowChangeDuringAReturnKeepsTheLateReadings() {
        val strip = Strip("dark")
        strip.watch.cause(Cause.SLOW, now = 0, first = 250)
        strip.follow(until = 400) { "white" }
        assertEquals(listOf(250L), strip.at)
        strip.watch.windows(now = 400)
        strip.follow { t -> if (t < 4_000) "white" else "dark" }
        // 700 serves the return's 1 000 too, and 1 700 its 2 000.
        assertEquals(listOf(250L, 700L, 1_700L, 3_500L, 6_000L, 10_000L), strip.at)
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

    /** A second window change before the first one's look was confirmed: its readings take the place of the confirming one. */
    @Test fun aQuickCauseTakesThePlaceOfAConfirmStillToCome() {
        val strip = Strip("a")
        strip.watch.cause(Cause.QUICK, now = 0, first = 300)
        strip.follow(until = 1_400) { t -> if (t < 1_000) "a" else "b" }
        assertEquals(2_100L - 1_300L, strip.watch.due(1_300))
        strip.watch.cause(Cause.QUICK, now = 1_500, first = 300)
        strip.follow { "b" }
        assertEquals(listOf(300L, 1_300L, 1_800L, 2_800L), strip.at)
    }

    // ---- screenshots on their way --------------------------------------------------------------

    /** Android refuses a screenshot within a third of a second of the last one: a cause right after one waits that out. */
    @Test fun aCauseRightAfterAScreenshotKeepsItsDistance() {
        val watch = ColorWatch()
        watch.shot(1_000, Result.SAME)
        watch.cause(Cause.QUICK, now = 1_010, first = 300)
        assertEquals(390L, watch.due(1_010))
        // Its second reading keeps its second.
        watch.taken(1_400)
        assertEquals(1_000L, watch.due(1_400))
    }

    /**
     * The answer takes a moment. A window change in that moment is not served by a screenshot asked for
     * before it, and the reading that screenshot is for is not asked for a second time.
     */
    @Test fun aCauseWhileAScreenshotIsOnItsWayGetsItsOwn() {
        val watch = ColorWatch()
        watch.cause(Cause.SLOW, now = 0, first = 250)
        watch.shot(250, Result.SAME)
        watch.taken(1_000)
        watch.windows(now = 1_020)
        assertEquals(380L, watch.due(1_020))
        assertFalse(watch.read(1_050, Result.SAME))
        assertEquals(350L, watch.due(1_050))
    }

    /** The main thread was busy and two readings fell due: one screenshot is taken for both. */
    @Test fun oneScreenshotServesAllThatAreOverdue() {
        val watch = ColorWatch()
        watch.cause(Cause.SLOW, now = 0, first = 250)
        watch.taken(1_100)
        assertEquals(900L, watch.due(1_100))
    }

    // ---- readings that give nothing -------------------------------------------------------------

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
        assertFalse(watch.shot(250, Result.NOTHING))
        assertFalse(watch.shot(1_000, Result.NOTHING))
        assertTrue(watch.shot(2_000, Result.NOTHING))
        assertEquals(1_500L, watch.due(2_000))
        assertFalse(watch.shot(3_500, Result.NOTHING))
        assertFalse(watch.shot(6_000, Result.NOTHING))
        assertFalse(watch.shot(10_000, Result.CHANGED))
        // The look changed at the last reading: one more.
        assertEquals(800L, watch.due(10_000))
    }

    /** What a reading is, against what the strip had; after the hints (the strip has no reading) any reading is a change. */
    @Test fun whatAReadingShows() {
        assertEquals(Result.SAME, ColorWatch.result("white", "white"))
        assertEquals(Result.CHANGED, ColorWatch.result("white", "dark"))
        assertEquals(Result.NOTHING, ColorWatch.result("white", null))
        assertEquals(Result.NOTHING, ColorWatch.result(null, null))
        assertEquals(Result.CHANGED, ColorWatch.result(null, "white"))
    }

    // ---- nothing to read -----------------------------------------------------------------------

    @Test fun nothingIsDueWithoutACauseOrAfterClear() {
        val watch = ColorWatch()
        assertNull(watch.due(0))
        watch.cause(Cause.SLOW, now = 0, first = 250)
        assertEquals(250L, watch.due(0))
        assertEquals(0L, watch.due(900))
        watch.clear()
        assertNull(watch.due(900))
    }

    /** A reading nobody asked for (the test hook) that finds another look is confirmed like any other. */
    @Test fun aReadingOutOfTurnThatFindsAChangeIsConfirmed() {
        val watch = ColorWatch()
        assertFalse(watch.shot(5_000, Result.SAME))
        assertNull(watch.due(5_000))
        watch.shot(6_000, Result.CHANGED)
        assertEquals(800L, watch.due(6_000))
    }

    // ---- causes that keep coming: the budget -----------------------------------------------------

    /**
     * Under a full-screen app every screen it opens is another window under the bar. The first readings keep
     * two seconds apart as before ([BarNeighbours.wait]): a new change takes the place of the second reading
     * of the one before, and only the last change gets both.
     */
    @Test fun windowChangesInAStreamKeepTheirReadingsApart() {
        val watch = ColorWatch()
        val at = ArrayList<Long>()
        for (t in 0L..24_000L step 100) {
            if (watch.due(t) == 0L) { at += t; watch.shot(t, Result.SAME) }
            if (t <= 20_000 && t % 400 == 0L) watch.windows(t)
        }
        val during = at.filter { it <= 20_000 }
        assertTrue("$during", during.size in 9..11)
        assertTrue("$during", during.zipWithNext().all { (a, b) -> b - a >= BarNeighbours.APART_MS })
        // After the last change: its reading, and the one a second later.
        assertEquals("$at", 2, at.size - during.size)
    }

    /**
     * A window change every two seconds, for minutes (an app that cycles its screens). Each gets both readings
     * until a dozen were taken; from then on one reading every two seconds, as 0.9 had it, and the last
     * change still gets its second one.
     */
    @Test fun aWindowChangeEveryTwoSecondsIsReadOnceEach() {
        val watch = ColorWatch()
        val at = ArrayList<Long>()
        for (t in 0L..200_000L step 100) {
            if (watch.due(t) == 0L) { at += t; watch.shot(t, Result.SAME) }
            if (t <= 180_000 && t % 2_000 == 0L) watch.windows(t)
        }
        assertTrue("${at.take(40)}", mostInAMinute(at) <= 36)
        assertTrue(mostInAMinute(at.filter { it > 60_000 }) <= 31)
        assertEquals(2, at.count { it > 180_000 })
    }

    /** A live wallpaper can report new colours every second. 0.9 read the bar every second for that; now every two. */
    @Test fun aWallpaperThatChangesEverySecondIsReadEveryTwoSeconds() {
        val watch = ColorWatch()
        val at = ArrayList<Long>()
        for (t in 0L..180_000L step 100) {
            if (watch.due(t) == 0L) { at += t; watch.shot(t, Result.SAME) }
            if (t % 1_000 == 0L) watch.cause(Cause.QUICK, t, 300)
        }
        assertTrue(mostInAMinute(at.filter { it > 60_000 }) <= 31)
        assertTrue(at.filter { it > 60_000 }.zipWithNext().all { (a, b) -> b - a >= BarNeighbours.APART_MS })
    }

    /** Something that hides and shows the strip every nine seconds must not cost five screenshots each time for good. */
    @Test fun aReturnEveryNineSecondsStaysWithinTheBudget() {
        val watch = ColorWatch()
        val at = ArrayList<Long>()
        for (t in 0L..300_000L step 50) {
            if (watch.due(t) == 0L) { at += t; watch.shot(t, Result.SAME) }
            if (t % 9_000 == 0L) watch.cause(Cause.SLOW, t, 250)
        }
        assertTrue("${mostInAMinute(at.filter { it > 60_000 })}", mostInAMinute(at.filter { it > 60_000 }) <= 20)
    }

    /**
     * Whatever happens, in whatever order, with answers that take up to 150 ms: two screenshots are never
     * asked for within 400 ms of each other, and never more than 45 in any minute.
     */
    @Test fun screenshotsKeepTheirDistanceAndTheirBudgetWhateverHappens() {
        for (seed in 1L..20L) {
            val random = java.util.Random(seed)
            val watch = ColorWatch()
            val asked = ArrayList<Long>()
            val answers = ArrayList<Long>()
            for (t in 0L..300_000L step 10) {
                while (answers.isNotEmpty() && answers.first() <= t) { answers.removeAt(0); watch.read(t, Result.values()[random.nextInt(3)]) }
                if (watch.due(t) == 0L) { watch.taken(t); asked += t; answers += t + random.nextInt(16) * 10; answers.sort() }
                when (random.nextInt(60)) { // something about every 200 ms
                    0 -> watch.windows(t)
                    1 -> watch.cause(Cause.QUICK, t, 100L + random.nextInt(5) * 100)
                    2 -> if (random.nextInt(10) == 0) watch.cause(Cause.SLOW, t, 250)
                    3 -> if (random.nextInt(20) == 0) watch.clear()
                }
            }
            assertTrue("seed $seed", asked.zipWithNext().all { (a, b) -> b - a >= 400 })
            assertTrue("seed $seed: ${mostInAMinute(asked)} in a minute", mostInAMinute(asked) <= 45)
        }
    }

    private companion object { const val HINTS = "hints" }
}
