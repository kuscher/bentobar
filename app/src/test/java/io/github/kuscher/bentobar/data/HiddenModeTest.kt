package io.github.kuscher.bentobar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HiddenModeTest {
    private val timer = ItemConfig("t", "timer", Section.HIDDEN, whenActive = true)
    private val memory = ItemConfig("m", "memory", Section.HIDDEN)
    private val clock = ItemConfig("c", "clock", Section.SHOWN)
    private fun v2(chevron: Boolean, hover: Boolean = false) =
        BarConfig(version = 2, chevron = chevron, revealOnHover = hover, items = listOf(timer, memory, clock))

    @Test fun oldSwitchesMapToModes() {
        assertEquals(HiddenMode.CLICK, v2(chevron = true).migrateToV3().hiddenMode)
        assertEquals(HiddenMode.HOVER, v2(chevron = true, hover = true).migrateToV3().hiddenMode)
        assertEquals(HiddenMode.SHOW_ALL, v2(chevron = false).migrateToV3().hiddenMode)
    }

    @Test fun chevronOffKeepsTheBarAsItWas() {
        // With ‹ off, a hidden item without a rule never showed: it moves to Off, rule items stay.
        val c = v2(chevron = false).migrateToV3()
        assertEquals(Section.OFF, c.items.first { it.id == "m" }.section)
        assertEquals(Section.HIDDEN, c.items.first { it.id == "t" }.section)
    }

    @Test fun showAllShowsRuleItemsOnlyWhenActive() {
        val c = BarConfig(hiddenMode = HiddenMode.SHOW_ALL, items = listOf(timer, memory, clock))
        assertFalse(c.shows(timer, active = false))
        assertTrue(c.shows(timer, active = true))
        assertTrue(c.shows(memory, active = false))
        assertTrue(c.shows(clock, active = false))
    }

    @Test fun clickModeKeepsHiddenItemsBehindTheChevron() {
        val c = BarConfig(hiddenMode = HiddenMode.CLICK, items = listOf(timer, memory, clock))
        assertFalse(c.shows(memory, active = false))
        assertTrue(c.shows(timer, active = true))
    }

    @Test fun everythingDrawnIsSampled() {
        // The ticker samples what couldShow() allows; an item drawn but not sampled freezes.
        for (mode in HiddenMode.entries) for (section in Section.entries) for (rule in listOf(false, true)) {
            val item = ItemConfig("x", "clock", section, whenActive = rule)
            val c = BarConfig(hiddenMode = mode, items = listOf(item))
            for (active in listOf(false, true)) {
                if (c.shows(item, active)) assertTrue("$mode $section rule=$rule active=$active", c.couldShow(item))
            }
        }
        // The case that froze: a hidden item without a rule under Show everything.
        assertTrue(BarConfig(hiddenMode = HiddenMode.SHOW_ALL).couldShow(memory))
        assertFalse(BarConfig(hiddenMode = HiddenMode.CLICK).couldShow(memory))
    }

    @Test fun alreadyV3IsUntouched() {
        val c = BarConfig(hiddenMode = HiddenMode.HOVER)
        assertEquals(c, c.migrateToV3())
    }
}
