package io.github.kuscher.bentobar.items

import io.github.kuscher.bentobar.items.FlightText.TimeForm
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The app's own words for the tests of what the Flight item says: read from its resource files as
 * text, so that a test of "LH 455 canceled" is a test of the string in `strings_flight.xml` too. Times
 * are written the way a US English device writes them (or with 24 hours).
 */
object FlightVoices {
    private val res = File("src/main/res")
    private val string = Regex("""<string\s+name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
    private val plurals = Regex("""<plurals\s+name="([^"]+)"[^>]*>(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL)
    private val item = Regex("""<item\s+quantity="([^"]+)"[^>]*>(.*?)</item>""", RegexOption.DOT_MATCHES_ALL)

    /** A resource's text as Android hands it out: the entities of XML and the backslashes of a string resource undone. */
    private fun plain(raw: String): String {
        val text = raw.trim().replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i++]
            if (c != '\\' || i >= text.length) { out.append(c); continue }
            when (val e = text[i++]) {
                'n' -> out.append('\n')
                't' -> out.append('\t')
                'u' -> { out.append(text.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                else -> out.append(e)
            }
        }
        return out.toString()
    }

    /** Every string and plural of [folders]' `strings.xml` and `strings_flight.xml`; a later folder's entry takes an earlier one's place, as a locale's does. */
    private class Words(folders: List<String>) {
        val strings = HashMap<String, String>()
        val counts = HashMap<String, Map<String, String>>()
        init {
            for (folder in folders) for (name in listOf("strings.xml", "strings_flight.xml")) {
                val text = File(res, "$folder/$name").takeIf { it.isFile }?.readText()?.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "") ?: continue
                string.findAll(text).forEach { strings[it.groupValues[1]] = plain(it.groupValues[2]) }
                plurals.findAll(text).forEach { p -> counts[p.groupValues[1]] = item.findAll(p.groupValues[2]).associate { it.groupValues[1] to plain(it.groupValues[2]) } }
            }
        }
    }

    private fun clock(h24: Boolean): (LocalDateTime, TimeForm) -> String {
        val time = if (h24) "H:mm" else "h:mm a"
        return { t, form ->
            t.format(DateTimeFormatter.ofPattern(when (form) {
                TimeForm.TIME -> time
                TimeForm.DAY -> "EEE"
                TimeForm.DAY_TIME -> "EEE $time"
                TimeForm.DATE -> "MMM d"
                TimeForm.DATE_TIME -> "MMM d, $time"
                TimeForm.DAY_DATE -> "EEE, MMM d"
            }, Locale.US))
        }
    }

    private fun voice(folders: List<String>, zone: ZoneId, h24: Boolean): FlightText.Voice {
        val words = Words(folders)
        return FlightText.Voice(Locale.US, zone,
            word = { words.strings[it.name.lowercase()] ?: error("no string ${it.name.lowercase()} in the resources") },
            plural = { c, n ->
                val forms = words.counts[c.name.lowercase()] ?: error("no plural ${c.name.lowercase()} in the resources")
                String.format(Locale.US, forms.getValue(if (n == 1) "one" else "other"), n)
            },
            clock = clock(h24))
    }

    /** US English, read on a device in [zone]. */
    fun us(zone: ZoneId = ZoneId.of("America/Los_Angeles"), h24: Boolean = false) = voice(listOf("values"), zone, h24)

    /** British English: the default's words with `values-en-rGB` over them. */
    fun british(zone: ZoneId = ZoneId.of("Europe/London")) = voice(listOf("values", "values-en-rGB"), zone, h24 = true)

    /** The text of a string that the pure code does not build (a label, a sentence without arguments), for comparing with the copy deck. */
    fun string(name: String): String = Words(listOf("values")).strings.getValue(name)
}
