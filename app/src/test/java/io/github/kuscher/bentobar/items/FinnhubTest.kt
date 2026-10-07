package io.github.kuscher.bentobar.items

import io.github.kuscher.bentobar.net.Reply
import io.github.kuscher.bentobar.net.Why
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What Finnhub's replies are read as, and what is a US symbol. Every odd shape a reply can have is no quote, never a crash. */
class FinnhubTest {
    @Test fun aUsSymbolIsUpperCaseLettersAndDigitsWithAClassLetter() {
        assertEquals("AAPL", Finnhub.symbol("aapl"))
        assertEquals("BRK.B", Finnhub.symbol(" brk.b "))
        assertEquals("S0", Finnhub.symbol("S0"))
        for (bad in listOf("", "SHOP.TO", "AAPL.SW", "APPLE INC", "1AAPL", "TOOLONGXY", "AAPL&x=1", "A-B", "ÄAPL")) assertNull(bad, Finnhub.symbol(bad))
    }

    @Test fun aQuoteHasAPriceAndATime() {
        val read = Finnhub.quote("""{"c":227.52,"d":2.7,"dp":1.2,"h":229.4,"l":222.1,"o":224,"pc":224.82,"t":1791392340}""") as Finnhub.Read.Numbers
        assertEquals(QuoteNumbers(227.52, 2.7, 1.2, 229.4, 222.1, 224.0, 224.82, 1791392340), read.numbers)
        // A value that makes no sense is left out; the rest is read.
        val odd = Finnhub.quote("""{"c":10,"d":"x","dp":null,"h":-1,"l":0,"o":1e12,"pc":9,"t":1791392340}""") as Finnhub.Read.Numbers
        assertEquals(QuoteNumbers(10.0, null, null, null, null, null, 9.0, 1791392340), odd.numbers)
    }

    @Test fun zerosAreAStockTheServiceDoesNotKnow() {
        assertEquals(Finnhub.Read.Unknown, Finnhub.quote("""{"c":0,"d":null,"dp":null,"h":0,"l":0,"o":0,"pc":0,"t":0}"""))
    }

    @Test fun whatIsNoQuoteIsNull() {
        for (text in listOf("", "null", "[]", "<html></html>", """{"error":"API limit reached."}""", """{"c":12}""", """{"c":"12","t":1791392340}""",
            "[".repeat(100_000))) assertNull(text.take(40), Finnhub.quote(text))
    }

    @Test fun aSearchListsUsStocksOnlyEightAtMost() {
        val many = (1..20).joinToString(",") { """{"description":"STOCK $it","symbol":"S$it","type":"Common Stock"}""" }
        assertEquals(Finnhub.MOST_FOUND, Finnhub.listings("""{"count":20,"result":[$many]}""")!!.size)
        // Another exchange's listing, one written in lower case, one without a symbol: left out. No name: the symbol.
        val mixed = Finnhub.listings("""{"result":[{"description":"SHOPIFY","symbol":"SHOP.TO"},{"description":"x","symbol":"aapl"},{"description":"y"},{"symbol":"MSFT"}]}""")
        assertEquals(listOf(Listing("MSFT", "MSFT")), mixed)
        assertEquals(emptyList<Listing>(), Finnhub.listings("""{"count":0}"""))
        assertNull(Finnhub.listings("""{"error":"Invalid API key."}"""))
        assertNull(Finnhub.listings("""{"result":"none"}"""))
        assertNull(Finnhub.listings("nonsense"))
    }

    @Test fun aFailedRequestStandsForThis() {
        assertNull(Finnhub.failure(Reply.Failed(Why.OFF)))
        assertEquals(Finnhub.Failure.OFFLINE, Finnhub.failure(Reply.Failed(Why.OFFLINE)))
        assertEquals(Finnhub.Failure.REFUSED, Finnhub.failure(Reply.Failed(Why.STATUS, 401)))
        assertEquals(Finnhub.Failure.NO_ACCESS, Finnhub.failure(Reply.Failed(Why.STATUS, 403)))
        assertEquals(Finnhub.Failure.SLOW_DOWN, Finnhub.failure(Reply.Failed(Why.STATUS, 429)))
        for (r in listOf(Reply.Failed(Why.STATUS, 500), Reply.Failed(Why.TIMEOUT), Reply.Failed(Why.TOO_LARGE), Reply.Failed(Why.UNREADABLE)))
            assertEquals(Finnhub.Failure.NO_ANSWER, Finnhub.failure(r))
    }

    @Test fun theSignUpAndTheSiteAreFinnhubsOwn() {
        assertTrue(Finnhub.SIGN_UP.startsWith("https://finnhub.io/"))
        assertTrue(Finnhub.SITE.startsWith("https://finnhub.io/"))
    }
}
