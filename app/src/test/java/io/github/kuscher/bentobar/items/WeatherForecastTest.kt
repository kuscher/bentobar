package io.github.kuscher.bentobar.items

import io.github.kuscher.bentobar.util.Sym
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The menu with a reading, as the design draws it: San Francisco on a Monday at 2:45 PM, read at
 * 2:40 PM. Every line of that picture is compared here, to the character.
 */
class WeatherForecastTest {
    private val w = WeatherFileWords()
    private val sf = ZoneId.of("America/Los_Angeles")
    private val t = WeatherFileWords.times(sf)
    private val nbsp = "\u00A0"

    private fun at(h: Int, m: Int = 0, day: Int = 5): Long = ZonedDateTime.of(2026, 10, day, h, m, 0, 0, sf).toInstant().toEpochMilli()
    private fun sec(h: Int, m: Int = 0, day: Int = 5) = at(h, m, day) / 1000
    private val now = at(14, 45)

    /**
     * The picture's numbers as the service sends them: °C and km/h, and each hour's chance of rain filed under
     * the time that hour ends at (the temperature and the sky are of the entry's own time). So the 40% the
     * picture has under 5 PM is in the entry of 6 PM, and the 70% under 6 PM in the entry of 7 PM.
     */
    private fun design(): Reading = Reading(
        place = "37.77,-122.42", fetchedAt = at(14, 40), zone = "America/Los_Angeles", offsetSec = -7 * 3600,
        current = Current(sec(14, 30), temp = 22.2, feels = 20.0, code = 2, day = true, windKmh = 14.5),
        hours = listOf(
            Hour(sec(14), 22.4, 0, 2, true),
            Hour(sec(15), 22.2, 0, 2, true),    // 72°
            Hour(sec(16), 21.7, 0, 3, true),    // 71°
            Hour(sec(17), 20.6, 10, 3, true),   // 69°; 10% from 4 to 5
            Hour(sec(18), 18.9, 40, 61, true),  // 66°; 40% from 5 to 6
            Hour(sec(19), 17.8, 70, 2, false),  // 64°; 70% from 6 to 7
            Hour(sec(20), 16.7, 19, 0, false),  // 62°; 19% from 7 to 8
            Hour(sec(21), 16.0, 0, 0, false),
        ),
        days = listOf(
            Day(sec(0), 2, 25.6, 16.1, 70, sec(7, 8), sec(18, 42)),                              // today: 78° / 61°, 70% at its wettest
            Day(sec(0, day = 6), 63, 25.6, 16.1, 60, sec(7, 9, 6), sec(18, 41, 6)),              // Tue: 60%, 78° 61°
            Day(sec(0, day = 7), 1, 23.9, 15.0, 0, sec(7, 10, 7), sec(18, 39, 7)),               // Wed: 75° 59°
            Day(sec(0, day = 8), 0, 26.7, 16.7, 5, sec(7, 11, 8), sec(18, 38, 8)),               // Thu: 80° 62°
            Day(sec(0, day = 9), 0, 27.8, 17.2, 19, sec(7, 12, 9), sec(18, 36, 9)),              // Fri: 82° 63°
            Day(sec(0, day = 10), 3, 23.3, 15.6, 20, sec(7, 13, 10), sec(18, 35, 10)),           // Sat: 20%, 74° 60°
            Day(sec(0, day = 11), 3, 22.0, 15.0, 30, sec(7, 14, 11), sec(18, 34, 11)),
        ),
    )

    private fun look(fahrenheit: Boolean = true, miles: Boolean = fahrenheit, show: String = WeatherRules.SHOW_TEMP) =
        Look("San Francisco", null, show, fahrenheit, miles, rainHours = 2)

    private fun menu(r: Reading = design(), look: Look = look(), at: Long = now, times: Times = t) = WeatherRules.menu(r, look, at, w, times)

    @Test fun theHeader() {
        val m = menu()
        assertEquals(Sym.PARTLY_CLOUDY_DAY, m.glyph)
        assertEquals("Partly cloudy · feels like 68°", m.subtitle)
    }

