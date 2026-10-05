package io.github.kuscher.bentobar.items

import io.github.kuscher.bentobar.util.Sym
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.ZoneId

/**
 * Reading what Open-Meteo sends. The files under `test/resources/openmeteo` are real replies, saved
 * on 5 October 2026 and trimmed of what nobody reads (the unit names, a place's postcodes). What a
 * reply could hold but these don't (a missing value, another shape) is made from them here.
 */
class WeatherReplyTest {
    private fun file(name: String) = File("src/test/resources/openmeteo/$name").readText()
    private val zurichPlace = Place("47.37", "8.55")
    /** When the Zurich reply was fetched: 2026-10-05 09:25:25 UTC, 11:25 in Zurich. */
    private val fetched = 1791192325_000L
    private fun zurich() = WeatherRules.read(file("forecast_zurich.json"), zurichPlace, fetched)!!

    private val w = WeatherFileWords()
    private val here = ZoneId.of("America/Los_Angeles")
    private val t = WeatherFileWords.times(here)
    private fun look(city: String, fahrenheit: Boolean = false) = Look(city, null, WeatherRules.SHOW_TEMP, fahrenheit, fahrenheit, rainHours = 2)

    // ---- the forecast --------------------------------------------------------------------------

    @Test fun aRealForecastIsRead() {
        val r = zurich()
        assertEquals("47.37,8.55", r.place)
        assertEquals(fetched, r.fetchedAt)
        assertEquals("Europe/Zurich", r.zone)
        assertEquals(7200, r.offsetSec)
        assertNull(r.failure)
        assertEquals(Current(at = 1791191700, temp = 18.1, feels = 18.5, code = 1, day = true, windKmh = 2.8), r.current)
        assertEquals(24, r.hours.size)
        assertEquals(Hour(at = 1791190800, temp = 17.8, chance = 5, code = 1, day = true), r.hours.first())
        assertEquals(Hour(at = 1791273600, temp = 13.8, chance = 0, code = 1, day = true), r.hours.last())
        assertEquals(Hour(at = 1791219600, temp = 20.2, chance = 0, code = 0, day = false), r.hours[8]) // 7 PM, after sunset
        assertEquals(7, r.days.size)
        assertEquals(Day(at = 1791151200, code = 80, high = 22.0, low = 14.9, chance = 90, sunrise = 1791178200, sunset = 1791219448), r.days.first())
        assertEquals(Day(at = 1791669600, code = 3, high = 14.6, low = 8.9, chance = 43, sunrise = 1791697100, sunset = 1791737157), r.days.last())
    }

    @Test fun aRealForecastAsTheBarAndTheMenuShowIt() {
        val r = zurich()
        // 2:25 AM in San Francisco, 11:25 AM in Zurich.
        val bar = WeatherRules.bar(WeatherRules.status(true, true, true, r, fetched), look("Zurich"), fetched, w, t)
        assertEquals("18°", bar.text)
        assertEquals(Sym.CLEAR_DAY, bar.icon)
        assertFalse(bar.active)
        assertEquals("Zurich: 18 degrees. Mostly clear.", bar.desc)
        assertEquals("65°", WeatherRules.bar(WeatherRules.status(true, true, true, r, fetched), look("Zurich", fahrenheit = true), fetched, w, t).text)

        val m = WeatherRules.menu(r, look("Zurich"), fetched, w, t)
        assertEquals("Mostly clear · feels like 19° · 11:25 AM there", m.subtitle)
        assertEquals("18°", m.temp)
        assertEquals("High 22° · Low 15°", m.highLow)
        assertEquals("Rain 90% · Wind 3\u00A0km/h", m.rainWind)
        // The hours are Zurich's, from the next full one.
        assertEquals(listOf("12 PM", "1 PM", "2 PM", "3 PM", "4 PM", "5 PM"), m.hours.map { it.time })
        assertEquals(listOf("19°", "21°", "22°", "22°", "22°", "22°"), m.hours.map { it.temp })
        assertEquals(listOf("Tue", "Wed", "Thu", "Fri", "Sat"), m.days.map { it.day })
        assertEquals(listOf("22°", "23°", "17°", "13°", "14°"), m.days.map { it.high })
        assertEquals(listOf("12°", "13°", "10°", "8°", "11°"), m.days.map { it.low })
        assertEquals(listOf(null, null, "93%", "41%", "41%"), m.days.map { it.chance })
        assertEquals(listOf(Sym.FOGGY, Sym.CLOUD, Sym.RAINY, Sym.CLOUD, Sym.RAINY), m.days.map { it.glyph })
        assertEquals("7:30 AM", m.sunrise)
        assertEquals("6:57 PM", m.sunset)
        assertEquals("Weather data by Open-Meteo.com · updated 2:25 AM", m.note)
    }

