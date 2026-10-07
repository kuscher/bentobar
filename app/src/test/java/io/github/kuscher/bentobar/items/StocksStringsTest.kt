package io.github.kuscher.bentobar.items

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The Stocks item's text file: every word of the rules is the resource of its name, and every string in it is used. */
class StocksStringsTest {
    private val files = StocksFileWords()

    @Test fun everyWordOfTheRulesIsTheResourceOfItsName() {
        for (word in SW.entries) {
            val id = Class.forName("io.github.kuscher.bentobar.R\$string").getField(word.res).getInt(null)
            assertEquals("$word should be R.string.${word.res}", id, stocksRes(word))
            assertTrue("${word.res} is no string in the files", word.res in files.strings)
        }
        assertEquals("no resource twice", SW.entries.size, SW.entries.map { stocksRes(it) }.toSet().size)
    }

    @Test fun everyNameIsTheFeaturesAndEveryStringIsUsed() {
        val code = File("src/main/java").walkTopDown().filter { it.isFile && it.extension == "kt" }.joinToString("\n") { it.readText() }
        val names = StringFiles.names("src/main/res/values/strings_stocks.xml")
        for (name in names) {
            assertTrue("$name: a Stocks name starts with stocks_, item_stocks_ or trigger_stocks", name.startsWith("stocks_") || name.startsWith("item_stocks_") || name.startsWith("trigger_stocks"))
            val kind = if (name in files.plurals) "plurals" else "string"
            assertTrue("R.$kind.$name is in strings_stocks.xml and nowhere in the code", Regex("R\\.$kind\\.$name\\b").containsMatchIn(code))
        }
    }

    @Test fun theBarsSignsAreSingleCharacters() {
        assertEquals("▲1.2%", files.say(SW.UP, "1.2"))
        assertEquals("▼0.4%", files.say(SW.DOWN, "0.4"))
        assertEquals("0.0%", files.say(SW.FLAT, "0.0"))
        assertEquals("+0.5%", files.say(SW.PERCENT, "+0.5"))
        assertTrue(files.strings.getValue("stocks_searching").endsWith("…"))
        assertTrue(files.strings.getValue("stocks_consent").contains("your key, the symbols of the stocks you follow and what you search for, nothing else"))
    }
}
