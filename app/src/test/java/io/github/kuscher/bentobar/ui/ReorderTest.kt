package io.github.kuscher.bentobar.ui

import io.github.kuscher.bentobar.data.ItemConfig
import io.github.kuscher.bentobar.data.Section
import io.github.kuscher.bentobar.data.moved
import org.junit.Assert.assertEquals
import org.junit.Test

/** The drop-target math and the move it previews (the same one the Store saves). */
class ReorderTest {
    private val cards = listOf(Section.SHOWN to 0f..200f, Section.HIDDEN to 214f..400f, Section.OFF to 414f..500f)
    private val rows = mapOf(Section.SHOWN to listOf(50f, 100f, 150f), Section.HIDDEN to listOf(260f), Section.OFF to emptyList())
    private val here = DropSpot(Section.SHOWN, 1)

    @Test fun indexCountsTheRowsAbove() {
        assertEquals(DropSpot(Section.SHOWN, 0), dropSpot(40f, cards, rows, here))
        assertEquals(DropSpot(Section.SHOWN, 2), dropSpot(120f, cards, rows, here))
        assertEquals(DropSpot(Section.SHOWN, 3), dropSpot(190f, cards, rows, here))
    }

    @Test fun otherCardsAndEmptyOnes() {
        assertEquals(DropSpot(Section.HIDDEN, 1), dropSpot(300f, cards, rows, here))
        assertEquals(DropSpot(Section.OFF, 0), dropSpot(450f, cards, rows, here))
    }

    @Test fun gapKeepsTheCurrentSpotAndEndsClamp() {
        assertEquals(here, dropSpot(207f, cards, rows, here))
        assertEquals(DropSpot(Section.SHOWN, 0), dropSpot(-30f, cards, rows, here))
        assertEquals(DropSpot(Section.OFF, 0), dropSpot(900f, cards, rows, here))
    }

    @Test fun movedMatchesSectionIndexes() {
        val items = listOf(ItemConfig("a", "x"), ItemConfig("b", "x"), ItemConfig("c", "x", Section.HIDDEN), ItemConfig("d", "x", Section.HIDDEN))
        assertEquals(listOf("b", "a", "c", "d"), items.moved("a", Section.SHOWN, 1).map { it.id })
        assertEquals(listOf("b", "c", "a", "d"), items.moved("a", Section.HIDDEN, 1).map { it.id })
        assertEquals(Section.HIDDEN, items.moved("a", Section.HIDDEN, 1).first { it.id == "a" }.section)
        assertEquals(listOf("a", "b", "c", "d"), items.moved("nope", Section.OFF, 0).map { it.id })
        assertEquals(listOf("b", "c", "d", "a"), items.moved("a", Section.OFF, 5).map { it.id })
    }
}
