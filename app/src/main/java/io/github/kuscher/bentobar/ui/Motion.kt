package io.github.kuscher.bentobar.ui

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import io.github.kuscher.bentobar.data.Store

/**
 * BentoBar's own motion, read in one place: the popups, the strip's highlight, items appearing, a drag. "No
 * animations" (Look) stops all of it, as the system's "Remove animations" does: popups are whole at once and gone at
 * once, the highlight jumps. Tolerances (a grace after the pointer leaves, a guard against a double click) stay.
 */
object Motion {
    /** "No animations" is set. */
    val off: Boolean get() = Store.config.value.noAnimations

    /** The scale every duration is stretched by: 0 (nothing moves) with "No animations" or the system's Remove animations. */
    fun scale(context: Context): Float = if (off) 0f else runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f)

    /** [spec], or straight to the end with "No animations". (The system's own scale Compose applies by itself.) */
    fun <T> spec(spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> = if (off) snap() else spec
}
