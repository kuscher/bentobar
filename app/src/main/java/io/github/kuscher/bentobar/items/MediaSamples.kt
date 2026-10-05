package io.github.kuscher.bentobar.items

/**
 * What a test on a device stages in place of real players (`./bento debug media stage <name>`), so
 * that every state of the bar and of the menu can be seen without one. The tracks are the examples
 * of the product's tables, so a staged state reads as written there; `MediaTextTest` checks that.
 * Pure data: the item turns a sample into what the source would publish. Only the adb hook of a
 * debug build asks for one.
 */
object MediaSamples {
    /** One made-up player. [art]: it has a picture (a plain square stands in for a cover). */
    data class Player(val app: String, val pkg: String, val title: String = "", val artist: String = "", val album: String = "",
                      val playing: Boolean = true, val durationMs: Long = 0, val positionMs: Long = 0, val canPrevious: Boolean = true,
                      val canNext: Boolean = true, val canSeek: Boolean = false, val art: Boolean = false)

    /**
     * One staged moment. [access]: the players are listed; [starting]: they are about to be;
     * [granted]: notification access counts as on. [paused]: playback stopped this moment, so the
     * item stays for the rule's minutes (move the clock with `now +2m` to see it leave).
     */
    class Sample(val players: List<Player> = emptyList(), val access: Boolean = true, val starting: Boolean = false, val paused: Boolean = false,
                 val audible: Boolean = players.any { it.playing }, val granted: Boolean = access || starting)

    private const val MUSIC = "com.spotify.music"
    private const val BROWSER = "com.android.chrome"

    private val blue = Player("Spotify", MUSIC, "Blue in Green", "Miles Davis", "Kind of Blue", durationMs = 337_000, positionMs = 102_000, canSeek = true, art = true)

    /** A title that tries the rules, playing in a player with every control. */
    private fun trying(title: String, artist: String) = Sample(listOf(Player("Spotify", MUSIC, title, artist, durationMs = 245_000, positionMs = 30_000, canSeek = true)))

    /** The sample called by [args] (the words after `stage`), or null for a name that isn't one. */
    fun of(args: List<String>): Sample? = when (args.joinToString(" ")) {
        "playing" -> Sample(listOf(blue))
        "paused" -> Sample(listOf(blue.copy(playing = false)), paused = true)
        "none" -> Sample()
        // A video in a browser: no title, no skipping, a length but no seeking (the position is a plain meter).
        "notitle" -> Sample(listOf(Player("Chrome", BROWSER, durationMs = 720_000, positionMs = 185_000, canPrevious = false, canNext = false)))
        "two" -> Sample(listOf(
            Player("Spotify", MUSIC, "Clocks", "Coldplay", "A Rush of Blood to the Head", durationMs = 307_000, positionMs = 61_000, canSeek = true, art = true),
            Player("Chrome", BROWSER, "Lo-fi beats to study to", playing = false, canPrevious = false, canNext = false)))
        "live" -> Sample(listOf(Player("Radio", "staged.radio", "Morning Show", "Radio One", canPrevious = false, canNext = false)))
        "noaccess" -> Sample(access = false, audible = true)
        "noaccess paused" -> Sample(access = false, paused = true)
        "noaccess none" -> Sample(access = false)
        "starting" -> Sample(access = false, starting = true)
        // Beyond the names the product spec lists: titles that try the length rule and the width cap.
        "long" -> trying("Sinfonia Concertante in E-flat major", "Mozart")
        "wide" -> trying("雨の日曜日に聴きたい静かなピアノ曲集 第二番 変ホ長調", "架空の楽団")
        // A sun, a beach with its selector, a flag, a family of four joined into one picture; a line break and a tab.
        "emoji" -> trying("Summer 🌞 Hits\n2026 🏖️ 🇸🇪 " +
            "👨‍👩‍👧‍👦 Road\tTrip", "Various 🎶 Artists")
        "rtl" -> trying("أغنية طويلة جدا لاختبار شريط الحالة", "فرقة الاختبار")
        else -> null
    }
}
