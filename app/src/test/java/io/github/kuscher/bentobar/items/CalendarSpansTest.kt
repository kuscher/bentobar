package io.github.kuscher.bentobar.items

import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarSpansTest {
    private val usual = 100L..200L

    @Test fun aMonthInsideTheUsualWindowAddsNothing() {
        assertEquals(listOf(usual), Meetings.spans(usual, null))
        assertEquals(listOf(usual), Meetings.spans(usual, 120L..180L))
    }

    @Test fun aLaterMonthLoadsOnlyWhatLiesBeyond() {
        assertEquals(listOf(usual, 200L..260L), Meetings.spans(usual, 150L..260L))
        assertEquals(listOf(usual, 300L..360L), Meetings.spans(usual, 300L..360L))
    }

    @Test fun anEarlierMonthLoadsOnlyWhatLiesBefore() {
        assertEquals(listOf(usual, 40L..100L), Meetings.spans(usual, 40L..130L))
        assertEquals(listOf(usual, 10L..60L), Meetings.spans(usual, 10L..60L))
    }

    @Test fun aSpanAroundTheWindowLoadsBothEnds() {
        assertEquals(listOf(usual, 50L..100L, 200L..250L), Meetings.spans(usual, 50L..250L))
    }
}
