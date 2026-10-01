package io.github.kuscher.bentobar.ui

import android.graphics.Typeface
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Typeface as ComposeTypeface

/** Material 3 with the device's dynamic colours and Google Sans where the device has it. */
@Composable
fun BentoBarTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    val typography = remember { typography() }
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}

private fun family(vararg names: String): FontFamily? {
    for (n in names) {
        val t = Typeface.create(n, Typeface.NORMAL)
        if (t != Typeface.DEFAULT) return FontFamily(ComposeTypeface(t))
    }
    return null
}

private fun typography(): Typography {
    val base = Typography()
    // Google Sans Flex is the Googlebook's UI font; older builds have google-sans-text.
    val text = family("google-sans-flex") ?: family("google-sans-text") ?: family("google-sans") ?: return base
    val display = family("google-sans-flex") ?: family("google-sans") ?: text
    // Google Sans is spaced for 0 tracking; Material's defaults (0.25 to 0.5 sp) are tuned for Roboto
    // and made body text look loosely set.
    fun TextStyle.t() = copy(fontFamily = text, letterSpacing = 0.sp)
    fun TextStyle.d() = copy(fontFamily = display, letterSpacing = 0.sp)
    return base.copy(
        displayLarge = base.displayLarge.d(), displayMedium = base.displayMedium.d(), displaySmall = base.displaySmall.d(),
        headlineLarge = base.headlineLarge.d(), headlineMedium = base.headlineMedium.d(), headlineSmall = base.headlineSmall.d(),
        titleLarge = base.titleLarge.d(), titleMedium = base.titleMedium.t(), titleSmall = base.titleSmall.t(),
        bodyLarge = base.bodyLarge.t(), bodyMedium = base.bodyMedium.t(), bodySmall = base.bodySmall.t(),
        labelLarge = base.labelLarge.t(), labelMedium = base.labelMedium.t(), labelSmall = base.labelSmall.t(),
    )
}