    @Test fun theHeroRow() {
        val m = menu()
        assertEquals("72°", m.temp)
        assertEquals("High 78° · Low 61°", m.highLow)
        assertEquals("Rain 70% · Wind 9${nbsp}mph", m.rainWind)
        assertEquals("72 degrees. High 78, low 61. Rain, 70 percent chance. Wind 9 miles per hour.", m.heroDesc)
    }

    /**
     * Seen with a real forecast: rain at breakfast, a clear afternoon, and "Rain 90%" beside six dry
     * hours, because the service's figure for the day counts the hours that are over.
     */
    @Test fun theChanceIsForWhatIsLeftOfToday() {
        // An entry's chance is for the hour that ends at its time. Rain at breakfast, and rain between 1 and 2 PM
        // (the entry of 2 PM): at 2:45 PM both are over.
        val wetMorning = design().let { it.copy(hours = listOf(Hour(sec(7), 15.0, 90, 61, true), Hour(sec(14), 22.4, 85, 61, true)) + it.hours.drop(1),
            days = listOf(it.days[0].copy(chance = 90)) + it.days.drop(1)) }
        assertEquals("Rain 70% · Wind 9${nbsp}mph", menu(wetMorning).rainWind)
        // The hour that is running counts: it is 2:45 PM, and what the entry of 3 PM says may still fall.
        val running = design().let { it.copy(hours = it.hours.map { h -> if (h.at == sec(15)) h.copy(chance = 95) else h }) }
        assertEquals("Rain 95% · Wind 9${nbsp}mph", menu(running).rainWind)
        // The day's last hour is filed under midnight, tomorrow's first moment: it is still today's.
        val lastHour = design().let { it.copy(hours = it.hours + Hour(sec(0, day = 6), 14.0, 88, 63, false)) }
        assertEquals("Rain 88% · Wind 9${nbsp}mph", menu(lastHour).rainWind)
        // The hour after that is tomorrow's.
        val wetNight = design().let { it.copy(hours = it.hours + Hour(sec(1, day = 6), 14.0, 99, 63, false)) }
        assertEquals("Rain 70% · Wind 9${nbsp}mph", menu(wetNight).rainWind)
        // Late in the evening only what is left counts, and an hour that ends this minute is over.
        assertEquals("Rain 0% · Wind 9${nbsp}mph", menu(at = at(20, 30)).rainWind)
        assertEquals("Rain 70% · Wind 9${nbsp}mph", menu(at = at(18, 59)).rainWind)
        assertEquals("Rain 19% · Wind 9${nbsp}mph", menu(at = at(19, 0)).rainWind)
        // No hours for today (an old reading, a reply without them): the day's own figure.
        assertEquals("Rain 70% · Wind 9${nbsp}mph", menu(design().copy(hours = emptyList())).rainWind)
        assertEquals("Rain 35% · Wind 9${nbsp}mph", menu(design().let { it.copy(hours = emptyList(), days = listOf(it.days[0].copy(chance = 35)) + it.days.drop(1)) }).rainWind)
        // Hours that name no chance say nothing.
        assertEquals("Rain 35% · Wind 9${nbsp}mph",
            menu(design().let { it.copy(hours = it.hours.map { h -> h.copy(chance = null) }, days = listOf(it.days[0].copy(chance = 35)) + it.days.drop(1)) }).rainWind)
    }

    @Test fun theHeroIsAlwaysTheMeasuredTemperatureWhateverShowSays() {
        assertEquals("72°", menu(look = look(show = WeatherRules.SHOW_FEELS)).temp)
        assertEquals("72°", menu(look = look(show = WeatherRules.SHOW_HIGH_LOW)).temp)
    }

    @Test fun theNextHoursAreSixFromTheNextFullHour() {
        val m = menu()
        assertEquals(listOf("3 PM", "4 PM", "5 PM", "6 PM", "7 PM", "8 PM"), m.hours.map { it.time })
        assertEquals(listOf("72°", "71°", "69°", "66°", "64°", "62°"), m.hours.map { it.temp })
        // The chance of rain only from 20%.
        assertEquals(listOf(null, null, "40%", "70%", null, null), m.hours.map { it.chance })
        // By day and by night, in the city's time.
        assertEquals(listOf(Sym.PARTLY_CLOUDY_DAY, Sym.CLOUD, Sym.CLOUD, Sym.RAINY, Sym.PARTLY_CLOUDY_NIGHT, Sym.CLEAR_NIGHT), m.hours.map { it.glyph })
    }

