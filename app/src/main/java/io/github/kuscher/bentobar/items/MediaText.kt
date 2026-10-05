package io.github.kuscher.bentobar.items

import io.github.kuscher.bentobar.util.Fmt
import java.util.Locale

/**
 * What Now playing says: the text in the bar, what is spoken, the tooltip, and what its menu shows
 * in each state. Pure Kotlin, unit-tested (`MediaTextTest`): the words come from outside ([Words]),
 * the moment is a number, and a player is a few texts ([Track]).
 *
 * A title comes from another app. It is never trusted to be short, one line or well formed:
 * [Track] makes it fit to show, and [cut] holds it to the user's "Longest title" without ever
 * leaving half of what reads as one character. Nothing here keeps a title, and no class here prints
 * one (none is a data class).
 */
object MediaText {
    /** "Longest title": how many characters the bar's text may have, and how many while the option is not set. */
    val CHARS = 8..40
    const val DEFAULT_CHARS = 20
    /** The rule's minutes: how long the item stays after a pause. */
    val LINGER = 1..10
    const val DEFAULT_LINGER = 2

    private const val ELLIPSIS = "…"
    private const val JOINER = 0x200D

    /** The options' names in a layout, and the two things a click can do. Saved layouts and the test hooks use them: never rename one. */
    const val OPTION_SHOW = "show"
    const val OPTION_CHARS = "maxChars"
    const val OPTION_CLICK = "click"
    const val OPTION_LINGER = "lingerMin"
    const val CLICK_MENU = "menu"
    const val CLICK_TOGGLE = "toggle"

    /** The Show option: what of a track the bar names. [id] is what the layout stores. */
    enum class Show(val id: String) {
        TITLE("title"), BOTH("both"), ARTIST("artist");

        companion object {
            /** Title, unless the layout says otherwise in a way that is understood. */
            fun of(id: String?): Show = entries.firstOrNull { it.id == id } ?: TITLE
        }
    }

    // ---- an item's options, read from its layout (which may have been pasted in, and can say anything)

    fun show(options: Map<String, String>): Show = Show.of(options[OPTION_SHOW])

    /** "Longest title", in characters, within the slider's range. */
    fun chars(options: Map<String, String>): Int = (options[OPTION_CHARS]?.toIntOrNull() ?: DEFAULT_CHARS).coerceIn(CHARS)

    /** "A click: Plays or pauses", instead of opening the menu. */
    fun toggles(options: Map<String, String>): Boolean = options[OPTION_CLICK] == CLICK_TOGGLE

    /** The words the rules use. The names are the copy deck's without their `media_` (two are older, shared words). */
    enum class Word {
        NOTHING, PLAYING, PAUSED, STARTING, PLAYER_PAUSED, APP_PLAYING, APP_PAUSED, BAR_BOTH, TRACK_BY, TRACK_ALBUM, POSITION_STATE,
        OTHER_PAUSE, OTHER_PLAY, OPEN_PLAYER, DESC_PLAYING_BY, DESC_PLAYING, DESC_PAUSED, DESC_PLAYING_UNKNOWN, DESC_PAUSED_UNKNOWN,
    }

    /** Where the words come from: the app's strings in the item, the copy deck in the test. A word is looked up when it is used. */
    class Words(private val text: (Word) -> String) {
        operator fun get(word: Word): String = text(word)

        /** [word] with its `%1$s` and `%2$s` filled in, as Android fills a string's arguments. */
        fun fill(word: Word, vararg args: String): String = String.format(Locale.ROOT, text(word), *args)
    }

    /** A player, as far as words go. Whatever it is given, each text is one line that can be shown ([line]); empty when unknown. */
    class Track(title: String?, artist: String?, app: String?, album: String? = null) {
        val title = line(title)
        val artist = line(artist)
        val album = line(album)
        /** The player's name: "Spotify". */
        val app = line(app)
        /** It says nothing about what it plays (a video in a browser). */
        val bare: Boolean get() = title.isEmpty() && artist.isEmpty() && album.isEmpty()
    }

