package io.github.kuscher.bentobar.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ImportTest {
    private val json = Json { encodeDefaults = true }
    private val clock = ItemConfig("c", "clock", Section.SHOWN)
    private val timer = ItemConfig("t", "timer", Section.SHOWN)

    @Test fun anExportComesBack() {
        val c = BarConfig(items = listOf(clock, timer), position = Position.LEFT)
        assertEquals(c, Store.parseLayout(json.encodeToString(BarConfig.serializer(), c)))
    }

    @Test fun somethingElseOnTheClipboardIsNotALayout() {
        // Each of these used to replace the bar with an empty one.
        assertNull(Store.parseLayout("{}"))
        assertNull(Store.parseLayout("""{"theme":"dark"}"""))
        assertNull(Store.parseLayout("""{"items":"none"}"""))
        assertNull(Store.parseLayout("hello"))
        assertNull(Store.parseLayout("[1,2,3]"))
    }

    @Test fun aRepeatedIdKeepsItsFirst() {
        val c = BarConfig(items = listOf(clock, timer, clock.copy(section = Section.HIDDEN)))
        assertEquals(listOf(clock, timer), Store.parseLayout(json.encodeToString(BarConfig.serializer(), c))!!.items)
    }

    @Test fun aPastedLayoutKeepsThisInstallsSwitches() {
        // Calendar was switched off here; the pasted layout comes from an install where it was on.
        val here = BarConfig(items = listOf(clock), turnedOff = setOf(Uses.CALENDAR), enabled = false, onboarded = true)
        val pasted = BarConfig(items = listOf(timer), position = Position.LEFT, presenting = true)
        val c = pasted.keepingLocal(here)
        assertEquals(listOf(timer), c.items)
        assertEquals(Position.LEFT, c.position)
        assertEquals(setOf(Uses.CALENDAR), c.turnedOff)
        assertEquals(false, c.enabled)
        assertEquals(false, c.presenting)
        assertEquals(true, c.onboarded)
    }

    @Test fun anOlderLayoutIsMigrated() {
        val old = """{"version":2,"chevron":false,"items":[{"id":"m","type":"memory","section":"HIDDEN"}]}"""
        val c = Store.parseLayout(old)
        assertNotNull(c)
        assertEquals(3, c!!.version)
        assertEquals(Section.OFF, c.items.single().section)
    }
}
