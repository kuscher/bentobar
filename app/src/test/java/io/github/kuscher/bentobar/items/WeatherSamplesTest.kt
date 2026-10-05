package io.github.kuscher.bentobar.items

import io.github.kuscher.bentobar.util.Sym
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The made-up readings a tester stages on a device (`./bento debug weather stage rain-soon`): each
 * must show the row of the product's table it stands for, as that row is written.
 */
class WeatherSamplesTest {
    private val w = WeatherFileWords()
    private val sf = ZoneId.of("America/Los_Angeles")
    private val t = WeatherFileWords.times(sf)
    private val hour = 3_600_000L
    /** Monday 5 October 2026, 1:30 PM in San Francisco. */
    private val now = ZonedDateTime.of(2026, 10, 5, 13, 30, 0, 0, sf).toInstant().toEpochMilli()
    private val us = Look("San Francisco", null, WeatherRules.SHOW_TEMP, fahrenheit = true, miles = true, rainHours = 2)

    private fun staged(name: String, at: Long = now) = WeatherSamples.of(name, at, sf)!!
    private fun status(name: String, at: Long = now, look: Long = at) = WeatherRules.status(true, true, true, staged(name, at).reading, look)
    private fun bar(name: String, at: Long = now, seen: Long = at, look: Look = us) = WeatherRules.bar(status(name, at, seen), look, seen, w, t)

    @Test fun theNamesATesterUsesAllExist() {
        for (name in listOf("clear", "rain-soon", "raining", "storm", "snow", "old", "error", "slow-down", "offline", "loading"))
            assertNotNull(name, WeatherSamples.of(name, now, sf))
        for (name in WeatherSamples.names) assertEquals(name, WeatherSamples.of(name, now, sf)!!.name)
        assertNull(WeatherSamples.of("sunny", now, sf))
        assertNull(WeatherSamples.of("", now, sf))
    }

    @Test fun clearIsTheTemperatureAlone() {
        val b = bar("clear")
        assertEquals("72°", b.text)
        assertEquals(Sym.CLEAR_DAY, b.icon)
        assertEquals(Tone.NORMAL, b.tone)
        assertFalse(b.active)
        assertEquals("San Francisco: 72 degrees. Clear.", b.desc)
        // Staged after dark it is the night's glyph.
        assertEquals(Sym.CLEAR_NIGHT, bar("clear", at = now + 8 * hour).icon)
    }

    @Test fun rainSoonIsAnEightyPercentChanceInNinetyMinutes() {
        val b = bar("rain-soon")
        assertEquals("72° · Rain 3 PM", b.text)
        assertEquals(Tone.ACCENT, b.tone)
        assertTrue(b.active)
        assertEquals(Sym.RAINY, b.icon)
        val wet = staged("rain-soon").reading!!.hours.single { (it.chance ?: 0) >= 50 }
        assertEquals(80, wet.chance)
        assertEquals(now + 90 * 60_000L, WeatherRules.begins(wet) * 1000)
        // The bar and the menu agree on the hour: the 80% stands under 3 PM, and what is left of today is 80% at its wettest.
        val m = WeatherRules.menu(staged("rain-soon").reading!!, us, now, w, t)
        assertEquals(listOf("2 PM", "3 PM", "4 PM", "5 PM", "6 PM", "7 PM"), m.hours.map { it.time })
        assertEquals(listOf(null, "80%", null, null, null, null), m.hours.map { it.chance })
        assertTrue(m.rainWind, m.rainWind!!.startsWith("Rain 80%"))
        // Whenever it is staged, the hour lies within the rule's two hours and beyond one.
        for (minute in listOf(0, 1, 29, 59)) {
            val at = ZonedDateTime.of(2026, 10, 5, 9, minute, 0, 0, sf).toInstant().toEpochMilli()
            assertEquals("staged at 9:$minute", "72° · Rain 11 AM", bar("rain-soon", at).text)
            assertFalse(bar("rain-soon", at, look = Look("San Francisco", null, WeatherRules.SHOW_TEMP, true, true, rainHours = 1)).active)
        }
    }