    // ---- a title made fit to show

    /**
     * [text] as one line ([NowPlayingRules.oneLine]), without two things a player can leave in a
     * title: half an emoji (it cut its own title by the string's length) and the marks that
     * override the direction of whatever follows (one that is never closed would turn the artist
     * beside the title around). Letters keep their own direction, right-to-left ones too.
     */
    fun line(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            // A code point in the surrogates' own range is one half without the other.
            if (cp in 0xD800..0xDFFF || cp in 0x202A..0x202E || cp in 0x2066..0x2069) continue
            out.appendCodePoint(cp)
        }
        return NowPlayingRules.oneLine(out)
    }

    /** How many characters [text] has, as "Longest title" counts them: an emoji is one, not the two halves a string holds. */
    fun count(text: String): Int = text.codePointCount(0, text.length)

    /**
     * [text] in at most [max] characters: as it is if it has no more, else its first [max] − 1,
     * without spaces at the end, and an ellipsis (the rule Next meeting uses). The cut never falls
     * inside what reads as one character ([whole]), so it can come out shorter; a first character
     * that alone is too long leaves the ellipsis.
     */
    fun cut(text: String, max: Int): String {
        val n = max.coerceAtLeast(1)
        if (count(text) <= n) return text
        val points = text.codePoints().toArray()
        var keep = n - 1
        while (keep > 0 && !whole(points, keep)) keep--
        return String(points, 0, keep).trimEnd(' ') + ELLIPSIS
    }

    /**
     * Whether [points] can be cut before index [at] without parting what reads as one character:
     * not before a mark that sits on the letter before it, a selector, a skin tone or a joiner, not
     * after a joiner, not inside a Korean syllable written in its parts, and not between the two
     * letters of a flag. The rules are spelled out here, rather than asked of the platform, so that
     * the test and every Android version cut alike.
     */
    private fun whole(points: IntArray, at: Int): Boolean {
        val before = points[at - 1]
        val after = points[at]
        if (attaches(after) || before == JOINER || before in 0x1100..0x115F || before in 0xA960..0xA97F) return false
        if (flagLetter(before) && flagLetter(after)) {
            // A flag is two such letters: this is between two flags only if a whole number of flags came before.
            var run = 0
            var i = at - 1
            while (i >= 0 && flagLetter(points[i])) { run++; i-- }
            return run % 2 == 0
        }
        return true
    }

    /** [cp] belongs to the character before it. */
    private fun attaches(cp: Int): Boolean = when (Character.getType(cp)) {
        Character.NON_SPACING_MARK.toInt(), Character.ENCLOSING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt() -> true
        else -> cp == JOINER || cp == 0x200C ||             // the joiner and its opposite (Persian, Indic scripts)
            cp in 0xFE00..0xFE0F || cp in 0xE0100..0xE01EF || // selectors: "as a picture", "as text", a variant
            cp in 0x1F3FB..0x1F3FF ||                         // skin tones
            cp in 0xE0020..0xE007F ||                         // the letters of a flag spelled out (England, Scotland, Wales)
            cp in 0x1160..0x11FF || cp in 0xD7B0..0xD7FF      // the vowel and the last sound of a Korean syllable in parts
    }

    /** One of the two letters a country's flag is written with. */
    private fun flagLetter(cp: Int) = cp in 0x1F1E6..0x1F1FF

    // ---- the bar

    /** What the note stands for. NOTHING is a glyph of its own: at the bar's size the note looks the same filled and outlined. */
    enum class Glyph { NOTE, PAUSE, OFF }

    /** PLAYING, PAUSED for the rule's minutes after it, then NOTHING. */
    enum class Phase { PLAYING, PAUSED, NOTHING }

    /**
     * Where playback stands at [now]: [playedAt] is the last moment something was seen playing, on the
     * same clock (0: never), and the item stays for [lingerMin] minutes after it, so that a pause for a
     * call doesn't lose it. A clock that was set back behind [playedAt] ends the stay rather than
     * stretching it.
     */
    fun phase(playing: Boolean, playedAt: Long, now: Long, lingerMin: Int): Phase = when {
        playing -> Phase.PLAYING
        playedAt > 0 && now - playedAt in 0 until lingerMin.coerceIn(LINGER) * 60_000L -> Phase.PAUSED
        else -> Phase.NOTHING
    }

    /**
     * Whether the first player is the one the bar's words are about: always while it plays; paused, only
     * if it is the app that was playing ([playedBy], its package). When that one has gone, a player
     * that paused long ago is first in the list, and "paused" is not about it.
     */
    fun follows(phase: Phase, pkg: String, playedBy: String?): Boolean = phase == Phase.PLAYING || (phase == Phase.PAUSED && pkg == playedBy)

    /** What one Now playing item shows in the bar. */
    class Bar(val glyph: Glyph, val text: String?, val desc: String, val tooltip: String?, val active: Boolean)

    /**
     * The item in the bar. [track] is the player the bar follows, or null when its words aren't known
     * (no notification access, or the players aren't listed yet): then there is the glyph and no text.
     * Paused, it shows what it showed while playing.
     */
    fun bar(phase: Phase, track: Track?, show: Show, max: Int, words: Words): Bar {
        if (phase == Phase.NOTHING) return Bar(Glyph.OFF, null, words[Word.NOTHING], null, active = false)
        val playing = phase == Phase.PLAYING
        return Bar(if (playing) Glyph.NOTE else Glyph.PAUSE, track?.let { text(it, show, max, words) }, said(playing, track, words),
            track?.let { tooltip(it, words) }, active = true)
    }

    /**
     * The bar's text for [track], held to [max] characters ("Longest title", brought into its range):
     * the title, title and artist, or the artist; where the player gives none of what is asked for,
     * its name. Null: it has not even that.
     */
    fun text(track: Track, show: Show, max: Int, words: Words): String? {
        val n = max.coerceIn(CHARS)
        val what = when (show) {
            Show.TITLE -> track.title
            // Both only where both fit. The artist is the part that goes, and whole: a cut artist ("Miles Da…") says nothing.
            Show.BOTH -> if (track.title.isEmpty() || track.artist.isEmpty()) track.title
                else words.fill(Word.BAR_BOTH, track.title, track.artist).takeIf { count(it) <= n } ?: track.title
            Show.ARTIST -> track.artist.ifEmpty { track.title }
        }.ifEmpty { track.app }
        return cut(what, n).ifEmpty { null }
    }

    /** What a screen reader says for the item, and for a player without a title in the menu: never cut. */
    private fun said(playing: Boolean, track: Track?, words: Words): String = when {
        // That something plays is all that is known, or the player has not even a name.
        track == null || (track.title.isEmpty() && track.app.isEmpty()) -> words[if (playing) Word.DESC_PLAYING_UNKNOWN else Word.DESC_PAUSED_UNKNOWN]
        track.title.isEmpty() -> words.fill(if (playing) Word.APP_PLAYING else Word.APP_PAUSED, track.app)
        !playing -> words.fill(Word.DESC_PAUSED, track.title)
        track.artist.isEmpty() -> words.fill(Word.DESC_PLAYING, track.title)
        else -> words.fill(Word.DESC_PLAYING_BY, track.title, track.artist)
    }

    /** The whole title and artist, for the tooltip (the strip cuts it at its own width). Null: the item's name will do. */
    private fun tooltip(track: Track, words: Words): String? = when {
        track.title.isEmpty() -> null
        track.artist.isEmpty() -> track.title
        else -> words.fill(Word.BAR_BOTH, track.title, track.artist)
    }

    // ---- the menu

    /** What the menu needs of the first player besides its words. */
    class Controls(val playing: Boolean, val durationMs: Long, val canPrevious: Boolean, val canPlayPause: Boolean, val canNext: Boolean)

    /**
     * What the menu shows, a row of the design's table each: [track] the artwork and the lines, or
     * [line] in their place for a player that says nothing about what it plays; [position] the bar
     * with the two times; [player] the other players and "Open …"; [consent] the words about
     * notification access and the way to it. What a button can't do is dimmed, never hidden.
     */
    class Menu(val subtitle: String, val track: Boolean, val line: String?, val position: Boolean, val playing: Boolean,
               val canPrevious: Boolean, val canPlayPause: Boolean, val canNext: Boolean, val player: Boolean, val consent: Boolean)

    /**
     * The menu's state. [access]: the players are listed; [starting]: they are about to be;
     * [granted]: notification access is on in Android, which can be so without [access] on a device
     * that lists no players all the same (then nothing asks for it again). [track] and [controls]
     * are the first player's; [phase] says what is known without one.
     */
    fun menu(access: Boolean, starting: Boolean, granted: Boolean, phase: Phase, track: Track?, controls: Controls?, words: Words): Menu {
        if (access && track != null && controls != null) return Menu(
            subtitle = when {
                track.app.isEmpty() -> words[if (controls.playing) Word.PLAYING else Word.PAUSED]
                // Without a title the line below says whether it plays or is paused.
                controls.playing || track.bare -> track.app
                else -> words.fill(Word.PLAYER_PAUSED, track.app)
            },
            track = !track.bare, line = if (track.bare) said(controls.playing, track, words) else null,
            // A stream has no length, so there is no position to show.
            position = controls.durationMs > 0, playing = controls.playing,
            canPrevious = controls.canPrevious, canPlayPause = controls.canPlayPause, canNext = controls.canNext, player = true, consent = false)
        val playing = phase == Phase.PLAYING
        // The buttons are media keys here, which Android gives to whichever player is in front. Without the list of
        // players BentoBar can't know that there is none to skip in, so all three are offered. With the list, and
        // nobody on it, there is nothing to skip unless something is audible all the same; Play stays, for the key.
        val keys = (!access && !starting) || playing
        return Menu(
            subtitle = words[when { starting -> Word.STARTING; playing -> Word.PLAYING; phase == Phase.PAUSED -> Word.PAUSED; else -> Word.NOTHING }],
            track = false, line = null, position = false, playing = playing, canPrevious = keys, canPlayPause = true, canNext = keys,
            player = false, consent = !access && !starting && !granted)
    }

    /** A track as one spoken sentence: "Blue in Green by Miles Davis, Kind of Blue", of the parts there are. */
    fun spoken(track: Track, words: Words): String {
        val who = when {
            track.title.isEmpty() -> track.artist
            track.artist.isEmpty() -> track.title
            else -> words.fill(Word.TRACK_BY, track.title, track.artist)
        }
        return when {
            who.isEmpty() -> track.album
            track.album.isEmpty() -> who
            else -> words.fill(Word.TRACK_ALBUM, who, track.album)
        }
    }

    /** A time in a track, elapsed or its whole length: "1:42", "1:02:03". */
    fun time(ms: Long): String = Fmt.clock(ms.coerceAtLeast(0), elapsed = true)

    /** "1:42 of 5:37", for a screen reader. */
    fun positionState(positionMs: Long, durationMs: Long, words: Words): String = words.fill(Word.POSITION_STATE, time(positionMs), time(durationMs))

    /** How far a track of [durationMs] is at [positionMs], 0 to 1; 0 where the length isn't known. */
    fun fraction(positionMs: Long, durationMs: Long): Float =
        if (durationMs <= 0) 0f else (positionMs.toDouble() / durationMs).coerceIn(0.0, 1.0).toFloat()

    /** An other player's row: its title, or its name when it gives none. */
    fun other(track: Track): String = track.title.ifEmpty { track.app }

    /** What an other player's button does: "Pause Chrome", "Play Chrome". */
    fun otherButton(track: Track, playing: Boolean, words: Words): String = words.fill(if (playing) Word.OTHER_PAUSE else Word.OTHER_PLAY, track.app)

    /** "Open Spotify". */
    fun open(track: Track, words: Words): String = words.fill(Word.OPEN_PLAYER, track.app)
}
