package io.github.kuscher.bentobar.items

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.kuscher.bentobar.R
import io.github.kuscher.bentobar.data.ItemConfig
import io.github.kuscher.bentobar.data.Online
import io.github.kuscher.bentobar.ui.MenuCard
import io.github.kuscher.bentobar.ui.OnlineWords
import io.github.kuscher.bentobar.util.Sym

/**
 * Weather: the temperature and conditions of a city the user picks, from Open-Meteo. One of the two
 * item types that go online ([online]), and only after the user set it up on this install: until
 * then nothing is asked, whatever the layout says.
 *
 * This is the first cut: the item, its rule and the words shown before anything is sent. The city
 * search, the forecast and the menu come next.
 */
object WeatherItem : ItemType("weather", R.string.item_weather_title, Sym.PARTLY_CLOUDY_DAY, R.string.item_weather_desc) {
    override val refreshMs = 10_000L
    override val menuWidthDp = 340
    override val online = Online.Service.OPEN_METEO
    override val canBeActive = true

    /** How many hours ahead rain or snow brings the item out. */
    private val within = Threshold("rainHours", 2, 1..12) { Env.plural(R.plurals.common_hours, it, it) }
    override val trigger = Trigger(R.string.trigger_weather, R.string.trigger_weather_short, within)

    // Outlined cloud: not set up.
    override fun state(item: ItemConfig) = ItemState(icon = Sym.CLOUD, filled = false, desc = Env.str(R.string.weather_not_set_up))

    override val menu: @Composable (ItemConfig, MenuHost) -> Unit = { _, _ ->
        MenuCard(Sym.CLOUD, stringResource(R.string.item_weather_title), stringResource(R.string.weather_not_set_up)) {
            OnlineWords(Online.Service.OPEN_METEO)
        }
    }
}