    @Test fun inPolarDayTheServiceSendsNoRealSunriseAndTheRowsAreLeftOut() {
        // The South Pole in October: the sun is up all day. The service then sends the day's start and its end.
        val text = file("forecast_polar_day.json")
        assertTrue(text.contains("\"sunrise\":[1791111600") && text.contains("\"sunset\":[1791198000"))
        val r = WeatherRules.read(text, Place("-90.00", "0.00"), fetched)!!
        assertEquals("Antarctica/McMurdo", r.zone)
        assertEquals(7, r.days.size)
        for (d in r.days) { assertNull(d.sunrise); assertNull(d.sunset) }
        assertEquals(-41.9, r.current!!.temp!!, 1e-9)
        val m = WeatherRules.menu(r, look("South Pole"), fetched, w, t)
        assertNull(m.sunrise)
        assertNull(m.sunset)
        assertEquals("−42°", m.temp)
        assertEquals("Cloudy · feels like −49° · 10:25 PM there", m.subtitle)
        // Today there (it is 10:25 PM on the same Monday) is a day of light snow: the chance says "Snow".
        assertEquals("High −41° · Low −45°", m.highLow)
        assertEquals("Snow 59% · Wind 21\u00A0km/h", m.rainWind)
    }

    @Test fun inPolarNightTheServiceSendsNoRealSunriseAndTheRowsAreLeftOut() {
        // Near the North Pole in October: the sun stays down. Sunrise and sunset are then the same moment.
        val text = file("forecast_polar_night.json")
        assertTrue(text.contains("\"sunrise\":[1791158400") && text.contains("\"sunset\":[1791158400"))
        val r = WeatherRules.read(text, Place("89.99", "0.00"), fetched)!!
        assertEquals("Etc/GMT", r.zone)
        for (d in r.days) { assertNull(d.sunrise); assertNull(d.sunset) }
        assertFalse(r.current!!.day)
        val m = WeatherRules.menu(r, look("North Pole"), fetched, w, t)
        assertNull(m.sunrise)
        assertNull(m.sunset)
        assertEquals("−9°", m.temp)
    }

    @Test fun aRealSunriseAndSunsetAreKept() {
        for (d in zurich().days) { assertNotNull(d.sunrise); assertNotNull(d.sunset); assertTrue(d.sunset!! > d.sunrise!!) }
    }

