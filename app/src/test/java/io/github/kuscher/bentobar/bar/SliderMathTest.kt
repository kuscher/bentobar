package io.github.kuscher.bentobar.bar

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.kuscher.bentobar.data.Pill
import io.github.kuscher.bentobar.data.TextSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The slider in the bar: which level a pointer stands for, and colors that can be told from the bar. */
class SliderMathTest {
    // A track 64 px wide that starts 10 px in.
    private fun at(x: Float, steps: Int = 0, rtl: Boolean = false) = SliderMath.level(x, left = 10f, width = 64f, steps = steps, rtl = rtl)

    @Test fun theLevelIsHowFarAlongTheTrackThePointerIs() {
        assertEquals(0f, at(10f), 0f)
        assertEquals(0.5f, at(42f), 0f)
        assertEquals(1f, at(74f), 0f)
        assertEquals(0.25f, at(26f), 0f)
    }

    @Test fun aPointerPastEitherEndStaysAtThatEnd() {
        // A drag that leaves the track (or the bar) keeps setting the nearest end.
        assertEquals(0f, at(-500f), 0f)
        assertEquals(0f, at(9f), 0f)
        assertEquals(1f, at(75f), 0f)
        assertEquals(1f, at(5_000f), 0f)
    }

    @Test fun withStepsTheLevelIsTheNearestStep() {
        // Media volume has 15 steps: a level is k / 15.
        assertEquals(0f, at(10f, steps = 15), 0f)
        assertEquals(8 / 15f, at(42f, steps = 15), 1e-6f)
        assertEquals(1f, at(74f, steps = 15), 0f)
        assertEquals(1 / 15f, at(10f + 64f / 15f, steps = 15), 1e-6f)
        // Just under halfway between two steps goes down, just over goes up.
        assertEquals(0f, at(10f + 64f / 30f - 0.1f, steps = 15), 0f)
        assertEquals(1 / 15f, at(10f + 64f / 30f + 0.1f, steps = 15), 1e-6f)
    }

    @Test fun aDragAcrossTheTrackVisitsEveryStepOnce() {
        val seen = (0..640).map { at(10f + it / 10f, steps = 15) }.distinct()
        assertEquals(16, seen.size) // 0 and the 15 steps
        assertEquals(seen.sorted(), seen)
    }

    @Test fun rightToLeftCountsFromTheOtherEnd() {
        assertEquals(1f, at(10f, rtl = true), 0f)
        assertEquals(0f, at(74f, rtl = true), 0f)
        assertEquals(0.75f, at(26f, rtl = true), 0f)
        assertEquals(14 / 15f, at(10f + 64f / 15f, steps = 15, rtl = true), 1e-6f)
    }

    @Test fun aTrackWithoutWidthIsEmpty() {
        assertEquals(0f, SliderMath.level(5f, 0f, 0f, 15), 0f)
        assertEquals(0f, SliderMath.level(5f, 0f, -3f, 0), 0f)
    }

    @Test fun snapAndStepAgree() {
        assertEquals(0.6f, SliderMath.snap(0.62f, 5), 1e-6f)
        assertEquals(3, SliderMath.step(0.62f, 5))
        assertEquals(15, SliderMath.step(1f, 15))
        assertEquals(0, SliderMath.step(0f, 15))
        assertEquals(9, SliderMath.step(0.62f, 15)) // 62% of 15 is 9.3
        assertEquals(0.62f, SliderMath.snap(0.62f, 0), 0f) // no steps: any level
        assertEquals(0, SliderMath.step(0.62f, 0))
        // Out of range, or not a number: within the track all the same.
        assertEquals(1f, SliderMath.snap(7f, 15), 0f)
        assertEquals(0f, SliderMath.snap(-1f, 15), 0f)
        assertEquals(0f, SliderMath.snap(Float.NaN, 15), 0f)
    }

    @Test fun theFilledPartIsTheLevelsShareOfTheTrack() {
        assertEquals(0f, SliderMath.filled(0f, 64f), 0f)
        assertEquals(39.68f, SliderMath.filled(0.62f, 64f), 1e-4f)
        assertEquals(64f, SliderMath.filled(1.5f, 64f), 0f)
        assertEquals(0f, SliderMath.filled(Float.NaN, 64f), 0f)
    }

    @Test fun trackAndFillStandOutFromEveryBar() {
        // The spec's 3:1 for the parts of a control, on every gray an opaque bar can be and on a grid of colors.
        val bars = (0..255 step 3).map { Color(it, it, it) } +
            (0..255 step 51).flatMap { r -> (0..255 step 51).flatMap { g -> (0..255 step 51).map { b -> Color(r, g, b) } } }
        for (bar in bars) {
            val live = Contrast.resolve(Contrast.readableOn(bar), bar)
            val look = StripLook(live.fg, live.barDark, TextSize.DEFAULT, 12.dp, Pill.NONE, live.background)
            assertTrue("track on $bar", Contrast.ratio(look.sliderTrack, bar) >= 3f)
            assertTrue("fill on $bar", Contrast.ratio(look.fg, bar) >= 3f)
            assertTrue("muted fill on $bar", Contrast.ratio(look.sliderDimmed, bar) >= 3f)
            // Muted, the kept level is still told from the track, and both from the full fill, wherever a thinner track exists.
            if (look.sliderTrack != look.fg) assertTrue("three different on $bar", look.sliderDimmed != look.sliderTrack && look.sliderDimmed != look.fg)
        }
    }

    @Test fun onASeeThroughBarTheTrackIsTheThinnestThatStillShows() {
        // White text over a dark wallpaper: the track is the faintest of the three choices.
        val dark = StripLook(Color.White, true, TextSize.DEFAULT, 12.dp, Pill.NONE)
        assertEquals(0.45f, dark.sliderTrack.red, 0.01f) // white at 45% over black
        assertTrue(Contrast.ratio(dark.sliderTrack, Color.Black) >= 3f)
        val light = StripLook(Contrast.DARK_TEXT, false, TextSize.DEFAULT, 12.dp, Pill.NONE)
        assertTrue(Contrast.ratio(light.sliderTrack, Color.White) >= 3f)
    }
}
