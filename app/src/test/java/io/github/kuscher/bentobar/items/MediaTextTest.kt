package io.github.kuscher.bentobar.items

import io.github.kuscher.bentobar.items.MediaText.Controls
import io.github.kuscher.bentobar.items.MediaText.Glyph
import io.github.kuscher.bentobar.items.MediaText.Phase
import io.github.kuscher.bentobar.items.MediaText.Show
import io.github.kuscher.bentobar.items.MediaText.Track
import io.github.kuscher.bentobar.items.MediaText.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * What Now playing says, in the bar and in its menu: every example of the product and design
 * tables, to the character, and the promises a title from another app must not break (one line,
 * never more characters than asked for, never half an emoji).
 */
class MediaTextTest {
    /** The copy deck, as written there. The rules below are run with these words. */
    private val deck = mapOf(
        "item_media_title" to "Now playing",
        "item_media_desc" to "What's playing, with pause and skip; shows while something plays",
        "trigger_media" to "Show while something is playing, and for %1\$s after it pauses",
        "trigger_media_short" to "shows while playing",
        "media_nothing" to "Nothing playing",
        "media_playing" to "Playing",
        "media_player_paused" to "%1\$s · Paused",
        "media_app_playing" to "%1\$s is playing",
        "media_app_paused" to "%1\$s is paused",
        "media_bar_both" to "%1\$s · %2\$s",
        "media_track_by" to "%1\$s by %2\$s",
        "media_position" to "Position",
        "media_position_state" to "%1\$s of %2\$s",
        "media_other_players" to "Other players",
        "media_other_pause" to "Pause %1\$s",
        "media_other_play" to "Play %1\$s",
        "media_open_player" to "Open %1\$s",
        "media_access_lead" to "See what's playing",
        "media_access_explain" to "Android shares the title, artist and artwork only with apps that have notification access. " +
            "BentoBar tells Android to send it no notifications at all; it reads only what your media players say is playing, and keeps that on this device.",
        "media_access_restricted" to "Switch grayed out? Tap it, tap OK, then turn on Allow restricted settings in App info.",
        "media_access_allow" to "Allow notification access",
        "media_show_title" to "Title",
        "media_show_both" to "Title and artist",
        "media_show_artist" to "Artist",
        "media_show_needs_access" to "Shows once notification access is on",
        "media_click" to "A click",
        "media_click_menu" to "Opens the menu",
        "media_click_toggle" to "Plays or pauses",
        "media_desc_playing_by" to "Playing: %1\$s by %2\$s",
        "media_desc_playing" to "Playing: %1\$s",
        "media_desc_paused" to "Paused: %1\$s",
        "media_desc_playing_unknown" to "Something is playing",
        "media_desc_paused_unknown" to "Playback paused",
    )

    /** Not in the copy deck: how the album joins a spoken track ("… by Miles Davis, Kind of Blue"), which the deck leaves to "joined". */
    private val added = mapOf("media_track_album" to "%1\$s, %2\$s")

    /** The two words that are older than this item and live in strings.xml. */
    private val shared = mapOf(Word.PAUSED to ("common_paused" to "Paused"), Word.STARTING to ("status_starting" to "Starting…"))

    private val words = MediaText.Words { word -> shared[word]?.second ?: (deck + added).getValue("media_" + word.name.lowercase()) }

    private fun u(vararg codePoints: Int) = String(codePoints, 0, codePoints.size)
    private val note = u(0x1F3B5)
    private val zwj = u(0x200D)
    private val family = u(0x1F468, 0x200D, 0x1F469, 0x200D, 0x1F467, 0x200D, 0x1F466)
    private val thumb = u(0x1F44D, 0x1F3FD)
    private val germany = u(0x1F1E9, 0x1F1EA)
    private val france = u(0x1F1EB, 0x1F1F7)
    private val keycap = u('1'.code, 0xFE0F, 0x20E3)
    private val england = u(0x1F3F4, 0xE0067, 0xE0062, 0xE0065, 0xE006E, 0xE0067, 0xE007F)
    private val kiss = u(0x1F469, 0x1F3FD, 0x200D, 0x2764, 0xFE0F, 0x200D, 0x1F48B, 0x200D, 0x1F468, 0x1F3FB)

    private val blue = Track("Blue in Green", "Miles Davis", "Spotify", "Kind of Blue")
    private val sinfonia = Track("Sinfonia Concertante in E-flat major", "Mozart", "Spotify")
    private val clocks = Track("Clocks", "Coldplay", "Spotify")
    private val bohemian = Track("Bohemian Rhapsody", "Queen", "Spotify")
    private val soWhat = Track("So What", "Miles Davis", "Spotify")
    private val video = Track("", "", "Chrome")

    private fun text(track: Track, show: Show = Show.TITLE, max: Int = 20) = MediaText.text(track, show, max, words)

    // ---- the cut

    @Test fun aTitleThatFitsStaysAsItIs() {
        assertEquals("Blue in Green", MediaText.cut("Blue in Green", 20))
        assertEquals(13, MediaText.count("Blue in Green"))
        // Exactly as long as allowed is not too long.
        assertEquals("Sinfonia Concertante", MediaText.cut("Sinfonia Concertante", 20))
        assertEquals("", MediaText.cut("", 20))
    }

