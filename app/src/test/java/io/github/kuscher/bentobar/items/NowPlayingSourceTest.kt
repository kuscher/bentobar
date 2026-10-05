package io.github.kuscher.bentobar.items

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What only reading the now-playing source can show: the order in which it does two things. Both are
 * about moments no unit test can make (two players' answers arriving a frame apart; a device that
 * refuses the players' list a second time), so the shape of the code is pinned instead. A test that
 * fails here names what has to stay true, not how to write it.
 */
class NowPlayingSourceTest {
    /** The source without its comments: they may name what the code must not do. */
    private val source = File("src/main/java/io/github/kuscher/bentobar/items/NowPlaying.kt").readText()
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "").lines().joinToString("\n") { it.substringBefore("//") }

    /** One function of the source, from its name to the next thing the object declares. */
    private fun body(name: String): String {
        val from = source.indexOf("fun $name(")
        assertTrue("NowPlaying.kt has no function called $name", from >= 0)
        val next = Regex("""\n    (?:private |internal )?(?:fun|val|var) """).find(source, from + 1)?.range?.first ?: source.length
        return source.substring(from, next)
    }

    @Test fun everyPlayersStateIsReadBeforeTheFirstValueIsPublished() {
        // When the bar comes back, the players are taken on in one go. Were each one's state to arrive in a post of its own,
        // the item would say "not playing" for some frames, and of two playing players the one whose answer came last would
        // count as the one that started last, whatever order the system gave them.
        val adopt = body("adopt")
        assertTrue("adopt hands the slow part (what plays: metadata can hold a large picture) to the background thread", "work.execute" in adopt)
        assertTrue("whether a player plays is asked in adopt itself, before anything is published",
            "playbackState" in adopt.substringBefore("work.execute"))
        assertFalse("and not asked again in the background, where the answer would arrive in a post of its own",
            "playbackState" in adopt.substringAfter("work.execute"))
        // The one value all of this ends in is made after every new player has been asked.
        assertTrue(adopt.trimEnd().removeSuffix("}").trimEnd().endsWith("changed()"))
    }
}
