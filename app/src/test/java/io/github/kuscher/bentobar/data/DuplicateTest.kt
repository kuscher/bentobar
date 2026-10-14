package io.github.kuscher.bentobar.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Duplicate in an item's settings: the copy is the item with a new id, beside it. */
class DuplicateTest {
    private val clock = ItemConfig("a", "clock")
    private val cpu = ItemConfig("b", "cpu", Section.HIDDEN, whenActive = true, display = Display.ICON, options = mapOf("above" to "60"))
    private val timer = ItemConfig("c", "timer")

    @Test fun aCopyKeepsEverySettingAndSitsBesideItsOriginal() {
        // It used to be rebuilt from the type, the section and the options: "Show when" and "Show as" were lost.
        assertEquals(listOf(clock, cpu, cpu.copy(id = "x"), timer), Store.duplicated(listOf(clock, cpu, timer), cpu, "x"))
    }

    @Test fun anItemThatIsGoneMeanwhileIsNotCopied() {
        assertEquals(listOf(clock, timer), Store.duplicated(listOf(clock, timer), cpu, "x"))
    }
}