    @Test fun aLongTitleLosesItsEndToAnEllipsis() {
        val title = "Sinfonia Concertante in E-flat major"
        assertEquals("Sinfonia Concertant…", MediaText.cut(title, 20))
        assertEquals(20, MediaText.count(MediaText.cut(title, 20)))
        assertEquals("Sinfoni…", MediaText.cut(title, 8))
        assertEquals(8, MediaText.count(MediaText.cut(title, 8)))
        // One too many is enough to be cut.
        assertEquals("Sinfonia Concertant…", MediaText.cut("Sinfonia Concertante!", 20))
    }

    @Test fun noSpaceIsLeftBeforeTheEllipsis() {
        assertEquals("Kind of…", MediaText.cut("Kind of Blue, Deluxe", 9))
        assertEquals("Sinfonia…", MediaText.cut("Sinfonia Concertante", 10))
    }

    @Test fun anEmojiCountsAsOneCharacterAndIsNeverCutInHalf() {
        assertEquals(1, MediaText.count(note))
        assertEquals("abcdefg…", MediaText.cut("abcdefg${note}hij", 8))
        val kept = MediaText.cut("abcdef${note}hij", 8)
        assertEquals("abcdef$note…", kept)
        assertEquals(8, MediaText.count(kept))
        // In a string it is two halves, which is why a cut by the string's own length could part them.
        assertEquals(9, kept.length)
    }

    @Test fun whatReadsAsOneCharacterStaysInOnePiece() {
        // A skin tone belongs to the hand before it.
        assertEquals("abcdef…", MediaText.cut("abcdef${thumb}xyz", 8))
        assertEquals("abcde$thumb…", MediaText.cut("abcde${thumb}xyz", 8))
        // People joined into a family are one picture: all of them or none.
        assertEquals("abc…", MediaText.cut("abc${family}xyz", 8))
        assertEquals("$family…", MediaText.cut("${family}abcdef", 8))
        // A keycap is a digit, a selector and the key around them.
        assertEquals("abcdef…", MediaText.cut("abcdef${keycap}xyz", 8))
        // A heart and the selector that makes it red.
        assertEquals("abcdef…", MediaText.cut("abcdef" + u(0x2764, 0xFE0F) + "xyz", 8))
        // A flag spelled with tag characters (England) has seven parts.
        assertEquals("ab…", MediaText.cut("ab${england}cde", 8))
        assertEquals("$england…", MediaText.cut("${england}cdef", 8))
    }

    @Test fun flagsAreKeptInPairs() {
        assertEquals("abcde$germany…", MediaText.cut("abcde$germany${france}x", 8))
        assertEquals("abcdef…", MediaText.cut("abcdef$germany$france", 8))
        assertEquals("abc$germany$france…", MediaText.cut("abc$germany$france${germany}x", 8))
    }

    @Test fun anAccentStaysOnItsLetter() {
        // "é" written as e and a combining accent: the e goes with its accent.
        assertEquals("Beyonc…", MediaText.cut("Beyoncé Live at Home", 8))
        assertEquals("Beyoncé…", MediaText.cut("Beyoncé Live at Home", 9))
        // Arabic with its vowel marks, Devanagari with a vowel sign.
        val marhaban = "مَرْحَبًا بك"
        assertEquals("مَرْحَ…", MediaText.cut(marhaban, 8))
        val namaste = "नमस्ते दुनिया"
        assertEquals("नमस्ते…", MediaText.cut(namaste, 9))
        assertEquals("नमस्ते दु…", MediaText.cut(namaste, 11))
        // Korean written in its parts: a syllable is not shown without its last sound.
        val han = "한"
        assertEquals("abcde…", MediaText.cut("abcde$han$han", 8))
        assertEquals("abcd$han…", MediaText.cut("abcd$han$han", 8))
    }

    @Test fun aFirstCharacterThatIsTooLongLeavesOnlyTheEllipsis() {
        assertEquals(10, MediaText.count(kiss))
        assertEquals("…", MediaText.cut("$kiss Our Song", 8))
        assertEquals("$kiss…", MediaText.cut("$kiss Our Song", 11))
    }

    @Test fun rightToLeftTitlesAreCutByTheSameRule() {
        val arabic = "مرحبا بالعالم"
        assertEquals(13, MediaText.count(arabic))
        assertEquals(arabic, MediaText.cut(arabic, 20))
        assertEquals("مرحبا ب…", MediaText.cut(arabic, 8))
        val hebrew = "שלום עולם"
        assertEquals("$hebrew · Queen", text(Track(hebrew, "Queen", "Spotify"), Show.BOTH))
    }

    // ---- a title made fit to show

