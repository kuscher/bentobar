package io.github.kuscher.bentobar.items

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Weather item's text, word for word as the design's copy deck has it, and the link between the
 * rules' words and the app's resources: the rules are tested with the text files, the app reads
 * resources by number, and this is what holds the two together.
 */
class WeatherStringsTest {
    private val files = WeatherFileWords()
    private val nbsp = "\u00A0"

    /** The copy deck, table 9.6: the resource and its US English text. */
    private val deck = mapOf(
        "item_weather_title" to "Weather",
        "item_weather_desc" to "Temperature and conditions for a city you pick or where you are; forecast in its menu",
        "trigger_weather" to "Show when rain or snow is falling, or likely within %1\$s",
        "trigger_weather_short" to "shows before rain or snow",
        "weather_clear" to "Clear",
        "weather_mostly_clear" to "Mostly clear",
        "weather_partly_cloudy" to "Partly cloudy",
        "weather_cloudy" to "Cloudy",
        "weather_fog" to "Fog",
        "weather_drizzle" to "Drizzle",
        "weather_freezing_drizzle" to "Freezing drizzle",
        "weather_light_rain" to "Light rain",
        "weather_rain" to "Rain",
        "weather_heavy_rain" to "Heavy rain",
        "weather_freezing_rain" to "Freezing rain",
        "weather_light_snow" to "Light snow",
        "weather_snow" to "Snow",
        "weather_heavy_snow" to "Heavy snow",
        "weather_showers" to "Showers",
        "weather_heavy_showers" to "Heavy showers",
        "weather_snow_showers" to "Snow showers",
        "weather_thunderstorm" to "Thunderstorm",
        "weather_thunderstorm_hail" to "Thunderstorm with hail",
        "weather_bar_rain" to "Rain",
        "weather_bar_snow" to "Snow",
        "weather_bar_storm" to "Storm",
        "weather_bar_soon" to "%1\$s · %2\$s %3\$s",
        "weather_bar_now" to "%1\$s · %2\$s",
        "weather_bar_high_low" to "%1\$s ↑%2\$s ↓%3\$s",
        "weather_bar_label" to "%1\$s %2\$s",
        "weather_tooltip" to "%1\$s · %2\$s",
        "weather_subtitle" to "%1\$s · feels like %2\$s",
        "weather_subtitle_there" to "%1\$s · feels like %2\$s · %3\$s there",
        "weather_high_low" to "High %1\$s · Low %2\$s",
        "weather_rain_wind" to "Rain %1\$d%% · Wind %2\$s",
        "weather_snow_wind" to "Snow %1\$d%% · Wind %2\$s",
        "weather_wind_mph" to "%1\$d${nbsp}mph",
        "weather_wind_kmh" to "%1\$d${nbsp}km/h",
        "weather_chance" to "%1\$d%%",
        "weather_next_hours" to "Next hours",
        "weather_next_days" to "Next days",
        "weather_sunrise" to "Sunrise",
        "weather_sunset" to "Sunset",
        "weather_note" to "Weather data by Open-Meteo.com · %1\$s",
        "weather_change_city" to "Change city",
        "weather_open_site" to "Open-Meteo.com",
        "weather_consent" to "Weather comes from Open-Meteo, a free weather service. BentoBar sends it the city you search for and that city's coordinates, or with My location where this device is, to about 10 km; nothing else. Like any website, it sees your IP address.",
        "weather_city_label" to "City or town",
        "weather_two_letters" to "Type at least two letters.",
        "weather_searching" to "Searching…",
        "weather_not_found" to "No place found. Check the spelling, or try a bigger town nearby.",
        "weather_search_no_answer" to "Open-Meteo didn't answer. Try again in a moment.",
        "weather_no_answer" to "No answer",
        "weather_no_answer_15" to "Open-Meteo didn't answer. BentoBar tries again in 15 minutes.",
        "weather_no_answer_hour" to "Open-Meteo didn't answer. BentoBar tries again in an hour.",
        "weather_not_turned_on" to "Not turned on",
        "weather_turn_on" to "Turn on weather",
        "weather_city_current" to "City: %1\$s",
        "weather_city_none" to "City: none yet",
        "weather_show_temp" to "Temperature",
        "weather_show_high_low" to "With high and low",
        "weather_show_feels" to "Feels like",
        "weather_label_help" to "Shown before the temperature, like SF",
        "weather_desc" to "%1\$s: %2\$s. %3\$s.",
        "weather_desc_soon" to "%1\$s likely at %2\$s.",
        "weather_desc_now" to "%1\$s now.",
        "weather_desc_not_set_up" to "Weather: not set up",
        "weather_desc_loading" to "Weather: loading",
        "weather_desc_off" to "Weather: off",
        "weather_desc_no_reading" to "Weather: no reading",
        "weather_high_low_desc" to "High %1\$s, low %2\$s.",
        "weather_chance_desc" to "%1\$d percent chance",
    )

