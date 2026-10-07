package io.github.kuscher.bentobar.items

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The Stocks item's words for its tests, read from the text files themselves (`strings_stocks.xml`
 * and the shared `strings.xml`): what a test compares is then the text the app shows.
 */
class StocksFileWords : StocksWords {
    private val files = StringFiles.read(listOf("strings.xml", "strings_stocks.xml"))
    val strings = files.strings
    val plurals = files.plurals

    override fun say(word: SW, vararg args: Any): String = String.format(Locale.US, strings.getValue(word.res), *args)

    companion object {
        /** Times as a test writes them: US English, 12 hours, in [here]. */
        fun times(here: ZoneId): StocksTimes {
            fun format(pattern: String): (Long, ZoneId) -> String = { ms, zone ->
                DateTimeFormatter.ofPattern(pattern, Locale.US).format(Instant.ofEpochMilli(ms).atZone(zone))
            }
            return StocksTimes(here, clock = format("h:mm a"), dayClock = format("EEE h:mm a"))
        }
    }
}