    @Test fun aTitleIsOneLine() {
        assertEquals("Blue in Green", Track("  Blue in\nGreen\t ", "", "").title)
        assertEquals("a b c", Track("a\r\n\r\nb   c", "", "").title)
        assertEquals("Live at the Apollo", Track("Live\u0000 at\u000Bthe\u0085Apollo", "", "").title)
        assertEquals("", Track(" \n ", "", "").title)
        val shown = text(Track("Line one\nLine two\nLine three", "", "Player"))
        assertEquals("Line one Line two L…", shown)
        assertFalse(shown!!.any { it == '\n' || it == '\r' })
    }

    @Test fun halfAnEmojiFromAPlayerIsDropped() {
        // A player that cut its own title in the middle of an emoji hands over half of one.
        assertEquals("abcd", Track("ab\uD83Ccd", "", "").title)
        assertEquals("ab", Track("ab\uD83C", "", "").title)
        assertEquals("ab", Track("\uDFB5ab", "", "").title)
        assertEquals("ab$note", Track("ab$note", "", "").title)
    }

    @Test fun aTitleCannotTurnTheTextBesideItAround() {
        // Marks that override the direction of what follows are left out; the letters keep their own direction.
        assertEquals("evil", Track("‮evil‬", "", "").title)
        assertEquals("abc", Track("⁦a⁧b⁨c⁩", "", "").title)
        assertEquals("a‏b", Track("a‏b", "", "").title)
    }

    // ---- the bar's text

    @Test fun everyRowOfTheTableInTheBar() {
        assertEquals("Blue in Green", text(blue, Show.TITLE, 20))
        assertEquals("Sinfonia Concertant…", text(sinfonia, Show.TITLE, 20))
        assertEquals("Sinfoni…", text(sinfonia, Show.TITLE, 8))
        assertEquals("Clocks · Coldplay", text(clocks, Show.BOTH, 20))
        assertEquals("Bohemian Rhapsody", text(bohemian, Show.BOTH, 20))
        assertEquals("Bohemian Rhapsody · Queen", text(bohemian, Show.BOTH, 30))
        assertEquals("Miles Davis", text(soWhat, Show.ARTIST, 20))
        for (show in Show.entries) assertEquals("Chrome", text(video, show, 20))
    }

    @Test fun theExamplesHaveTheLengthsTheTablesGive() {
        assertEquals(13, MediaText.count(text(blue)!!))
        assertEquals(20, MediaText.count(text(sinfonia)!!))
        assertEquals(8, MediaText.count(text(sinfonia, max = 8)!!))
        assertEquals(17, MediaText.count(text(clocks, Show.BOTH)!!))
        assertEquals(17, MediaText.count(text(bohemian, Show.BOTH)!!))
        assertEquals(25, MediaText.count(text(bohemian, Show.BOTH, 30)!!))
        assertEquals(11, MediaText.count(text(soWhat, Show.ARTIST)!!))
        assertEquals(6, MediaText.count(text(video)!!))
    }

    @Test fun titleAndArtistDropTheArtistWholeWhenTheyDoNotFit() {
        // 25 characters with the artist: at 24 the artist goes, at 25 it stays. It is never cut ("Bohemian Rhapsody · Qu…").
        assertEquals("Bohemian Rhapsody", text(bohemian, Show.BOTH, 24))
        assertEquals("Bohemian Rhapsody · Queen", text(bohemian, Show.BOTH, 25))
        // The title alone is then cut like any title.
        assertEquals("Sinfonia Concertant…", text(sinfonia, Show.BOTH, 20))
        // No artist: the title. No title: the player.
        assertEquals("Clocks", text(Track("Clocks", "", "Spotify"), Show.BOTH))
        assertEquals("Spotify", text(Track("", "Coldplay", "Spotify"), Show.BOTH))
    }

    @Test fun showArtistFallsBackToTheTitleThenToThePlayer() {
        assertEquals("So What", text(Track("So What", "", "Spotify"), Show.ARTIST))
        assertEquals("Chrome", text(video, Show.ARTIST))
        assertEquals("Wolfgang Amadeus Mo…", text(Track("Sinfonia", "Wolfgang Amadeus Mozart", "Spotify"), Show.ARTIST))
    }

    @Test fun aPlayersNameIsHeldToTheLengthToo() {
        assertEquals("A Very Long Player…", text(Track("", "", "A Very Long Player Name")))
        assertNull(text(Track("", "", "")))
    }

    /** Titles a player might hand over: long, wide, in pieces, with pictures made of several parts, with marks on their letters. */
    private val titles = listOf("Blue in Green", "Sinfonia Concertante in E-flat major", "x".repeat(200), "W".repeat(41), "a b c d e f g h i j k l m n o p q r s t u",
        "abc${family}xyz$family", "$kiss Our Song", "$germany$france$germany$france$germany$france", "Beyoncé Live at Home, the long version",
        "一二三四五六七八九十".repeat(5), "$note ".repeat(30), "line\none\ntwo\tthree four five six seven",
        "مَرْحَبًا ".repeat(8), "ab\uD83Ccd".repeat(20), keycap.repeat(15), england.repeat(5), "$thumb$zwj".repeat(12),
        "한글".repeat(8), "ab$thumb".repeat(15), "नमस्ते दुनिया ".repeat(4),
        "a${germany}b$germany$france".repeat(6), "x$note️$zwj${note}y".repeat(9))

