package io.github.kuscher.bentobar.bar

/** An app window as the window list gives it: its id and its bounds on screen. */
data class AppWindow(val id: Int, val left: Int, val top: Int, val right: Int, val bottom: Int)

/**
 * The app windows that decide how the status bar looks, remembered from one look at the window
 * list to the next: the bar changes its look for them without an event of its own. No Android
 * types, so the cases the devices showed are unit-tested.
 *
 * Two kinds count. A window [against] the bar's lower edge (maximized, or snapped to a side):
 * SystemUI then gives the bar a background of its own, whatever the app. And a window [under] the
 * bar (full screen, with the bar kept): the bar is see-through there and takes that app's light
 * or dark icons, so which window it is counts as well as where it is. A narrow window (a menu, a
 * tooltip) changes nothing.
 */
data class BarNeighbours(val against: String = "", val under: Set<Under> = emptySet()) {
    data class Under(val id: Int, val left: Int, val right: Int)

    /**
     * What to remember after this list, and whether the bar may look different now.
     *
     * The list is not all there is: with some windows on top (a dialog, some apps' windows) it ends
     * at that window, and everything below is missing, the home screen too, which is the window
     * under the bar on the desktop. So a list with no window under the bar says nothing about the
     * bar: the home screen coming and going with every dialog would cost a reading each time, for a
     * bar that never changed. Only another window under the bar counts, or the remembered one being
     * in the list and no longer under the bar (it left full screen).
     */
    fun next(barBottom: Int, barWidth: Int, windows: List<AppWindow>): Pair<BarNeighbours, Boolean> {
        val near = windows.filter { it.top <= barBottom + EDGE && it.bottom > barBottom + EDGE && it.right - it.left >= barWidth / 4 }
        val againstNow = near.filter { it.top >= barBottom - EDGE }.sortedBy { it.left }.joinToString(" ") { "${it.left}-${it.right}" }
        val underNow = near.filter { it.top < barBottom - EDGE }.mapTo(HashSet()) { Under(it.id, it.left, it.right) }
        val left = underNow.isEmpty() && under.any { u -> windows.any { it.id == u.id } }
        val moved = againstNow != against || (underNow.isNotEmpty() && underNow != under) || left
        return BarNeighbours(againstNow, if (underNow.isNotEmpty() || left) underNow else under) to moved
    }

    companion object {
        private const val EDGE = 2
        /** The bar fades to its new look: a reading asked for by a change of the windows waits this long. */
        const val SETTLE_MS = 300L
        /**
         * And such readings keep this far apart. A reading is a screenshot of the bar's own window: under
         * a full-screen app every screen it opens is another window under the bar, and one reading each
         * would be dozens in a minute, of a bar that mostly looks the same.
         */
        const val APART_MS = 2_000L

        /** How long a reading asked for now waits, [sinceLastMs] after the last one was taken. A later change re-times it, so the last change is always read. */
        fun wait(sinceLastMs: Long): Long = if (sinceLastMs >= APART_MS) SETTLE_MS else maxOf(SETTLE_MS, APART_MS - sinceLastMs.coerceAtLeast(0))
    }
}