    @Test fun aCellsChanceIsForTheHourThatStartsThere() {
        // A cell is a time, the temperature and the sky at that time, and the chance for the hour from then on:
        // the service files that under the time the hour ends at, so it is the next entry's.
        assertEquals("40%", menu().hours.single { it.time == "5 PM" }.chance)
        // 90% in the entry of 4 PM: likely between 3 and 4, so it stands under 3 PM, beside 3 PM's own temperature and sky.
        val moved = design().let { it.copy(hours = it.hours.map { h -> if (h.at == sec(16)) h.copy(chance = 90) else h }) }
        val cells = menu(moved).hours
        assertEquals(listOf("90%", null, "40%", "70%", null, null), cells.map { it.chance })
        assertEquals("3 PM, Partly cloudy, 72 degrees, 90 percent chance", cells[0].desc)
        assertEquals("4 PM, Cloudy, 71 degrees", cells[1].desc)
    }

    @Test fun theLastCellNeedsTheEntryAfterItForItsChance() {
        // 60% in the entry of 9 PM: likely between 8 and 9, which is the last of the six cells.
        val wetLate = design().let { it.copy(hours = it.hours.dropLast(1) + it.hours.last().copy(chance = 60)) }
        assertEquals("8 PM", menu(wetLate).hours.last().time)
        assertEquals("60%", menu(wetLate).hours.last().chance)
        // Without that entry nothing is known of the hour from 8 PM: the cell stays, without a chance.
        val short = wetLate.copy(hours = wetLate.hours.dropLast(1))
        assertEquals(6, menu(short).hours.size)
        assertNull(menu(short).hours.last().chance)
        // An entry missing in between leaves only the hour before it without a chance: entries are found by their time.
        val gap = design().let { it.copy(hours = it.hours.filter { h -> h.at != sec(19) } + Hour(sec(22), 15.0, 0, 0, false)) }
        assertEquals(listOf("3 PM", "4 PM", "5 PM", "6 PM", "8 PM", "9 PM"), menu(gap).hours.map { it.time })
        assertEquals(listOf(null, null, "40%", null, null, null), menu(gap).hours.map { it.chance })
    }

    @Test fun theHourThatHasBegunIsNotAmongTheNext() {
        assertEquals("3 PM", menu(at = at(14, 59)).hours.first().time)
        assertEquals("4 PM", menu(at = at(15, 0)).hours.first().time)
        assertEquals("4 PM", menu(at = at(15, 1)).hours.first().time)
    }

    @Test fun with24HoursTheHoursAreWrittenThatWay() {
        val m = menu(times = WeatherFileWords.times(sf, h24 = true))
        assertEquals(listOf("15:00", "16:00", "17:00", "18:00", "19:00", "20:00"), m.hours.map { it.time })
        assertEquals("07:08", m.sunrise)
        assertEquals("18:42", m.sunset)
        assertEquals("Weather data by Open-Meteo.com · updated 14:40", m.note)
    }

    @Test fun theNextDaysAreFiveFromTomorrowInTwoColumnsOfNumbers() {
        val m = menu()
        assertEquals(listOf("Tue", "Wed", "Thu", "Fri", "Sat"), m.days.map { it.day })
        assertEquals(listOf("78°", "75°", "80°", "82°", "74°"), m.days.map { it.high })
        assertEquals(listOf("61°", "59°", "62°", "63°", "60°"), m.days.map { it.low })
        assertEquals(listOf("60%", null, null, null, "20%"), m.days.map { it.chance })
        // A day's glyph is the day's, never the night's.
        assertEquals(listOf(Sym.RAINY, Sym.CLEAR_DAY, Sym.CLEAR_DAY, Sym.CLEAR_DAY, Sym.CLOUD), m.days.map { it.glyph })
    }

