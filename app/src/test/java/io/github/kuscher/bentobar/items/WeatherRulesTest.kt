package io.github.kuscher.bentobar.items

import io.github.kuscher.bentobar.data.ItemConfig
import io.github.kuscher.bentobar.util.Sym
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * What the Weather item shows in the bar and says, for every row of the product's and the design's
 * tables, to the character. The words are the app's own, read from its text files.
 */
class WeatherRulesTest {
    private val w = WeatherFileWords()
    private val sf = ZoneId.of("America/Los_Angeles")
    private val t = WeatherFileWords.times(sf)
    private val min = 60_000L
    private val hour = 60 * min

    /** A moment on Monday 5 October 2026 in San Francisco, or on another day of that month. */
    private fun at(h: Int, m: Int = 0, day: Int = 5): Long = ZonedDateTime.of(2026, 10, day, h, m, 0, 0, sf).toInstant().toEpochMilli()

    /** 72 °F, feels like 68 °F, a high of 78 °F and a low of 61 °F: the design's numbers, as the metric values the service sends. */
    private val c72 = 22.2
    private val c68 = 20.0
    private val c78 = 25.6
    private val c61 = 16.1

    /** One reading of a partly cloudy afternoon, read at 1:20 PM, with a dry forecast: [hours] are (hour of the day, chance, code). */
    private fun reading(temp: Double? = c72, feels: Double? = c68, code: Int? = 2, day: Boolean = true,
                        hours: List<Triple<Int, Int?, Int?>> = emptyList(), hourTemp: Double? = c72, fetchedAt: Long = at(13, 20)): Reading {
        val wet = hours.associateBy { it.first }
        return Reading(place = "37.77,-122.42", fetchedAt = fetchedAt, zone = "America/Los_Angeles", offsetSec = -7 * 3600,
            current = Current(at = at(13, 15) / 1000, temp = temp, feels = feels, code = code, day = day, windKmh = 14.5),
            hours = (13..23).map { h -> Hour(at(h) / 1000, hourTemp, if (h in wet) wet.getValue(h).second else 0, if (h in wet) wet.getValue(h).third else 2, day = h < 19) },
            days = listOf(Day(at(0) / 1000, 2, c78, c61, 20, at(7, 8) / 1000, at(18, 42) / 1000),
                Day(at(0, day = 6) / 1000, 63, c78, c61, 60, at(7, 9, 6) / 1000, at(18, 41, 6) / 1000)))
    }

    private fun look(city: String? = "San Francisco", label: String? = null, show: String = WeatherRules.SHOW_TEMP, fahrenheit: Boolean = true,
                     rainHours: Int = 2) = Look(city, label, show, fahrenheit, miles = fahrenheit, rainHours = rainHours)

    private fun bar(r: Reading?, look: Look = look(), now: Long = at(13, 30), times: Times = t): Bar =
        WeatherRules.bar(WeatherRules.status(hasPlace = true, on = true, setUp = true, reading = r, now = now), look, now, w, times)

    // ---- the bar: what it says ----------------------------------------------------------------

    @Test fun aReadingIsTheTemperatureAsAWholeNumber() {
        val b = bar(reading())
        assertEquals("72°", b.text)
        assertEquals(3, WeatherRules.count(b.text!!))
        assertEquals(Tone.NORMAL, b.tone)
        assertFalse(b.active)
        assertEquals(Sym.PARTLY_CLOUDY_DAY, b.icon)
        assertTrue(b.filled)
    }

    @Test fun belowZeroHasARealMinusSign() {
        val b = bar(reading(temp = -20.0)) // −4 °F
        assertEquals("−4°", b.text)
        assertEquals('−', b.text!!.first())
        assertEquals(3, WeatherRules.count(b.text!!))
        assertEquals("−4°", bar(reading(temp = -4.4), look(fahrenheit = false)).text)
    }

    @Test fun minusZeroIsWrittenZero() {
        assertEquals("0°", bar(reading(temp = -0.4), look(fahrenheit = false)).text)
        assertEquals("0°", bar(reading(temp = -17.9)).text) // −0.22 °F
        assertEquals("0°", bar(reading(temp = 0.0), look(fahrenheit = false)).text)
    }

    @Test fun theUnitIsTheItemsAndIsAppliedOnTheDevice() {
        val r = reading()
        assertEquals("72°", bar(r, look(fahrenheit = true)).text)
        assertEquals("22°", bar(r, look(fahrenheit = false)).text)
    }

    @Test fun rainLikelyWithinTheRulesHoursSaysWhatAndWhen() {
        // The product's own case: an 80% chance of rain in 90 minutes.
        val b = bar(reading(hours = listOf(Triple(15, 80, 61))))
        assertEquals("72° · Rain 3 PM", b.text)
        assertEquals(15, WeatherRules.count(b.text!!))
        assertEquals(Tone.ACCENT, b.tone)
        assertTrue(b.active)
        // The glyph is what is coming, not the sky of this minute.
        assertEquals(Sym.RAINY, b.icon)
    }

    @Test fun withA24HourClockTheHourIsWrittenThatWay() {
        val b = bar(reading(hours = listOf(Triple(15, 80, 73))), times = WeatherFileWords.times(sf, h24 = true))
        assertEquals("72° · Snow 15:00", b.text)
        assertEquals(16, WeatherRules.count(b.text!!))
        assertEquals(Sym.WEATHER_SNOWY, b.icon)
        assertEquals(Tone.ACCENT, b.tone)
    }