    @Test fun theCutNeverFallsInsideACharacter() {
        // Checked by rules written down a second time, the plain way: what comes after the cut is never a part of the character before it.
        val marks = setOf(Character.NON_SPACING_MARK.toInt(), Character.ENCLOSING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt())
        fun flag(cp: Int) = cp in 0x1F1E6..0x1F1FF
        for (title in titles) for (max in 1..60) {
            val full = Track(title, "", "").title
            val shown = MediaText.cut(full, max)
            assertTrue("more than asked for", MediaText.count(shown) <= max)
            if (shown == full) continue
            assertTrue("no ellipsis", shown.endsWith("…"))
            val kept = shown.dropLast(1)
            assertTrue("not the title's own beginning", full.startsWith(kept))
            val rest = full.substring(kept.length)
            // After spaces that were trimmed away, the next character starts afresh anyway.
            if (rest.startsWith(" ") || kept.isEmpty()) continue
            val next = rest.codePointAt(0)
            val last = kept.codePointBefore(kept.length)
            assertFalse("a mark without its letter", Character.getType(next) in marks)
            assertFalse("a selector, a skin tone, a joiner or a flag's letter without its picture",
                next == 0x200D || next == 0x200C || next in 0xFE00..0xFE0F || next in 0x1F3FB..0x1F3FF || next in 0xE0020..0xE007F)
            assertFalse("a joiner left hanging", last == 0x200D)
            assertFalse("a Korean syllable in two", next in 0x1160..0x11FF || last in 0x1100..0x115F)
            if (flag(next) && flag(last)) assertEquals("half a flag", 0, kept.codePoints().toArray().reversed().takeWhile(::flag).size % 2)
        }
    }

    @Test fun longestTitleNeverGivesMoreCharactersThanAskedFor() {
        for (title in titles) for (artist in listOf("", "Queen", titles[2])) for (show in Show.entries) for (max in MediaText.CHARS) {
            val shown = text(Track(title, artist, "Spotify"), show, max)!!
            assertTrue("$max: more than asked for", MediaText.count(shown) <= max)
            assertFalse("a line break", shown.any { it == '\n' || it == '\r' || it == '\t' })
            assertFalse("a space before the ellipsis", shown.endsWith(" …"))
            // Where the text was cut, it was not cut behind a joiner (which would leave the next picture's first half out).
            if (shown.endsWith("…")) assertFalse("a joiner left hanging", shown.dropLast(1).endsWith(zwj))
            // Every surrogate has its other half beside it.
            shown.forEachIndexed { i, c ->
                if (c.isHighSurrogate()) assertTrue(i + 1 < shown.length && shown[i + 1].isLowSurrogate())
                if (c.isLowSurrogate()) assertTrue(i > 0 && shown[i - 1].isHighSurrogate())
            }
        }
    }

    @Test fun aLengthOutsideTheSlidersRangeIsBroughtIntoIt() {
        // A layout that was pasted in can say anything.
        assertEquals("Sinfoni…", text(sinfonia, max = 0))
        assertEquals("Sinfoni…", text(sinfonia, max = -3))
        assertEquals("Sinfonia Concertante in E-flat major", text(sinfonia, max = 500))
        assertEquals("x".repeat(39) + "…", text(Track("x".repeat(60), "", ""), max = 500))
    }

    @Test fun theShowOptionHasThreeValuesAndTitleIsTheDefault() {
        assertEquals(Show.TITLE, Show.of("title"))
        assertEquals(Show.BOTH, Show.of("both"))
        assertEquals(Show.ARTIST, Show.of("artist"))
        assertEquals(Show.TITLE, Show.of(null))
        assertEquals(Show.TITLE, Show.of("something else"))
        assertEquals(listOf("title", "both", "artist"), Show.entries.map { it.id })
    }

    // ---- the bar, state by state

    private fun bar(phase: Phase, track: Track?, show: Show = Show.TITLE, max: Int = 20) = MediaText.bar(phase, track, show, max, words)

    @Test fun everyStateOfTheBar() {
        // Playing, title known.
        bar(Phase.PLAYING, blue).let { assertEquals(Glyph.NOTE, it.glyph); assertEquals("Blue in Green", it.text); assertTrue(it.active) }
        // Playing, no title.
        bar(Phase.PLAYING, video).let { assertEquals(Glyph.NOTE, it.glyph); assertEquals("Chrome", it.text); assertTrue(it.active) }
        // Paused, for the rule's minutes: what it showed while playing.
        bar(Phase.PAUSED, blue).let { assertEquals(Glyph.PAUSE, it.glyph); assertEquals("Blue in Green", it.text); assertTrue(it.active) }
        // Nothing playing, also with a player that paused long ago.
        bar(Phase.NOTHING, blue).let { assertEquals(Glyph.OFF, it.glyph); assertNull(it.text); assertFalse(it.active) }
        bar(Phase.NOTHING, null).let { assertEquals(Glyph.OFF, it.glyph); assertNull(it.text); assertFalse(it.active) }
        // No access (and while the players are not listed yet): playing, paused, nothing.
        bar(Phase.PLAYING, null).let { assertEquals(Glyph.NOTE, it.glyph); assertNull(it.text); assertTrue(it.active) }
        bar(Phase.PAUSED, null).let { assertEquals(Glyph.PAUSE, it.glyph); assertNull(it.text); assertTrue(it.active) }
    }

