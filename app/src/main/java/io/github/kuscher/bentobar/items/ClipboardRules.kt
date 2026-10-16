package io.github.kuscher.bentobar.items

import io.github.kuscher.bentobar.data.ItemConfig
import io.github.kuscher.bentobar.util.Sym
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Pure rules for the Clipboard and scratchpad item: pinned snippets in [ItemConfig.options],
 * session-only recent clips in memory, and what the bar shows. No Android imports.
 */
object ClipboardRules {
    const val MOST_PINS = 8
    const val MOST_RECENT = 8
    const val BAR_CHARS = 20
    const val MENU_CHARS = 80
    const val MAX_CHARS = 500

    class Bar(
        val icon: String,
        val filled: Boolean,
        val text: String?,
        val active: Boolean,
        val desc: String,
        val tooltip: String?,
    )

    /** Normalizes a snippet for storage or recent history; null when blank. */
    fun clean(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return TextRules.first(trimmed, MAX_CHARS)
    }

    /** A single-line preview of [snippet] for the bar or a menu row. */
    fun preview(snippet: String, maxChars: Int = BAR_CHARS): String {
        val line = TextRules.oneLine(snippet, maxChars + 1)
        return if (TextRules.fits(line, maxChars)) line else TextRules.first(line, maxChars - 1).trimEnd() + "…"
    }

    /** Pinned snippets stored on [item], in order, without blanks or duplicates. */
    fun pins(item: ItemConfig): List<String> = try {
        val raw = item.options["pins"].orEmpty()
        if (raw.isEmpty()) emptyList()
        else (Json.parseToJsonElement(raw) as? JsonArray).orEmpty().asSequence().take(64)
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.let(::clean) }
            .distinct()
            .take(MOST_PINS)
            .toList()
    } catch (e: Exception) {
        emptyList()
    }

    fun withPins(item: ItemConfig, pins: List<String>): ItemConfig {
        val cleaned = pins.mapNotNull(::clean).distinct().take(MOST_PINS)
        val encoded = if (cleaned.isEmpty()) null else buildJsonArray { cleaned.forEach { add(JsonPrimitive(it)) } }.toString()
        return item.with("pins", encoded)
    }

    fun addPin(item: ItemConfig, text: String): ItemConfig {
        val c = clean(text) ?: return item
        val current = pins(item)
        if (c in current || current.size >= MOST_PINS) return item
        return withPins(item, current + c)
    }

    fun removePin(item: ItemConfig, text: String): ItemConfig = withPins(item, pins(item).filterNot { it == text })

    fun movePin(item: ItemConfig, text: String, delta: Int): ItemConfig {
        val list = pins(item).toMutableList()
        val i = list.indexOf(text)
        val target = i + delta
        if (i < 0 || target !in list.indices) return item
        list[i] = list[target].also { list[target] = list[i] }
        return withPins(item, list)
    }

    /** Adds [clip] to the front of [recent], deduplicating and keeping at most [MOST_RECENT] entries. */
    fun recordRecent(recent: List<String>, clip: String?): List<String> {
        val c = clip?.let(::clean) ?: return recent
        return (listOf(c) + recent.filterNot { it == c }).take(MOST_RECENT)
    }

    /** Recent clips that aren't already pinned on [item]. */
    fun unpinnedRecent(recent: List<String>, pins: List<String>): List<String> {
        val pinnedSet = pins.toHashSet()
        return recent.filterNot { it in pinnedSet }.take(MOST_RECENT)
    }

    /** What the bar shows for [pins] and [recent]. Spoken description never quotes the snippet text. */
    fun bar(item: ItemConfig, pins: List<String>, recent: List<String>, emptyDesc: String, countDesc: (Int) -> String): Bar {
        val unpinned = unpinnedRecent(recent, pins)
        val total = pins.size + unpinned.size
        if (total == 0) {
            return Bar(Sym.CONTENT_PASTE, filled = false, text = null, active = false, desc = emptyDesc, tooltip = emptyDesc)
        }
        val showCount = item.opt("bar", "preview") == "count"
        val top = unpinned.firstOrNull() ?: pins.first()
        val text = if (showCount) total.toString() else preview(top, BAR_CHARS)
        val desc = countDesc(total)
        return Bar(Sym.CONTENT_PASTE, filled = true, text = text, active = true, desc = desc, tooltip = desc)
    }
}
