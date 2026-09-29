package io.github.kuscher.bentobar.util

import android.content.Context
import android.graphics.Typeface
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
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

    @Synchronized
    fun init(context: Context) {
        if (::symbols.isInitialized) return
        val am = context.applicationContext.assets
        symbols = FontFamily(ComposeTypeface(Typeface.createFromAsset(am, "fonts/MaterialSymbolsRounded.ttf")))
        symbolsFilled = FontFamily(ComposeTypeface(Typeface.createFromAsset(am, "fonts/MaterialSymbolsRounded_Fill.ttf")))
        barTypeface = systemFamily("google-sans-text-medium") ?: systemFamily("google-sans-medium")
            ?: Typeface.create("sans-serif-medium", Typeface.NORMAL)
        bar = FontFamily(ComposeTypeface(barTypeface))
    }

    /** A named system family, or null when the device doesn't have it (Typeface.create falls back silently). */
    private fun systemFamily(name: String): Typeface? {
        val t = Typeface.create(name, Typeface.NORMAL)
        return if (t == Typeface.DEFAULT || t == Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)) null else t
    }
}

/** One Material Symbol. [size] is the glyph's em size; symbols fill their em box. */
@Composable
fun SymIcon(
    sym: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 20.sp,
    filled: Boolean = false,
    color: Color = LocalContentColor.current,
) {
    Text(
        text = sym,
        modifier = modifier,
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
