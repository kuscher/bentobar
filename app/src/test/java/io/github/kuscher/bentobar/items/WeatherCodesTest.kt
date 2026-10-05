package io.github.kuscher.bentobar.items

import io.github.kuscher.bentobar.util.Sym
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** Open-Meteo's weather codes as the design's table has them: the word in the menu, the glyph by day and by night, the word in the bar. */
class WeatherCodesTest {
    private class Row(val codes: List<Int>, val sky: Sky, val day: String, val night: String, val falls: Falls?)

    private val table = listOf(
        Row(listOf(0), Sky.CLEAR, Sym.CLEAR_DAY, Sym.CLEAR_NIGHT, null),
        Row(listOf(1), Sky.MOSTLY_CLEAR, Sym.CLEAR_DAY, Sym.CLEAR_NIGHT, null),
        Row(listOf(2), Sky.PARTLY_CLOUDY, Sym.PARTLY_CLOUDY_DAY, Sym.PARTLY_CLOUDY_NIGHT, null),
        Row(listOf(3), Sky.CLOUDY, Sym.CLOUD, Sym.CLOUD, null),
        Row(listOf(45, 48), Sky.FOG, Sym.FOGGY, Sym.FOGGY, null),
        Row(listOf(51, 53, 55), Sky.DRIZZLE, Sym.RAINY, Sym.RAINY, Falls.RAIN),
        Row(listOf(56, 57), Sky.FREEZING_DRIZZLE, Sym.WEATHER_MIX, Sym.WEATHER_MIX, Falls.RAIN),
        Row(listOf(61), Sky.LIGHT_RAIN, Sym.RAINY, Sym.RAINY, Falls.RAIN),
        Row(listOf(63), Sky.RAIN, Sym.RAINY, Sym.RAINY, Falls.RAIN),
        Row(listOf(65), Sky.HEAVY_RAIN, Sym.RAINY, Sym.RAINY, Falls.RAIN),
        Row(listOf(66, 67), Sky.FREEZING_RAIN, Sym.WEATHER_MIX, Sym.WEATHER_MIX, Falls.RAIN),
        Row(listOf(71), Sky.LIGHT_SNOW, Sym.WEATHER_SNOWY, Sym.WEATHER_SNOWY, Falls.SNOW),
        Row(listOf(73), Sky.SNOW, Sym.WEATHER_SNOWY, Sym.WEATHER_SNOWY, Falls.SNOW),
        Row(listOf(75, 77), Sky.HEAVY_SNOW, Sym.WEATHER_SNOWY, Sym.WEATHER_SNOWY, Falls.SNOW),
        Row(listOf(80, 81), Sky.SHOWERS, Sym.RAINY, Sym.RAINY, Falls.RAIN),
        Row(listOf(82), Sky.HEAVY_SHOWERS, Sym.RAINY, Sym.RAINY, Falls.RAIN),
        Row(listOf(85, 86), Sky.SNOW_SHOWERS, Sym.WEATHER_SNOWY, Sym.WEATHER_SNOWY, Falls.SNOW),
        Row(listOf(95), Sky.THUNDERSTORM, Sym.THUNDERSTORM, Sym.THUNDERSTORM, Falls.STORM),
        Row(listOf(96, 99), Sky.THUNDERSTORM_HAIL, Sym.THUNDERSTORM, Sym.THUNDERSTORM, Falls.STORM),
    )

    @Test fun everyCodeOfTheTableHasItsWordItsGlyphsAndItsWordForTheBar() {
        for (row in table) for (code in row.codes) {
            assertEquals("code $code", row.sky, WeatherCodes.sky(code))
            assertEquals("code $code by day", row.day, WeatherCodes.glyph(code, day = true))
            assertEquals("code $code by night", row.night, WeatherCodes.glyph(code, day = false))
            assertEquals("code $code in the bar", row.falls, WeatherCodes.falls(code))
        }
    }

    @Test fun everyWordOfTheMenuIsUsedByACode() {
        assertEquals(Sky.entries.toSet(), table.map { it.sky }.toSet())
        assertEquals(19, Sky.entries.size)
    }

    @Test fun aCodeThatIsNotInTheTableIsACloudWithoutAWord() {
        val known = table.flatMap { it.codes }.toSet()
        assertEquals(28, known.size)
        for (code in (-5..120).filter { it !in known } + listOf(Int.MIN_VALUE, Int.MAX_VALUE)) {
            assertNull("code $code", WeatherCodes.sky(code))
            assertEquals("code $code", Sym.CLOUD, WeatherCodes.glyph(code, day = true))
            assertEquals("code $code", Sym.CLOUD, WeatherCodes.glyph(code, day = false))
            assertNull("code $code", WeatherCodes.falls(code))
        }
    }

