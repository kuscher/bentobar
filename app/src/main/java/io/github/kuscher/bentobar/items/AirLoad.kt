package io.github.kuscher.bentobar.items

import io.github.kuscher.bentobar.data.ItemConfig
import io.github.kuscher.bentobar.data.Section
import io.github.kuscher.bentobar.items.AirRules.AirReading
import io.github.kuscher.bentobar.net.Host
import io.github.kuscher.bentobar.net.Http
import io.github.kuscher.bentobar.net.Reply
import io.github.kuscher.bentobar.net.Request
import io.github.kuscher.bentobar.net.Why

// Requests and loading for the Air quality and UV item (Open-Meteo). No Android, and no logging.

object AirLoad {
    fun request(place: Place) = Request(Host.OPEN_METEO_AIR, "/v1/air-quality", listOf(
        "latitude" to place.lat,
        "longitude" to place.lon,
        "current" to "us_aqi,european_aqi,pm2_5,pm10,ozone,uv_index",
        "hourly" to "uv_index",
        "timezone" to "auto",
        "timeformat" to "unixtime",
        "forecast_hours" to "24",
    ))

    fun place(item: ItemConfig, here: Fix?): Place? =
        if (WeatherRules.here(item)) here?.let { WeatherRules.nearby(it.lat, it.lon) } else WeatherRules.place(item)

    fun places(items: List<ItemConfig>, here: Fix? = null): Set<Place> =
        items.filter { it.type == "air" && it.section != Section.OFF }.mapNotNullTo(HashSet()) { place(it, here) }

    fun load(place: Place, last: AirReading?, now: Long): AirReading? {
        val reply = Http.get(request(place))
        val good = (reply as? Reply.Ok)?.let { AirRules.read(it.text, place, now) }
        if (good != null) return good
        val failed = reply as? Reply.Failed
        if (failed?.why == Why.OFF) return null
        val failure = when {
            failed?.why == Why.OFFLINE -> Failure.OFFLINE
            failed?.status == 429 -> Failure.SLOW_DOWN
            else -> Failure.NO_ANSWER
        }
        val known = last?.takeIf { it.place == place.key } ?: AirReading(place.key)
        return known.copy(failure = failure)
    }
}