    @Test fun sunriseAndSunsetAreTodaysInTheCitysTime() {
        val m = menu()
        assertEquals("7:08 AM", m.sunrise)
        assertEquals("6:42 PM", m.sunset)
    }

    @Test fun theNoteCreditsTheServiceAndSaysWhenItWasRead() {
        assertEquals("Weather data by Open-Meteo.com · updated 2:40 PM", menu().note)
    }

    @Test fun notLiveOnlyTheEndOfTheNoteChangesAndTheCreditStays() {
        val offline = menu(design().copy(failure = Failure.OFFLINE))
        assertEquals("Weather data by Open-Meteo.com · no connection, updated 2:40 PM", offline.note)
        assertEquals("72°", offline.temp)
        assertEquals(6, offline.hours.size)
        assertEquals("Weather data by Open-Meteo.com · no answer, updated 2:40 PM", menu(design().copy(failure = Failure.NO_ANSWER)).note)
        assertEquals("Weather data by Open-Meteo.com · no answer, updated 2:40 PM", menu(design().copy(failure = Failure.SLOW_DOWN)).note)
    }

    @Test fun whatIsSpokenForAnHourAndForADay() {
        val m = menu()
        assertEquals("3 PM, Partly cloudy, 72 degrees", m.hours[0].desc)
        assertEquals("5 PM, Cloudy, 69 degrees, 40 percent chance", m.hours[2].desc)
        assertEquals("6 PM, Light rain, 66 degrees, 70 percent chance", m.hours[3].desc)
        assertEquals("Tuesday, Rain, high 78, low 61, 60 percent chance", m.days[0].desc)
        assertEquals("Wednesday, Mostly clear, high 75, low 59", m.days[1].desc)
    }

    @Test fun metricWhereTheRegionIsMetric() {
        val m = menu(look = look(fahrenheit = false))
        assertEquals("22°", m.temp)
        assertEquals("Partly cloudy · feels like 20°", m.subtitle)
        assertEquals("High 26° · Low 16°", m.highLow)
        assertEquals("Rain 70% · Wind 15${nbsp}km/h", m.rainWind)
        assertEquals(listOf("22°", "22°", "21°", "19°", "18°", "17°"), m.hours.map { it.temp })
        assertEquals("22 degrees. High 26, low 16. Rain, 70 percent chance. Wind 15 kilometers per hour.", m.heroDesc)
    }

    @Test fun inTheUnitedKingdomDegreesAreCelsiusAndWindIsInMiles() {
        val m = menu(look = look(fahrenheit = false, miles = true))
        assertEquals("22°", m.temp)
        assertEquals("Rain 70% · Wind 9${nbsp}mph", m.rainWind)
    }

    @Test fun britishSpellingWhereItDiffers() {
        val british = WeatherRules.menu(design(), look(fahrenheit = false), now, WeatherFileWords("values-en-rGB"), t)
        assertEquals("22 degrees. High 26, low 16. Rain, 70 percent chance. Wind 15 kilometres per hour.", british.heroDesc)
        val one = WeatherRules.menu(design().let { it.copy(current = it.current!!.copy(windKmh = 1.0)) }, look(fahrenheit = false), now, WeatherFileWords("values-en-rGB"), t)
        assertTrue(one.heroDesc, one.heroDesc.endsWith("Wind 1 kilometre per hour."))
    }

    @Test fun oneMileIsSingular() {
        val calm = menu(design().let { it.copy(current = it.current!!.copy(windKmh = 1.7)) })
        assertEquals("Rain 70% · Wind 1${nbsp}mph", calm.rainWind)
        assertTrue(calm.heroDesc, calm.heroDesc.endsWith("Wind 1 mile per hour."))
    }

    @Test fun onASnowDayTodaysChanceSaysSnow() {
        val r = design().let { it.copy(days = listOf(it.days[0].copy(code = 73)) + it.days.drop(1)) }
        val m = menu(r)
        assertEquals("Snow 70% · Wind 9${nbsp}mph", m.rainWind)
        assertTrue(m.heroDesc, m.heroDesc.contains(" Snow, 70 percent chance. "))
    }

