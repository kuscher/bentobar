package io.github.kuscher.bentobar.items

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arithmetic of the route line: where the dots stand, how far the solid part reaches and where the
 * plane is, for a line of a given width. In dp here (the canvas gives pixels; the rules are the same).
 */
class FlightLineTest {
    private val eps = 1e-3f
    private fun line(width: Float, share: Double?) = FlightLine.of(width, share, dot = 3f, apart = 8f, plane = 20f, behind = 4f, ahead = 2f)

    @Test fun theDotsAreAboutEightApartWithTheFirstAndTheLastOnTheLinesEnds() {
        val l = line(224f, null)
        // 221 between the first and the last dot's centers: 28 steps of 7.89, not 27.6 of 8.
        assertEquals(221f / 28, l.pitch, eps)
        assertEquals(1.5f, l.start, eps)
        assertEquals(222.5f, l.end, eps)
        assertEquals(29, l.dots.size)
        assertEquals(1.5f, l.dots.first(), eps)
        assertEquals(222.5f, l.dots.last(), eps)
        for (width in listOf(40f, 100f, 173f, 224f, 311.5f)) {
            val any = line(width, null)
            assertTrue("$width", any.pitch in 6.5f..9.5f)
            assertEquals("$width", width - 1.5f, any.dots.last(), eps)
        }
    }

    @Test fun withNoShareThereIsNoPlaneAndEveryDot() {
        val l = line(224f, null)
        assertNull(l.center)
        assertNull(l.flownUntil)
        assertEquals(29, l.dots.size)
    }

    @Test fun beforeItLeavesThePlaneRestsAtTheStartAndTheDotsBeginClearOfItsNose() {
        val l = line(224f, 0.0)
        assertEquals(10f, l.center!!, eps)
        // Nothing is flown yet: no solid part.
        assertNull(l.flownUntil)
        // No dot under the plane (20 wide) or within 2 of its nose; the dots that are drawn keep their places on the grid.
        val grid = line(224f, null).dots
        assertTrue(l.dots.all { it - 1.5f >= 10f + 10f + 2f - eps })
        assertEquals(grid.filter { it - 1.5f >= 22f - eps }, l.dots)
        assertEquals(grid.last(), l.dots.last(), eps)
    }

    @Test fun inTheAirTheFlownPartIsSolidUpToFourBehindThePlane() {
        val l = line(224f, 0.5)
        assertEquals(112f, l.center!!, eps)                            // 10 + half of what is left between the two rests
        assertEquals(112f - 10f - 4f, l.flownUntil!!, eps)
        assertTrue(l.dots.first() - 1.5f >= 112f + 10f + 2f - eps)
        assertTrue(l.dots.first() - 1.5f < 112f + 10f + 2f + l.pitch)   // and no further off than one step
        // Further along, fewer dots are left; none ever moves.
        val later = line(224f, 0.75)
        assertTrue(later.dots.size < l.dots.size)
        assertEquals(l.dots.takeLast(later.dots.size), later.dots)
    }

    @Test fun landedTheLineIsSolidAndThePlaneIsAtTheEnd() {
        val l = line(224f, 1.0)
        assertEquals(214f, l.center!!, eps)
        assertEquals(200f, l.flownUntil!!, eps)
        assertEquals(emptyList<Float>(), l.dots)
    }

    @Test fun aShareOutsideItsRangeIsTakenAsTheNearestEnd() {
        assertEquals(10f, line(224f, -0.5).center!!, eps)
        assertEquals(214f, line(224f, 7.0).center!!, eps)
        assertEquals(10f, line(224f, Double.NaN).center!!, eps)
    }

    @Test fun aLineWithNoRoomDrawsNothingAndNeverFails() {
        for (width in listOf(0f, 1f, 3f, -5f, Float.NaN)) {
            val l = line(width, 0.5)
            assertEquals("$width", emptyList<Float>(), l.dots)
            assertNull("$width", l.center)
            assertNull("$width", l.flownUntil)
        }
        // Too short for a plane to have a way to go: it stands in the middle, and nothing fails.
        val short = line(18f, 0.5)
        assertEquals(9f, short.center!!, eps)
        assertNull(short.flownUntil)
        // Just room for the plane and a little more.
        assertTrue(line(30f, 0.0).dots.size <= 2)
    }
}
