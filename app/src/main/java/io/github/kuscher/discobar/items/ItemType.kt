package io.github.kuscher.discobar.items

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import io.github.kuscher.discobar.data.ItemConfig

/** How an item's text is coloured in the bar. */
enum class Tone { NORMAL, ACCENT, WARN, ALERT }

/** What one item shows right now. Immutable (never mutate [image]), so Compose can skip unchanged items. */
@androidx.compose.runtime.Immutable
data class ItemState(
    val icon: String? = null,
    /** Filled glyphs match the system's status bar icons; outlined reads as "off". */
    val filled: Boolean = true,
    val text: String? = null,
    /** Spoken description and tooltip. */
    val desc: String = "",
    /** "Has something to say": hidden items with whenActive pop into the bar while this is true. */
    val active: Boolean = false,
    val tone: Tone = Tone.NORMAL,
    /** An app icon instead of a symbol. */
    val image: Bitmap? = null,
    /** Spacers: a fixed gap in dp (text and icon unused). */
    val gapDp: Int = 0,
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
    /** Opens DiscoBar's settings on this item. */
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
    val title: String,
    val icon: String,
    /** One line for the catalog. */
    val blurb: String,
) {
    /** How often [state] is recomputed while the bar shows. */
    open val refreshMs: Long = 1000

    /** Width of the drop-down menu. */
    open val menuWidthDp: Int = 300

    /** Whether "show when active" means something for this type. */
    open val canBeActive: Boolean = false

    /** Runtime permissions the type needs to show its data (asked from settings). */
    open val permissions: List<String> = emptyList()

    abstract fun state(item: ItemConfig): ItemState

    /** A primary click the item handles itself (true), instead of opening its menu. */
    open fun onClick(item: ItemConfig): Boolean = false

    /** Mouse wheel over the item; [steps] > 0 is up/away. */
    open fun onScroll(item: ItemConfig, steps: Int) {}

    /** The drop-down menu. Null means the type has none (clicks go to [onClick]). */
    open val menu: (@Composable (item: ItemConfig, host: MenuHost) -> Unit)? = null

    /** Type-specific settings, shown in DiscoBar's settings. */
    open val options: (@Composable (item: ItemConfig, set: (ItemConfig) -> Unit) -> Unit)? = null

    open fun defaultOptions(): Map<String, String> = emptyMap()
}
