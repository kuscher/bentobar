package io.github.kuscher.bentobar.data

import kotlinx.serialization.Serializable

/** Where an item lives, like Bartender's sections. */
@Serializable
enum class Section { SHOWN, HIDDEN, OFF }

/** How an item draws in the bar. */
@Serializable
enum class Display { ICON_AND_TEXT, TEXT, ICON }

/** Where BentoBar's strip sits in the status bar's free space. */
@Serializable
enum class Position { RIGHT, CENTER, LEFT }

@Serializable
enum class ColorMode { AUTO, LIGHT, DARK }

@Serializable
enum class TextSize { SMALL, DEFAULT, LARGE }

@Serializable
enum class Pill { NONE, SUBTLE, SOLID }

/** When the running timer or next meeting also shows as an Android Live Update chip. */
@Serializable
enum class ChipMode { OFF, FALLBACK, ALWAYS }

/** One item the user added. [id] is unique per instance; [type] picks the [ItemType]. */
@Serializable
@androidx.compose.runtime.Immutable
data class ItemConfig(
    val id: String,
    val type: String,
    val section: Section = Section.SHOWN,
    /** Hidden items with this on pop into the bar while they have something to say. */
    val whenActive: Boolean = false,
    val display: Display = Display.ICON_AND_TEXT,
    val options: Map<String, String> = emptyMap(),
) {
    fun opt(key: String, default: String) = options[key] ?: default
    fun optInt(key: String, default: Int) = options[key]?.toIntOrNull() ?: default
    fun optBool(key: String, default: Boolean) = options[key]?.toBooleanStrictOrNull() ?: default
    fun with(key: String, value: String?) =
        copy(options = if (value == null) options - key else options + (key to value))
}

@Serializable
data class BarConfig(
    val version: Int = 2,
    /** Master switch (the Quick Settings tile flips it, e.g. for presenting). */
    val enabled: Boolean = true,
    val items: List<ItemConfig> = emptyList(),
    val position: Position = Position.RIGHT,
    /** Show the ‹ button that reveals hidden items. */
    val chevron: Boolean = true,
    /** Reveal hidden items while the pointer rests on BentoBar's strip (off by default: it can surprise). */
    val revealOnHover: Boolean = false,
    /** Hide revealed items again after this many seconds; 0 (default) keeps them until ‹ is clicked. */
    val autoCollapseSec: Int = 0,
    /** Hidden items pinned open with ‹, kept across restarts (an update, a reboot). */
    val pinnedOpen: Boolean = false,
    /** Presenting: only a running timer or a meeting about to start, plus ‹ for the menu. */
    val presenting: Boolean = false,
    /** Permissions the user switched off in Setup ([Uses] keys): BentoBar doesn't use them even if granted. */
    val turnedOff: Set<String> = emptySet(),
    val textSize: TextSize = TextSize.DEFAULT,
    val pill: Pill = Pill.NONE,
    val color: ColorMode = ColorMode.AUTO,
    /** Space between items in dp (12 matches the system icons' rhythm on the desktop bar). */
    val spacing: Int = 12,
    /**
     * Mirror the running timer or next meeting as an Android Live Update chip: FALLBACK only
     * while BentoBar's own bar isn't showing (accessibility off, or hidden with the tile).
     */
    val chipMode: ChipMode = ChipMode.FALLBACK,
    val onboarded: Boolean = false,
)

/**
 * What BentoBar uses, as switched in Setup: a permission counts only if Android granted it and the
 * user hasn't switched it off here.
 */
object Uses {
    const val NOTIFICATIONS = "notifications"
    const val CALENDAR = "calendar"
    const val EXACT_ALARMS = "exactAlarms"

    fun on(key: String) = key !in Store.config.value.turnedOff
}
