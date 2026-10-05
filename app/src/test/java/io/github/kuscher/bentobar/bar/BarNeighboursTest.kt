package io.github.kuscher.bentobar.bar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which changes in the window list ask for a new reading of the status bar's colours, and which don't. */
class BarNeighboursTest {
    private val barBottom = 54
    private val barWidth = 2880

    private val home = AppWindow(196, 0, 0, 2880, 1800)
    private val floating = AppWindow(194, 1483, 116, 2880, 1443)
    private val other = AppWindow(193, 489, 199, 2559, 1567)
    private val maximized = AppWindow(7, 0, 54, 2880, 1716)
    private val fullScreen = AppWindow(215, 0, 0, 2880, 1800)
    private val dialog = AppWindow(235, 1020, 577, 1860, 1276)

    /** Runs the lists one after the other and says for each whether a reading was asked for. */
    private fun readings(vararg lists: List<AppWindow>): List<Boolean> {
        var n = BarNeighbours()
        return lists.map { l -> n.next(barBottom, barWidth, l).let { (next, moved) -> n = next; moved } }
    }

    private fun desktop(vararg above: AppWindow) = above.toList() + home

    @Test fun theFirstLookAtTheDesktopAsksOnce() {
        assertEquals(listOf(true, false, false), readings(desktop(floating), desktop(floating), desktop(floating, other)))
    }

    @Test fun windowsAwayFromTheBarDontCount() {
        assertEquals(listOf(false, false), readings(listOf(floating), listOf(floating, other)))
    }

    @Test fun aWindowMaximizedAndRestoredAsksBothTimes() {
        assertEquals(listOf(true, true, false, true),
            readings(desktop(floating), listOf(maximized, floating), listOf(maximized, floating), desktop(maximized.copy(top = 200, right = 2000), floating)))
    }

    @Test fun aPixelOrTwoOffIsStillAgainstTheBar() {
        fun against(top: Int) = BarNeighbours().next(barBottom, barWidth, listOf(maximized.copy(top = top))).first.against
        val there = setOf(BarNeighbours.Against(7, 0, 2880))
        assertEquals(there, against(54))
        assertEquals(there, against(56))
        assertEquals(there, against(52))
        assertEquals(emptySet<BarNeighbours.Against>(), against(57))
    }

    @Test fun snappedWindowsAreReadLeftToRight() {
        val left = AppWindow(8, 0, 54, 1440, 1716)
        val right = AppWindow(9, 1440, 54, 2880, 1716)
        assertEquals(setOf(BarNeighbours.Against(8, 0, 1440), BarNeighbours.Against(9, 1440, 2880)),
            BarNeighbours().next(barBottom, barWidth, listOf(right, left)).first.against)
        assertEquals(listOf(true, true, false), readings(listOf(left), listOf(left, right), listOf(right, left)))
    }

    @Test fun anotherWindowAgainstTheBarInTheSamePlaceIsNoChange() {
        // The bar is the system's own black there whatever the app.
        assertEquals(listOf(true, false), readings(listOf(maximized), listOf(maximized.copy(id = 12))))
    }

    /**
     * What a list that ends early costs against the bar: a dialog over a maximized window is the
     * whole list, the maximized window is not in it, and the bar is as black as before. That was a
     * reading when the dialog opened and another when it closed.
     */
    @Test fun aDialogOverAMaximizedWindowCostsNoReading() {
        assertEquals(listOf(true, false, false, false, false),
            readings(listOf(maximized, floating), listOf(dialog), listOf(maximized, floating), listOf(dialog), listOf(maximized)))
        // The same with two windows snapped side by side.
        val left = AppWindow(8, 0, 54, 1440, 1716)
        val right = AppWindow(9, 1440, 54, 2880, 1716)
        assertEquals(listOf(true, false, false), readings(listOf(left, right), listOf(dialog), listOf(right, left)))
    }

    @Test fun aMaximizedWindowThatIsClosedAsksWhenTheDesktopShows() {
        // Closed or minimized: it is not in the list, and the list reaches the home screen again.
        assertEquals(listOf(true, true, false), readings(listOf(maximized), desktop(floating), desktop(floating)))
        // Closed behind a dialog: nothing is known until the dialog goes; then the desktop says it.
        assertEquals(listOf(true, false, true, false), readings(listOf(maximized), listOf(dialog), desktop(floating), desktop()))
        // Covered by another maximized window, which then closes: the bar was black all along.
        assertEquals(listOf(true, false, false), readings(listOf(maximized), listOf(maximized.copy(id = 12)), listOf(maximized)))
    }

