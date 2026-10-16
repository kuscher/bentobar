package io.github.kuscher.bentobar.items

import io.github.kuscher.bentobar.data.ItemConfig
import io.github.kuscher.bentobar.util.Sym
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.roundToInt

/**
 * Pure rules for the Air quality and UV item: parsing Open-Meteo's `/v1/air-quality` reply,
 * US and European AQI bands, UV bands, and what the bar and menu show. No Android imports.
 */
object AirRules {
    const val FRESH_MS = 30 * 60_000L
    const val MENU_MS = 10 * 60_000L
    const val RETRY_MS = 15 * 60_000L
    const val BAR_CHARS = 20

    enum class Band { GOOD, MODERATE, SENSITIVE, UNHEALTHY, VERY_UNHEALTHY, HAZARDOUS }
    enum class UvBand { LOW, MODERATE, HIGH, VERY_HIGH, EXTREME }

    data class AirReading(
        val place: String,
        val usAqi: Int? = null,
        val euAqi: Int? = null,
        val pm25: Double? = null,
        val pm10: Double? = null,
        val ozone: Double? = null,
        val uv: Double? = null,
        val uvMax: Double? = null,
        val fetchedAt: Long = 0,
        val failure: Failure? = null,
    ) {
        val hasValues: Boolean get() = usAqi != null || euAqi != null || uv != null
        override fun toString() = "AirReading(hasValues=$hasValues, failure=$failure)"
    }

    class Bar(
        val icon: String,
        val filled: Boolean,
        val text: String?,
        val tone: Tone,
        val active: Boolean,
        val desc: String,
        val tooltip: String,
    )

    fun usBand(aqi: Int): Band = when {
        aqi <= 50 -> Band.GOOD
        aqi <= 100 -> Band.MODERATE
        aqi <= 150 -> Band.SENSITIVE
        aqi <= 200 -> Band.UNHEALTHY
        aqi <= 300 -> Band.VERY_UNHEALTHY
        else -> Band.HAZARDOUS
    }

    fun euBand(aqi: Int): Band = when {
        aqi <= 20 -> Band.GOOD
        aqi <= 40 -> Band.MODERATE
        aqi <= 60 -> Band.SENSITIVE
        aqi <= 80 -> Band.UNHEALTHY
        aqi <= 100 -> Band.VERY_UNHEALTHY
        else -> Band.HAZARDOUS
    }

    fun uvBand(uv: Double): UvBand = when {
        uv < 3.0 -> UvBand.LOW
        uv < 6.0 -> UvBand.MODERATE
        uv < 8.0 -> UvBand.HIGH
        uv < 11.0 -> UvBand.VERY_HIGH
        else -> UvBand.EXTREME
    }

    fun formatUv(uv: Double): String {
        val rounded10 = (uv * 10.0).roundToInt()
        return if (rounded10 % 10 == 0) "${rounded10 / 10}" else "${rounded10 / 10}.${rounded10 % 10}"
    }

    fun formatUg(v: Double): String {
        val rounded10 = (v * 10.0).roundToInt()
        return if (rounded10 % 10 == 0) "${rounded10 / 10}" else "${rounded10 / 10}.${rounded10 % 10}"
    }

    /** Parses Open-Meteo's `/v1/air-quality` JSON reply. */
    fun read(jsonText: String, place: Place, now: Long): AirReading? = try {
        val root = Json.parseToJsonElement(jsonText).jsonObject
        val cur = root["current"] as? JsonObject ?: return null
        val us = cur["us_aqi"]?.jsonPrimitive?.intOrNull
        val eu = cur["european_aqi"]?.jsonPrimitive?.intOrNull
        val pm25 = cur["pm2_5"]?.jsonPrimitive?.doubleOrNull
        val pm10 = cur["pm10"]?.jsonPrimitive?.doubleOrNull
        val ozone = cur["ozone"]?.jsonPrimitive?.doubleOrNull
        val uv = cur["uv_index"]?.jsonPrimitive?.doubleOrNull
        val hourly = root["hourly"] as? JsonObject
        val uvHourly = (hourly?.get("uv_index") as? JsonArray)?.mapNotNull { it.jsonPrimitive.doubleOrNull }.orEmpty()
        val uvMax = (uvHourly + listOfNotNull(uv)).maxOrNull()
        if (us == null && eu == null && uv == null) null
        else AirReading(place.key, us, eu, pm25, pm10, ozone, uv, uvMax, fetchedAt = now)
    } catch (e: Exception) {
        null
    }

    fun every(reading: AirReading): Long = if (reading.failure != null) RETRY_MS else FRESH_MS

    fun aqiOf(reading: AirReading, eu: Boolean): Int? = if (eu) reading.euAqi ?: reading.usAqi else reading.usAqi ?: reading.euAqi

    fun bandOf(aqi: Int, eu: Boolean): Band = if (eu) euBand(aqi) else usBand(aqi)

    fun toneOf(band: Band?, uv: Double?): Tone {
        val aqiTone = when (band) {
            Band.UNHEALTHY, Band.VERY_UNHEALTHY, Band.HAZARDOUS -> Tone.ALERT
            Band.SENSITIVE -> Tone.WARN
            else -> Tone.NORMAL
        }
        val uvTone = when {
            uv == null -> Tone.NORMAL
            uv >= 8.0 -> Tone.ALERT
            uv >= 6.0 -> Tone.WARN
            else -> Tone.NORMAL
        }
        return when {
            aqiTone == Tone.ALERT || uvTone == Tone.ALERT -> Tone.ALERT
            aqiTone == Tone.WARN || uvTone == Tone.WARN -> Tone.WARN
            else -> Tone.NORMAL
        }
    }

    fun active(reading: AirReading, thresholdAqi: Int, eu: Boolean): Boolean {
        val aqi = aqiOf(reading, eu)
        val uv = reading.uv
        val aqiHit = if (aqi == null) false else if (eu) aqi >= (thresholdAqi * 0.6).roundToInt() else aqi >= thresholdAqi
        val uvHit = uv != null && uv >= 6.0
        return aqiHit || uvHit
    }

    /** Debug samples (`./bento debug air stage good|moderate|smoke|highuv|offline`). */
    fun sample(name: String, now: Long): AirReading? = when (name) {
        "good" -> AirReading("47.37,8.55", usAqi = 28, euAqi = 16, pm25 = 6.4, pm10 = 12.1, ozone = 54.0, uv = 2.0, uvMax = 4.5, fetchedAt = now - 60_000)
        "moderate" -> AirReading("47.37,8.55", usAqi = 74, euAqi = 42, pm25 = 22.8, pm10 = 38.0, ozone = 82.0, uv = 5.0, uvMax = 6.2, fetchedAt = now - 60_000)
        "smoke" -> AirReading("47.37,8.55", usAqi = 168, euAqi = 88, pm25 = 84.5, pm10 = 112.0, ozone = 96.0, uv = 3.0, uvMax = 5.0, fetchedAt = now - 60_000)
        "highuv" -> AirReading("47.37,8.55", usAqi = 42, euAqi = 24, pm25 = 9.2, pm10 = 18.0, ozone = 68.0, uv = 8.5, uvMax = 9.2, fetchedAt = now - 60_000)
        "offline" -> AirReading("47.37,8.55", usAqi = 35, euAqi = 20, pm25 = 8.0, pm10 = 15.0, ozone = 60.0, uv = 3.0, uvMax = 5.0, fetchedAt = now - 600_000, failure = Failure.OFFLINE)
        else -> null
    }
}
