package io.github.kuscher.bentobar.util

import android.content.Context
import android.graphics.Typeface
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Typeface as ComposeTypeface
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** Fonts: the Material Symbols subset from assets, and the device's own UI families when present. */
object Fonts {
    lateinit var symbols: FontFamily private set
    lateinit var symbolsFilled: FontFamily private set
    /** The status bar's look: Google Sans on Google devices, the platform sans-serif elsewhere. */
    lateinit var bar: FontFamily private set
    lateinit var barTypeface: Typeface private set
    /**
     * The weight of the status bar's own text. A text style has to name it: Compose asks the typeface
     * for the style's weight, and for a style without one that is 400, whatever weight the typeface
     * was made with. (The strip drew regular text next to the system's semibold clock that way.)
     */
    var barWeight: FontWeight = FontWeight.Medium; private set

    @Synchronized
    fun init(context: Context) {
        if (::symbols.isInitialized) return
        val am = context.applicationContext.assets
        symbols = FontFamily(ComposeTypeface(Typeface.createFromAsset(am, "fonts/MaterialSymbolsRounded.ttf")))
        symbolsFilled = FontFamily(ComposeTypeface(Typeface.createFromAsset(am, "fonts/MaterialSymbolsRounded_Fill.ttf")))
        // Googlebooks name their UI font "google-sans-flex" (variable). The status bar draws its clock in
        // the emphasized label style: weight 600 with rounded ends, which has a family name of its own.
        // Older builds had "google-sans-text-medium". Without any of them, BentoBar fell back to the
        // generic sans-serif, which looked technical next to the system's clock.
        val flex = systemFamily("variable-label-large-emphasized") ?: systemFamily("google-sans-flex")
        if (flex != null) barWeight = FontWeight.SemiBold
        barTypeface = flex?.let { Typeface.create(it, barWeight.weight, false) }
            ?: systemFamily("google-sans-text-medium") ?: systemFamily("google-sans-medium")
            ?: systemFamily("google-sans")?.let { Typeface.create(it, 500, false) }
            ?: Typeface.create("sans-serif-medium", Typeface.NORMAL)
        bar = FontFamily(ComposeTypeface(barTypeface))
    }

    /** A named system family, or null when the device doesn't have it (Typeface.create falls back silently). */
    private fun systemFamily(name: String): Typeface? {
        val t = Typeface.create(name, Typeface.NORMAL)
        return if (t == Typeface.DEFAULT || t == Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)) null else t
    }
}

/**
 * One Material Symbol. [size] is the glyph's em size; symbols fill their em box. The glyph is a
 * private-use character, so a screen reader would read nonsense: give [contentDescription] when
 * the icon carries meaning on its own (an icon-only button); without it the icon is silent.
 */
@Composable
fun SymIcon(
    sym: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 20.sp,
    filled: Boolean = false,
    color: Color = LocalContentColor.current,
    contentDescription: String? = null,
) {
    Text(
        text = sym,
        modifier = modifier.clearAndSetSemantics { if (contentDescription != null) this.contentDescription = contentDescription },
        color = color,
        style = TextStyle(
            fontFamily = if (filled) Fonts.symbolsFilled else Fonts.symbols,
            fontSize = size,
            lineHeight = size,
            textAlign = TextAlign.Center,
        ),
        maxLines = 1,
    )
}