    @Test fun aMaximizedWindowThatGoesFullScreenAsks() {
        assertEquals(listOf(true, true, true), readings(listOf(maximized), listOf(maximized.copy(top = 0, bottom = 1800)), listOf(maximized)))
    }

    @Test fun aNarrowWindowChangesNothing() {
        assertEquals(listOf(false, false), readings(listOf(AppWindow(30, 100, 54, 500, 400)), listOf(AppWindow(31, 100, 0, 500, 400))))
    }

    @Test fun aWindowThatEndsAtTheBarIsNotUnderIt() {
        assertEquals(listOf(false), readings(listOf(AppWindow(40, 0, 0, 2880, 54))))
    }

    /**
     * Seen on a device: a light app opened full screen with the bar kept. The bar's icons turned dark
     * and the strip stayed white, because no window sat against the bar's lower edge before or after.
     */
    @Test fun aFullScreenAppUnderTheBarAsksOnTheWayInAndOut() {
        assertEquals(listOf(true, true, false, true),
            readings(desktop(floating), listOf(fullScreen), listOf(fullScreen), desktop(floating)))
        // Another full-screen app in its place can ask for other icons.
        assertEquals(listOf(true, true), readings(listOf(fullScreen), listOf(fullScreen.copy(id = 216))))
    }

    /**
     * Seen on a device too: with some windows on top (a dialog, some apps' own windows) the list ends
     * there. The home screen leaving the list and coming back is no change of the bar, and no reading.
     */
    @Test fun aListThatEndsEarlySaysNothingAboutTheBar() {
        assertEquals(listOf(true, false, false, false, false),
            readings(desktop(floating), listOf(dialog), desktop(floating), listOf(other), desktop(other, floating)))
        // The same over a full-screen app: its dialog comes and goes.
        assertEquals(listOf(true, false, false), readings(listOf(fullScreen), listOf(dialog), listOf(fullScreen)))
    }

    @Test fun aFullScreenAppThatBecomesAWindowAgainAsksEvenWhenTheListEndsAtIt() {
        val windowed = fullScreen.copy(left = 403, top = 191, right = 2476, bottom = 1487)
        assertEquals(listOf(true, true, false, true), readings(listOf(fullScreen), listOf(windowed), listOf(windowed), desktop(windowed)))
    }

    /**
     * Under a full-screen app every screen it opens is another window under the bar, and each may
     * bring other icons: twenty screens were forty readings. A reading is a screenshot of the bar, so
     * they keep two seconds apart, and the last change is always read.
     */
    @Test fun readingsAskedForByTheWindowsKeepApart() {
        assertEquals(300, BarNeighbours.wait(sinceLastMs = 60_000))
        assertEquals(300, BarNeighbours.wait(sinceLastMs = 2_000))
        assertEquals(2_000, BarNeighbours.wait(sinceLastMs = 0))
        assertEquals(1_000, BarNeighbours.wait(sinceLastMs = 1_000))
        // Never sooner than the bar needs to settle.
        assertEquals(300, BarNeighbours.wait(sinceLastMs = 1_900))
        // A window change every 400 ms for 20 seconds, the reading always re-timed by the latest: one every two seconds.
        var lastRead = -10_000L
        var due: Long? = null
        var readings = 0
        for (t in 0L..20_000L step 100) {
            due?.let { if (t >= it) { readings++; lastRead = t; due = null } }
            if (t % 400 == 0L) due = t + BarNeighbours.wait(t - lastRead)
        }
        assertTrue("$readings readings", readings in 9..11)
    }

    @Test fun whatIsRemembered() {
        val (seen, moved) = BarNeighbours().next(barBottom, barWidth, desktop(floating))
        assertTrue(moved)
        assertEquals(setOf(BarNeighbours.Under(196, 0, 2880)), seen.under)
        val (after, again) = seen.next(barBottom, barWidth, listOf(dialog))
        assertFalse(again)
        assertEquals(seen, after)
    }
}
