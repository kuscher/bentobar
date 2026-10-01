package io.github.kuscher.bentobar.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ClockFormatTest {
    @Test fun timeLeftRoundsUp() {
        assertEquals("0:01", Fmt.clock(1))
        assertEquals("0:00", Fmt.clock(0))
        assertEquals("25:00", Fmt.clock(24 * 60_000L + 59_001))
    }

    @Test fun timeTakenRoundsDown() {
        assertEquals("0:00", Fmt.clock(1, elapsed = true))
        assertEquals("0:00", Fmt.clock(999, elapsed = true))
        assertEquals("0:01", Fmt.clock(1_000, elapsed = true))
        assertEquals("1:00:00", Fmt.clock(3_600_999, elapsed = true))
    }
}