    @Test fun whatTheBarSaysAloud() {
        assertEquals("Playing: Blue in Green by Miles Davis", bar(Phase.PLAYING, blue).desc)
        assertEquals("Playing: Blue in Green", bar(Phase.PLAYING, Track("Blue in Green", "", "Spotify")).desc)
        assertEquals("Chrome is playing", bar(Phase.PLAYING, video).desc)
        assertEquals("Paused: Blue in Green", bar(Phase.PAUSED, blue).desc)
        assertEquals("Chrome is paused", bar(Phase.PAUSED, video).desc)
        assertEquals("Something is playing", bar(Phase.PLAYING, null).desc)
        assertEquals("Playback paused", bar(Phase.PAUSED, null).desc)
        assertEquals("Nothing playing", bar(Phase.NOTHING, null).desc)
        assertEquals("Nothing playing", bar(Phase.NOTHING, blue).desc)
        // What is said does not depend on what is shown, and is not cut.
        assertEquals("Playing: Sinfonia Concertante in E-flat major by Mozart", bar(Phase.PLAYING, sinfonia, Show.ARTIST, 8).desc)
        // A player without a name is not called by one.
        assertEquals("Something is playing", bar(Phase.PLAYING, Track("", "", "")).desc)
        assertEquals("Playback paused", bar(Phase.PAUSED, Track("", "", "")).desc)
    }

    @Test fun theTooltipIsTheWholeTitleAndArtist() {
        assertEquals("Blue in Green · Miles Davis", bar(Phase.PLAYING, blue).tooltip)
        assertEquals("Sinfonia Concertante in E-flat major · Mozart", bar(Phase.PLAYING, sinfonia, Show.TITLE, 8).tooltip)
        assertEquals("Blue in Green · Miles Davis", bar(Phase.PAUSED, blue).tooltip)
        assertEquals("Blue in Green", bar(Phase.PLAYING, Track("Blue in Green", "", "Spotify")).tooltip)
        // Without a title the strip shows the item's own name, as for every item.
        assertNull(bar(Phase.PLAYING, video).tooltip)
        assertNull(bar(Phase.PLAYING, null).tooltip)
        assertNull(bar(Phase.NOTHING, blue).tooltip)
    }

    // ---- the rule: while playing, and for some minutes after

    @Test fun theItemStaysForTheRulesMinutesAfterAPause() {
        val min = 60_000L
        val played = 1_000_000L
        assertEquals(Phase.PLAYING, MediaText.phase(true, played, played, 2))
        assertEquals(Phase.PAUSED, MediaText.phase(false, played, played, 2))
        assertEquals(Phase.PAUSED, MediaText.phase(false, played, played + 2 * min - 1, 2))
        assertEquals(Phase.NOTHING, MediaText.phase(false, played, played + 2 * min, 2))
        assertEquals(Phase.PAUSED, MediaText.phase(false, played, played + 9 * min, 10))
        assertEquals(Phase.NOTHING, MediaText.phase(false, played, played + 1 * min, 1))
        // Nothing has played yet.
        assertEquals(Phase.NOTHING, MediaText.phase(false, 0, played, 2))
        // Playing counts whatever the clock says.
        assertEquals(Phase.PLAYING, MediaText.phase(true, 0, played, 2))
    }

    @Test fun aClockSetBackOrAnOddNumberOfMinutesDoesNotKeepTheItemForever() {
        val played = 1_000_000L
        assertEquals(Phase.NOTHING, MediaText.phase(false, played, played - 1, 2))
        // The slider goes from 1 to 10; a pasted layout can say anything.
        assertEquals(Phase.NOTHING, MediaText.phase(false, played, played + 10 * 60_000L, 5_000))
        assertEquals(Phase.PAUSED, MediaText.phase(false, played, played + 30_000, -4))
        assertEquals(Phase.NOTHING, MediaText.phase(false, played, played + 60_000, -4))
    }

    @Test fun pausedTheBarNamesOnlyTheAppThatWasPlaying() {
        assertTrue(MediaText.follows(Phase.PLAYING, "music", null))
        assertTrue(MediaText.follows(Phase.PLAYING, "music", "browser"))
        assertTrue(MediaText.follows(Phase.PAUSED, "music", "music"))
        // The player that was playing has gone, and one that paused long ago is first now: "paused" is not about that one.
        assertFalse(MediaText.follows(Phase.PAUSED, "browser", "music"))
        assertFalse(MediaText.follows(Phase.PAUSED, "browser", null))
        assertFalse(MediaText.follows(Phase.NOTHING, "music", "music"))
    }

    // ---- the menu, state by state

