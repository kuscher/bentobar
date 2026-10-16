package io.github.kuscher.bentobar.items

import io.github.kuscher.bentobar.data.ItemConfig
import io.github.kuscher.bentobar.util.Sym
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.abs

/**
 * Pure rules for the Exchange rates item: parsing Frankfurter's ECB rate table, cross-rate and
 * daily-change math for any currency pair, rotation in the bar, and inline conversion.
 * No Android imports, and no logging.
 */
object FxRules {
    const val FRESH_MS = 60 * 60_000L
    const val MENU_MS = 15 * 60_000L
    const val RETRY_MS = 15 * 60_000L
    const val BAR_CHARS = 20
    const val MOST_PAIRS = 6
    const val TURN_SEC = 5
    val TURNS = listOf(3, 5, 10, 30, 60)

    val CURRENCIES: Set<String> = setOf(
        "EUR", "USD", "GBP", "JPY", "CHF", "CAD", "AUD", "NZD", "CNY", "HKD", "SGD",
        "SEK", "NOK", "DKK", "KRW", "INR", "MXN", "BRL", "ZAR", "PLN", "CZK", "HUF",
        "TRY", "ILS", "THB", "MYR", "PHP", "IDR", "ISK", "RON", "BGN",
    )

    const val DEFAULT_PAIRS = "EUR/USD,USD/JPY,GBP/USD"

    data class FxPair(val base: String, val quote: String) {
        val code: String get() = "$base/$quote"
    }

    data class FxTable(
        /** Rates per 1 EUR on the latest date (`EUR` itself is 1.0). */
        val latest: Map<String, Double> = emptyMap(),
        /** Rates per 1 EUR on the previous business day, when available. */
        val previous: Map<String, Double> = emptyMap(),
        val date: String = "",
        val fetchedAt: Long = 0,
        val failure: Failure? = null,
    ) {
        val hasRates: Boolean get() = latest.size > 1
        override fun toString() = "FxTable(hasRates=$hasRates, failure=$failure)"
    }

    data class PairQuote(
        val pair: FxPair,
        val rate: Double,
        val changePct: Double,
        val formattedRate: String,
        val formattedPct: String,
    )

    class Bar(
        val icon: String,
        val filled: Boolean,
        val text: String?,
        val turns: List<String>?,
        val tone: Tone,
        val active: Boolean,
        val desc: String,
        val tooltip: String,
    )

    /** Parses a user-entered currency pair such as `"eur/usd"`, `"EUR USD"`, or `"EUR-USD"`. */
    fun parsePair(raw: String): FxPair? {
        val cleaned = raw.trim().uppercase(Locale.ROOT).replace('-', '/').replace(Regex("\\s+"), "/")
        val parts = cleaned.split('/').filter { it.isNotEmpty() }
        if (parts.size != 2) return null
        val (b, q) = parts
        if (b == q || b !in CURRENCIES || q !in CURRENCIES) return null
        return FxPair(b, q)
    }

    fun pairs(item: ItemConfig): List<FxPair> {
        val raw = item.options["pairs"] ?: DEFAULT_PAIRS
        if (raw.isBlank()) return emptyList()
        return raw.split(',').mapNotNull(::parsePair).distinct().take(MOST_PAIRS)
    }

    fun withPairs(item: ItemConfig, list: List<FxPair>): ItemConfig {
        val clean = list.distinct().take(MOST_PAIRS)
        return item.with("pairs", if (clean.isEmpty()) "" else clean.joinToString(",") { it.code })
    }

    fun addPair(item: ItemConfig, pair: FxPair): ItemConfig {
        val current = pairs(item)
        if (pair in current || current.size >= MOST_PAIRS) return item
        return withPairs(item, current + pair)
    }

    fun removePair(item: ItemConfig, pair: FxPair): ItemConfig = withPairs(item, pairs(item).filterNot { it == pair })

    fun movePair(item: ItemConfig, pair: FxPair, delta: Int): ItemConfig {
        val list = pairs(item).toMutableList()
        val i = list.indexOf(pair)
        val target = i + delta
        if (i < 0 || target !in list.indices) return item
        list[i] = list[target].also { list[target] = list[i] }
        return withPairs(item, list)
    }

    fun turnSec(item: ItemConfig): Int = item.optInt("turnSec", TURN_SEC).let { if (it in TURNS) it else TURN_SEC }

    /** Start date 10 calendar days before [nowMs] in UTC, so weekends/holidays still yield at least 2 business days. */
    fun startDate(nowMs: Long): String =
        Instant.ofEpochMilli(nowMs).atZone(ZoneOffset.UTC).toLocalDate().minusDays(10).toString()

