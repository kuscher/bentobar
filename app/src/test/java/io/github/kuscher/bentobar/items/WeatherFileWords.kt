package io.github.kuscher.bentobar.items

import org.w3c.dom.Element
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The Weather item's words for its tests, read from the text files themselves (`strings_weather.xml`
 * and the shared `strings.xml`): what a test compares to the specs' examples is then the text the
 * app shows, not a copy of it. [folder] is `values` (US English) or a British one.
 */
class WeatherFileWords(folder: String = "values") : WeatherWords {
    val strings = HashMap<String, String>()
    val plurals = HashMap<String, Map<String, String>>()

    init {
        // The default files first, then the folder's own on top, as Android resolves them.
        for (dir in listOf("values", folder).distinct()) for (file in listOf("strings.xml", "strings_weather.xml")) {
            val f = File("src/main/res/$dir/$file")
            if (!f.isFile) continue
            val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f).documentElement
            val nodes = root.childNodes
            for (i in 0 until nodes.length) {
                val e = nodes.item(i) as? Element ?: continue
                when (e.tagName) {
                    "string" -> strings[e.getAttribute("name")] = unescape(e.textContent)
                    "plurals" -> {
                        val items = e.getElementsByTagName("item")
                        plurals[e.getAttribute("name")] = (0 until items.length).map { items.item(it) as Element }
                            .associate { it.getAttribute("quantity") to unescape(it.textContent) }
                    }
                }
            }
        }
    }

    override fun say(word: W, vararg args: Any): String = String.format(Locale.US, strings.getValue(word.res), *args)

    override fun count(word: W, quantity: Int, vararg args: Any): String =
        String.format(Locale.US, plurals.getValue(word.res).getValue(if (quantity == 1) "one" else "other"), *args)

    /** A string by its resource name, formatted: for the words a test checks that no rule says ("Type at least two letters."). */
    fun text(name: String, vararg args: Any): String = String.format(Locale.US, strings.getValue(name), *args)

    companion object {
        /** The names of the strings and plurals one file defines, in its order. */
        fun names(path: String): List<String> {
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path)).documentElement.childNodes
            return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }.map { it.getAttribute("name") }
        }

        /** What Android makes of a resource's text: `\'` is an apostrophe, `\u00A0` a character, `\n` a new line. */
        fun unescape(raw: String): String {
            val out = StringBuilder()
            var i = 0
            while (i < raw.length) {
                val c = raw[i]
                if (c != '\\' || i + 1 >= raw.length) { out.append(c); i++; continue }
                when (val next = raw[i + 1]) {
                    'n' -> { out.append('\n'); i += 2 }
                    't' -> { out.append('\t'); i += 2 }
                    'u' -> { out.append(raw.substring(i + 2, i + 6).toInt(16).toChar()); i += 6 }
                    else -> { out.append(next); i += 2 }
                }
            }
            return out.toString()
        }

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
