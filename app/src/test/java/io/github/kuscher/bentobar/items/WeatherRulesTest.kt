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
    private val sec = 1_000L
    private val min = 60 * sec
    private val hour = 60 * min

    /** A moment on Monday 5 October 2026 in San Francisco, or on another day of that month. */
    private fun at(h: Int, m: Int = 0, day: Int = 5): Long = ZonedDateTime.of(2026, 10, day, h, m, 0, 0, sf).toInstant().toEpochMilli()

    /** 72 °F, feels like 68 °F, a high of 78 °F and a low of 61 °F: the design's numbers, as the metric values the service sends. */
    private val c72 = 22.2
    private val c68 = 20.0
    private val c78 = 25.6
    private val c61 = 16.1

    /**
     * One reading of a partly cloudy afternoon, read at 1:20 PM, with a dry forecast. [hours] are the wet ones:
     * (the hour of the day in which something is likely to fall, its chance, its code). The service files an
     * hour's chance under the time that hour ends at, and so does this: rain between 3 and 4 PM is in the entry
     * of 4 PM.
     */
    private fun reading(temp: Double? = c72, feels: Double? = c68, code: Int? = 2, day: Boolean = true,
                        hours: List<Triple<Int, Int?, Int?>> = emptyList(), hourTemp: Double? = c72, fetchedAt: Long = at(13, 20)): Reading {
        val wet = hours.associateBy { it.first + 1 }
        return Reading(place = "37.77,-122.42", fetchedAt = fetchedAt, zone = "America/Los_Angeles", offsetSec = -7 * 3600,
            current = Current(at = at(13, 15) / 1000, temp = temp, feels = feels, code = code, day = day, windKmh = 14.5),
            // An entry an hour from 1 PM to midnight.
            hours = (13..24).map { h -> Hour(at(13) / 1000 + (h - 13) * 3600L, hourTemp, if (h in wet) wet.getValue(h).second else 0, if (h in wet) wet.getValue(h).third else 2, day = h < 19) },
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
        assertEquals(3, TextRules.count(b.text!!))
        assertEquals(Tone.NORMAL, b.tone)
        assertFalse(b.active)
        assertEquals(Sym.PARTLY_CLOUDY_DAY, b.icon)
        assertTrue(b.filled)
    }

    @Test fun belowZeroHasARealMinusSign() {
        val b = bar(reading(temp = -20.0)) // −4 °F
        assertEquals("−4°", b.text)
        assertEquals('−', b.text!!.first())
        assertEquals(3, TextRules.count(b.text!!))
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
        assertEquals(15, TextRules.count(b.text!!))
        assertEquals(Tone.ACCENT, b.tone)
        assertTrue(b.active)
        // The glyph is what is coming, not the sky of this minute.
        assertEquals(Sym.RAINY, b.icon)
    }

    @Test fun anHoursChanceIsForTheHourThatEndsAtItsTime() {
        // As the service files it ("preceding hour"): 80% in the entry of 3 PM means rain is likely between 2 and 3.
        val r = reading().let { it.copy(hours = it.hours.map { h -> if (h.at == at(15) / 1000) h.copy(chance = 80, code = 61) else h }) }
        val b = bar(r) // at 1:30 PM
        assertEquals("72° · Rain 2 PM", b.text)
        assertEquals("San Francisco: 72 degrees. Partly cloudy. Rain likely at 2 PM.", b.desc)
        assertTrue(b.active)
        assertEquals(at(14) / 1000, WeatherRules.begins(WeatherRules.likely(r, at(13, 30), 2)!!))
        // The rule's two hours are counted to where the wet hour begins: at noon that is two hours off, a minute earlier it is more.
        val early = r.copy(fetchedAt = at(11, 50))
        assertTrue(bar(early, now = at(12, 0)).active)
        assertEquals("72° · Rain 2 PM", bar(early, now = at(12, 0)).text)
        assertFalse(bar(early, now = at(11, 59)).active)
    }

    @Test fun aLikelyHourThatIsRunningKeepsTheItemOutAndIsSaidWithoutATime() {
        // 80% in the entry of 3 PM: rain is likely between 2 and 3, and nothing else is coming.
        val r = reading().let { it.copy(hours = it.hours.map { h -> if (h.at == at(15) / 1000) h.copy(chance = 80, code = 61) else h }) }
        // 1:30 PM: it is coming, and has its time.
        assertEquals("72° · Rain 2 PM", bar(r, now = at(13, 30)).text)
        assertEquals("72° · Rain 2 PM", bar(r, now = at(13, 59)).text)
        // From 2:00 to 3:00 the hour is running. The warning does not go at the moment rain is nearest: the item stays
        // out, says what is likely and no time, and says so aloud.
        for (now in listOf(at(14, 0), at(14, 10), at(14, 59))) {
            val b = bar(r, now = now)
            assertEquals("72° · Rain", b.text)
            assertEquals("San Francisco: 72 degrees. Partly cloudy. Rain likely this hour.", b.desc)
            assertTrue(b.active)
            assertEquals(Tone.ACCENT, b.tone)
            assertEquals(Sym.RAINY, b.icon)
            assertEquals(at(14) / 1000, WeatherRules.begins(WeatherRules.likely(r, now, 2)!!))
        }
        // After 3:00 it is over.
        for (now in listOf(at(15, 0), at(15, 1), at(15, 30))) {
            val b = bar(r, now = now)
            assertEquals("72°", b.text)
            assertEquals("San Francisco: 72 degrees. Partly cloudy.", b.desc)
            assertFalse(b.active)
            assertEquals(Tone.NORMAL, b.tone)
            assertEquals(Sym.PARTLY_CLOUDY_DAY, b.icon)
            assertNull(WeatherRules.likely(r, now, 2))
        }
    }

    @Test fun theHourThatIsRunningBesideTheOtherRules() {
        // Likely between 1 and 2 PM, and it is 1:30: "likely within N hours" includes this hour, whatever N is.
        val r = reading(hours = listOf(Triple(13, 80, 61)))
        for (hours in listOf(1, 2, 12)) {
            val b = bar(r, look(rainHours = hours))
            assertEquals("72° · Rain", b.text)
            assertTrue(b.active)
            assertEquals(Tone.ACCENT, b.tone)
        }
        // Under fifty percent it is not likely.
        assertFalse(bar(reading(hours = listOf(Triple(13, 49, 61)))).active)
        assertEquals("72°", bar(reading(hours = listOf(Triple(13, 49, 61)))).text)
        // Snow; and a storm, which warns as a storm does.
        val snow = bar(reading(hours = listOf(Triple(13, 70, 73))))
        assertEquals("72° · Snow", snow.text)
        assertEquals("San Francisco: 72 degrees. Partly cloudy. Snow likely this hour.", snow.desc)
        assertEquals(Sym.WEATHER_SNOWY, snow.icon)
        val storm = bar(reading(hours = listOf(Triple(13, 70, 95))))
        assertEquals("72° · Storm", storm.text)
        assertEquals("San Francisco: 72 degrees. Partly cloudy. Storm likely this hour.", storm.desc)
        assertEquals(Tone.WARN, storm.tone)
        // The hour after it is likely too: the first is the one that is running, so there is still no time.
        assertEquals("72° · Rain", bar(reading(hours = listOf(Triple(13, 80, 61), Triple(14, 90, 95)))).text)
        // This hour dry and the next one wet: that one is coming, with its time.
        assertEquals("72° · Rain 2 PM", bar(reading(hours = listOf(Triple(13, 49, 61), Triple(14, 80, 61)))).text)
        // When the sky of this minute is already wet, it speaks: "now", not "likely".
        val wet = bar(reading(code = 63, hours = listOf(Triple(13, 80, 61))))
        assertEquals("72° · Rain", wet.text)
        assertEquals("San Francisco: 72 degrees. Rain. Rain now.", wet.desc)
        // A label leads while there is room, the number is the one Show leads with, and without one the word stands alone.
        assertEquals("SF 72° · Rain", bar(r, look(label = "SF")).text)
        assertEquals("72° · Rain", bar(r, look(label = "Lake Tahoe")).text)
        assertEquals("68° · Rain", bar(r, look(show = WeatherRules.SHOW_FEELS)).text)
        assertEquals("72° · Rain", bar(r, look(show = WeatherRules.SHOW_HIGH_LOW)).text)
        assertEquals("Rain", bar(reading(temp = null, feels = null, hours = listOf(Triple(13, 80, 61)))).text)
        // A likely hour whose code names nothing that falls is rain, or snow when it freezes.
        assertEquals("72° · Rain", bar(reading(hours = listOf(Triple(13, 60, 3)))).text)
        assertEquals("−4° · Snow", bar(reading(temp = -20.0, hours = listOf(Triple(13, 60, null)), hourTemp = -6.0)).text)
    }

    @Test fun withA24HourClockTheHourIsWrittenThatWay() {
        val b = bar(reading(hours = listOf(Triple(15, 80, 73))), times = WeatherFileWords.times(sf, h24 = true))
        assertEquals("72° · Snow 15:00", b.text)
        assertEquals(16, TextRules.count(b.text!!))
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
        assertEquals(10, TextRules.count(rain.text!!))
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
        assertEquals(13, TextRules.count(both.text!!))
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
        assertEquals(18, TextRules.count(b.text!!))
    }

    // ---- the bar: twenty characters ------------------------------------------------------------

    @Test fun longerThanTwentyFirstTheTimeGoesThenTheLabel() {
        val rain = reading(hours = listOf(Triple(15, 80, 61)))
        // 72°, rain at 3 PM
        assertEquals("72° · Rain 3 PM", bar(rain).text)
        // label Tahoe: "Tahoe 72° · Rain 3 PM" is 21
        assertEquals(21, TextRules.count("Tahoe 72° · Rain 3 PM"))
        val tahoe = bar(rain, look(label = "Tahoe"))
        assertEquals("Tahoe 72° · Rain", tahoe.text)
        assertEquals(16, TextRules.count(tahoe.text!!))
        // label Lake Tahoe, −12°, snow at 10 PM: 28, then 22 without the time
        assertEquals(28, TextRules.count("Lake Tahoe −12° · Snow 10 PM"))
        assertEquals(22, TextRules.count("Lake Tahoe −12° · Snow"))
        val snow = bar(reading(temp = -24.4, hours = listOf(Triple(22, 70, 73))), look(label = "Lake Tahoe", rainHours = 12))
        assertEquals("−12° · Snow", snow.text)
        assertEquals(11, TextRules.count(snow.text!!))
        // label Lake Tahoe, 72°, high and low: 24
        assertEquals(24, TextRules.count("Lake Tahoe 72° ↑78° ↓61°"))
        val both = bar(reading(), look(label = "Lake Tahoe", show = WeatherRules.SHOW_HIGH_LOW))
        assertEquals("72° ↑78° ↓61°", both.text)
        assertEquals(13, TextRules.count(both.text!!))
    }

    @Test fun exactlyTwentyStays() {
        // "Monterey 72° · Rain" is 19, with the time it would be 24; "Lake Tahoe 72° · Rain" is 21.
        val b = bar(reading(code = 63), look(label = "Sacramento")) // 10 + 1 + 10
        assertEquals("72° · Rain", b.text)
        val fits = bar(reading(code = 63), look(label = "San Diego")) // 9 + 1 + 10 = 20
        assertEquals("San Diego 72° · Rain", fits.text)
        assertEquals(20, TextRules.count(fits.text!!))
    }

    @Test fun charactersAreCountedAsAReaderCountsThem() {
        // An accent written as its own code point and a symbol outside the basic plane are one character each.
        assertEquals(6, TextRules.count("Zu\u0308rich"))
        assertEquals(1, TextRules.count("\uD83C\uDF27"))
        assertEquals("São Paulo 72° · Rain", bar(reading(code = 63), look(label = "São Paulo")).text)
        // Written with the accent apart it is one unit longer in memory and still twenty characters to a reader: it stays.
        val apart = "Sa\u0303o Paulo"
        assertEquals(10, apart.length)
        assertEquals("$apart 72° · Rain", bar(reading(code = 63), look(label = apart)).text)
        assertEquals(20, TextRules.count("$apart 72° · Rain"))
    }

    @Test fun theItemSaysItNeverHasMoreThanTwentyCharacters() {
        for (label in listOf(null, "SF", "Tahoe", "Lake Tahoe", "WWWWWWWWWWWW")) for (show in listOf(WeatherRules.SHOW_TEMP, WeatherRules.SHOW_HIGH_LOW, WeatherRules.SHOW_FEELS))
            for (r in listOf(reading(), reading(temp = -40.0, feels = -51.0), reading(code = 95), reading(hours = listOf(Triple(22, 99, 86))))) {
                val text = bar(r, look(label = label, show = show, rainHours = 12)).text!!
                assertTrue("$text is ${TextRules.count(text)}", TextRules.count(text) <= WeatherRules.BAR_CHARS)
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
        assertNull(WeatherRules.likely(reading(hours = listOf(Triple(15, 49, 61))), at(13, 30), 2))
        assertEquals(at(15) / 1000, WeatherRules.begins(WeatherRules.likely(reading(hours = listOf(Triple(15, 50, 61))), at(13, 30), 2)!!))
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

    @Test fun anHourThatHasBegunLosesItsTimeAndOneThatIsOverIsGone() {
        // Likely between 3 and 4 PM.
        val r = reading(hours = listOf(Triple(15, 80, 61)))
        assertEquals("72° · Rain 3 PM", bar(r, now = at(14, 59)).text)
        assertEquals("72° · Rain", bar(r, now = at(15, 10)).text)
        assertTrue(bar(r, now = at(15, 10)).active)
        assertEquals("72°", bar(r, now = at(16, 0)).text)
        assertFalse(bar(r, now = at(16, 0)).active)
    }

    @Test fun anHourIsFoundByItsTimeNotByItsPlaceInTheReply() {
        val r = reading(hours = listOf(Triple(15, 80, 61), Triple(21, 90, 73)))
        val shuffled = r.copy(hours = r.hours.reversed())
        assertEquals("72° · Rain 3 PM", bar(shuffled).text)
        assertEquals(at(15) / 1000, WeatherRules.begins(WeatherRules.likely(shuffled, at(13, 30), 2)!!))
        // The reply's first place holds the hour it was read in; a later look still finds the right one.
        val evening = shuffled.copy(fetchedAt = at(19, 0))
        assertEquals("72° · Snow 9 PM", bar(evening, now = at(20, 30)).text)
    }

    @Test fun pastMidnightTheHoursAndTheDayAreStillFoundByTheirTime() {
        // Read at 10:40 PM, shown at 12:20 AM: the reply's first day is yesterday by then.
        val r = Reading(place = "37.77,-122.42", fetchedAt = at(22, 40), zone = "America/Los_Angeles", offsetSec = -7 * 3600,
            current = Current(at(22, 30) / 1000, c61, c61, 3, false, 5.0),
            hours = (22..23).map { Hour(at(it) / 1000, c61, 0, 3, false) } + (0..9).map { Hour(at(it, day = 6) / 1000, c61, if (it == 3) 80 else 0, if (it == 3) 61 else 3, false) }, // likely between 2 and 3 AM: the entry of 3 AM
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
            hours = (5..23).map { Hour(there(it) / 1000, c61, if (it == 8) 80 else 0, if (it == 8) 61 else 3, it >= 6) }, // likely between 7 and 8 AM: the entry of 8 AM
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
        assertEquals("تهران", TextRules.oneLine(" تهران ", 80))
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

    // ---- Refresh and Try again: when a press asks ---------------------------------------------------

    @Test fun byHandItIsAMinuteAfterAnAnswerAndTenSecondsAfterATryThatFailed() {
        // After an answer there is nothing new to fetch for a while.
        assertEquals(1 * min, WeatherRules.againAfter(reading()))
        // After a try that reached nobody or got no answer, the retry is the one thing the menu offers.
        assertEquals(10 * sec, WeatherRules.againAfter(reading().copy(failure = Failure.OFFLINE)))
        assertEquals(10 * sec, WeatherRules.againAfter(reading().copy(failure = Failure.OFFLINE, misses = 5)))
        assertEquals(10 * sec, WeatherRules.againAfter(reading().copy(failure = Failure.NO_ANSWER, misses = 1)))
        assertEquals(10 * sec, WeatherRules.againAfter(Reading(place = "1.00,2.00", failure = Failure.NO_ANSWER, misses = 7)))
        // Told to slow down, with no time named: no answer like the others.
        assertEquals(10 * sec, WeatherRules.againAfter(reading().copy(failure = Failure.SLOW_DOWN, misses = 1)))
    }

    @Test fun byHandItIsNeverSoonerThanTheServiceAskedFor() {
        assertEquals(5 * min, WeatherRules.againAfter(reading().copy(failure = Failure.SLOW_DOWN, retryAfterSec = 300)))
        assertEquals(2 * hour, WeatherRules.againAfter(reading().copy(failure = Failure.SLOW_DOWN, retryAfterSec = 7200)))
        // A service that is down for a while can name a time too.
        assertEquals(2 * min, WeatherRules.againAfter(reading().copy(failure = Failure.NO_ANSWER, retryAfterSec = 120)))
        // A wait shorter than the ten seconds changes nothing, and one second more than them is kept.
        assertEquals(10 * sec, WeatherRules.againAfter(reading().copy(failure = Failure.SLOW_DOWN, retryAfterSec = 3)))
        assertEquals(10 * sec, WeatherRules.againAfter(reading().copy(failure = Failure.NO_ANSWER, retryAfterSec = 10)))
        assertEquals(11 * sec, WeatherRules.againAfter(reading().copy(failure = Failure.NO_ANSWER, retryAfterSec = 11)))
    }

    @Test fun refreshIsDimmedForAMinuteAfterAnAnswerAndSaysItIsUpToDate() {
        val good = reading()
        assertEquals(Again.UP_TO_DATE, WeatherRules.again(good, age = 0, loading = false))
        assertEquals(Again.UP_TO_DATE, WeatherRules.again(good, age = 1 * min - 1, loading = false))
        assertEquals(Again.READY, WeatherRules.again(good, age = 1 * min, loading = false))
        assertEquals(Again.READY, WeatherRules.again(good, age = 5 * hour, loading = false))
    }

    @Test fun tryAgainIsDimmedForTenSecondsAfterATryThatFailedAndAddsNoWordToWhatTheMenuSays() {
        // With the numbers from before (not live) and without any (no reading): dimmed, and never "up to date".
        for (failure in Failure.entries) for (r in listOf(reading().copy(failure = failure), Reading(place = "1.00,2.00", failure = failure, misses = 1))) {
            assertEquals("$failure", Again.WAIT, WeatherRules.again(r, age = 0, loading = false))
            assertEquals("$failure", Again.WAIT, WeatherRules.again(r, age = 10 * sec - 1, loading = false))
            assertEquals("$failure", Again.READY, WeatherRules.again(r, age = 10 * sec, loading = false))
        }
        // The service's own wait holds it longer, still without a word.
        val told = reading().copy(failure = Failure.SLOW_DOWN, retryAfterSec = 300)
        assertEquals(Again.WAIT, WeatherRules.again(told, age = 1 * min, loading = false))
        assertEquals(Again.WAIT, WeatherRules.again(told, age = 5 * min - 1, loading = false))
        assertEquals(Again.READY, WeatherRules.again(told, age = 5 * min, loading = false))
    }

    @Test fun whileARequestIsOnItsWayAPressAsksNothingAndNothingIsClaimed() {
        for (age in listOf(0L, 30 * sec, 5 * hour)) {
            // Not "up to date": what comes back is not known yet.
            assertEquals(Again.WAIT, WeatherRules.again(reading(), age, loading = true))
            assertEquals(Again.WAIT, WeatherRules.again(reading().copy(failure = Failure.NO_ANSWER), age, loading = true))
        }
        assertEquals(Again.WAIT, WeatherRules.again(null, null, loading = true))
        // Nothing known yet and nothing on its way: a press asks.
        assertEquals(Again.READY, WeatherRules.again(null, null, loading = false))
    }

    @Test fun threeHoursIsTheAgeAtWhichAReadingIsNoReading() {
        val r = reading(fetchedAt = at(10, 0))
        assertFalse(WeatherRules.old(r, at(10, 0)))
        assertFalse(WeatherRules.old(r, at(12, 59)))
        assertTrue(WeatherRules.old(r, at(13, 0)))
        assertTrue(WeatherRules.old(Reading(place = "1.00,2.00"), at(13, 0))) // never fetched
    }

    @Test fun aReadingIsOldWhenEitherClockSaysThreeHoursHavePassed() {
        // The clock on the wall can be set; the time since boot can't, but it starts again with every boot. A reading
        // carries both, and is old as soon as one of them says so.
        val up = 50 * hour
        val r = reading(fetchedAt = at(10, 0)).copy(fetchedUp = up)
        assertFalse(WeatherRules.old(r, at(12, 59), up + 3 * hour - min))
        assertTrue(WeatherRules.old(r, at(13, 0), up + 3 * hour))
        // The clock was set five hours back while offline: by the wall the reading is from the future, by the time since boot it is old.
        assertFalse(WeatherRules.old(r, at(5, 30), up + 30 * min))
        assertTrue(WeatherRules.old(r, at(8, 0), up + 3 * hour))
        // The clock was set four hours on: by the wall it is old at once.
        assertTrue(WeatherRules.old(r, at(14, 2), up + 2 * min))
        // After a restart of the device the time since boot has begun again: it says nothing until it has itself run three hours past the mark.
        assertFalse(WeatherRules.old(r, at(11, 0), 5 * min))
        assertTrue(WeatherRules.old(r, at(14, 0), 5 * min))
        // A reading that carries no such mark (a sample, one kept before there was one) goes by the wall alone.
        val plain = reading(fetchedAt = at(10, 0))
        assertFalse(WeatherRules.old(plain, at(12, 59), up + 100 * hour))
        assertTrue(WeatherRules.old(plain, at(13, 0), 0))
        // The state follows: old numbers with a failure are no reading, without one they are being asked for.
        assertEquals(Status.Missing(Failure.OFFLINE), WeatherRules.status(true, true, true, r.copy(failure = Failure.OFFLINE), at(8, 0), up + 3 * hour))
        assertEquals(Status.Loading, WeatherRules.status(true, true, true, r, at(8, 0), up + 3 * hour))
        assertEquals(Status.Live(r), WeatherRules.status(true, true, true, r, at(8, 0), up + 3 * hour - min))
    }

    @Test fun aReadingRemembersBothClocksOfTheMomentItWasRead() {
        val text = java.io.File("src/test/resources/openmeteo/forecast_zurich.json").readText()
        val r = WeatherRules.read(text, Place("47.37", "8.55"), now = at(13, 20), up = 7 * hour)!!
        assertEquals(at(13, 20), r.fetchedAt)
        assertEquals(7 * hour, r.fetchedUp)
        // Kept and read back, it still does: a restart of the app is not a restart of the device.
        assertEquals(r, WeatherRules.kept(WeatherRules.keep(r)))
        // What a reading kept before the mark existed says of it: nothing.
        assertEquals(0L, WeatherRules.kept("""{"place":"47.37,8.55","fetchedAt":1}""")!!.fetchedUp)
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
