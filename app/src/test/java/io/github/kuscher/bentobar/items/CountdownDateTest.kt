package io.github.kuscher.bentobar.items

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class CountdownDateTest {
    @Test fun aRealDateParses() =
        assertEquals(LocalDateTime.of(2026, 12, 24, 18, 0), CountdownItem.parse("2026-12-24 18:00"))

    @Test fun impossibleDatesAreRejectedNotMoved() {
        assertNull(CountdownItem.parse("2026-02-30 18:00"))
        assertNull(CountdownItem.parse("2026-02-29 09:00"))
        assertEquals(LocalDateTime.of(2024, 2, 29, 9, 0), CountdownItem.parse("2024-02-29 09:00"))
    }

    @Test fun partialInputIsNotADate() {
        assertNull(CountdownItem.parse("2026-12-24"))
        assertNull(CountdownItem.parse(""))
    }
}
