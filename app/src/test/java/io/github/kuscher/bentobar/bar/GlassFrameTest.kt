package io.github.kuscher.bentobar.bar

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Fitting a popup's window to its glass before a frame (bar/MenuWindow.kt). Each step of that work asks for another
 * frame, so it must stop once the glass is still: in 1.3 it ran every frame while a popup was open (60 frames a
 * second at rest, a tenth of a CPU core, and the screen behind re-blurred once a second).
 */
class GlassFrameTest {
    private val nothing = GlassFrame.Work(bounds = false, outline = false)
    private val both = GlassFrame.Work(bounds = true, outline = true)

    @Test fun aPopupAtRestDoesNothing() {
        assertEquals(nothing, GlassFrame.work(rootRight = 510, rootBottom = 924, width = 510, bottom = 924, corner = 36f, lastCorner = 36f))
    }

    @Test fun aLayoutPassThatGaveTheRootTheWholeWindowIsUndone() {
        // While opening, a layout pass sets the root back to the whole window: the blur would show below the glass.
        assertEquals(both, GlassFrame.work(rootRight = 510, rootBottom = 924, width = 510, bottom = 300, corner = 36f, lastCorner = 36f))
    }

    @Test fun aGrowingGlassIsFollowed() {
        assertEquals(both, GlassFrame.work(rootRight = 510, rootBottom = 280, width = 510, bottom = 300, corner = 36f, lastCorner = 36f))
        assertEquals(both, GlassFrame.work(rootRight = 480, rootBottom = 300, width = 510, bottom = 300, corner = 36f, lastCorner = 36f))
    }

    @Test fun cornersThatChangeAloneOnlyRedoTheOutline() {
        // The lip: its corners are half its height, so they shrink with it.
        assertEquals(GlassFrame.Work(bounds = false, outline = true),
            GlassFrame.work(rootRight = 510, rootBottom = 30, width = 510, bottom = 30, corner = 15f, lastCorner = 16f))
    }
}
