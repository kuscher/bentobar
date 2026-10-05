package io.github.kuscher.bentobar.items

import io.github.kuscher.bentobar.items.NowPlayingRules.Standing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The now-playing source's rules that need no device: which player is first, where a track stands, a title in one line. */
class NowPlayingRulesTest {
    @Test fun theBarFollowsThePlayerThatStartedLast() {
        val order = NowPlayingRules.order(listOf(
            Standing("music", playing = true, startedAt = 100, lastPlayedAt = 500),
            Standing("browser", playing = true, startedAt = 400, lastPlayedAt = 500),
            Standing("podcasts", playing = false, startedAt = 450, lastPlayedAt = 460),
        ))
        assertEquals(listOf("browser", "music", "podcasts"), order)
    }

    @Test fun pausedPlayersComeAfterPlayingOnesMostRecentFirst() {
        val order = NowPlayingRules.order(listOf(
            Standing("old", playing = false, startedAt = 10, lastPlayedAt = 20),
            Standing("recent", playing = false, startedAt = 5, lastPlayedAt = 300),
            Standing("never", playing = false, startedAt = 0, lastPlayedAt = 0),
            Standing("now", playing = true, startedAt = 1, lastPlayedAt = 1),
        ))
        assertEquals(listOf("now", "recent", "old", "never"), order)
    }

    @Test fun aTieKeepsTheSystemsOrder() {
        val order = NowPlayingRules.order(listOf(Standing("a", false, 0, 0), Standing("b", false, 0, 0), Standing("c", false, 0, 0)))
        assertEquals(listOf("a", "b", "c"), order)
        assertEquals(emptyList<String>(), NowPlayingRules.order(emptyList()))
    }

    @Test fun aPlayingTrackCountsOnByTheClock() {
        // 1:40 in at second 1000, five minutes long.
        fun at(now: Long, playing: Boolean = true, speed: Float = 1f, duration: Long = 300_000) =
            NowPlayingRules.position(100_000, 1_000_000, speed, playing, duration, now)
        assertEquals(100_000, at(1_000_000))
        assertEquals(102_000, at(1_002_000))
        assertEquals(104_000, at(1_002_000, speed = 2f)) // a podcast at double speed
        assertEquals(100_000, at(1_002_000, playing = false)) // paused: it stays
        assertEquals(100_000, at(1_002_000, speed = 0f))
        assertEquals(300_000, at(9_000_000)) // never past the end
        assertEquals(8_100_000, at(9_000_000, duration = 0)) // a stream has no end
        assertEquals(100_000, at(900_000)) // a clock that went back doesn't move it back
    }

    @Test fun aPositionNobodyStampedStaysWhereItIs() {
        assertEquals(5_000, NowPlayingRules.position(5_000, 0, 1f, true, 0, 1_000_000))
        assertEquals(0, NowPlayingRules.position(-1, 0, 1f, true, 60_000, 1_000_000)) // "unknown" is the start
    }

    @Test fun aTitleBecomesOneLine() {
        assertEquals("Blue in Green", NowPlayingRules.oneLine("  Blue in\nGreen\t "))
        assertEquals("a b c", NowPlayingRules.oneLine("a\r\n\r\nb   c"))
        assertEquals("", NowPlayingRules.oneLine(null))
        assertEquals("", NowPlayingRules.oneLine(" \n "))
        assertEquals("مرحبا بالعالم", NowPlayingRules.oneLine("مرحبا\nبالعالم")) // right-to-left text is left as it is
    }

    @Test fun aLongTitleIsCutBetweenWholeCharacters() {
        assertEquals("Sinfonia C", NowPlayingRules.oneLine("Sinfonia Concertante", max = 10))
        assertEquals("Sinfonia", NowPlayingRules.oneLine("Sinfonia Concertante", max = 9)) // no space left hanging at the end
        // An emoji is two halves in a string: both stay or both go.
        val cut = NowPlayingRules.oneLine("ab🎵cd", max = 3)
        assertEquals("ab", cut)
        assertEquals("ab🎵", NowPlayingRules.oneLine("ab🎵cd", max = 4))
        assertFalse(cut.last().isSurrogate())
        assertTrue(NowPlayingRules.oneLine("x".repeat(500)).length == 200)
    }
}
