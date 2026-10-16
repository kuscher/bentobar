package io.github.kuscher.bentobar.items

import io.github.kuscher.bentobar.data.ItemConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardRulesTest {
    @Test fun pinsAreCleanedDedupedAndMoved() {
        var item = ItemConfig("c1", "clipboard")
        item = ClipboardRules.addPin(item, "  First line  ")
        item = ClipboardRules.addPin(item, "Second line")
        item = ClipboardRules.addPin(item, "First line")
        item = ClipboardRules.addPin(item, "   ")
        item = ClipboardRules.addPin(item, "Third line")
        assertEquals(listOf("First line", "Second line", "Third line"), ClipboardRules.pins(item))

        item = ClipboardRules.movePin(item, "Third line", -1)
        assertEquals(listOf("First line", "Third line", "Second line"), ClipboardRules.pins(item))

        item = ClipboardRules.removePin(item, "Third line")
        assertEquals(listOf("First line", "Second line"), ClipboardRules.pins(item))
    }

    @Test fun previewCollapsesWhitespaceAndTruncates() {
        assertEquals("hello world", ClipboardRules.preview("  hello   \n\t  world  ", 40))
        assertEquals("12345…", ClipboardRules.preview("123456789", 6))
        assertNull(ClipboardRules.clean("   \n  "))
    }

    @Test fun recordRecentMovesEntryToFrontAndSkipsPinnedInUnpinnedView() {
        var recent = emptyList<String>()
        recent = ClipboardRules.recordRecent(recent, "first")
        recent = ClipboardRules.recordRecent(recent, "second")
        assertEquals(listOf("second", "first"), recent)

        recent = ClipboardRules.recordRecent(recent, "first")
        assertEquals(listOf("first", "second"), recent)

        assertEquals(recent, ClipboardRules.recordRecent(recent, "   "))
        assertEquals(listOf("second"), ClipboardRules.unpinnedRecent(recent, listOf("first")))
    }

    @Test fun barFollowsBarModeAndCountsUnpinnedPlusPinned() {
        val previewItem = ItemConfig("c1", "clipboard")
        val countItem = ItemConfig("c2", "clipboard", options = mapOf("bar" to "count"))

        val emptyBar = ClipboardRules.bar(previewItem, emptyList(), emptyList(), "Empty") { "$it snippets" }
        assertNull(emptyBar.text)
        assertFalse(emptyBar.active)

        val previewBar = ClipboardRules.bar(previewItem, listOf("pinned"), listOf("latest clip"), "Empty") { "$it snippets" }
        assertEquals("latest clip", previewBar.text)
        assertTrue(previewBar.active)

        val countBar = ClipboardRules.bar(countItem, listOf("same", "other"), listOf("same"), "Empty") { "$it snippets" }
        assertEquals("2", countBar.text)
        assertEquals("2 snippets", countBar.desc)
    }
}
