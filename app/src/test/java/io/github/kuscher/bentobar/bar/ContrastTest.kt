package io.github.kuscher.bentobar.bar

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastTest {
    private val black = Color.Black
    private val white = Color.White
    private val grey = Color(0xFF474747) // what the old sampler produced on the Acer's black bar

    @Test fun ratiosMatchWcag() {
        assertEquals(21f, Contrast.ratio(white, black), 0.01f)
        assertEquals(2.25f, Contrast.ratio(grey, black), 0.05f)
    }

    @Test fun greyOnABlackBarBecomesWhite() {
        val look = Contrast.resolve(grey, black)
        assertEquals(white, look.fg)
        assertTrue(look.barDark)
        assertTrue(look.opaque)
    }

    @Test fun readableSampleIsKept() {
        val pale = Color(0xFFE8EAED)
        assertEquals(pale, Contrast.resolve(pale, black).fg)
    }

    @Test fun lightOpaqueBarGetsDarkText() {
        val look = Contrast.resolve(Color(0xFFBDBDBD), Color(0xFFF1F3F4))
        assertFalse(look.barDark)
        assertTrue(Contrast.ratio(look.fg, look.background) >= Contrast.MIN)
    }

    @Test fun transparentBarJudgedByTheText() {
        assertTrue(Contrast.resolve(white, null).barDark)
        assertFalse(Contrast.resolve(Contrast.DARK_TEXT, null).barDark)
    }

    @Test fun midToneBarStillReaches45() {
        val teal = Color(0xFF3F6F73)
        val look = Contrast.resolve(Color(0xFF6A8E91), teal)
        assertTrue(Contrast.ratio(look.fg, teal) >= Contrast.MIN)
    }

    @Test fun accentFallsBackWhenItWouldNotRead() {
        val teal = Color(0xFF3F6F73)
        val look = Contrast.resolve(white, teal)
        val strip = StripLook(look.fg, look.barDark, io.github.kuscher.bentobar.data.TextSize.DEFAULT,
            androidx.compose.ui.unit.Dp(12f), io.github.kuscher.bentobar.data.Pill.NONE, look.background)
        assertTrue(Contrast.ratio(strip.accent, teal) >= Contrast.MIN)
        assertTrue(Contrast.ratio(strip.warn, teal) >= Contrast.MIN)
    }
}
