package io.github.kuscher.bentobar.bar

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlinx.coroutines.flow.MutableStateFlow

/** The status bar where the clock is: its text colour, and its own colour when it's opaque. */
data class BarColors(val text: Color?, val background: Color?)

/**
 * The strip's resolved colours. [background] is the sampled bar colour when [opaque], otherwise
 * the black or white the text was checked against (a transparent bar shows the wallpaper).
 */
@Immutable
data class LiveLook(val fg: Color, val background: Color, val barDark: Boolean, val opaque: Boolean)

/** The running strip's colours, for the settings preview; null while BentoBar isn't running. */
object BarLook {
    val current = MutableStateFlow<LiveLook?>(null)
}

/** WCAG contrast, which Android's accessibility guidance uses: 4.5:1 for text, 3:1 for icons. */
object Contrast {
    val DARK_TEXT = Color(0xFF1F1F1F)
    const val MIN = 4.5f

    fun ratio(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
    }

    /** White or near-black, whichever reads better on [bg]. */
    fun readableOn(bg: Color) = if (ratio(Color.White, bg) >= ratio(DARK_TEXT, bg)) Color.White else DARK_TEXT

    /** [candidate] if it reads on [bg] at [min], else [fallback]. */
    fun orElse(candidate: Color, bg: Color, fallback: Color, min: Float = MIN) =
        if (ratio(candidate, bg) >= min) candidate else fallback

    /**
     * The strip's colours from the sampled [text] and, on an opaque bar, its [bg]. Dark or light
     * comes from the background when there is one, else from the text. Text that wouldn't reach
     * 4.5:1 is replaced by white or near-black.
     */
    fun resolve(text: Color, bg: Color?): LiveLook {
        val dark = if (bg != null) ratio(Color.White, bg) >= ratio(DARK_TEXT, bg)
        else ratio(text, Color.Black) >= ratio(text, Color.White)
        val back = bg ?: if (dark) Color.Black else Color.White
        return LiveLook(orElse(text, back, readableOn(back)), back, dark, bg != null)
    }
}