    @Test fun aMissingCodeIsACloudWithoutAWordToo() {
        assertNull(WeatherCodes.sky(null))
        assertEquals(Sym.CLOUD, WeatherCodes.glyph(null, day = true))
        assertNull(WeatherCodes.falls(null))
    }

    // The two below ask the code, for every code there could be, and compare its answers to what the design says
    // in words. (They once compared this file's own table to itself, which nothing in the code could fail.)

    /** Every code a reply could hold, and then some. */
    private val codes = -1..120

    @Test fun onlyTheClearAndThePartlyCloudySkyLookDifferentAtNight() {
        val differs = codes.filter { WeatherCodes.glyph(it, day = true) != WeatherCodes.glyph(it, day = false) }
        assertEquals(listOf(0, 1, 2), differs)
        assertEquals(setOf(Sky.CLEAR, Sky.MOSTLY_CLEAR, Sky.PARTLY_CLOUDY), differs.mapTo(HashSet()) { WeatherCodes.sky(it) })
        assertEquals(listOf(Sym.CLEAR_NIGHT, Sym.CLEAR_NIGHT, Sym.PARTLY_CLOUDY_NIGHT), differs.map { WeatherCodes.glyph(it, day = false) })
        // No night glyph is ever shown by day, nor a day's by night.
        val nights = setOf(Sym.CLEAR_NIGHT, Sym.PARTLY_CLOUDY_NIGHT)
        val days = setOf(Sym.CLEAR_DAY, Sym.PARTLY_CLOUDY_DAY)
        for (code in codes) {
            assertFalse("code $code by day", WeatherCodes.glyph(code, day = true) in nights)
            assertFalse("code $code by night", WeatherCodes.glyph(code, day = false) in days)
        }
    }

    @Test fun theBarSaysOneOfThreeWords() {
        // Drizzle, rain, showers and the freezing kinds are "Rain"; every kind of snow is "Snow"; a thunderstorm is "Storm".
        val byWord = codes.filter { WeatherCodes.falls(it) != null }.groupBy({ WeatherCodes.falls(it) }, { WeatherCodes.sky(it) })
        assertEquals(setOf<Falls?>(Falls.RAIN, Falls.SNOW, Falls.STORM), byWord.keys)
        assertEquals(setOf<Sky?>(Sky.DRIZZLE, Sky.FREEZING_DRIZZLE, Sky.LIGHT_RAIN, Sky.RAIN, Sky.HEAVY_RAIN, Sky.FREEZING_RAIN, Sky.SHOWERS, Sky.HEAVY_SHOWERS),
            byWord.getValue(Falls.RAIN).toSet())
        assertEquals(setOf<Sky?>(Sky.LIGHT_SNOW, Sky.SNOW, Sky.HEAVY_SNOW, Sky.SNOW_SHOWERS), byWord.getValue(Falls.SNOW).toSet())
        assertEquals(setOf<Sky?>(Sky.THUNDERSTORM, Sky.THUNDERSTORM_HAIL), byWord.getValue(Falls.STORM).toSet())
        // By their numbers: nothing falls for any other code.
        assertEquals(listOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82), codes.filter { WeatherCodes.falls(it) == Falls.RAIN })
        assertEquals(listOf(71, 73, 75, 77, 85, 86), codes.filter { WeatherCodes.falls(it) == Falls.SNOW })
        assertEquals(listOf(95, 96, 99), codes.filter { WeatherCodes.falls(it) == Falls.STORM })
        // What falls has the glyph of what falls, by day and by night alike: the freezing kinds have their own.
        for (code in codes) WeatherCodes.falls(code)?.let { falls ->
            val expected = if (WeatherCodes.sky(code) in setOf(Sky.FREEZING_DRIZZLE, Sky.FREEZING_RAIN)) Sym.WEATHER_MIX else WeatherCodes.glyph(falls)
            assertEquals("code $code", expected, WeatherCodes.glyph(code, day = true))
            assertEquals("code $code", expected, WeatherCodes.glyph(code, day = false))
        }
        assertEquals(listOf(Sym.RAINY, Sym.WEATHER_SNOWY, Sym.THUNDERSTORM, Sym.CLOUD), listOf(Falls.RAIN, Falls.SNOW, Falls.STORM, null).map { WeatherCodes.glyph(it) })
    }
}