    @Test fun aValueMissingFromTheReplyLeavesItsCellOutAndNothingElse() {
        val text = file("forecast_zurich.json")
            .replace("\"temperature_2m\":[17.8,19.1,20.5,", "\"temperature_2m\":[17.8,null,20.5,")
            .replace("\"precipitation_probability\":[5,0,0,", "\"precipitation_probability\":[5,0,null,")
            .replace("\"weather_code\":[1,1,1,1,2,", "\"weather_code\":[1,1,1,null,2,")
            .replace("\"temperature_2m_max\":[22.0,22.3,", "\"temperature_2m_max\":[22.0,null,")
            .replace("\"sunset\":[1791219448,", "\"sunset\":[null,")
            .replace("\"apparent_temperature\":18.5,", "\"apparent_temperature\":null,")
        assertTrue(text != file("forecast_zurich.json"))
        val r = WeatherRules.read(text, zurichPlace, fetched)!!
        assertEquals(24, r.hours.size)
        assertNull(r.hours[1].temp)
        assertEquals(20.5, r.hours[2].temp!!, 1e-9)
        assertNull(r.hours[2].chance)
        assertNull(r.hours[3].code)
        assertEquals(0, r.hours[3].chance)
        assertNull(r.days[1].high)
        assertEquals(11.6, r.days[1].low!!, 1e-9)
        // A sunrise without its sunset is no pair: neither row is drawn.
        assertNull(r.days[0].sunrise)
        assertNull(r.days[0].sunset)
        assertNull(r.current!!.feels)
        assertEquals(18.1, r.current!!.temp!!, 1e-9)
    }

    @Test fun listsOfDifferentLengthsAreReadAsFarAsTheyGo() {
        val text = file("forecast_zurich.json").replace(Regex("\"is_day\":\\[1,1,1,1,1,1,1,1,0,[01,]+]"), "\"is_day\":[1,1]")
            .replace(Regex("\"temperature_2m_min\":\\[[^]]+]"), "\"temperature_2m_min\":[14.9]")
        val r = WeatherRules.read(text, zurichPlace, fetched)!!
        assertEquals(24, r.hours.size)
        assertEquals(7, r.days.size)
        assertEquals(14.9, r.days[0].low!!, 1e-9)
        assertNull(r.days[1].low)
        assertEquals(22.3, r.days[1].high!!, 1e-9)
    }

    @Test fun valuesOfAnotherKindOrBeyondAllSenseAreLeftOut() {
        val text = file("forecast_zurich.json")
            .replace("\"temperature_2m\":18.1,", "\"temperature_2m\":\"warm\",")
            .replace("\"weather_code\":1,\"wind_speed_10m\":2.8", "\"weather_code\":1.0,\"wind_speed_10m\":1e300")
            .replace("\"temperature_2m_max\":[22.0,", "\"temperature_2m_max\":[9000,")
            .replace("\"precipitation_probability_max\":[90,", "\"precipitation_probability_max\":[250,")
            .replace("\"timezone\":\"Europe/Zurich\"", "\"timezone\":12")
        val r = WeatherRules.read(text, zurichPlace, fetched)!!
        assertNull(r.current!!.temp)
        assertEquals(1, r.current!!.code)
        assertNull(r.current!!.windKmh)
        assertNull(r.days[0].high)
        assertNull(r.days[0].chance)
        assertEquals("", r.zone)
        assertEquals(7200, WeatherRules.zone(r).rules.getOffset(Instant.EPOCH).totalSeconds)
    }

    @Test fun whatIsNoForecastIsNoReading() {
        // An error's body, should it ever come with a good status; a sign-in page; half a reply; nothing.
        assertNull(WeatherRules.read(file("error_400.json"), zurichPlace, fetched))
        assertNull(WeatherRules.read("<html><body>Sign in to this network</body></html>", zurichPlace, fetched))
        assertNull(WeatherRules.read(file("forecast_zurich.json").take(700), zurichPlace, fetched))
        assertNull(WeatherRules.read("", zurichPlace, fetched))
        assertNull(WeatherRules.read("null", zurichPlace, fetched))
        assertNull(WeatherRules.read("[1,2,3]", zurichPlace, fetched))
        assertNull(WeatherRules.read("{}", zurichPlace, fetched))
        assertNull(WeatherRules.read("{\"current\":null}", zurichPlace, fetched))
        assertNull(WeatherRules.read("{\"current\":[18.1]}", zurichPlace, fetched))
        assertNull(WeatherRules.read("{\"hourly\":{\"time\":[1791190800]}}", zurichPlace, fetched))
        assertNull(WeatherRules.read("{\"current\":{\"time\":1791191700},\"hourly\":{},\"daily\":{}}", zurichPlace, fetched))
        assertNull(WeatherRules.read("[".repeat(20_000), zurichPlace, fetched))
    }

