package io.github.kuscher.bentobar.items

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The Sound item's slider without a device: how full it is, which volume a level is, and what it shows while muted. */
class SoundRulesTest {
    @Test fun theFillIsTheVolumeOverItsRange() {
        assertEquals(0f, SoundRules.level(0, 0, 15), 0f)
        assertEquals(0.6f, SoundRules.level(9, 0, 15), 1e-6f)
        assertEquals(1f, SoundRules.level(15, 0, 15), 0f)
        assertEquals(15, SoundRules.steps(0, 15))
        // A stream whose lowest volume isn't 0 is empty at that lowest volume.
        assertEquals(0f, SoundRules.level(1, 1, 7), 0f)
        assertEquals(0.5f, SoundRules.level(4, 1, 7), 1e-6f)
        assertEquals(6, SoundRules.steps(1, 7))
        // What Android should never say is still drawn within the track.
        assertEquals(1f, SoundRules.level(99, 0, 15), 0f)
        assertEquals(0f, SoundRules.level(-1, 0, 15), 0f)
        assertEquals(0f, SoundRules.level(3, 5, 5), 0f)
        assertEquals(0, SoundRules.steps(5, 5))
    }

    @Test fun everyVolumeComesBackFromItsLevel() {
        for (min in 0..2) for (max in min + 1..40) for (volume in min..max)
            assertEquals("volume $volume of $min to $max", volume, SoundRules.volume(SoundRules.level(volume, min, max), min, max))
    }

    @Test fun aLevelIsTheNearestVolume() {
        assertEquals(0, SoundRules.volume(0f, 0, 15))
        assertEquals(15, SoundRules.volume(1f, 0, 15))
        assertEquals(8, SoundRules.volume(0.5f, 0, 15)) // a click at the middle sets about half
        assertEquals(9, SoundRules.volume(0.62f, 0, 15))
        assertEquals(1, SoundRules.volume(1f / 15, 0, 15)) // the least a click sets
        assertEquals(1, SoundRules.volume(0f, 1, 7))
        assertEquals(7, SoundRules.volume(1f, 1, 7))
        assertEquals(15, SoundRules.volume(7f, 0, 15))
        assertEquals(0, SoundRules.volume(-1f, 0, 15))
        assertEquals(0, SoundRules.volume(Float.NaN, 0, 15))
    }

    @Test fun theSliderMatchesTheVolumeWithinOneStep() {
        for (volume in 0..15) {
            val slider = SoundRules.slider(on = true, fixed = false, volume = volume, min = 0, max = 15, muted = false, kept = 0f)!!
            assertEquals(volume / 15f, slider.level, 1e-6f)
            assertEquals(15, slider.steps)
            assertEquals(false, slider.dimmed)
        }
    }

    @Test fun switchedOffTheItemHasNoSlider() {
        assertNull(SoundRules.slider(on = false, fixed = false, volume = 9, min = 0, max = 15, muted = false, kept = 0.6f))
    }

    @Test fun anOutputWithAFixedVolumeDrawsNoSlider() {
        assertNull(SoundRules.slider(on = true, fixed = true, volume = 15, min = 0, max = 15, muted = false, kept = 1f))
        // Nor does a stream with a single volume: there is nothing to slide.
        assertNull(SoundRules.slider(on = true, fixed = false, volume = 5, min = 5, max = 5, muted = false, kept = 1f))
    }

    @Test fun whileMutedTheLastLevelSeenBeforeIsKept() {
        // Android reports 0 for a muted stream: what the slider draws then is the level it saw last.
        var kept = 0f
        kept = SoundRules.kept(kept, volume = 9, min = 0, max = 15, muted = false)
        assertEquals(0.6f, kept, 1e-6f)
        kept = SoundRules.kept(kept, volume = 0, min = 0, max = 15, muted = true)
        assertEquals(0.6f, kept, 1e-6f)
        val slider = SoundRules.slider(on = true, fixed = false, volume = 0, min = 0, max = 15, muted = true, kept = kept)!!
        assertEquals(0.6f, slider.level, 1e-6f)
        assertEquals(true, slider.dimmed)
        assertEquals(15, slider.steps)
        // Unmuted at another level: that one is kept from then on.
        kept = SoundRules.kept(kept, volume = 12, min = 0, max = 15, muted = false)
        assertEquals(0.8f, kept, 1e-6f)
    }

    @Test fun mutedBeforeAnyLevelWasSeenNothingIsFilled() {
        val slider = SoundRules.slider(on = true, fixed = false, volume = 0, min = 0, max = 15, muted = true, kept = SoundRules.kept(0f, 0, 0, 15, muted = true))!!
        assertEquals(0f, slider.level, 0f)
        assertEquals(true, slider.dimmed)
    }

    @Test fun aVolumeLoweredToNothingWithoutAMuteKeepsNothing() {
        // Where Android says "volume 0, not muted", there is no level underneath to come back to.
        val kept = SoundRules.kept(0.6f, volume = 0, min = 0, max = 15, muted = false)
        assertEquals(0f, kept, 0f)
        val slider = SoundRules.slider(on = true, fixed = false, volume = 0, min = 0, max = 15, muted = false, kept = 0.6f)!!
        assertEquals(0f, slider.level, 0f)
        assertEquals(false, slider.dimmed)
    }

    @Test fun aKeptLevelStaysWithinTheTrack() {
        assertEquals(1f, SoundRules.slider(on = true, fixed = false, volume = 0, min = 0, max = 15, muted = true, kept = 3f)!!.level, 0f)
        assertEquals(0f, SoundRules.slider(on = true, fixed = false, volume = 0, min = 0, max = 15, muted = true, kept = Float.NaN)!!.level, 0f)
    }

    @Test fun thePercentageIsTheOneTheItemAlwaysShowed() {
        assertEquals(60, SoundRules.percent(9, 15))
        assertEquals(100, SoundRules.percent(15, 15))
        assertEquals(0, SoundRules.percent(0, 15))
        assertEquals(62, SoundRules.percent(93, 150))
        assertEquals(6, SoundRules.percent(1, 15))
    }

    @Test fun theWordsAreTheCopyDecks() {
        assertEquals("Slider in the bar", appText("sound_slider"))
        assertEquals("Click or drag it to set the volume.", appText("sound_slider_help"))
        assertEquals("This output's volume is fixed, so the bar shows the number.", appText("sound_slider_fixed"))
        // As a resource the percent sign is doubled; filled in, the tooltip reads as designed.
        assertEquals("Volume %1\$d%%", appText("sound_tooltip"))
        assertEquals("Volume 62%", String.format(appText("sound_tooltip"), 62))
        assertEquals("Muted", appText("sound_muted"))
    }
}
