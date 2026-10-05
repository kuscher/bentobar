package io.github.kuscher.bentobar.bar

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BarPixelsTest {
    private val w = 54
    private val h = 26

    /**
     * A clock-sized box: [bg] per row, with digit-like bars of [ink] in rows 6..19. Each bar has a
     * solid core and one column of half-blended edge on both sides, as anti-aliased text has.
     */
    private fun box(ink: Int, bg: (row: Int) -> Int): IntArray {
        val px = IntArray(w * h) { bg(it / w) }
        for (y in 6..19) for (bar in 0 until 5) {
            val x = 4 + bar * 10
            px[y * w + x] = blend(ink, bg(y))
            for (dx in 1..3) px[y * w + x + dx] = ink
            px[y * w + x + 4] = blend(ink, bg(y))
        }
        return px
    }

    private fun blend(a: Int, b: Int): Int {
        fun ch(shift: Int) = (((a ushr shift) and 0xFF) + ((b ushr shift) and 0xFF)) / 2
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun rgb(c: Color?) = c!!.toArgb() and 0xFFFFFF

    @Test fun seeThroughBarReadsItsGlyphs() {
        val c = BarPixels.colors(box(WHITE) { 0 }, w)!!
        assertEquals(0xFFFFFF, rgb(c.text))
        assertNull(c.background)
    }

    @Test fun blackBarReadsWhiteTextNotGray() {
        val c = BarPixels.colors(box(WHITE) { BLACK }, w)!!
        assertEquals(0x000000, rgb(c.background))
        // The blended edges used to be averaged in: #EFEFEF beside the system's white on a device.
        assertEquals(0xFFFFFF, rgb(c.text))
    }

    @Test fun lightBarReadsDarkText() {
        val c = BarPixels.colors(box(0xFF1F1F1F.toInt()) { WHITE }, w)!!
        assertEquals(0xFFFFFF, rgb(c.background))
        assertEquals(0x1F1F1F, rgb(c.text))
    }

    /** No single background colour is as common as the digits' solid white: the edge of the box still says which is which. */
    @Test fun gradedBackgroundIsNotTakenForTheText() {
        val c = BarPixels.colors(box(WHITE) { row -> (0xFF shl 24) or ((row * 9) shl 16) or ((row * 9) shl 8) or (row * 9) }, w)!!
        assertTrue("background is dark", Contrast.ratio(Color.White, c.background!!) > 4.5f)
        assertEquals(0xFFFFFF, rgb(c.text))
    }

    @Test fun emptyBoxHasABackgroundAndNoText() {
        val c = BarPixels.colors(IntArray(w * h) { BLACK }, w)!!
        assertNull(c.text)
        assertNotNull(c.background)
    }

    /** Caught while the bar fades in: nothing is opaque yet, so there is no reading (the last one is kept). */
    @Test fun fadingBarIsNoReading() {
        assertNull(BarPixels.colors(box(0x80FFFFFF.toInt()) { 0x40000000 }, w))
        assertNull(BarPixels.colors(IntArray(0), w))
    }

    private companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
        const val BLACK = 0xFF000000.toInt()
    }
}
