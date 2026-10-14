package io.github.kuscher.bentobar.ui

import io.github.kuscher.bentobar.data.ItemConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** How long a deleted item is offered back by the snackbar on Bar. */
class UndoTest {
    private val gone = Undo.Deleted(ItemConfig("a", "cpu"), 2, at = 1_000)

    @Test fun aDeletionIsOfferedBackForAsLongAsTheSnackbarShowsAndNeverLater() {
        // The window recreated (a resize, a theme) within that time: the snackbar comes back.
        assertTrue(Undo.offered(gone, now = 1_000))
        assertTrue(Undo.offered(gone, now = 1_000 + Undo.OFFER_MS - 1))
        // Settings left and opened again later: the old deletion is not offered again.
        assertFalse(Undo.offered(gone, now = 1_000 + Undo.OFFER_MS))
        assertFalse(Undo.offered(gone, now = 500))
    }
}
