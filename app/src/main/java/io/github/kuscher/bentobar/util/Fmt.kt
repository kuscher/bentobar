package io.github.kuscher.bentobar.util

import java.util.Locale
import kotlin.math.abs

/** Short, width-stable number formats for a 36 dp tall bar. */
object Fmt {
    /** Bytes per second as "0K", "840K", "1.2M", "12M". */
    fun rate(bytesPerSec: Double): String = if (bytesPerSec < 1000) "0K" else compact(bytesPerSec)

    /** Bytes as "840 KB", "1.2 GB". */
    fun bytes(b: Double): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var v = b
        var i = 0
        while (v >= 1000 && i < units.lastIndex) { v /= 1000; i++ }
        return if (i == 0) "${v.toInt()} B" else "${oneDecimal(v)} ${units[i]}"
    }

    private fun compact(b: Double): String {
        val units = arrayOf("B", "K", "M", "G")
        var v = b
        var i = 0
        while (v >= 1000 && i < units.lastIndex) { v /= 1000; i++ }
        return if (i == 0) "${v.toInt()}B" else oneDecimal(v) + units[i]
    }

    /** One decimal below 10, none above: "1.2", "12", "123". */
    fun oneDecimal(v: Double): String =
        if (abs(v) < 9.95) String.format(Locale.ROOT, "%.1f", v) else String.format(Locale.ROOT, "%.0f", v)

    /** Durations: "45s", "12m", "1h 05m", "3d 4h". */
    fun duration(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val d = s / 86400
        val h = (s % 86400) / 3600
        val m = (s % 3600) / 60
        return when {
            d > 0 -> "${d}d ${h}h"
            h > 0 -> String.format(Locale.ROOT, "%dh %02dm", h, m)
            m > 0 -> "${m}m"
            else -> "${s}s"
        }
    }

    /** Timer faces: "4:05", "12:30", "1:02:03". */
    fun clock(ms: Long): String {
        val s = (ms + 999) / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec)
        else String.format(Locale.ROOT, "%d:%02d", m, sec)
    }

    fun percent(f: Double) = "${(f * 100).toInt()}%"

    /**
     * The widest text [text] can become while its numbers change, for a fixed-width slot in the
     * strip. A number, whole or with one decimal ("0", "8.4", "999"), becomes "888": the formats
     * here switch from 9.9 to 10 and stay under 1000 before the unit steps up, and with tabular
     * digits three 8s are wider than any of them. Clock digits keep their shape ("9:42" holds
     * "88:88"), and K/M/G/T after a number take the widest letter.
     */
    fun widthTemplate(text: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (!c.isDigit()) {
                // A unit right after a number ("4K", "15.9 GB"), not the start of a word ("Keep").
                val afterNumber = text.getOrNull(i - 1)?.isDigit() == true ||
                    (text.getOrNull(i - 1) == ' ' && text.getOrNull(i - 2)?.isDigit() == true)
                val unit = c in "KMGT" && afterNumber && text.getOrNull(i + 1)?.isLowerCase() != true
                out.append(if (unit) 'M' else c)
                i++
                continue
            }
            var j = i
            while (j < text.length && text[j].isDigit()) j++
            val clock = text.getOrNull(i - 1) == ':' || text.getOrNull(j) == ':'
            if (clock) {
                val len = j - i
                repeat(if (text.getOrNull(j) == ':' && text.getOrNull(i - 1) != ':') maxOf(len, 2) else len) { out.append('8') }
                i = j
                continue
            }
            // Take a decimal part with it: "8.4" is one number.
            if (j + 1 < text.length && (text[j] == '.' || text[j] == ',') && text[j + 1].isDigit()) {
                j++
                while (j < text.length && text[j].isDigit()) j++
            }
            val digits = text.substring(i, j).count { it.isDigit() }
            repeat(maxOf(digits, 3)) { out.append('8') }
            i = j
        }
        return out.toString()
    }
}