    private val all = Controls(playing = true, durationMs = 337_000, canPrevious = true, canPlayPause = true, canNext = true)
    private fun menu(access: Boolean = true, starting: Boolean = false, granted: Boolean = access, phase: Phase = Phase.PLAYING, track: Track? = null,
                     controls: Controls? = null) = MediaText.menu(access, starting, granted, phase, track, controls, words)

    @Test fun theMenuWhilePlaying() {
        val m = menu(track = blue, controls = all)
        assertEquals("Spotify", m.subtitle)
        assertTrue(m.track); assertNull(m.line); assertTrue(m.position)
        assertTrue(m.playing); assertTrue(m.canPrevious); assertTrue(m.canPlayPause); assertTrue(m.canNext)
        assertTrue(m.player); assertFalse(m.consent)
    }

    @Test fun theMenuWhilePaused() {
        val m = menu(phase = Phase.NOTHING, track = blue, controls = Controls(false, 337_000, true, true, true))
        assertEquals("Spotify · Paused", m.subtitle)
        assertTrue(m.track); assertTrue(m.position); assertFalse(m.playing); assertTrue(m.player); assertFalse(m.consent)
    }

    @Test fun theMenuForAPlayerThatSaysNothingAboutWhatItPlays() {
        val playing = menu(track = video, controls = Controls(true, 0, false, true, false))
        assertEquals("Chrome", playing.subtitle)
        assertFalse(playing.track); assertEquals("Chrome is playing", playing.line); assertFalse(playing.position)
        assertFalse(playing.canPrevious); assertTrue(playing.canPlayPause); assertFalse(playing.canNext); assertTrue(playing.player)
        val paused = menu(phase = Phase.PAUSED, track = video, controls = Controls(false, 720_000, false, true, false))
        assertEquals("Chrome", paused.subtitle)
        assertEquals("Chrome is paused", paused.line); assertTrue(paused.position)
        // An artist or an album alone is something to show.
        val some = menu(track = Track("", "", "Radio", album = "Morning Show"), controls = all)
        assertTrue(some.track); assertNull(some.line)
    }

    @Test fun theMenuForALiveStream() {
        val m = menu(track = Track("Morning Show", "Radio One", "Radio"), controls = Controls(true, 0, false, true, false))
        assertEquals("Radio", m.subtitle)
        assertTrue(m.track); assertFalse(m.position); assertFalse(m.canPrevious); assertFalse(m.canNext); assertTrue(m.canPlayPause)
    }

    @Test fun theMenuWithNothingPlaying() {
        val m = menu(phase = Phase.NOTHING)
        assertEquals("Nothing playing", m.subtitle)
        assertFalse(m.track); assertNull(m.line); assertFalse(m.position)
        // Play stays: it sends the media key. There is nothing to skip.
        assertFalse(m.playing); assertTrue(m.canPlayPause); assertFalse(m.canPrevious); assertFalse(m.canNext)
        assertFalse(m.player); assertFalse(m.consent)
    }

    @Test fun theMenuWhileThePlayersAreNotListedYet() {
        val m = menu(access = false, starting = true, granted = true, phase = Phase.NOTHING)
        assertEquals("Starting…", m.subtitle)
        assertFalse(m.track); assertFalse(m.position); assertTrue(m.canPlayPause); assertFalse(m.canPrevious); assertFalse(m.canNext)
        assertFalse(m.player); assertFalse(m.consent)
    }

    @Test fun theMenuWithoutAccess() {
        val playing = menu(access = false, phase = Phase.PLAYING)
        assertEquals("Playing", playing.subtitle)
        assertFalse(playing.track); assertFalse(playing.position); assertFalse(playing.player)
        // The three buttons are media keys, which need no access: all there, pause while something plays.
        assertTrue(playing.playing); assertTrue(playing.canPrevious); assertTrue(playing.canPlayPause); assertTrue(playing.canNext)
        assertTrue(playing.consent)
        val paused = menu(access = false, phase = Phase.PAUSED)
        assertEquals("Paused", paused.subtitle)
        assertFalse(paused.playing); assertTrue(paused.canPrevious); assertTrue(paused.canNext); assertTrue(paused.consent)
        val nothing = menu(access = false, phase = Phase.NOTHING)
        assertEquals("Nothing playing", nothing.subtitle)
        assertFalse(nothing.playing); assertTrue(nothing.canPrevious); assertTrue(nothing.canPlayPause); assertTrue(nothing.canNext); assertTrue(nothing.consent)
    }

    @Test fun accessThatIsOnIsNotAskedForAgain() {
        // Turned on in Android, and the device still lists no players: the keys, and no words asking for what was given.
        val m = menu(access = false, granted = true, phase = Phase.PLAYING)
        assertEquals("Playing", m.subtitle)
        assertTrue(m.canPrevious); assertTrue(m.canPlayPause); assertTrue(m.canNext)
        assertFalse(m.consent)
    }