    @Test fun theLargestReplyTheRequestHelperLetsThroughIsReadOrRefusedNeverACrash() {
        // 256 KB at most arrive. Nested as deep as that allows, or as wide: no forecast, and no crash.
        val max = 256 * 1024
        assertNull(WeatherRules.read("[".repeat(max), zurichPlace, fetched))
        assertNull(WeatherRules.read("{\"current\":".repeat(max / 11), zurichPlace, fetched))
        assertNull(WeatherRules.cities("{\"results\":[".repeat(max / 12)))
        assertNull(WeatherRules.cities("[".repeat(max)))
        val wide = "{\"current\":{\"temperature_2m\":3,\"weather_code\":" + "9".repeat(max - 100) + "},\"hourly\":{\"time\":[" + "1,".repeat(1000) + "1]}}"
        val r = WeatherRules.read(wide, zurichPlace, fetched)!!
        assertNull(r.current!!.code) // a number of a quarter of a million digits is no weather code
        assertEquals(3.0, r.current!!.temp!!, 1e-9)
        assertTrue(r.hours.size <= WeatherRules.MAX_HOURS)
        val long = "x".repeat(max - 200)
        val places = WeatherRules.cities("{\"results\":[{\"name\":\"$long\",\"latitude\":1,\"longitude\":2,\"admin1\":\"$long\"}]}")!!
        assertEquals(WeatherRules.NAME_CHARS, places.single().name.length)
    }

    @Test fun aReplyOfTheRightShapeWithLittleInItIsStillAReading() {
        val r = WeatherRules.read("{\"current\":{\"time\":1791191700,\"temperature_2m\":3},\"hourly\":\"none\",\"daily\":{\"time\":7}}", zurichPlace, fetched)!!
        assertEquals(3.0, r.current!!.temp!!, 1e-9)
        assertTrue(r.current!!.day) // not said: day
        assertEquals(emptyList<Hour>(), r.hours)
        assertEquals(emptyList<Day>(), r.days)
        assertEquals("3°", WeatherRules.bar(WeatherRules.status(true, true, true, r, fetched), look("Zurich"), fetched, w, t).text)
    }

    @Test fun noMoreIsKeptThanTheMenuCanShow() {
        val many = (0 until 5000).joinToString(",") { (1791190800L + it * 3600).toString() }
        val r = WeatherRules.read("{\"current\":{\"temperature_2m\":3},\"hourly\":{\"time\":[$many]},\"daily\":{\"time\":[$many]}}", zurichPlace, fetched)!!
        assertEquals(WeatherRules.MAX_HOURS, r.hours.size)
        assertEquals(WeatherRules.MAX_DAYS, r.days.size)
    }

    @Test fun aReadingSurvivesBeingKeptAndReadBack() {
        val r = zurich()
        val kept = WeatherRules.keep(r)
        assertEquals(r, WeatherRules.kept(kept))
        // What is kept is the app's own model, nothing of the reply's text.
        assertFalse(kept.contains("generationtime_ms"))
        assertFalse(kept.contains("temperature_2m"))
        assertNull(WeatherRules.kept("not a reading"))
        assertNull(WeatherRules.kept("{}"))
        assertNull(WeatherRules.kept(file("forecast_zurich.json")))
    }

    // ---- the search ----------------------------------------------------------------------------

