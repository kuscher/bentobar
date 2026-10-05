package io.github.kuscher.bentobar.items

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the ticker computes an item again: the arithmetic that decides whether a tick is the item's turn. */
class TickRulesTest {
    /**
     * Seen on a device: the volume slider followed the volume keys after up to two seconds. Ticks aim
     * at the start of each second, so two of them can be 990 ms apart, and "a second has not passed"
     * then cost a one-second item its turn.
     */
    @Test fun aTickThatLandsALittleEarlyIsStillAOneSecondItemsTurn() {
        assertTrue(TickRules.due(now = 10_990, last = 10_000, everyMs = 1000))
        assertTrue(TickRules.due(now = 11_000, last = 10_000, everyMs = 1000))
        assertTrue(TickRules.due(now = 11_040, last = 10_000, everyMs = 1000))
        // Every tick of a minute with jitter either way: none is skipped.
        var last = 0L
        var computed = 0
        for (i in 1..60) {
            val now = i * 1000L + if (i % 2 == 0) -40 else 35
            if (TickRules.due(now, last, 1000)) { computed++; last = now }
        }
        assertEquals(60, computed)
    }

    @Test fun anItemJustComputedByHandWaitsForTheNextTickButOne() {
        // A click recomputed it 300 ms before the tick: nothing new to say yet.
        assertFalse(TickRules.due(now = 10_300, last = 10_000, everyMs = 1000))
        assertTrue(TickRules.due(now = 11_300, last = 10_000, everyMs = 1000))
    }

    @Test fun slowerItemsKeepTheirPace() {
        // Every ten seconds is every tenth tick, also when ticks jitter.
        var last = 0L
        val turns = ArrayList<Int>()
        for (i in 1..40) {
            val now = i * 1000L + if (i % 3 == 0) -30 else 20
            if (TickRules.due(now, last, 10_000)) { turns += i; last = now }
        }
        assertEquals(listOf(10, 20, 30, 40), turns)
        assertFalse(TickRules.due(now = 9_000, last = 0, everyMs = 10_000))
    }

    @Test fun underBatterySaverEveryOtherSecondIsEveryTick() {
        var last = 0L
        var computed = 0
        for (i in 1..30) {
            val now = i * 2000L - 25
            if (TickRules.due(now, last, 1000)) { computed++; last = now }
        }
        assertEquals(30, computed)
    }
}