    @Test fun soundWithoutAPlayerIsPlayingAndNoMore() {
        // The players are listed and none is there, but something is audible: all that is known is that it plays.
        val m = menu(phase = Phase.PLAYING)
        assertEquals("Playing", m.subtitle)
        assertTrue(m.playing); assertTrue(m.canPrevious); assertTrue(m.canNext); assertFalse(m.track); assertFalse(m.player); assertFalse(m.consent)
        assertEquals("Paused", menu(phase = Phase.PAUSED).subtitle)
    }

    @Test fun whatThePlayerDoesNotOfferIsDimmed() {
        val m = menu(track = blue, controls = Controls(true, 337_000, canPrevious = false, canPlayPause = false, canNext = true))
        assertFalse(m.canPrevious); assertFalse(m.canPlayPause); assertTrue(m.canNext)
    }

    // ---- the menu's other words

    @Test fun aTracksTimesAreMinutesAndSeconds() {
        assertEquals("1:42", MediaText.time(102_000))
        assertEquals("5:37", MediaText.time(337_000))
        assertEquals("1:02:03", MediaText.time(3_723_000))
        assertEquals("0:00", MediaText.time(0))
        assertEquals("0:00", MediaText.time(999))
        assertEquals("0:01", MediaText.time(1_000))
        assertEquals("0:00", MediaText.time(-5_000))
        assertEquals("1:42 of 5:37", MediaText.positionState(102_000, 337_000, words))
    }

    @Test fun thePositionIsAShareOfTheLength() {
        assertEquals(0f, MediaText.fraction(0, 337_000), 0f)
        assertEquals(0.5f, MediaText.fraction(100_000, 200_000), 0.0001f)
        assertEquals(1f, MediaText.fraction(400_000, 337_000), 0f)
        assertEquals(0f, MediaText.fraction(-1, 337_000), 0f)
        assertEquals(0f, MediaText.fraction(5_000, 0), 0f)
    }

    @Test fun aTrackIsReadAsOneSentence() {
        assertEquals("Blue in Green by Miles Davis, Kind of Blue", MediaText.spoken(blue, words))
        assertEquals("Blue in Green by Miles Davis", MediaText.spoken(Track("Blue in Green", "Miles Davis", "Spotify"), words))
        assertEquals("Blue in Green, Kind of Blue", MediaText.spoken(Track("Blue in Green", "", "Spotify", "Kind of Blue"), words))
        assertEquals("Blue in Green", MediaText.spoken(Track("Blue in Green", "", "Spotify"), words))
        assertEquals("Miles Davis, Kind of Blue", MediaText.spoken(Track("", "Miles Davis", "Spotify", "Kind of Blue"), words))
        assertEquals("", MediaText.spoken(video, words))
    }

    @Test fun otherPlayersAndTheWayToThePlayer() {
        val lofi = Track("Lo-fi beats to study to", "", "Chrome")
        assertEquals("Lo-fi beats to study to", MediaText.other(lofi))
        assertEquals("Chrome", MediaText.other(video))
        assertEquals("Pause Chrome", MediaText.otherButton(video, playing = true, words))
        assertEquals("Play Chrome", MediaText.otherButton(lofi, playing = false, words))
        assertEquals("Open Spotify", MediaText.open(blue, words))
    }

    // ---- the samples QA stages

    private fun sample(vararg name: String) = MediaSamples.of(name.toList())!!
    private fun first(s: MediaSamples.Sample) = s.players.firstOrNull()?.let { Track(it.title, it.artist, it.app, it.album) }
    private fun phase(s: MediaSamples.Sample) = when {
        s.players.firstOrNull()?.playing ?: s.audible -> Phase.PLAYING
        s.paused -> Phase.PAUSED
        else -> Phase.NOTHING
    }
    private fun bar(s: MediaSamples.Sample, show: Show = Show.TITLE, max: Int = 20) = bar(phase(s), first(s), show, max)

    @Test fun theSamplesHaveTheNamesTheHooksAreCalledBy() {
        for (name in listOf("playing", "paused", "none", "notitle", "two", "live", "noaccess", "starting")) {
            assertTrue("no sample called $name", MediaSamples.of(listOf(name)) != null)
        }
        assertNull(MediaSamples.of(listOf("nonsense")))
        assertNull(MediaSamples.of(emptyList()))
        assertNull(MediaSamples.of(listOf("playing", "nonsense")))
    }

    @Test fun aStagedSampleReadsAsTheTablesSay() {
        bar(sample("playing")).let { assertEquals(Glyph.NOTE, it.glyph); assertEquals("Blue in Green", it.text); assertEquals("Playing: Blue in Green by Miles Davis", it.desc) }
        bar(sample("paused")).let { assertEquals(Glyph.PAUSE, it.glyph); assertEquals("Blue in Green", it.text); assertEquals("Paused: Blue in Green", it.desc) }
        bar(sample("none")).let { assertEquals(Glyph.OFF, it.glyph); assertNull(it.text); assertEquals("Nothing playing", it.desc) }
        bar(sample("notitle")).let { assertEquals(Glyph.NOTE, it.glyph); assertEquals("Chrome", it.text); assertEquals("Chrome is playing", it.desc) }
        bar(sample("two"), Show.BOTH).let { assertEquals("Clocks · Coldplay", it.text) }
        bar(sample("long")).let { assertEquals("Sinfonia Concertant…", it.text) }
        bar(sample("long"), max = 8).let { assertEquals("Sinfoni…", it.text) }
        bar(sample("noaccess")).let { assertEquals(Glyph.NOTE, it.glyph); assertNull(it.text); assertEquals("Something is playing", it.desc) }
        bar(sample("noaccess", "paused")).let { assertEquals(Glyph.PAUSE, it.glyph); assertNull(it.text); assertEquals("Playback paused", it.desc) }
        bar(sample("noaccess", "none")).let { assertEquals(Glyph.OFF, it.glyph); assertNull(it.text) }
        bar(sample("starting")).let { assertNull(it.text) }
    }