    @Test fun aRealSearchListsPlacesThatCanBeToldApart() {
        val found = WeatherRules.cities(file("search_springfield.json"))!!
        assertEquals(5, found.size)
        assertEquals(City("Springfield", "Missouri", "United States", "37.22", "-93.30", "America/Chicago"), found[0])
        assertEquals(City("Springfield", "Illinois", "United States", "39.80", "-89.64", "America/Chicago"), found[1])
        assertEquals(listOf("Missouri", "Illinois", "Massachusetts", "Ohio", "Tennessee"), found.map { it.region })
        assertEquals(5, found.map { WeatherRules.region(it, w) }.toSet().size)
        assertEquals("Illinois, United States", WeatherRules.region(found[1], w))
        // What a place stores for its forecast is already rounded to two decimals.
        for (c in found) { assertTrue(c.lat, c.lat.matches(Regex("-?\\d{1,2}\\.\\d{2}"))); assertTrue(c.lon, c.lon.matches(Regex("-?\\d{1,3}\\.\\d{2}"))) }
    }

    @Test fun noMatchIsAnEmptyListNotAFailure() {
        assertEquals(emptyList<City>(), WeatherRules.cities(file("search_none.json")))
        assertEquals(emptyList<City>(), WeatherRules.cities("{\"results\":[]}"))
    }

    @Test fun aPlaceIsNamedByThePartsTheReplyHas() {
        val text = """{"results":[
            {"name":"Monaco","latitude":43.73333,"longitude":7.41667,"country":"Monaco","timezone":"Europe/Monaco"},
            {"name":"Atlantis","latitude":0.0,"longitude":-30.0},
            {"name":"Springfield","latitude":39.80172,"longitude":-89.64371,"admin1":"Illinois"}]}"""
        val found = WeatherRules.cities(text)!!
        assertEquals("Monaco", WeatherRules.region(found[0], w))
        assertNull(WeatherRules.region(found[1], w))
        assertEquals("", found[1].zone)
        assertEquals("Illinois", WeatherRules.region(found[2], w))
    }

    @Test fun whatIsNoPlaceIsLeftOutOfTheList() {
        val text = """{"results":[
            {"name":"","latitude":1,"longitude":2},
            {"latitude":1,"longitude":2},
            {"name":"No coordinates"},
            {"name":"Off the map","latitude":91,"longitude":2},
            {"name":"Words","latitude":"north","longitude":2},
            "a string", null, 7,
            {"name":"Real","latitude":1.004,"longitude":2.006,"admin1":7,"country":["x"],"timezone":"Africa/Lagos"}]}"""
        val found = WeatherRules.cities(text)!!
        assertEquals(listOf(City("Real", "", "", "1.00", "2.01", "Africa/Lagos")), found)
    }

    @Test fun aNameFromTheNetworkIsOneShortLine() {
        val long = "x".repeat(5000)
        val text = "{\"results\":[{\"name\":\"New\\nYork\\t City\",\"latitude\":40.7,\"longitude\":-74,\"admin1\":\"$long\",\"country\":\"$long\",\"timezone\":\"$long\"}]}"
        val c = WeatherRules.cities(text)!!.single()
        assertEquals("New York City", c.name)
        assertEquals(WeatherRules.NAME_CHARS, c.region.length)
        assertEquals(WeatherRules.NAME_CHARS, c.country.length)
        assertEquals("", c.zone) // no zone has a name that long
    }

    @Test fun atMostFivePlacesAndNoneTwice() {
        val one = "{\"name\":\"Springfield\",\"latitude\":39.80172,\"longitude\":-89.64371,\"admin1\":\"Illinois\"}"
        val other = (1..9).joinToString(",") { "{\"name\":\"Town $it\",\"latitude\":$it,\"longitude\":$it}" }
        val found = WeatherRules.cities("{\"results\":[$one,$one,$other]}")!!
        assertEquals(5, found.size)
        assertEquals(listOf("Springfield", "Town 1", "Town 2", "Town 3", "Town 4"), found.map { it.name })
    }

    @Test fun aSearchReplyOfAnotherShapeIsNoAnswer() {
        assertNull(WeatherRules.cities("<html>Sign in</html>"))
        assertNull(WeatherRules.cities(""))
        assertNull(WeatherRules.cities("[]"))
        assertNull(WeatherRules.cities("{\"results\":\"none\"}"))
        assertNull(WeatherRules.cities(file("error_400.json")))
    }
}
