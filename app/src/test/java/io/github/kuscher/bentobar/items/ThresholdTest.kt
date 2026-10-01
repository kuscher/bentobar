package io.github.kuscher.bentobar.items

import io.github.kuscher.bentobar.data.ItemConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class ThresholdTest {
    private val net = Threshold("activeKBs", 500, 50..5000, step = 50) { "$it" }

    @Test fun missingKeyUsesTheOldDefault() = assertEquals(500, net.of(ItemConfig("a", "network")))

    @Test fun storedValuesStayAsTheyAre() {
        // An older slider could save values off the step, or (by hand) out of range: the bar keeps using them.
        val odd = ItemConfig("a", "network", options = mapOf("activeKBs" to "9000"))
        assertEquals(9000, net.of(odd))
        assertEquals(5000, net.shown(odd))
    }

    @Test fun snapsToSteps() {
        assertEquals(500, net.snap(523f))
        assertEquals(550, net.snap(530f))
        assertEquals(50, net.snap(0f))
        assertEquals(5000, net.snap(6000f))
    }
}
