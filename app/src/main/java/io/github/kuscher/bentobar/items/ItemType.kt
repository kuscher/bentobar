package io.github.kuscher.bentobar.items

import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import io.github.kuscher.bentobar.data.ItemConfig

/** How an item's text is coloured in the bar. */
enum class Tone { NORMAL, ACCENT, WARN, ALERT }

/** What one item shows right now. Immutable (never mutate [image]), so Compose can skip unchanged items. */
@androidx.compose.runtime.Immutable
data class ItemState(
    val icon: String? = null,
    /** Filled glyphs match the system's status bar icons; outlined reads as "off". */
    val filled: Boolean = true,
    val text: String? = null,
    /** In a menu's list of items, instead of [text] when that wouldn't fit: a meeting's title without its countdown. */
    val label: String? = null,
    /** Spoken description and tooltip. */
    val desc: String = "",
    /** "Has something to say": hidden items with whenActive pop into the bar while this is true. */
    val active: Boolean = false,
    val tone: Tone = Tone.NORMAL,
    /** An app icon instead of a symbol. */
    val image: Bitmap? = null,
    /** Spacers: a fixed gap in dp (text and icon unused). */
    val gapDp: Int = 0,
    /** Calendar: today's day number, drawn as a small date badge instead of [icon] in the bar. */
    val dayNumber: Int? = null,
    /** Spacers: draw a thin divider line. */
    val divider: Boolean = false,
    /**
     * For text that ticks (speeds, timers): the bar keeps the widest width seen for a while, so
     * neighbours don't jump every second. A different key (e.g. a timer that says "Done") starts over.
     * Null: always the natural width.
     */
    val widthKey: String? = null,
)

/** Things a menu can do besides its own content. */
interface MenuHost {
    fun close()
    /** Opens BentoBar's settings on this item. */
    fun openItemSettings(id: String)
    /** Runs [action] after the menu has gone (e.g. before a screenshot). */
    fun afterClose(action: () -> Unit)
}

/**
 * One kind of bar item (network speed, timer, …). Types are stateless objects; per-instance
 * settings live in [ItemConfig.options], shared live data in [Env].
 */
abstract class ItemType(
    val type: String,
    @StringRes val titleRes: Int,
    val icon: String,
    /** One line for the catalog. */
    @StringRes val blurbRes: Int,
) {
    /** The name in the app's language, read when asked (never cached, so it follows a locale change). */
    val title: String get() = Env.app.getString(titleRes)
    val blurb: String get() = Env.app.getString(blurbRes)

    /** How often [state] is recomputed while the bar shows. */
    open val refreshMs: Long = 1000

    /** Width of the drop-down menu. */
    open val menuWidthDp: Int = 300

    /** Whether "show when active" means something for this type. */
    open val canBeActive: Boolean = false
    /** Only ever an icon in the bar (no text to show), so settings don't offer "Show as". */
    open val iconOnly: Boolean = false

    /** The "Show when…" rule in words, for types that [canBeActive]. */
    open val trigger: Trigger? = null

    /** Runtime permissions the type needs to show its data (asked from settings). */
    open val permissions: List<String> = emptyList()

    abstract fun state(item: ItemConfig): ItemState

    /** A primary click the item handles itself (true), instead of opening its menu. */
    open fun onClick(item: ItemConfig): Boolean = false

    /** Mouse wheel over the item; [steps] > 0 is up/away. */
    open fun onScroll(item: ItemConfig, steps: Int) {}
    /** Uses the mouse wheel itself ([onScroll]); over other items the wheel reveals or folds hidden items. */
    open val usesWheel: Boolean get() = false

    /** The drop-down menu. Null means the type has none (clicks go to [onClick]). */
    open val menu: (@Composable (item: ItemConfig, host: MenuHost) -> Unit)? = null

    /** Type-specific settings, shown in BentoBar's settings. */
    open val options: (@Composable (item: ItemConfig, set: (ItemConfig) -> Unit) -> Unit)? = null

    open fun defaultOptions(): Map<String, String> = emptyMap()
}

/**
 * When a hidden item with "show when active" pops into the bar, in words. [sentence] is the long
 * form for the item's settings ("Show when CPU load is above %1$s"), [short] the row detail
 * ("shows above %1$s CPU"). Both take the [threshold]'s value, formatted, as %1$s; rules without a
 * threshold take no argument.
 */
class Trigger(@StringRes val sentence: Int, @StringRes val short: Int, val threshold: Threshold? = null) {
    fun sentence(item: ItemConfig, value: Int? = null): String = text(sentence, item, value)
    fun short(item: ItemConfig): String = text(short, item, null)
    private fun text(@StringRes id: Int, item: ItemConfig, value: Int?): String =
        threshold?.let { Env.str(id, it.format(value ?: it.shown(item))) } ?: Env.str(id)
}

/**
 * A number the user picks for a [Trigger], stored as [key] in [ItemConfig.options]. [default] applies
 * while the key is missing (configs saved before it existed), so it must match the old behavior.
 */
class Threshold(val key: String, val default: Int, val range: IntRange, val step: Int = 1, val format: (Int) -> String) {
    /** The stored value, as is: an out-of-range value from an older config keeps working. */
    fun of(item: ItemConfig): Int = item.optInt(key, default)
    /** For the slider and the words: the stored value, kept in range. */
    fun shown(item: ItemConfig): Int = of(item).coerceIn(range)
    fun snap(v: Float): Int = (range.first + Math.round((v - range.first) / step) * step).coerceIn(range)
}