    /** The copy deck's plurals, as one and other. */
    private val deckPlurals = mapOf(
        "weather_degrees" to ("%1\$s degree" to "%1\$s degrees"),
        "weather_wind_mph_desc" to ("Wind %1\$d mile per hour." to "Wind %1\$d miles per hour."),
        "weather_wind_kmh_desc" to ("Wind %1\$d kilometer per hour." to "Wind %1\$d kilometers per hour."),
    )

    /** Shared words this item uses (copy deck 9.1 and the strings of 0.8): theirs to define, here to rely on. */
    private val shared = mapOf(
        "common_refresh" to "Refresh", "common_try_again" to "Try again", "common_no_connection" to "No connection", "common_cancel" to "Cancel",
        "common_search" to "Search", "common_not_set_up" to "Not set up", "common_off_in_setup" to "Off in Setup",
        "common_updated" to "updated %1\$s", "common_updated_offline" to "no connection, updated %1\$s", "common_updated_no_answer" to "no answer, updated %1\$s",
        "common_opens_browser" to "%1\$s, opens in the browser", "option_temperature_unit" to "Temperature unit", "option_celsius" to "°C",
        "option_fahrenheit" to "°F", "usage_loading" to "Loading…", "option_show" to "Show", "option_label" to "Label", "option_none" to "None",
        "option_like_system" to "Like the system",
    )

    /**
     * What this item adds to the deck for cases it leaves open: a reading without a condition, and parts spoken as
     * one line. And what was decided after it: the spoken line while the hour that is running is the likely one,
     * and why Refresh is dimmed after an answer; and the bar's words for the sky.
     */
    private val added = mapOf(
        "weather_desc_short" to "%1\$s: %2\$s.",
        "weather_list" to "%1\$s, %2\$s",
        "weather_sentence" to "%1\$s.",
        "weather_day_high_low" to "high %1\$s, low %2\$s",
        "weather_likely_now_desc" to "%1\$s likely this hour.",
        "weather_up_to_date" to "up to date",
        // Show "with conditions": the sky's word in the bar, and "Windy" where a strong wind is the news.
        "weather_show_sky" to "With conditions",
        "weather_windy" to "Windy",
        // My location: where the device is instead of a city, and the three ways it can be unknown.
        "weather_here" to "My location",
        "weather_use_here" to "Use my location",
        "weather_here_current" to "Place: My location, to about 10 km",
        "weather_allow_location" to "Allow location",
        "weather_location_settings" to "Location settings",
        "weather_here_not_allowed" to "Location not allowed",
        "weather_here_not_allowed_note" to "To show the weather where you are, BentoBar needs Android's approximate location. It sends Open-Meteo that location rounded to about 10 km, and nothing else.",
        "weather_here_off" to "Location is off",
        "weather_here_off_note" to "Location is turned off on this device. Turn it on in Android's settings, or pick a city.",
        "weather_here_none" to "Location not found",
        "weather_here_none_note" to "Android doesn't know where this device is right now. BentoBar asks again, less often each time, then every half hour; or pick a city.",
        "weather_desc_no_location" to "Weather: no location",
    )