    @Test fun theWidestHeroRowTheDesignMeasured() {
        val r = design().let { it.copy(current = it.current!!.copy(temp = 37.8, windKmh = 112.0), hours = it.hours.dropLast(1) + it.hours.last().copy(chance = 100), days = listOf(it.days[0].copy(chance = 100)) + it.days.drop(1)) }
        val m = menu(r, look(fahrenheit = true, miles = false))
        assertEquals("100°", m.temp)
        assertEquals("Rain 100% · Wind 112${nbsp}km/h", m.rainWind)
    }

    @Test fun belowZeroInTheMenu() {
        val r = design().let { it.copy(current = it.current!!.copy(temp = -24.4, feels = -30.0), days = listOf(it.days[0].copy(high = -20.0, low = -24.4)) + it.days.drop(1)) }
        val m = menu(r)
        assertEquals("−12°", m.temp)
        assertEquals("Partly cloudy · feels like −22°", m.subtitle)
        assertEquals("High −4° · Low −12°", m.highLow)
        assertTrue(m.heroDesc, m.heroDesc.startsWith("−12 degrees. High −4, low −12. "))
    }

    @Test fun theCitysOwnTimeIsInTheSubtitleOnlyWhenItIsInAnotherZone() {
        assertEquals("Partly cloudy · feels like 68°", menu().subtitle)
        // Another name for the same time is not another zone.
        assertEquals("Partly cloudy · feels like 68°", menu(design().copy(zone = "America/Vancouver")).subtitle)
        val denver = WeatherFileWords.times(ZoneId.of("America/Denver"))
        assertEquals("Partly cloudy · feels like 68° · 2:45 PM there", menu(times = denver).subtitle)
        // The note's time is the device's: read at 2:40 PM in San Francisco is 3:40 PM in Denver.
        assertEquals("Weather data by Open-Meteo.com · updated 3:40 PM", menu(times = denver).note)
    }

    // ---- missing values ------------------------------------------------------------------------

    @Test fun aValueMissingFromTheReplyLeavesItsCellOut() {
        val r = design().let { d ->
            d.copy(current = d.current!!.copy(feels = null),
                // 4 PM without its temperature, 5 PM without its sky, and no chance for the hour from 5 PM (which the entry of 6 PM would hold).
                hours = d.hours.map { when (it.at) { sec(16) -> it.copy(temp = null); sec(17) -> it.copy(code = null); sec(18) -> it.copy(chance = null); else -> it } },
                days = d.days.map { if (it.at == sec(0, day = 7)) it.copy(high = null, code = null) else it })
        }
        val m = menu(r)
        // Without what it feels like, the subtitle is the sky alone.
        assertEquals("Partly cloudy", m.subtitle)
        // The six cells keep their places; what is missing is left empty.
        assertEquals(listOf("3 PM", "4 PM", "5 PM", "6 PM", "7 PM", "8 PM"), m.hours.map { it.time })
        assertEquals(listOf("72°", null, "69°", "66°", "64°", "62°"), m.hours.map { it.temp })
        assertEquals(listOf(Sym.PARTLY_CLOUDY_DAY, Sym.CLOUD, null, Sym.RAINY, Sym.PARTLY_CLOUDY_NIGHT, Sym.CLEAR_NIGHT), m.hours.map { it.glyph })
        assertEquals(listOf(null, null, null, "70%", null, null), m.hours.map { it.chance })
        assertEquals("4 PM, Cloudy", m.hours[1].desc)
        assertEquals("5 PM, 69 degrees", m.hours[2].desc)
        assertEquals(listOf("78°", null, "80°", "82°", "74°"), m.days.map { it.high })
        assertEquals(listOf("61°", "59°", "62°", "63°", "60°"), m.days.map { it.low })
        assertNull(m.days[1].glyph)
        assertEquals("Wednesday", m.days[1].desc)
    }

    @Test fun withoutTodayInTheReplyTheLinesThatNeedItAreLeftOut() {
        val m = menu(design().let { it.copy(days = it.days.drop(1)) })
        assertNull(m.highLow)
        assertNull(m.rainWind)
        assertNull(m.sunrise)
        assertNull(m.sunset)
        assertEquals("72 degrees. Wind 9 miles per hour.", m.heroDesc)
        assertEquals(listOf("Tue", "Wed", "Thu", "Fri", "Sat"), m.days.map { it.day })
    }