    @Test fun rainThisHourIsLikelyInTheHourThatIsRunning() {
        val b = bar("rain-this-hour")
        assertEquals("72° · Rain", b.text)
        assertEquals("San Francisco: 72 degrees. Partly cloudy. Rain likely this hour.", b.desc)
        assertEquals(Tone.ACCENT, b.tone)
        assertTrue(b.active)
        assertEquals(Sym.RAINY, b.icon)
        // Staged at 1:30 PM, the hour is the one from 1 to 2: at 2 PM it is over.
        assertEquals("72° · Rain", bar("rain-this-hour", seen = now + 29 * 60_000L).text)
        assertEquals("72°", bar("rain-this-hour", seen = now + 30 * 60_000L).text)
        // And rain-soon, left staged, becomes it: at 3 PM its hour begins and its time goes.
        assertEquals("72° · Rain 3 PM", bar("rain-soon", seen = now + 89 * 60_000L).text)
        assertEquals("72° · Rain", bar("rain-soon", seen = now + 90 * 60_000L).text)
        assertEquals("72°", bar("rain-soon", seen = now + 150 * 60_000L).text)
    }

    @Test fun rainLaterLeavesTheItemHidden() {
        val b = bar("rain-later")
        assertEquals("72°", b.text)
        assertFalse(b.active)
        assertEquals(Tone.NORMAL, b.tone)
        val wet = staged("rain-later").reading!!.hours.single { (it.chance ?: 0) >= 50 }
        assertTrue(WeatherRules.begins(wet) * 1000 - now >= 4 * hour)
    }

    @Test fun rainingStormAndSnowSayWhatFalls() {
        assertEquals("72° · Rain", bar("raining").text)
        assertEquals(Tone.ACCENT, bar("raining").tone)
        assertTrue(bar("raining").active)
        assertEquals("72° · Storm", bar("storm").text)
        assertEquals(Tone.WARN, bar("storm").tone)
        assertEquals(Sym.THUNDERSTORM, bar("storm").icon)
        assertEquals("−4° · Snow", bar("snow").text)
        assertEquals(Tone.ACCENT, bar("snow").tone)
        assertEquals(Sym.WEATHER_SNOWY, bar("snow").icon)
        // On the snow day the menu's chance says "Snow". While something falls the hours ahead are likely wet too:
        // a menu that read "Rain 0%" under the rain would contradict itself.
        assertTrue(WeatherRules.menu(staged("snow").reading!!, us, now, w, t).rainWind!!.startsWith("Snow 90%"))
        val raining = WeatherRules.menu(staged("raining").reading!!, us, now, w, t)
        assertTrue(raining.rainWind, raining.rainWind!!.startsWith("Rain 90%"))
        assertEquals(listOf("80%", "60%", null, null, null, null), raining.hours.map { it.chance })
    }

    @Test fun oldIsNoReadingForThreeHours() {
        val b = bar("old")
        assertEquals(Sym.CLOUD_OFF, b.icon)
        assertFalse(b.filled)
        assertNull(b.text)
        assertEquals(Status.Missing(Failure.OFFLINE), status("old"))
    }

    @Test fun errorAndSlowDownAreTheTwoNoAnswerStates() {
        assertEquals(Status.Missing(Failure.NO_ANSWER), status("error"))
        assertEquals(Status.Missing(Failure.SLOW_DOWN), status("slow-down"))
        for (name in listOf("error", "slow-down")) {
            assertEquals(Sym.CLOUD_OFF, bar(name).icon)
            assertNull(bar(name).text) // a state with words, never "!"
        }
        assertEquals(Status.Missing(Failure.OFFLINE), status("offline-new"))
    }

    @Test fun offlineKeepsItsReadingAndSaysSoInTheNote() {
        val s = status("offline") as Status.Live
        assertEquals("72°", bar("offline").text)
        assertEquals("Weather data by Open-Meteo.com · no connection, updated 1:30 PM", WeatherRules.menu(s.reading, us, now, w, t).note)
        // With the clock staged three hours on, the number goes.
        assertEquals(Sym.CLOUD_OFF, bar("offline", seen = now + 3 * hour).icon)
        assertNull(bar("offline", seen = now + 3 * hour).text)
        assertEquals("72°", bar("offline", seen = now + 3 * hour - 60_000).text)
        val noAnswer = status("no-answer") as Status.Live
        assertEquals("Weather data by Open-Meteo.com · no answer, updated 1:30 PM", WeatherRules.menu(noAnswer.reading, us, now, w, t).note)
    }