    @Test fun everyStringOfTheCopyDeckIsThereToTheCharacter() {
        for ((name, text) in deck + shared + added) assertEquals(name, text, files.strings[name])
        for ((name, forms) in deckPlurals) {
            assertEquals(name, forms.first, files.plurals.getValue(name)["one"])
            assertEquals(name, forms.second, files.plurals.getValue(name)["other"])
        }
    }

    @Test fun theWeatherFileHoldsNothingBesides() {
        val own = WeatherFileWords.names("src/main/res/values/strings_weather.xml")
        assertEquals((deck.keys + added.keys + deckPlurals.keys).sorted(), own.sorted())
    }

    @Test fun everyStringOfTheWeatherFileIsShownSomewhere() {
        // A text nobody asks for is a leftover: each is named in the app's code, as a string or as a plural.
        val code = java.io.File("src/main/java").walkTopDown().filter { it.isFile && it.extension == "kt" }.joinToString("\n") { it.readText() }
        for (name in WeatherFileWords.names("src/main/res/values/strings_weather.xml")) {
            val kind = if (name in deckPlurals) "plurals" else "string"
            assertTrue("R.$kind.$name is in strings_weather.xml and nowhere in the code", Regex("R\\.$kind\\.$name\\b").containsMatchIn(code))
        }
    }

    @Test fun britishEnglishDiffersInOneWord() {
        val british = WeatherFileWords("values-en-rGB")
        assertEquals("Wind %1\$d kilometre per hour.", british.plurals.getValue("weather_wind_kmh_desc")["one"])
        assertEquals("Wind %1\$d kilometres per hour.", british.plurals.getValue("weather_wind_kmh_desc")["other"])
        assertEquals(listOf("weather_wind_kmh_desc"), WeatherFileWords.names("src/main/res/values-en-rGB/strings_weather.xml"))
        // Everything else is the default's.
        for ((name, text) in deck) assertEquals(name, text, british.strings[name])
    }

    @Test fun theSpecialCharactersAreTheOnesTheDeckNames() {
        assertTrue(files.strings.getValue("weather_bar_soon").contains('·'))            // the middle dot
        assertTrue(files.strings.getValue("weather_bar_high_low").contains('↑'))        // up
        assertTrue(files.strings.getValue("weather_bar_high_low").contains('↓'))        // down
        assertTrue(files.strings.getValue("weather_searching").endsWith("…"))           // one character, not three dots
        assertEquals("9${nbsp}mph", files.say(W.WIND_MPH, 9))                                 // the unit doesn't part from its number
        assertEquals("15${nbsp}km/h", files.say(W.WIND_KMH, 15))
        assertEquals("20%", files.say(W.CHANCE, 20))
        assertFalse(files.strings.getValue("weather_two_letters").contains("..."))
    }

    @Test fun everyWordOfTheRulesIsTheResourceOfItsName() {
        // The app asks for a resource by number, the tests for a text by name: for each word both must mean the same one.
        for (word in W.entries) {
            val kind = if (word.plural) "plurals" else "string"
            val id = Class.forName("io.github.kuscher.bentobar.R\$$kind").getField(word.res).getInt(null)
            assertEquals("$word should be R.$kind.${word.res}", id, weatherRes(word))
            if (word.plural) assertTrue("${word.res} is no plural in the files", word.res in files.plurals)
            else assertTrue("${word.res} is no string in the files", word.res in files.strings)
        }
        assertEquals("no resource twice", W.entries.size, W.entries.map { weatherRes(it) }.toSet().size)
    }

    @Test fun theWordsOfTheConditionsAndOfWhatFallsAreTheirOwn() {
        assertEquals(Sky.entries.map { it.name }, Sky.entries.map { it.word.name })
        assertEquals(listOf(W.BAR_RAIN, W.BAR_SNOW, W.BAR_STORM), Falls.entries.map { it.word })
        assertEquals(listOf("Rain", "Snow", "Storm"), Falls.entries.map { files.say(it.word) })
        assertEquals("Thunderstorm with hail", files.say(Sky.THUNDERSTORM_HAIL.word))
    }
}