    @Test fun theSamplesCoverTheMenusStates() {
        assertTrue(sample("playing").let { it.access && it.players.single().let { p -> p.playing && p.canSeek && p.durationMs == 337_000L && p.positionMs == 102_000L } })
        assertFalse(sample("paused").players.single().playing)
        assertTrue(sample("none").let { it.access && it.players.isEmpty() && !it.audible })
        assertTrue(sample("two").players.let { it.size == 2 && it[0].playing && !it[1].playing && it[1].title == "Lo-fi beats to study to" && it[1].app == "Chrome" })
        assertTrue(sample("live").players.single().let { it.durationMs == 0L && !it.canPrevious && !it.canNext && !it.canSeek })
        assertTrue(sample("notitle").players.single().let { it.title.isEmpty() && it.artist.isEmpty() && it.album.isEmpty() && !it.canSeek && it.durationMs > 0 })
        assertTrue(sample("noaccess").let { !it.access && !it.granted && it.players.isEmpty() && it.audible })
        assertTrue(sample("starting").let { !it.access && it.starting && it.granted && it.players.isEmpty() })
        // Titles that try the rules: wide letters, pictures and line breaks, right to left.
        for (name in listOf("wide", "emoji", "rtl")) {
            val shown = bar(sample(name)).text!!
            assertTrue(name, MediaText.count(shown) <= 20)
            assertFalse(name, shown.any { it == '\n' })
        }
    }

    // ---- the text file, and what the item's own code may not do

    private fun strings(path: String): Map<String, String> {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path)).documentElement
        val list = root.getElementsByTagName("string")
        return (0 until list.length).map { list.item(it) as Element }.associate { it.getAttribute("name") to unescaped(it.textContent) }
    }

    /** A string as Android reads it from the file: `\'` is an apostrophe, ` ` a character. */
    private fun unescaped(text: String): String = Regex("""\\u([0-9A-Fa-f]{4})""").replace(text) { it.groupValues[1].toInt(16).toChar().toString() }
        .replace("\\'", "'").replace("\\\"", "\"")

    @Test fun theTextFileSaysWhatTheCopyDeckSays() {
        val file = strings("src/main/res/values/strings_media.xml")
        for ((name, text) in deck + added) assertEquals(name, text, file[name])
        assertEquals("a string the copy deck does not know", (deck + added).keys, file.keys)
        val all = strings("src/main/res/values/strings.xml")
        for ((name, text) in shared.values) assertEquals(name, text, all[name])
    }

    @Test fun britishEnglishSpellsGreyedWithAnE() {
        val british = strings("src/main/res/values-en-rGB/strings_media.xml")
        assertEquals(mapOf("media_access_restricted" to "Switch greyed out? Tap it, tap OK, then turn on Allow restricted settings in App info."), british)
    }

    private val items = File("src/main/java/io/github/kuscher/bentobar/items")
    private fun code(name: String) = File(items, name).readText().replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "").lines()
        .joinToString("\n") { it.substringBefore("//") }

    @Test fun everyWordHasItsOwnStringInTheItem() {
        // The item hands the rules their words by name: "DESC_PAUSED" is media_desc_paused, and so on.
        val item = code("MediaItem.kt")
        for (word in Word.entries) {
            val resource = shared[word]?.first ?: ("media_" + word.name.lowercase())
            assertTrue("$word should be R.string.$resource", Regex("""Word\.${word.name}\s*->\s*R\.string\.$resource\b""").containsMatchIn(item))
        }
    }

    @Test fun nowPlayingsOwnCodeWritesNoLogLinesAndKeepsNothing() {
        // A title, an artist and artwork pass through these files. None of them names a log, a file, a preference or the layout's store.
        val never = listOf("Log.", "println(", "printStackTrace", "System.out", "System.err", "File(", "openFileOutput", "SharedPreferences", "Kept.",
            "Store.update", "Env.copy", "rememberSaveable")
        for (name in listOf("MediaItem.kt", "MediaMenu.kt", "MediaText.kt", "MediaSamples.kt")) {
            val text = code(name)
            for (n in never) assertFalse("items/$name has $n", n in text)
        }
    }

    @Test fun theRulesNeedNoAndroid() {
        for (name in listOf("MediaText.kt", "MediaSamples.kt")) assertFalse("items/$name imports Android", Regex("""import\s+android""").containsMatchIn(code(name)))
    }
}
