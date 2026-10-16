package io.github.kuscher.bentobar.items

import io.github.kuscher.bentobar.data.ItemConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FxRulesTest {
    private val seriesJson = """
        {
          "amount": 1.0,
          "base": "EUR",
          "start_date": "2026-04-10",
          "end_date": "2026-04-15",
          "rates": {
            "2026-04-14": {
              "USD": 1.0850,
              "GBP": 0.8540,
              "JPY": 162.10
            },
            "2026-04-15": {
              "USD": 1.0920,
              "GBP": 0.8520,
              "JPY": 163.40
            }
          }
        }
    """.trimIndent()

    @Test fun parsesTimeSeriesAndComputesCrossRatesAndDailyChange() {
        val table = FxRules.read(seriesJson, now = 50_000L)
        assertNotNull(table)
        table!!
        assertEquals("2026-04-15", table.date)
        assertTrue(table.hasRates)

        val eurUsd = FxRules.quoteFor(FxRules.FxPair("EUR", "USD"), table)!!
        assertEquals(1.0920, eurUsd.rate, 0.0001)
        assertTrue(eurUsd.changePct > 0.6)
        assertEquals("1.0920", eurUsd.formattedRate)

        val usdJpy = FxRules.quoteFor(FxRules.FxPair("USD", "JPY"), table)!!
        assertEquals(163.40 / 1.0920, usdJpy.rate, 0.01)
    }

    @Test fun rejectsMalformedJson() {
        assertNull(FxRules.read("not json", 10_000L))
        assertNull(FxRules.read("""{"base":"EUR","rates":{}}""", 10_000L))
    }

    @Test fun parsesAndManagesFollowedCurrencyPairs() {
        assertEquals(FxRules.FxPair("EUR", "USD"), FxRules.parsePair(" eur / usd "))
        assertEquals(FxRules.FxPair("GBP", "JPY"), FxRules.parsePair("gbp-jpy"))
        assertNull(FxRules.parsePair("USD/USD"))
        assertNull(FxRules.parsePair("ABC/USD"))

        var item = ItemConfig("f1", "fx", options = mapOf("pairs" to "EUR/USD"))
        item = FxRules.addPair(item, FxRules.FxPair("USD", "JPY"))
        assertEquals(listOf(FxRules.FxPair("EUR", "USD"), FxRules.FxPair("USD", "JPY")), FxRules.pairs(item))

        item = FxRules.movePair(item, FxRules.FxPair("USD", "JPY"), -1)
        assertEquals(listOf(FxRules.FxPair("USD", "JPY"), FxRules.FxPair("EUR", "USD")), FxRules.pairs(item))

        item = FxRules.removePair(item, FxRules.FxPair("EUR", "USD"))
        assertEquals(listOf(FxRules.FxPair("USD", "JPY")), FxRules.pairs(item))
    }

    @Test fun formatsConvertedAmountsCleanly() {
        assertEquals("109.20", FxRules.formatConverted(100.0, 1.092))
        assertEquals("16,340.00", FxRules.formatConverted(100.0, 163.4))
        assertEquals(250.0, FxRules.parseAmount(" 250 ")!!, 0.001)
        assertNull(FxRules.parseAmount("-5"))
    }
}
