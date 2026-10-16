package io.github.kuscher.bentobar.items

import androidx.compose.runtime.Composable
import io.github.kuscher.bentobar.R
import io.github.kuscher.bentobar.data.ItemConfig
import io.github.kuscher.bentobar.data.Online
import io.github.kuscher.bentobar.data.Section
import io.github.kuscher.bentobar.data.Store
import io.github.kuscher.bentobar.items.AirRules.AirReading
import io.github.kuscher.bentobar.util.Now
import io.github.kuscher.bentobar.util.Sym

/**
 * Air quality and UV: US or European AQI, PM2.5, PM10, Ozone and UV index from Open-Meteo for a
 * chosen city or "My location" ([WeatherHere]). Shares the Open-Meteo online service switch with
 * [WeatherItem]. No logging.
 */
object AirItem : ItemType("air", R.string.item_air_title, Sym.AIR, R.string.item_air_desc) {
    override val refreshMs = 10_000L
    override val menuWidthDp = 340
    override val online = Online.Service.OPEN_METEO
    override val canBeActive = true

    private val aboveAqi = Threshold("aqi", 100, 50..200, step = 25) { "$it" }
    override val trigger = Trigger(R.string.trigger_air, R.string.trigger_air_short, aboveAqi)

    internal val readings: Refresher<Place, AirReading> = Background.refresher(
        type = type,
        service = online,
        every = { _, r -> AirRules.every(r) },
        load = { place, last -> AirLoad.load(place, last, Now.wall()) },
    )

    @Volatile private var staged: AirReading? = null

    private fun firstShown(): ItemConfig? = Store.config.value.items.firstOrNull { it.type == type && it.section != Section.OFF }

    internal fun isStaged(item: ItemConfig): Boolean = staged != null && firstShown()?.id == item.id

    internal fun reading(item: ItemConfig, ask: Boolean = true): AirReading? {
        staged?.let { if (firstShown()?.id == item.id) return it }
        if (!Online.on(online)) return null
        val place = AirLoad.place(item, WeatherHere.fix) ?: return null
        if (ask && item.section != Section.OFF) readings.want(place)
        return readings.peek(place)
    }

    internal fun opened(item: ItemConfig) {
        if (isStaged(item) || item.section == Section.OFF || !Online.on(online)) return
        val place = AirLoad.place(item, WeatherHere.fix) ?: return
        val r = readings.peek(place)
        if (r == null) readings.want(place) else if (r.failure == null) readings.refresh(place, floorMs = AirRules.MENU_MS)
    }

    internal fun refresh(item: ItemConfig) {
        if (isStaged(item) || !Online.on(online)) return
        val place = AirLoad.place(item, WeatherHere.fix) ?: return
        readings.refresh(place, floorMs = 10_000L)
    }

    internal fun bandLabel(band: AirRules.Band): String = Env.str(when (band) {
        AirRules.Band.GOOD -> R.string.air_good
        AirRules.Band.MODERATE -> R.string.air_moderate
        AirRules.Band.SENSITIVE -> R.string.air_sensitive
        AirRules.Band.UNHEALTHY -> R.string.air_unhealthy
        AirRules.Band.VERY_UNHEALTHY -> R.string.air_very_unhealthy
        AirRules.Band.HAZARDOUS -> R.string.air_hazardous
    })

    internal fun uvBandLabel(band: AirRules.UvBand): String = Env.str(when (band) {
        AirRules.UvBand.LOW -> R.string.air_uv_low
        AirRules.UvBand.MODERATE -> R.string.air_uv_moderate
        AirRules.UvBand.HIGH -> R.string.air_uv_high
        AirRules.UvBand.VERY_HIGH -> R.string.air_uv_very_high
        AirRules.UvBand.EXTREME -> R.string.air_uv_extreme
    })

    override fun state(item: ItemConfig): ItemState {
        val here = WeatherRules.here(item)
        val place = AirLoad.place(item, WeatherHere.fix)
        val hasPlace = place != null || (!here && WeatherRules.place(item) != null) || isStaged(item)
        if (!hasPlace && !here) {
            val d = Env.str(R.string.air_desc_not_set_up)
            return ItemState(icon = Sym.AIR, filled = false, text = null, desc = d, tooltip = d)
        }
        if (!Online.on(online) && !isStaged(item)) {
            val d = Env.str(R.string.air_desc_off)
            return ItemState(icon = Sym.AIR, filled = false, text = null, desc = d, tooltip = d)
        }
        val r = reading(item)
        if (r == null || !r.hasValues) {
            val d = Env.str(if (r?.failure != null) R.string.air_desc_no_reading else R.string.air_desc_loading)
            return ItemState(icon = Sym.AIR, filled = false, text = null, desc = d, tooltip = d)
        }
        val eu = item.opt("scale", "us") == "eu"
        val show = item.opt("show", "aqi")
        val aqi = AirRules.aqiOf(r, eu) ?: 0
        val band = AirRules.bandOf(aqi, eu)
        val uv = r.uv ?: 0.0
        val uvStr = AirRules.formatUv(uv)
        val aqiText = Env.str(if (eu) R.string.air_bar_eaqi else R.string.air_bar_aqi, aqi)
        val text = when (show) {
            "uv" -> Env.str(R.string.air_bar_uv, uvStr)
            "both" -> Env.str(R.string.air_bar_both, aqiText, uvStr)
            else -> aqiText
        }
        val city = if (here) Env.str(R.string.weather_here) else WeatherRules.city(item) ?: Env.str(R.string.item_air_title)
        val desc = Env.str(R.string.air_desc_live, city, aqi, bandLabel(band), uvStr)
        val tone = AirRules.toneOf(band, r.uv)
        val active = AirRules.active(r, aboveAqi.shown(item), eu)
        val icon = if (show == "uv") Sym.SUNNY else Sym.AIR
        return ItemState(icon = icon, filled = true, text = text, tone = tone, active = active, desc = desc, tooltip = desc, textLimit = AirRules.BAR_CHARS)
    }

    override fun sample(now: Long) {
        WeatherHere.follow(Store.config.value.items, Ticker.sampled, Online.on(online))
        readings.keepOnly(AirLoad.places(Store.config.value.items, WeatherHere.fix))
    }

    override fun onIdle() {
        WeatherHere.follow(Store.config.value.items, sampled = emptyList(), on = Online.on(online))
        readings.keepOnly(AirLoad.places(Store.config.value.items, WeatherHere.fix))
    }

    override fun forgetFetched() {
        staged = null
    }

    override val menu: @Composable (ItemConfig, MenuHost) -> Unit = { item, host -> AirMenu(item, host) }
    override val options: @Composable (ItemConfig, (ItemConfig) -> Unit) -> Unit = { item, set -> AirOptions(item, set) }

    override fun debug(args: List<String>): String? = when (args.firstOrNull()) {
        null -> "staged=${staged != null} on=${Online.on(online)}"
        "stage" -> {
            val s = AirRules.sample(args.getOrNull(1).orEmpty(), Now.wall())
            if (s == null) "air stage good|moderate|smoke|highuv|offline" else {
                staged = s
                Ticker.refresh(type)
                "staged ${args[1]}"
            }
        }
        "off" -> {
            staged = null
            Ticker.refresh(type)
            "staging off"
        }
        else -> null
    }
}
