package io.github.kuscher.bentobar.items

import io.github.kuscher.bentobar.bar.SliderMath

/**
 * The Sound item's slider in the bar, without Android (`SoundRulesTest`): how full it is for a
 * volume, which volume a level it was dragged to stands for, and what it draws while the sound is
 * muted. A volume is one of the steps Android has for media, from [min] (0 on the devices seen so
 * far) to [max]; a level is 0 to 1, as the strip draws and reports it.
 */
object SoundRules {
    /** How many steps the slider has. */
    fun steps(min: Int, max: Int): Int = (max - min).coerceAtLeast(0)

    /** How full the slider is at [volume]: its place between the lowest volume and the highest. */
    fun level(volume: Int, min: Int, max: Int): Float = if (max <= min) 0f else ((volume - min).toFloat() / (max - min)).coerceIn(0f, 1f)

    /** The volume a level stands for, the nearest there is: what is handed to Android. */
    fun volume(level: Float, min: Int, max: Int): Int = min + SliderMath.step(level, steps(min, max))

    /** The percentage the item says, in its text and its spoken description: the same number with and without the slider. */
    fun percent(volume: Int, max: Int): Int = volume * 100 / max.coerceAtLeast(1)

    /**
     * The level to draw while muted. Android reports a volume of 0 for a muted stream, whatever it is
     * underneath, so the item keeps the last level it saw while the stream was not muted: [before]
     * stays while [muted], and follows the volume otherwise (down to 0, for a volume that was lowered
     * to nothing rather than muted).
     */
    fun kept(before: Float, volume: Int, min: Int, max: Int, muted: Boolean): Float = if (muted) before else level(volume, min, max)

    /**
     * What the bar draws in the number's place, or null for the number itself: with the option [on],
     * unless the output's volume is [fixed] (nothing could be set) or there is only one volume. With
     * the stream [muted], it is the [kept] level, dimmed.
     */
    fun slider(on: Boolean, fixed: Boolean, volume: Int, min: Int, max: Int, muted: Boolean, kept: Float): BarSlider? =
        if (!on || fixed || max <= min) null
        else BarSlider(level = if (muted) (if (kept.isNaN()) 0f else kept.coerceIn(0f, 1f)) else level(volume, min, max), steps = steps(min, max), dimmed = muted)
}
