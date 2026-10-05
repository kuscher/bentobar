package io.github.kuscher.bentobar.bar

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
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

    @Test fun aClickNeverSetsNothingButADragCan() {
        // The level under a click is one step at the least; what a drag reports is the level as it is.
        assertEquals(1 / 15f, SliderMath.atLeastOneStep(at(10f, steps = 15), 15), 1e-6f)
        assertEquals(1 / 15f, SliderMath.atLeastOneStep(0f, 15), 1e-6f)
        assertEquals(8 / 15f, SliderMath.atLeastOneStep(at(42f, steps = 15), 15), 1e-6f)
        assertEquals(1f, SliderMath.atLeastOneStep(1f, 15), 0f)
        assertEquals(0f, at(10f, steps = 15), 0f)
        // Without steps the least is one dp of the 64 dp track.
        assertEquals(1 / 64f, SliderMath.atLeastOneStep(0f, 0), 1e-6f)
        assertEquals(0.5f, SliderMath.atLeastOneStep(0.5f, 0), 0f)
    }

    @Test fun theTrackZoneRunsToTheItemsEndAndThatMeansFull() {
        // The zone is the 64 px track and 6 px after it: a press in those last 6 is the highest step.
        assertEquals(1f, SliderMath.level(66f, left = 0f, width = 64f, steps = 15), 0f)
        assertEquals(1f, SliderMath.level(70f, left = 0f, width = 64f, steps = 15), 0f)
        // Right to left the zone starts with those 6 px, and the track's own start is its right end.
        assertEquals(1f, SliderMath.level(2f, left = 6f, width = 64f, steps = 15, rtl = true), 0f)
        assertEquals(0f, SliderMath.level(70f, left = 6f, width = 64f, steps = 15, rtl = true), 0f)
    }

    @Test fun anyLevelAboveNothingShowsAtLeastADot() {
        assertEquals(0f, SliderMath.fillWidth(0f, 64f, min = 4f), 0f)
        assertEquals(4f, SliderMath.fillWidth(1 / 100f, 64f, min = 4f), 0f)
        assertEquals(4.2666f, SliderMath.fillWidth(1 / 15f, 64f, min = 4f), 1e-3f)
        assertEquals(39.68f, SliderMath.fillWidth(0.62f, 64f, min = 4f), 1e-4f)
        assertEquals(64f, SliderMath.fillWidth(1.5f, 64f, min = 4f), 0f)
        assertEquals(0f, SliderMath.fillWidth(Float.NaN, 64f, min = 4f), 0f)
        assertEquals(0f, SliderMath.fillWidth(0.5f, 0f, min = 4f), 0f)
    }

    @Test fun theHandleStandsOnTheLevelButNeverHangsOverAnEnd() {
        assertEquals(2f, SliderMath.handleCenter(0f, 64f, edge = 2f), 0f)
        assertEquals(32f, SliderMath.handleCenter(0.5f, 64f, edge = 2f), 0f)
        assertEquals(62f, SliderMath.handleCenter(1f, 64f, edge = 2f), 0f)
        assertEquals(1.5f, SliderMath.handleCenter(0.9f, 3f, edge = 2f), 0f) // a track too short for it: the middle
    }

    @Test fun theTrackIsDrawnAsTheDesignSaysOnBlackAndOnWhite() {
        val dark = StripLook(Color.White, true, TextSize.DEFAULT, 12.dp, Pill.NONE)
        assertEquals(0.38f, dark.sliderTrackAlpha, 0f)
        assertEquals(3.4f, Contrast.ratio(dark.sliderTrack.compositeOver(Color.Black), Color.Black), 0.1f)
        val light = StripLook(Contrast.DARK_TEXT, false, TextSize.DEFAULT, 12.dp, Pill.NONE)
        assertEquals(0.50f, light.sliderTrackAlpha, 0f)
        assertEquals(3.2f, Contrast.ratio(light.sliderTrack.compositeOver(Color.White), Color.White), 0.1f)
        // Muted, the kept level is between the two: told from the track and from a full fill.
        assertEquals(0.62f, dark.sliderMuted.alpha, 0.01f)
        assertEquals(0.74f, light.sliderMuted.alpha, 0.01f)
    }

    @Test fun trackAndFillStandOutFromEveryBar() {
        // The 3:1 the parts of a control need, on every gray an opaque bar can be and on a grid of colors: where the
        // usual strength is too faint on a bar of another color, the track is drawn stronger, up to 0.7 of the text
        // color. Past that it would no longer be told from the fill, so the few mid-tone bars where the text itself
        // only just reaches 4.5:1 get a track a little under 3:1, and never under 2.75:1.
        val bars = (0..255 step 3).map { Color(it, it, it) } +
            (0..255 step 51).flatMap { r -> (0..255 step 51).flatMap { g -> (0..255 step 51).map { b -> Color(r, g, b) } } }
        var short = 0
        for (bar in bars) {
            val live = Contrast.resolve(Contrast.readableOn(bar), bar)
            val look = StripLook(live.fg, live.barDark, TextSize.DEFAULT, 12.dp, Pill.NONE, live.background)
            val alpha = look.sliderTrackAlpha
            val track = Contrast.ratio(look.sliderTrack.compositeOver(bar), bar)
            assertTrue("the track's strength on $bar", alpha >= (if (look.lightText) 0.38f else 0.50f) && alpha <= 0.7f + 1e-6f)
            if (alpha < 0.7f - 1e-6f) assertTrue("track on $bar", track >= 3f)
            assertTrue("track at its strongest on $bar: $track", track >= 2.75f)
            if (track < 3f) short++
            assertTrue("fill on $bar", Contrast.ratio(look.fg, bar) >= 4.5f)
            assertTrue("muted fill on $bar", Contrast.ratio(look.sliderMuted.compositeOver(bar), bar) >= 3f)
            assertTrue("muted fill and track differ on $bar", look.sliderMuted.alpha >= alpha + 0.2f)
        }
        // Four of the 302 today (a mid gray, a green, a magenta, a pink). More would mean the rule changed.
        assertTrue("$short bars with a track under 3:1", short <= 4)
    }
}
