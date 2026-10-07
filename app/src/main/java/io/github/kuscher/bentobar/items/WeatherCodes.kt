package io.github.kuscher.bentobar.items

import io.github.kuscher.bentobar.util.Sym

/**
 * What the sky does, in the menu's words: one for each group of the weather codes Open-Meteo sends
 * (the WMO's). [word] names the text.
 */
enum class Sky(val word: W) {
    CLEAR(W.CLEAR), MOSTLY_CLEAR(W.MOSTLY_CLEAR), PARTLY_CLOUDY(W.PARTLY_CLOUDY), CLOUDY(W.CLOUDY), FOG(W.FOG),
    DRIZZLE(W.DRIZZLE), FREEZING_DRIZZLE(W.FREEZING_DRIZZLE),
    LIGHT_RAIN(W.LIGHT_RAIN), RAIN(W.RAIN), HEAVY_RAIN(W.HEAVY_RAIN), FREEZING_RAIN(W.FREEZING_RAIN),
    LIGHT_SNOW(W.LIGHT_SNOW), SNOW(W.SNOW), HEAVY_SNOW(W.HEAVY_SNOW),
    SHOWERS(W.SHOWERS), HEAVY_SHOWERS(W.HEAVY_SHOWERS), SNOW_SHOWERS(W.SNOW_SHOWERS),
    THUNDERSTORM(W.THUNDERSTORM), THUNDERSTORM_HAIL(W.THUNDERSTORM_HAIL),
}

/** What falls, in the one word the bar has room for. */
enum class Falls(val word: W) { RAIN(W.BAR_RAIN), SNOW(W.BAR_SNOW), STORM(W.BAR_STORM) }

/** Open-Meteo's weather codes as words and glyphs. Pure: every row of the design's table is a test. */
object WeatherCodes {
    /** The condition a code stands for, or null for a code that isn't in the table (and for none at all): a cloud without a word. */
    fun sky(code: Int?): Sky? = when (code) {
        0 -> Sky.CLEAR
        1 -> Sky.MOSTLY_CLEAR
        2 -> Sky.PARTLY_CLOUDY
        3 -> Sky.CLOUDY
        45, 48 -> Sky.FOG
        51, 53, 55 -> Sky.DRIZZLE
        56, 57 -> Sky.FREEZING_DRIZZLE
        61 -> Sky.LIGHT_RAIN
        63 -> Sky.RAIN
        65 -> Sky.HEAVY_RAIN
        66, 67 -> Sky.FREEZING_RAIN
        71 -> Sky.LIGHT_SNOW
        73 -> Sky.SNOW
        75, 77 -> Sky.HEAVY_SNOW
        80, 81 -> Sky.SHOWERS
        82 -> Sky.HEAVY_SHOWERS
        85, 86 -> Sky.SNOW_SHOWERS
        95 -> Sky.THUNDERSTORM
        96, 99 -> Sky.THUNDERSTORM_HAIL
        else -> null
    }

    /** The glyph, filled. Only a clear and a partly cloudy sky have one of their own for the night. */
    fun glyph(code: Int?, day: Boolean): String = when (sky(code)) {
        Sky.CLEAR, Sky.MOSTLY_CLEAR -> if (day) Sym.CLEAR_DAY else Sym.CLEAR_NIGHT
        Sky.PARTLY_CLOUDY -> if (day) Sym.PARTLY_CLOUDY_DAY else Sym.PARTLY_CLOUDY_NIGHT
        Sky.FOG -> Sym.FOGGY
        Sky.FREEZING_DRIZZLE, Sky.FREEZING_RAIN -> Sym.WEATHER_MIX
        Sky.CLOUDY, null -> Sym.CLOUD
        else -> glyph(falls(code))
    }

    /** The glyph for what falls, where no code says more. */
    fun glyph(falls: Falls?): String = when (falls) {
        Falls.RAIN -> Sym.RAINY
        Falls.SNOW -> Sym.WEATHER_SNOWY
        Falls.STORM -> Sym.THUNDERSTORM
        null -> Sym.CLOUD
    }

    /** A sustained wind from this many km/h is "Windy" (20 mph, where the US National Weather Service's "windy" begins). */
    const val WINDY_KMH = 32.0

    /**
     * Windy: a strong wind ([WINDY_KMH]) under a sky where nothing falls and no fog stands. Rain, snow,
     * a storm and fog say more about the hour than the wind does, so they keep their own word and glyph.
     */
    fun windy(code: Int?, windKmh: Double?): Boolean =
        windKmh != null && windKmh >= WINDY_KMH && falls(code) == null && sky(code) != Sky.FOG

    /** The bar's word for a code: what falls, or null when nothing does. */
    fun falls(code: Int?): Falls? = when (sky(code)) {
        Sky.DRIZZLE, Sky.FREEZING_DRIZZLE, Sky.LIGHT_RAIN, Sky.RAIN, Sky.HEAVY_RAIN, Sky.FREEZING_RAIN, Sky.SHOWERS, Sky.HEAVY_SHOWERS -> Falls.RAIN
        Sky.LIGHT_SNOW, Sky.SNOW, Sky.HEAVY_SNOW, Sky.SNOW_SHOWERS -> Falls.SNOW
        Sky.THUNDERSTORM, Sky.THUNDERSTORM_HAIL -> Falls.STORM
        else -> null
    }
}