    @Test fun loadingIsTheOutlinedCloud() {
        assertNull(staged("loading").reading)
        assertEquals(Status.Loading, status("loading"))
        assertEquals(Sym.CLOUD, bar("loading").icon)
        assertFalse(bar("loading").filled)
    }

    @Test fun theStagedMenuIsTheDesignsPicture() {
        // Staged at 2:45 PM, the menu reads as the design draws it.
        val at = ZonedDateTime.of(2026, 10, 5, 14, 45, 0, 0, sf).toInstant().toEpochMilli()
        val m = WeatherRules.menu(staged("clear", at).reading!!, us, at, w, t)
        assertEquals("72°", m.temp)
        assertEquals("Clear · feels like 68°", m.subtitle)
        assertEquals("High 78° · Low 61°", m.highLow)
        assertEquals(listOf("3 PM", "4 PM", "5 PM", "6 PM", "7 PM", "8 PM"), m.hours.map { it.time })
        assertEquals(listOf("72°", "71°", "69°", "66°", "64°", "62°"), m.hours.map { it.temp })
        assertEquals(listOf("Tue", "Wed", "Thu", "Fri", "Sat"), m.days.map { it.day })
        assertEquals(listOf("78°", "75°", "80°", "82°", "74°"), m.days.map { it.high })
        assertEquals(listOf("61°", "59°", "62°", "63°", "60°"), m.days.map { it.low })
        assertEquals(listOf("60%", null, null, null, "20%"), m.days.map { it.chance })
        assertEquals("7:08 AM", m.sunrise)
        assertEquals("6:42 PM", m.sunset)
        assertEquals("Weather data by Open-Meteo.com · updated 2:45 PM", m.note)
    }

    @Test fun aSampleNamesNoRealPlaceAndIsInTheZoneItIsStagedIn() {
        for (name in WeatherSamples.names) staged(name).reading?.let { r ->
            assertEquals(WeatherSamples.PLACE, r.place)
            if (r.current != null) assertEquals(sf, WeatherRules.zone(r))
        }
        val tokyo = ZoneId.of("Asia/Tokyo")
        assertEquals(tokyo, WeatherRules.zone(WeatherSamples.of("clear", now, tokyo)!!.reading!!))
        // The menu's title for an item that has no city yet is the one the design draws; it is a title and nothing else.
        assertEquals("San Francisco", WeatherSamples.CITY)
    }

    @Test fun aStagedFailuresRetryIsThereAfterTenSecondsAndAStagedReadingsRefreshAfterAMinute() {
        // Counted from the moment a sample is staged, as from a try that just came back. No sample has a wait of the service's.
        val sec = 1_000L
        val failed = listOf("old", "error", "slow-down", "offline", "offline-new", "no-answer")
        val answered = listOf("clear", "rain-soon", "rain-this-hour", "rain-later", "raining", "storm", "snow")
        for (name in failed) {
            val r = staged(name).reading
            assertEquals(name, Again.WAIT, WeatherRules.again(r, age = 0, loading = false))
            assertEquals(name, Again.WAIT, WeatherRules.again(r, age = 10 * sec - 1, loading = false))
            assertEquals(name, Again.READY, WeatherRules.again(r, age = 10 * sec, loading = false))
        }
        for (name in answered) {
            val r = staged(name).reading
            assertEquals(name, Again.UP_TO_DATE, WeatherRules.again(r, age = 0, loading = false))
            assertEquals(name, Again.UP_TO_DATE, WeatherRules.again(r, age = 60 * sec - 1, loading = false))
            assertEquals(name, Again.READY, WeatherRules.again(r, age = 60 * sec, loading = false))
        }
        // Every sample is one of the two, or the one without a reading (whose menu has no such entry).
        assertEquals(WeatherSamples.names.toSet(), (failed + answered + "loading").toSet())
        assertNull(staged("loading").reading)
    }
}