    @Test fun withoutATemperatureTheHeroIsLeftOutAndTheRestStays() {
        val m = menu(design().let { it.copy(current = it.current!!.copy(temp = null)) })
        assertNull(m.temp)
        assertEquals("High 78° · Low 61°", m.highLow)
        assertEquals("High 78, low 61. Rain, 70 percent chance. Wind 9 miles per hour.", m.heroDesc)
    }

    @Test fun inPolarDayAndNightThereAreNoSunriseAndSunsetRows() {
        val noSun = design().let { it.copy(days = listOf(it.days[0].copy(sunrise = null, sunset = null)) + it.days.drop(1)) }
        val m = menu(noSun)
        assertNull(m.sunrise)
        assertNull(m.sunset)
        assertEquals("High 78° · Low 61°", m.highLow)
    }

    @Test fun onTheNightTheClocksChangeTheDaysAndHoursStayRight() {
        // Zurich, Sunday 25 October 2026: at 3:00 the clocks go back to 2:00, so the day has 25 hours and 2 AM comes twice.
        val zurich = ZoneId.of("Europe/Zurich")
        fun day(d: Int) = ZonedDateTime.of(2026, 10, d, 0, 0, 0, 0, zurich).toEpochSecond()
        val midnight = day(25)
        assertEquals(25 * 3600L, day(26) - midnight)
        val r = Reading(place = "47.37,8.55", fetchedAt = midnight * 1000, zone = "Europe/Zurich", offsetSec = 7200,
            current = Current(midnight, 10.0, 9.0, 3, false, 5.0),
            hours = (0 until 24).map { Hour(midnight + it * 3600L, 10.0, 0, 3, false) },
            days = (24..30).map { d -> Day(day(d), 3, 10.0 + d, d.toDouble(), 0, day(d) + 8 * 3600, day(d) + 17 * 3600) })
        val here = WeatherFileWords.times(zurich)
        val metric = look(fahrenheit = false)
        // 0:30: an hour and a half before the change.
        val before = WeatherRules.menu(r, metric, (midnight + 1800) * 1000, w, here)
        assertEquals("High 35° · Low 25°", before.highLow)
        assertEquals(listOf("Mon", "Tue", "Wed", "Thu", "Fri"), before.days.map { it.day })
        assertEquals(listOf("1 AM", "2 AM", "2 AM", "3 AM", "4 AM", "5 AM"), before.hours.map { it.time })
        // 2:30 for the second time, and the last minute of the long day: still Sunday, the same today and the same tomorrow.
        for (later in listOf(3 * 3600L + 1800, 25 * 3600L - 60)) {
            val m = WeatherRules.menu(r.copy(fetchedAt = (midnight + later) * 1000), metric, (midnight + later) * 1000, w, here)
            assertEquals("High 35° · Low 25°", m.highLow)
            assertEquals("Mon", m.days.first().day)
        }
        // And the first minute of Monday.
        val monday = WeatherRules.menu(r, metric, day(26) * 1000 + 60_000, w, here)
        assertEquals("High 36° · Low 26°", monday.highLow)
        assertEquals("Tue", monday.days.first().day)
    }

    @Test fun fewerHoursThanSixAreFewerCellsAndNeverPastOnes() {
        val late = menu(at = at(17, 30)) // read at 2:40 PM, looked at 5:30 PM: 6, 7, 8 and 9 PM are left
        assertEquals(listOf("6 PM", "7 PM", "8 PM", "9 PM"), late.hours.map { it.time })
        assertFalse(late.hours.any { it.time == "5 PM" })
    }

    @Test fun twoHoursOfOneTimeAreOneCell() {
        val twice = design().let { it.copy(hours = it.hours + it.hours) }
        assertEquals(listOf("3 PM", "4 PM", "5 PM", "6 PM", "7 PM", "8 PM"), menu(twice).hours.map { it.time })
    }
}
