package io.github.kuscher.bentobar.items

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The Weather item's words for its tests, read from the text files themselves (`strings_weather.xml`
 * and the shared `strings.xml`): what a test compares to the specs' examples is then the text the
 * app shows, not a copy of it. [folder] is `values` (US English) or a British one.
 */
class WeatherFileWords(folder: String = "values") : WeatherWords {
    private val files = StringFiles.read(listOf("strings.xml", "strings_weather.xml"), folder)
    val strings = files.strings
    val plurals = files.plurals

    override fun say(word: W, vararg args: Any): String = String.format(Locale.US, strings.getValue(word.res), *args)

    override fun count(word: W, quantity: Int, vararg args: Any): String =
        String.format(Locale.US, plurals.getValue(word.res).getValue(if (quantity == 1) "one" else "other"), *args)

    /** A string by its resource name, formatted: for the words a test checks that no rule says ("Type at least two letters."). */
    fun text(name: String, vararg args: Any): String = String.format(Locale.US, strings.getValue(name), *args)

    companion object {
        /** Times as a test writes them: US English, 12 or 24 hours, with plain spaces. */
        fun times(here: ZoneId, h24: Boolean = false): Times {
            fun format(pattern: String): (Long, ZoneId) -> String = { ms, zone ->
                DateTimeFormatter.ofPattern(pattern, Locale.US).format(Instant.ofEpochMilli(ms).atZone(zone))
            }
            return Times(here, hour = format(if (h24) "HH:mm" else "h a"), clock = format(if (h24) "HH:mm" else "h:mm a"),
                weekday = format("EEE"), weekdayLong = format("EEEE"))
        }
    }
}