    @Test fun aStormThatIsComingWarns() {
        val b = bar(reading(hours = listOf(Triple(14, 60, 95))))
        assertEquals("72° · Storm 2 PM", b.text)
        assertEquals(Tone.WARN, b.tone)
        assertTrue(b.active)
        assertEquals(Sym.THUNDERSTORM, b.icon)
    }

    @Test fun whatFallsNowIsSaidWithoutATime() {
        val rain = bar(reading(code = 63))
        assertEquals("72° · Rain", rain.text)
        assertEquals(10, WeatherRules.count(rain.text!!))
        assertEquals(Tone.ACCENT, rain.tone)
        assertTrue(rain.active)
        assertEquals(Sym.RAINY, rain.icon)

        val snow = bar(reading(code = 71))
        assertEquals("72° · Snow", snow.text)
        assertEquals(Tone.ACCENT, snow.tone)
        assertEquals(Sym.WEATHER_SNOWY, snow.icon)

        val storm = bar(reading(code = 96))
        assertEquals("72° · Storm", storm.text)
        assertEquals(Tone.WARN, storm.tone) // tone is never the only signal: the word says it too
        assertTrue(storm.active)
        assertEquals(Sym.THUNDERSTORM, storm.icon)
    }

    @Test fun everyKindOfRainIsRainInTheBar() {
        for (code in listOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82)) assertEquals("code $code", "72° · Rain", bar(reading(code = code)).text)
        for (code in listOf(71, 73, 75, 77, 85, 86)) assertEquals("code $code", "72° · Snow", bar(reading(code = code)).text)
        for (code in listOf(95, 96, 99)) assertEquals("code $code", "72° · Storm", bar(reading(code = code)).text)
        for (code in listOf(0, 1, 2, 3, 45, 48, 4, 100)) assertEquals("code $code", "72°", bar(reading(code = code)).text)
    }

    @Test fun whileItFallsNothingIsSaidAboutLater() {
        val b = bar(reading(code = 61, hours = listOf(Triple(15, 90, 95))))
        assertEquals("72° · Rain", b.text)
        assertEquals(Tone.ACCENT, b.tone)
    }

    @Test fun showDecidesTheTextWhileNothingIsComing() {
        val r = reading()
        assertEquals("72°", bar(r, look(show = WeatherRules.SHOW_TEMP)).text)
        val both = bar(r, look(show = WeatherRules.SHOW_HIGH_LOW))
        assertEquals("72° ↑78° ↓61°", both.text)
        assertEquals(13, WeatherRules.count(both.text!!))
        assertEquals(Tone.NORMAL, both.tone)
        assertFalse(both.active)
        assertEquals("68°", bar(r, look(show = WeatherRules.SHOW_FEELS)).text)
        // A choice nobody knows (a layout from elsewhere) is the plain temperature.
        assertEquals("72°", bar(r, look(show = "something")).text)
    }

    @Test fun theWarningWinsOverHighAndLowAndLeadsWithTheNumberShowWouldLeadWith() {
        val r = reading(hours = listOf(Triple(15, 80, 61)))
        assertEquals("72° · Rain 3 PM", bar(r, look(show = WeatherRules.SHOW_HIGH_LOW)).text)
        assertEquals("68° · Rain 3 PM", bar(r, look(show = WeatherRules.SHOW_FEELS)).text)
        assertEquals("68° · Rain", bar(reading(code = 63), look(show = WeatherRules.SHOW_FEELS)).text)
    }

    @Test fun aLabelLeads() {
        assertEquals("SF 72°", bar(reading(), look(label = "SF")).text)
        val b = bar(reading(hours = listOf(Triple(15, 80, 61))), look(label = "SF"))
        assertEquals("SF 72° · Rain 3 PM", b.text)
        assertEquals(18, WeatherRules.count(b.text!!))
    }

    // ---- the bar: twenty characters ------------------------------------------------------------

    @Test fun longerThanTwentyFirstTheTimeGoesThenTheLabel() {
        val rain = reading(hours = listOf(Triple(15, 80, 61)))
        // 72°, rain at 3 PM
        assertEquals("72° · Rain 3 PM", bar(rain).text)
        // label Tahoe: "Tahoe 72° · Rain 3 PM" is 21
        assertEquals(21, WeatherRules.count("Tahoe 72° · Rain 3 PM"))
        val tahoe = bar(rain, look(label = "Tahoe"))
        assertEquals("Tahoe 72° · Rain", tahoe.text)
        assertEquals(16, WeatherRules.count(tahoe.text!!))
        // label Lake Tahoe, −12°, snow at 10 PM: 28, then 22 without the time
        assertEquals(28, WeatherRules.count("Lake Tahoe −12° · Snow 10 PM"))
        assertEquals(22, WeatherRules.count("Lake Tahoe −12° · Snow"))
        val snow = bar(reading(temp = -24.4, hours = listOf(Triple(22, 70, 73))), look(label = "Lake Tahoe", rainHours = 12))
        assertEquals("−12° · Snow", snow.text)
        assertEquals(11, WeatherRules.count(snow.text!!))
        // label Lake Tahoe, 72°, high and low: 24
        assertEquals(24, WeatherRules.count("Lake Tahoe 72° ↑78° ↓61°"))
        val both = bar(reading(), look(label = "Lake Tahoe", show = WeatherRules.SHOW_HIGH_LOW))
        assertEquals("72° ↑78° ↓61°", both.text)
        assertEquals(13, WeatherRules.count(both.text!!))
    }

    @Test fun exactlyTwentyStays() {
        // "Monterey 72° · Rain" is 19, with the time it would be 24; "Lake Tahoe 72° · Rain" is 21.
        val b = bar(reading(code = 63), look(label = "Sacramento")) // 10 + 1 + 10
        assertEquals("72° · Rain", b.text)
        val fits = bar(reading(code = 63), look(label = "San Diego")) // 9 + 1 + 10 = 20
        assertEquals("San Diego 72° · Rain", fits.text)
        assertEquals(20, WeatherRules.count(fits.text!!))
    }

    @Test fun charactersAreCountedAsAReaderCountsThem() {
        // An accent written as its own code point and a symbol outside the basic plane are one character each.
        assertEquals(6, WeatherRules.count("Zu\u0308rich"))
        assertEquals(1, WeatherRules.count("\uD83C\uDF27"))
        assertEquals("São Paulo 72° · Rain", bar(reading(code = 63), look(label = "São Paulo")).text)
        // Written with the accent apart it is one unit longer in memory and still twenty characters to a reader: it stays.
        val apart = "Sa\u0303o Paulo"
        assertEquals(10, apart.length)
        assertEquals("$apart 72° · Rain", bar(reading(code = 63), look(label = apart)).text)
        assertEquals(20, WeatherRules.count("$apart 72° · Rain"))
    }

    @Test fun theItemSaysItNeverHasMoreThanTwentyCharacters() {
        for (label in listOf(null, "SF", "Tahoe", "Lake Tahoe", "WWWWWWWWWWWW")) for (show in listOf(WeatherRules.SHOW_TEMP, WeatherRules.SHOW_HIGH_LOW, WeatherRules.SHOW_FEELS))
            for (r in listOf(reading(), reading(temp = -40.0, feels = -51.0), reading(code = 95), reading(hours = listOf(Triple(22, 99, 86))))) {
                val text = bar(r, look(label = label, show = show, rainHours = 12)).text!!
                assertTrue("$text is ${WeatherRules.count(text)}", WeatherRules.count(text) <= WeatherRules.BAR_CHARS)
            }
    }

    // ---- the bar: the states without a number ---------------------------------------------------

    @Test fun notSetUpIsAnOutlinedCloudWithoutText() {
        val b = WeatherRules.bar(WeatherRules.status(hasPlace = false, on = false, setUp = false, reading = null, now = at(13, 30)), look(city = null), at(13, 30), w, t)
        assertEquals(Sym.CLOUD, b.icon)
        assertFalse(b.filled)
        assertNull(b.text)
        assertEquals(Tone.NORMAL, b.tone)
        assertFalse(b.active)
        assertEquals("Weather: not set up", b.desc)
        assertNull(b.tooltip)
    }

    @Test fun switchedOffIsAnOutlinedCloudWithoutTextWhateverWasRead() {
        for (setUp in listOf(false, true)) {
            val b = WeatherRules.bar(WeatherRules.status(hasPlace = true, on = false, setUp = setUp, reading = reading(), now = at(13, 30)), look(), at(13, 30), w, t)
            assertEquals(Sym.CLOUD, b.icon)
            assertFalse(b.filled)
            assertNull(b.text)
            assertFalse(b.active)
            assertEquals("Weather: off", b.desc)
        }
    }

    @Test fun theFirstReadingStillLoadingIsAnOutlinedCloudWithoutText() {
        val b = bar(null)
        assertEquals(Sym.CLOUD, b.icon)
        assertFalse(b.filled)
        assertNull(b.text)
        assertFalse(b.active)
        assertEquals("Weather: loading", b.desc)
    }

    @Test fun afterThreeHoursWithoutAnAnswerTheNumberGoes() {
        val r = reading(fetchedAt = at(10, 30)).copy(failure = Failure.OFFLINE)
        val still = bar(r, now = at(13, 29))
        assertEquals("72°", still.text) // not live, but under three hours old: the bar keeps its reading
        assertEquals(Sym.PARTLY_CLOUDY_DAY, still.icon)
        val gone = bar(r, now = at(13, 30))
        assertEquals(Sym.CLOUD_OFF, gone.icon)
        assertFalse(gone.filled)
        assertNull(gone.text)
        assertEquals(Tone.NORMAL, gone.tone)
        assertFalse(gone.active)
        assertEquals("Weather: no reading", gone.desc)
    }

    @Test fun anOldReadingBringsNoRainWarningEither() {
        val r = reading(code = 95, fetchedAt = at(10, 0)).copy(failure = Failure.NO_ANSWER)
        val b = bar(r, now = at(13, 30))
        assertNull(b.text)
        assertFalse(b.active)
        assertEquals(Tone.NORMAL, b.tone)
    }

    @Test fun noReadingAndAFailureIsTheCrossedCloudNeverTheExclamationMark() {
        for (f in Failure.entries) {
            val b = bar(Reading(place = "37.77,-122.42", failure = f))
            assertEquals(Sym.CLOUD_OFF, b.icon)
            assertFalse(b.filled)
            assertNull(b.text)
            assertEquals("Weather: no reading", b.desc)
        }
    }

    @Test fun anOldReadingThatIsBeingAskedAgainIsLoading() {
        // The bar returned after a long while: the kept numbers are too old to show, and nothing has failed.
        val b = bar(reading(fetchedAt = at(8, 0)), now = at(13, 30))
        assertEquals(Sym.CLOUD, b.icon)
        assertNull(b.text)
        assertEquals("Weather: loading", b.desc)
    }

    @Test fun theSkyOfThisMinuteHasItsGlyphByDayAndByNight() {
        assertEquals(Sym.CLEAR_DAY, bar(reading(code = 0)).icon)
        assertEquals(Sym.CLEAR_NIGHT, bar(reading(code = 0, day = false)).icon)
        assertEquals(Sym.CLEAR_NIGHT, bar(reading(code = 1, day = false)).icon)
        assertEquals(Sym.PARTLY_CLOUDY_NIGHT, bar(reading(code = 2, day = false)).icon)
        assertEquals(Sym.CLOUD, bar(reading(code = 3)).icon)
        assertEquals(Sym.FOGGY, bar(reading(code = 45)).icon)
        assertEquals(Sym.WEATHER_MIX, bar(reading(code = 66)).icon)
        // A code that isn't in the list: a cloud and no word.
        val unknown = bar(reading(code = 42))
        assertEquals(Sym.CLOUD, unknown.icon)
        assertTrue(unknown.filled)
        assertEquals("72°", unknown.text)
        assertEquals("San Francisco: 72 degrees.", unknown.desc)
        assertEquals("San Francisco", unknown.tooltip)
    }

    // ---- the rain rule ------------------------------------------------------------------------

    @Test fun likelyMeansFiftyPercentOrMoreInAnyHourOfTheSpan() {
        assertNull(WeatherRules.coming(reading(hours = listOf(Triple(15, 49, 61))), at(13, 30), 2))
        assertEquals(at(15) / 1000, WeatherRules.coming(reading(hours = listOf(Triple(15, 50, 61))), at(13, 30), 2)!!.at)
        assertEquals("72°", bar(reading(hours = listOf(Triple(15, 49, 61)))).text)
        assertFalse(bar(reading(hours = listOf(Triple(15, 49, 61)))).active)
        assertEquals("72° · Rain 3 PM", bar(reading(hours = listOf(Triple(15, 50, 61)))).text)
        // Any hour of the span: the first that is likely gives the time.
        assertEquals("72° · Rain 2 PM", bar(reading(hours = listOf(Triple(14, 55, 61), Triple(15, 90, 95)))).text)
    }

    @Test fun rainFourHoursOffLeavesTheItemHiddenUntilTheRuleReachesIt() {
        val later = reading(hours = listOf(Triple(17, 80, 61))) // 3.5 hours from 1:30 PM
        assertFalse(bar(later).active)
        assertEquals("72°", bar(later).text)
        assertEquals(Tone.NORMAL, bar(later).tone)
        assertFalse(bar(later, look(rainHours = 3)).active)
        val reached = bar(later, look(rainHours = 4))
        assertTrue(reached.active)
        assertEquals("72° · Rain 5 PM", reached.text)
        // Two hours later the same reading speaks up by itself.
        assertEquals("72° · Rain 5 PM", bar(later, now = at(15, 30)).text)
    }

    @Test fun theSpanEndsExactlyAtItsLastHour() {
        val r = reading(hours = listOf(Triple(15, 80, 61)))
        assertTrue(bar(r, now = at(13, 0)).active)             // 3 PM is two hours off, to the minute
        assertFalse(bar(r, now = at(12, 59)).active)           // and a minute more than two
        assertTrue(bar(r, look(rainHours = 1), now = at(14, 0)).active)
        assertFalse(bar(r, look(rainHours = 1), now = at(13, 59)).active)
    }

    @Test fun anHourThatHasBegunIsNotComingAnyMore() {
        // At 3:10 PM the 3 PM hour is the present: the sky of this minute speaks for it.
        val r = reading(hours = listOf(Triple(15, 80, 61)))
        assertFalse(bar(r, now = at(15, 10)).active)
        assertEquals("72°", bar(r, now = at(15, 10)).text)
        assertTrue(bar(r, now = at(14, 59)).active)
    }

    @Test fun anHourIsFoundByItsTimeNotByItsPlaceInTheReply() {
        val r = reading(hours = listOf(Triple(15, 80, 61), Triple(21, 90, 73)))
        val shuffled = r.copy(hours = r.hours.reversed())
        assertEquals("72° · Rain 3 PM", bar(shuffled).text)
        assertEquals(at(15) / 1000, WeatherRules.coming(shuffled, at(13, 30), 2)!!.at)
        // The reply's first place holds the hour it was read in; a later look still finds the right one.
        val evening = shuffled.copy(fetchedAt = at(19, 0))
        assertEquals("72° · Snow 9 PM", bar(evening, now = at(20, 30)).text)
    }

    @Test fun pastMidnightTheHoursAndTheDayAreStillFoundByTheirTime() {
        // Read at 10:40 PM, shown at 12:20 AM: the reply's first day is yesterday by then.
        val r = Reading(place = "37.77,-122.42", fetchedAt = at(22, 40), zone = "America/Los_Angeles", offsetSec = -7 * 3600,
            current = Current(at(22, 30) / 1000, c61, c61, 3, false, 5.0),
            hours = (22..23).map { Hour(at(it) / 1000, c61, 0, 3, false) } + (0..9).map { Hour(at(it, day = 6) / 1000, c61, if (it == 2) 80 else 0, if (it == 2) 61 else 3, false) },
            days = listOf(Day(at(0) / 1000, 3, c78, c61, 10, at(7, 8) / 1000, at(18, 42) / 1000),
                Day(at(0, day = 6) / 1000, 61, 20.0, 10.0, 80, at(7, 9, 6) / 1000, at(18, 41, 6) / 1000),
                Day(at(0, day = 7) / 1000, 0, 21.0, 11.0, 0, at(7, 10, 7) / 1000, at(18, 40, 7) / 1000)))
        val now = at(0, 20, day = 6)
        assertEquals("61° · Rain 2 AM", bar(r, now = now).text)
        // "With high and low" takes the day it is now in the city, the second of the reply.
        val dry = r.copy(hours = r.hours.map { it.copy(chance = 0, code = 3) })
        assertEquals("61° ↑68° ↓50°", bar(dry, look(show = WeatherRules.SHOW_HIGH_LOW), now = now).text)
        val menu = WeatherRules.menu(dry, look(), now, w, t)
        assertEquals("High 68° · Low 50°", menu.highLow)
        assertEquals(listOf("1 AM", "2 AM", "3 AM", "4 AM", "5 AM", "6 AM"), menu.hours.map { it.time })
        assertEquals(listOf("Wed"), menu.days.map { it.day })
        assertEquals("7:09 AM", menu.sunrise)
    }

    @Test fun aLikelyHourWithoutAWordOfItsOwnIsRainOrBelowFreezingSnow() {
        // The chance comes from many forecasts and the code from one: a cloudy hour can still be a likely wet one.
        assertEquals("72° · Rain 3 PM", bar(reading(hours = listOf(Triple(15, 60, 3)))).text)
        assertEquals(Sym.RAINY, bar(reading(hours = listOf(Triple(15, 60, 3)))).icon)
        val cold = bar(reading(temp = -20.0, hours = listOf(Triple(15, 60, null)), hourTemp = -6.0))
        assertEquals("−4° · Snow 3 PM", cold.text)
        assertEquals(Sym.WEATHER_SNOWY, cold.icon)
    }

    // ---- another time zone ---------------------------------------------------------------------

    @Test fun aCityInAnotherTimeZoneKeepsItsOwnHours() {
        // Tokyo from San Francisco: 1:30 PM here is 5:30 AM there, the next day.
        val tokyo = ZoneId.of("Asia/Tokyo")
        fun there(h: Int, m: Int = 0) = ZonedDateTime.of(2026, 10, 6, h, m, 0, 0, tokyo).toInstant().toEpochMilli()
        assertEquals(at(13, 30), there(5, 30))
        val r = Reading(place = "35.69,139.69", fetchedAt = at(13, 20), zone = "Asia/Tokyo", offsetSec = 9 * 3600,
            current = Current(there(5, 15) / 1000, c61, c61, 3, false, 5.0),
            hours = (5..23).map { Hour(there(it) / 1000, c61, if (it == 7) 80 else 0, if (it == 7) 61 else 3, it >= 6) },
            days = listOf(Day(there(0) / 1000, 61, c72, c61, 80, there(5, 40) / 1000, there(17, 20) / 1000)))
        assertEquals("61° · Rain 7 AM", bar(r, look(city = "Tokyo")).text)
        assertEquals("Tokyo: 61 degrees. Cloudy. Rain likely at 7 AM.", bar(r, look(city = "Tokyo")).desc)
        val menu = WeatherRules.menu(r, look(city = "Tokyo"), at(13, 30), w, t)
        assertEquals(listOf("6 AM", "7 AM", "8 AM", "9 AM", "10 AM", "11 AM"), menu.hours.map { it.time })
        assertEquals("5:40 AM", menu.sunrise)
        assertEquals("5:20 PM", menu.sunset)
        assertEquals("Cloudy · feels like 61° · 5:30 AM there", menu.subtitle)
        // The note's time is the device's own.
        assertEquals("Weather data by Open-Meteo.com · updated 1:20 PM", menu.note)
    }

    @Test fun aZoneThisDeviceDoesNotKnowFallsBackToItsDistanceFromUtc() {
        val r = reading().copy(zone = "Mars/Olympus_Mons", offsetSec = 2 * 3600)
        assertEquals(2 * 3600, WeatherRules.zone(r).rules.getOffset(java.time.Instant.EPOCH).totalSeconds)
        assertEquals(ZoneId.of("Europe/Zurich"), WeatherRules.zone(reading().copy(zone = "Europe/Zurich")))
        // Nothing at all: the service's time is UTC.
        assertEquals(0, WeatherRules.zone(Reading(place = "0.00,0.00")).rules.getOffset(java.time.Instant.EPOCH).totalSeconds)
    }

    // ---- what is spoken ------------------------------------------------------------------------

    @Test fun theSpokenSentences() {
        assertEquals("San Francisco: 72 degrees. Partly cloudy.", bar(reading()).desc)
        assertEquals("San Francisco: 72 degrees. Partly cloudy. Rain likely at 3 PM.", bar(reading(hours = listOf(Triple(15, 80, 61)))).desc)
        assertEquals("San Francisco: 72 degrees. Rain. Rain now.", bar(reading(code = 63)).desc)
        assertEquals("San Francisco: 72 degrees. Thunderstorm with hail. Storm now.", bar(reading(code = 99)).desc)
        assertEquals("San Francisco: 72 degrees. Partly cloudy. Snow likely at 3 PM.", bar(reading(hours = listOf(Triple(15, 80, 85)))).desc)
    }

    @Test fun degreesAreSpokenWithTheirSignAndInTheSingularForOne() {
        assertEquals("San Francisco: −4 degrees. Partly cloudy.", bar(reading(temp = -20.0)).desc)
        assertEquals("San Francisco: 1 degree. Partly cloudy.", bar(reading(temp = 1.2), look(fahrenheit = false)).desc)
        assertEquals("San Francisco: −1 degree. Partly cloudy.", bar(reading(temp = -1.2), look(fahrenheit = false)).desc)
        assertEquals("San Francisco: 0 degrees. Partly cloudy.", bar(reading(temp = -0.2), look(fahrenheit = false)).desc)
    }

    @Test fun theSpokenNumberIsTheOneTheBarLeadsWith() {
        assertEquals("San Francisco: 68 degrees. Partly cloudy.", bar(reading(), look(show = WeatherRules.SHOW_FEELS)).desc)
        // A label is for the eye; the ear gets the city.
        assertEquals("San Francisco: 72 degrees. Partly cloudy.", bar(reading(), look(label = "SF")).desc)
        // When the time had to go from the bar, it is still spoken.
        assertEquals("San Francisco: 72 degrees. Partly cloudy. Rain likely at 3 PM.", bar(reading(hours = listOf(Triple(15, 80, 61))), look(label = "Tahoe")).desc)
    }

    @Test fun theTooltipNamesTheCityAndTheSky() {
        assertEquals("San Francisco · Partly cloudy", bar(reading()).tooltip)
        assertEquals("San Francisco · Heavy rain", bar(reading(code = 65)).tooltip)
        // A layout without a city's name still has a name to say.
        assertEquals("Weather · Partly cloudy", bar(reading(), look(city = null)).tooltip)
        assertEquals("Weather: 72 degrees. Partly cloudy.", bar(reading(), look(city = null)).desc)
    }

    // ---- missing values ------------------------------------------------------------------------

    @Test fun aReadingWithoutATemperatureShowsTheSkyAndStillWarns() {
        val calm = bar(reading(temp = null, feels = null))
        assertNull(calm.text)
        assertEquals(Sym.PARTLY_CLOUDY_DAY, calm.icon)
        assertEquals("San Francisco: Partly cloudy.", calm.desc)
        val wet = bar(reading(temp = null, feels = null, code = 63))
        assertEquals("Rain", wet.text)
        assertTrue(wet.active)
        val soon = bar(reading(temp = null, feels = null, hours = listOf(Triple(15, 80, 61))))
        assertEquals("Rain", soon.text)
        assertEquals("San Francisco: Partly cloudy. Rain likely at 3 PM.", soon.desc)
        // Nothing to say at all.
        assertEquals("Weather: no reading", bar(reading(temp = null, feels = null, code = null)).desc)
    }

    @Test fun feelsLikeWithoutThatValueIsTheTemperature() {
        assertEquals("72°", bar(reading(feels = null), look(show = WeatherRules.SHOW_FEELS)).text)
    }

    @Test fun highAndLowWithoutBothIsTheTemperature() {
        val r = reading()
        val noLow = r.copy(days = r.days.map { it.copy(low = null) })
        assertEquals("72°", bar(noLow, look(show = WeatherRules.SHOW_HIGH_LOW)).text)
        assertEquals("72°", bar(r.copy(days = emptyList()), look(show = WeatherRules.SHOW_HIGH_LOW)).text)
    }

    @Test fun anHourWithoutAChanceIsNotLikely() {
        assertFalse(bar(reading(hours = listOf(Triple(15, null, 61)))).active)
    }

    // ---- options as they come from a layout -----------------------------------------------------

    private fun item(vararg options: Pair<String, String>) = ItemConfig("w1", "weather", options = mapOf(*options))

    @Test fun coordinatesAreRoundedToTwoDecimals() {
        assertEquals("47.37", WeatherRules.coordinate(47.36667, 90.0))
        assertEquals("8.55", WeatherRules.coordinate(8.55, 180.0))
        assertEquals("8.50", WeatherRules.coordinate(8.5, 180.0))
        assertEquals("-122.42", WeatherRules.coordinate(-122.41942, 180.0))
        assertEquals("37.77", WeatherRules.coordinate(37.77493, 90.0))
        assertEquals("0.00", WeatherRules.coordinate(-0.001, 90.0)) // no minus before zero
        assertEquals("0.00", WeatherRules.coordinate(0.0, 90.0))
        assertEquals("-33.87", WeatherRules.coordinate(-33.86785, 90.0))
        assertEquals("90.00", WeatherRules.coordinate(90.0, 90.0))
        assertEquals("-180.00", WeatherRules.coordinate(-180.0, 180.0))
    }

    @Test fun whatIsNoCoordinateIsNoPlace() {
        for (bad in listOf(90.01, -90.01, 999.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) assertNull("$bad", WeatherRules.coordinate(bad, 90.0))
        assertNull(WeatherRules.coordinate(180.01, 180.0))
        assertNull(WeatherRules.place(item()))
        assertNull(WeatherRules.place(item("lat" to "47.37")))
        assertNull(WeatherRules.place(item("lat" to "north", "lon" to "8.55")))
        assertNull(WeatherRules.place(item("lat" to "47.37", "lon" to "")))
        assertNull(WeatherRules.place(item("lat" to "91", "lon" to "8.55")))
        assertNull(WeatherRules.place(item("lat" to "47.37", "lon" to "181")))
        assertNull(WeatherRules.place(item("lat" to "NaN", "lon" to "8.55")))
        assertNull(WeatherRules.place(item("lat" to "4".repeat(400), "lon" to "8.55")))
    }

    @Test fun aLayoutsCoordinatesAreRoundedAgainSoNoMoreThanTwoDecimalsAreEverSent() {
        // A layout written by hand, or by something else: whatever it holds, the place has two decimals.
        val place = WeatherRules.place(item("lat" to "47.3666712345", "lon" to " 8.5500001 "))!!
        assertEquals("47.37", place.lat)
        assertEquals("8.55", place.lon)
        assertEquals("47.37,8.55", place.key)
        assertEquals(Place("47.37", "8.55"), WeatherRules.place(item("lat" to "47.37", "lon" to "8.55")))
        assertEquals("1.00,10.00", WeatherRules.place(item("lat" to "1", "lon" to "1e1"))!!.key)
    }

    @Test fun twoItemsWithOneCityAreOnePlace() {
        val a = WeatherRules.place(item("lat" to "47.37", "lon" to "8.55", "city" to "Zurich", "label" to "ZRH"))
        val b = WeatherRules.place(item("lat" to "47.370", "lon" to "8.5500", "city" to "Zürich"))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test fun pickingACityStoresItRoundedAndKeepsTheItemsOtherOptions() {
        val springfield = City("Springfield", "Illinois", "United States", "39.80", "-89.64", "America/Chicago")
        val before = item("label" to "Home", "show" to "feels", "unit" to "c", "rainHours" to "4", "city" to "Zurich", "region" to "Zurich, Switzerland",
            "lat" to "47.37", "lon" to "8.55", "zone" to "Europe/Zurich")
        val after = WeatherRules.picked(before, springfield, "Illinois, United States")
        assertEquals(mapOf("label" to "Home", "show" to "feels", "unit" to "c", "rainHours" to "4", "city" to "Springfield",
            "region" to "Illinois, United States", "lat" to "39.80", "lon" to "-89.64", "zone" to "America/Chicago"), after.options)
        assertEquals(before.id, after.id)
        assertEquals(Place("39.80", "-89.64"), WeatherRules.place(after))
        // A place without a region or a zone leaves none of the old one behind.
        val bare = WeatherRules.picked(before, City("Nowhere", "", "", "1.00", "2.00", ""), null)
        assertEquals(setOf("label", "show", "unit", "rainHours", "city", "lat", "lon"), bare.options.keys)
    }

    @Test fun theOptionsOfALayoutAreReadCarefully() {
        val l = WeatherRules.look(item("city" to "  San\nFrancisco  ", "label" to " S\tF ", "show" to "highlow"), fahrenheit = true, miles = true, rainHours = 2)
        assertEquals("San Francisco", l.city)
        assertEquals("S F", l.label)
        assertEquals("highlow", l.show)
        val bare = WeatherRules.look(item(), fahrenheit = false, miles = false, rainHours = 2)
        assertNull(bare.city)
        assertNull(bare.label)
        assertEquals(WeatherRules.SHOW_TEMP, bare.show)
        // A label is twelve characters at most, a city's name eighty, whatever a layout brings.
        val long = WeatherRules.look(item("city" to "x".repeat(5000), "label" to "Lake Tahoe West Shore"), true, true, 2)
        assertEquals("Lake Tahoe W", long.label)
        assertEquals(80, long.city!!.length)
        assertNull(WeatherRules.look(item("label" to " \n "), true, true, 2).label)
    }

    @Test fun textFromOutsideCannotTurnTheBarsTextAround() {
        // The characters that override the direction of what follows them are taken out of a label and of a name, from a
        // layout or from the service. What a script needs to join or part its letters stays.
        val overrides = listOf(0x202A, 0x202B, 0x202C, 0x202D, 0x202E, 0x2066, 0x2067, 0x2068, 0x2069).joinToString("") { String(Character.toChars(it)) }
        assertEquals("SF", WeatherRules.label(overrides.take(5) + "S" + overrides + "F"))
        assertEquals("", WeatherRules.label(overrides))
        val l = WeatherRules.look(item("city" to String(Character.toChars(0x202E)) + "Zurich", "label" to String(Character.toChars(0x2067)) + "ZRH"), true, true, 2)
        assertEquals("Zurich", l.city)
        assertEquals("ZRH", l.label)
        val joiner = String(Character.toChars(0x200C))
        assertEquals("a${joiner}b", WeatherRules.label("a${joiner}b"))
        // A name in a script written from the right is a name like any other.
        assertEquals("تهران", WeatherRules.oneLine(" تهران ", 80))
        assertEquals("72° · Rain", bar(reading(code = 63), look(label = WeatherRules.label(overrides + "Lake Tahoe West"))).text)
    }

    @Test fun aLabelIsCutBetweenCharactersNeverInsideOne() {
        assertEquals("SF", WeatherRules.label("SF"))
        assertEquals("", WeatherRules.label("   "))
        assertEquals("Lake Tahoe W", WeatherRules.label("Lake Tahoe West"))
        // Twelve accented letters, each written as a letter and its accent: all twelve stay whole.
        val accented = "e\u0301".repeat(13)
        assertEquals("e\u0301".repeat(12), WeatherRules.label(accented))
        val symbols = "\uD83C\uDF27".repeat(13)
        assertEquals("\uD83C\uDF27".repeat(12), WeatherRules.label(symbols))
    }

    // ---- the search field ----------------------------------------------------------------------

    @Test fun aSearchNeedsTwoLetters() {
        assertNull(WeatherRules.query(""))
        assertNull(WeatherRules.query("   "))
        assertNull(WeatherRules.query("Z"))
        assertNull(WeatherRules.query(" Z \n"))
        assertEquals("Zu", WeatherRules.query("Zu"))
        assertEquals("Springfield", WeatherRules.query("  Springfield "))
        // Names in any script work: two characters are two characters.
        assertEquals("東京", WeatherRules.query("東京"))
        assertNull(WeatherRules.query("東"))
        assertNull(WeatherRules.query("\uD83C\uDF27")) // one symbol is one character, though it is two in memory
    }

    @Test fun whatIsSentIsOneShortLine() {
        assertEquals("New York", WeatherRules.query("New\r\nYork"))
        assertEquals("San Francisco", WeatherRules.query("San \t  Francisco"))
        val pasted = WeatherRules.query("a".repeat(10_000))!!
        assertEquals(WeatherRules.QUERY_CHARS, pasted.length)
    }

    @Test fun theFieldStartsWithTheCityOfTheDevicesTimeZone() {
        assertEquals("Los Angeles", WeatherRules.cityOf("America/Los_Angeles"))
        assertEquals("Zurich", WeatherRules.cityOf("Europe/Zurich"))
        assertEquals("Buenos Aires", WeatherRules.cityOf("America/Argentina/Buenos_Aires"))
        assertEquals("Ho Chi Minh", WeatherRules.cityOf("Asia/Ho_Chi_Minh"))
        assertEquals("Auckland", WeatherRules.cityOf("Pacific/Auckland"))
        // A zone that names no city leaves the field empty.
        for (zone in listOf("UTC", "GMT", "Etc/GMT+8", "Etc/UTC", "US/Pacific", "EST5EDT", "", "Europe/", "+02:00")) assertEquals(zone, "", WeatherRules.cityOf(zone))
    }

    // ---- when to ask again ---------------------------------------------------------------------

    @Test fun aGoodReadingIsFreshForHalfAnHour() {
        assertEquals(30 * min, WeatherRules.every(reading()))
        assertEquals(30 * min, WeatherRules.FRESH_MS)
    }

    @Test fun afterNoAnswerItIsAskedAgainInFifteenMinutes() {
        assertEquals(15 * min, WeatherRules.every(reading().copy(failure = Failure.NO_ANSWER)))
        assertEquals(15 * min, WeatherRules.every(Reading(place = "1.00,2.00", failure = Failure.NO_ANSWER, misses = 7)))
    }

    @Test fun toldToSlowDownItWaitsAnHourOrAsLongAsItWasTold() {
        assertEquals(60 * min, WeatherRules.every(reading().copy(failure = Failure.SLOW_DOWN)))
        assertEquals(60 * min, WeatherRules.every(reading().copy(failure = Failure.SLOW_DOWN, retryAfterSec = 120)))
        assertEquals(2 * hour, WeatherRules.every(reading().copy(failure = Failure.SLOW_DOWN, retryAfterSec = 7200)))
    }

    @Test fun withoutANetworkTheCheckCostsNothingAndIsMadeEveryMinute() {
        assertEquals(1 * min, WeatherRules.every(reading().copy(failure = Failure.OFFLINE)))
    }

    @Test fun aNetworkThatDoesNotReachTheServiceIsTriedLessAndLessOften() {
        // A sign-in page, a name that can't be found: those tries do go out, so they step back.
        val tries = (1..8).map { WeatherRules.every(reading().copy(failure = Failure.OFFLINE, misses = it)) / min }
        assertEquals(listOf(1L, 2L, 4L, 8L, 15L, 15L, 15L, 15L), tries)
    }

    @Test fun threeHoursIsTheAgeAtWhichAReadingIsNoReading() {
        val r = reading(fetchedAt = at(10, 0))
        assertFalse(WeatherRules.old(r, at(10, 0)))
        assertFalse(WeatherRules.old(r, at(12, 59)))
        assertTrue(WeatherRules.old(r, at(13, 0)))
        assertTrue(WeatherRules.old(Reading(place = "1.00,2.00"), at(13, 0))) // never fetched
    }

    // ---- which state -------------------------------------------------------------------------

    @Test fun theStatesInTheirOrder() {
        val now = at(13, 30)
        val fresh = reading()
        // No city: not set up, whatever else holds.
        assertEquals(Status.NotSetUp, WeatherRules.status(hasPlace = false, on = true, setUp = true, reading = fresh, now = now))
        // A city, the switch off: a layout that came with a city, or switched off in Setup.
        assertEquals(Status.Off(everOn = false), WeatherRules.status(true, on = false, setUp = false, reading = null, now = now))
        assertEquals(Status.Off(everOn = true), WeatherRules.status(true, on = false, setUp = true, reading = fresh, now = now))
        assertEquals(Status.Loading, WeatherRules.status(true, true, true, null, now))
        assertEquals(Status.Live(fresh), WeatherRules.status(true, true, true, fresh, now))
        val notLive = fresh.copy(failure = Failure.OFFLINE)
        assertEquals(Status.Live(notLive), WeatherRules.status(true, true, true, notLive, now))
        assertEquals(Status.Missing(Failure.OFFLINE), WeatherRules.status(true, true, true, notLive, now + 3 * hour))
        assertEquals(Status.Missing(Failure.SLOW_DOWN), WeatherRules.status(true, true, true, Reading(place = "1.00,2.00", failure = Failure.SLOW_DOWN), now))
        assertEquals(Status.Loading, WeatherRules.status(true, true, true, fresh, now + 3 * hour))
    }

    // ---- nothing a log could hold ---------------------------------------------------------------

    @Test fun whatIsPrintedOfAReadingAPlaceOrACityNamesNoPlace() {
        val r = reading()
        val city = City("Springfield", "Illinois", "United States", "39.80", "-89.64", "America/Chicago")
        for (text in listOf(r.toString(), Place("39.80", "-89.64").toString(), city.toString(), Status.Live(r).toString(), look().toString(),
            WeatherRules.menu(r, look(), at(13, 30), w, t).toString(), bar(r).toString())) {
            for (secret in listOf("37.77", "122.42", "39.80", "89.64", "Springfield", "San Francisco", "Illinois", "Los_Angeles"))
                assertFalse("$text names $secret", text.contains(secret))
        }
    }
}