    /**
     * Parses either Frankfurter's time-series reply (`/v1/2026-10-05..`, where `rates` is keyed by date)
     * or its single-day reply (`/v1/latest`, where `rates` maps currency codes to numbers).
     */
    fun read(jsonText: String, now: Long): FxTable? = try {
        val root = Json.parseToJsonElement(jsonText).jsonObject
        val base = root["base"]?.jsonPrimitive?.content ?: "EUR"
        val ratesObj = root["rates"] as? JsonObject ?: return null
        val firstVal = ratesObj.values.firstOrNull()
        if (firstVal is JsonObject) {
            val dates = ratesObj.keys.sorted()
            if (dates.isEmpty()) null else {
                val latestDate = dates.last()
                val prevDate = if (dates.size >= 2) dates[dates.size - 2] else null
                val latestMap = extractDay(base, ratesObj[latestDate]!!.jsonObject)
                val prevMap = prevDate?.let { extractDay(base, ratesObj[it]!!.jsonObject) }.orEmpty()
                if (latestMap.size <= 1) null else FxTable(latestMap, prevMap, latestDate, fetchedAt = now)
            }
        } else {
            val date = root["date"]?.jsonPrimitive?.content.orEmpty()
            val latestMap = extractDay(base, ratesObj)
            if (latestMap.size <= 1) null else FxTable(latestMap, emptyMap(), date, fetchedAt = now)
        }
    } catch (e: Exception) {
        null
    }

    private fun extractDay(base: String, obj: JsonObject): Map<String, Double> {
        val out = HashMap<String, Double>()
        out[base] = 1.0
        for ((k, v) in obj) {
            val d = v.jsonPrimitive.doubleOrNull
            if (d != null && d > 0.0 && d.isFinite()) out[k] = d
        }
        return out
    }

    fun formatRate(rate: Double): String = when {
        rate >= 100.0 -> String.format(Locale.US, "%.2f", rate)
        rate >= 10.0 -> String.format(Locale.US, "%.3f", rate)
        else -> String.format(Locale.US, "%.4f", rate)
    }

    fun formatPct(pct: Double): String = String.format(Locale.US, "%.2f", abs(pct))

    fun formatConverted(amount: Double, rate: Double): String {
        val v = amount * rate
        return if (v >= 100.0) String.format(Locale.US, "%,.2f", v) else String.format(Locale.US, "%.2f", v)
    }

    fun quoteFor(pair: FxPair, table: FxTable): PairQuote? {
        val baseRate = table.latest[pair.base] ?: return null
        val quoteRate = table.latest[pair.quote] ?: return null
        if (baseRate <= 0.0) return null
        val cross = quoteRate / baseRate
        val prevBase = table.previous[pair.base]
        val prevQuote = table.previous[pair.quote]
        val changePct = if (prevBase != null && prevQuote != null && prevBase > 0.0) {
            val prevCross = prevQuote / prevBase
            if (prevCross > 0.0) ((cross - prevCross) / prevCross) * 100.0 else 0.0
        } else 0.0
        return PairQuote(pair, cross, changePct, formatRate(cross), formatPct(changePct))
    }

    fun quotesFor(pairs: List<FxPair>, table: FxTable): List<PairQuote> =
        pairs.mapNotNull { quoteFor(it, table) }

    fun parseAmount(raw: String): Double? =
        raw.trim().replace(",", "").toDoubleOrNull()?.takeIf { it > 0.0 && it <= 1_000_000_000.0 && it.isFinite() }

    fun every(table: FxTable): Long = if (table.failure != null) RETRY_MS else FRESH_MS

    /** Debug samples (`./bento debug fx stage calm|big|offline`). */
    fun sample(name: String, now: Long): FxTable? = when (name) {
        "calm" -> FxTable(
            latest = mapOf("EUR" to 1.0, "USD" to 1.0942, "JPY" to 163.40, "GBP" to 0.8340, "CHF" to 0.9380, "CAD" to 1.5020),
            previous = mapOf("EUR" to 1.0, "USD" to 1.0915, "JPY" to 162.90, "GBP" to 0.8355, "CHF" to 0.9370, "CAD" to 1.4990),
            date = LocalDate.now(ZoneOffset.UTC).toString(),
            fetchedAt = now - 120_000,
        )
        "big" -> FxTable(
            latest = mapOf("EUR" to 1.0, "USD" to 1.1120, "JPY" to 160.10, "GBP" to 0.8420, "CHF" to 0.9310, "CAD" to 1.5180),
            previous = mapOf("EUR" to 1.0, "USD" to 1.0910, "JPY" to 163.80, "GBP" to 0.8350, "CHF" to 0.9380, "CAD" to 1.5000),
            date = LocalDate.now(ZoneOffset.UTC).toString(),
            fetchedAt = now - 60_000,
        )
        "offline" -> FxTable(
            latest = mapOf("EUR" to 1.0, "USD" to 1.0942, "JPY" to 163.40, "GBP" to 0.8340),
            previous = mapOf("EUR" to 1.0, "USD" to 1.0915, "JPY" to 162.90, "GBP" to 0.8355),
            date = LocalDate.now(ZoneOffset.UTC).toString(),
            fetchedAt = now - 900_000,
            failure = Failure.OFFLINE,
        )
        else -> null
    }
}
