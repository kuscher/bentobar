package io.github.kuscher.bentobar.util

import android.icu.text.SimpleDateFormat
import android.icu.util.TimeZone
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date
import java.util.Locale

/**
 * Dates and times laid out the way the current locale writes them. Callers name the fields (a
 * skeleton such as "EEEEdMMMM" or "Hm") and [android.text.format.DateFormat.getBestDateTimePattern]
 * picks their order and punctuation: "Wednesday, September 30" in the US, "Wednesday 30 September"
 * in the UK. Formatting is done by ICU, which understands every pattern letter that call can return.
 * Formatters are cached per locale, skeleton and zone, since the clock asks once a second.
 */
object Dates {
    private val cache = HashMap<String, SimpleDateFormat>()

    fun format(skeleton: String, millis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val locale = Locale.getDefault()
        val key = "${locale.toLanguageTag()}|$skeleton|${zone.id}"
        synchronized(cache) {
            val f = cache.getOrPut(key) {
                SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton), locale)
                    .apply { timeZone = TimeZone.getTimeZone(zone.id) }
            }
            return f.format(Date(millis))
        }
    }

    fun format(skeleton: String, t: ZonedDateTime): String = format(skeleton, t.toInstant().toEpochMilli(), t.zone)

    fun format(skeleton: String, d: LocalDate): String = format(skeleton, d.atStartOfDay(ZoneId.systemDefault()))

    /** Hours and minutes (and seconds), 24-hour or 12-hour as asked, in the locale's own layout. */
    fun timeSkeleton(h24: Boolean, seconds: Boolean = false): String =
        (if (h24) "H" else "h") + "m" + (if (seconds) "s" else "")
}
