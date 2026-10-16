package io.github.kuscher.bentobar.items

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.kuscher.bentobar.R
import io.github.kuscher.bentobar.data.ItemConfig
import io.github.kuscher.bentobar.data.Online
import io.github.kuscher.bentobar.data.Section
import io.github.kuscher.bentobar.data.Store
import io.github.kuscher.bentobar.ui.ChoiceRow
import io.github.kuscher.bentobar.ui.InfoRow
import io.github.kuscher.bentobar.ui.MenuCard
import io.github.kuscher.bentobar.ui.MenuDivider
import io.github.kuscher.bentobar.ui.MenuEntry
import io.github.kuscher.bentobar.ui.MenuNote
import io.github.kuscher.bentobar.ui.Setup
import io.github.kuscher.bentobar.ui.rememberTick
import io.github.kuscher.bentobar.util.Now
import io.github.kuscher.bentobar.util.Sym
import java.time.ZoneId

// Menu and settings for the Air quality and UV item. No logging.

private const val OPEN_METEO_SITE = "https://open-meteo.com/"

@Composable
internal fun AirMenu(item: ItemConfig, host: MenuHost) {
    rememberTick()
    val online by Online.state.collectAsState()
    val on = Online.Service.OPEN_METEO in online.on
    val now = Now.wall()
    val place = AirLoad.place(item, WeatherHere.fix)
    LaunchedEffect(item.id, place, on) { AirItem.opened(item) }

    var changing by remember(item.id) { mutableStateOf(false) }
    val name = stringResource(R.string.item_air_title)
    val here = WeatherRules.here(item)
    val city = if (here) stringResource(R.string.weather_here) else WeatherRules.city(item) ?: if (AirItem.isStaged(item)) WeatherSamples.CITY else name
    val by = "air-menu:" + item.id
    val source = WeatherItem.source
    val pick: (City, String?) -> Unit = { found, region ->
        Store.updateItem(item.id) { WeatherRules.picked(it, found, region) }
        source.typed(by)
        changing = false
        Ticker.refresh(AirItem.type)
    }
    val changeCity: @Composable () -> Unit = { MenuEntry(Sym.LOCATION_ON, stringResource(R.string.weather_change_city)) { changing = true } }
    val kept = WeatherRules.city(item)
    val useCity: @Composable () -> Unit = {
        if (here && kept != null) MenuEntry(Sym.LOCATION_ON, stringResource(R.string.weather_use_city, kept)) {
            Store.updateItem(item.id) { WeatherRules.useCity(it) }
            source.typed(by)
            changing = false
            Ticker.refresh(AirItem.type)
        }
    }
    val useHere: @Composable () -> Unit = {
        MenuEntry(Sym.MY_LOCATION, stringResource(R.string.weather_use_here)) {
            Store.updateItem(item.id) { WeatherRules.useHere(it) }
            source.typed(by)
            changing = false
            if (hereChosen()) host.close()
        }
    }

    val reading = AirItem.reading(item, ask = false)
    when {
        !here && WeatherRules.place(item) == null && !AirItem.isStaged(item) -> MenuCard(Sym.AIR, name, stringResource(R.string.common_not_set_up)) {
            MenuNote(stringResource(R.string.weather_consent))
            CitySearch(item, by, initial = WeatherRules.cityOf(ZoneId.systemDefault().id), onPick = pick)
            MenuDivider()
            useHere()
        }
        changing -> MenuCard(Sym.AIR, city, stringResource(R.string.weather_change_city)) {
            CitySearch(item, by, initial = WeatherRules.city(item).orEmpty(), onPick = pick)
            MenuDivider()
            if (!here) useHere() else useCity()
            MenuEntry(Sym.CLOSE, stringResource(R.string.common_cancel)) { source.typed(by); changing = false }
        }
        !on && !AirItem.isStaged(item) -> MenuCard(Sym.AIR, city, stringResource(if (Online.setUp(Online.Service.OPEN_METEO)) R.string.common_off_in_setup else R.string.weather_not_turned_on)) {
            MenuNote(stringResource(R.string.weather_consent))
            MenuEntry(Sym.TOGGLE_ON, stringResource(R.string.weather_turn_on)) { Online.turnOn(Online.Service.OPEN_METEO); Ticker.refresh(AirItem.type) }
        }
        here && place == null && !AirItem.isStaged(item) -> {
            val why = WeatherHere.why
            MenuCard(Sym.AIR, city, stringResource(when (why) {
                Locate.NOT_ALLOWED -> R.string.weather_here_not_allowed
                Locate.OFF -> R.string.weather_here_off
                else -> R.string.weather_here_none
            })) {
                MenuNote(stringResource(when (why) {
                    Locate.NOT_ALLOWED -> R.string.weather_here_not_allowed_note
                    Locate.OFF -> R.string.weather_here_off_note
                    else -> R.string.weather_here_none_note
                }))
                when (why) {
                    Locate.NOT_ALLOWED -> MenuEntry(Sym.MY_LOCATION, stringResource(R.string.weather_allow_location)) { host.close(); askLocation() }
                    Locate.OFF -> MenuEntry(Sym.SETTINGS, stringResource(R.string.weather_location_settings)) {
                        host.close(); Env.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    }
                    else -> {}
                }
                useCity()
                changeCity()
            }
        }
        reading == null || (!reading.hasValues && reading.failure == null) -> MenuCard(Sym.AIR, city, stringResource(R.string.usage_loading)) {
            useCity(); changeCity()
        }
        !reading.hasValues -> {
            val offline = reading.failure == Failure.OFFLINE
            MenuCard(Sym.AIR, city, stringResource(if (offline) R.string.common_no_connection else R.string.weather_no_answer)) {
                MenuEntry(Sym.REFRESH, stringResource(R.string.common_try_again)) { AirItem.refresh(item) }
                useCity(); changeCity()
            }
        }
        else -> {
            val eu = item.opt("scale", "us") == "eu"
            val aqi = AirRules.aqiOf(reading, eu) ?: 0
            val band = AirItem.bandLabel(AirRules.bandOf(aqi, eu))
            val uv = reading.uv ?: 0.0
            val uvStr = AirRules.formatUv(uv)
            val uvBand = AirItem.uvBandLabel(AirRules.uvBand(uv))
            val sub = stringResource(R.string.air_subtitle, "$band ($aqi)", uvStr, uvBand)
            MenuCard(Sym.AIR, city, sub) {
                InfoRow(stringResource(if (eu) R.string.air_scale_eu else R.string.air_scale_us), "$aqi · $band")
                InfoRow(stringResource(R.string.air_uv_index), "$uvStr · $uvBand")
                reading.uvMax?.let { InfoRow(stringResource(R.string.air_uv_peak), AirRules.formatUv(it)) }
                reading.pm25?.let { InfoRow(stringResource(R.string.air_pm25), stringResource(R.string.air_ugm3, AirRules.formatUg(it))) }
                reading.pm10?.let { InfoRow(stringResource(R.string.air_pm10), stringResource(R.string.air_ugm3, AirRules.formatUg(it))) }
                reading.ozone?.let { InfoRow(stringResource(R.string.air_ozone), stringResource(R.string.air_ugm3, AirRules.formatUg(it))) }
                Spacer(Modifier.height(4.dp))
                val clock = WeatherItem.times().clock(reading.fetchedAt, ZoneId.systemDefault())
                val updated = when (reading.failure) {
                    Failure.OFFLINE -> stringResource(R.string.common_updated_offline, clock)
                    null -> stringResource(R.string.common_updated, clock)
                    else -> stringResource(R.string.common_updated_no_answer, clock)
                }
                MenuNote(stringResource(R.string.air_note, updated))
                MenuDivider()
                MenuEntry(Sym.REFRESH, stringResource(R.string.common_refresh)) { AirItem.refresh(item) }
                useCity()
                changeCity()
                val site = stringResource(R.string.weather_open_site)
                val opens = stringResource(R.string.common_opens_browser, site)
                MenuEntry(Sym.OPEN_IN_NEW, site, modifier = Modifier.semantics { contentDescription = opens }) {
                    host.close()
                    Env.launch(Intent(Intent.ACTION_VIEW, Uri.parse(OPEN_METEO_SITE)))
                }
            }
        }
    }
}

@Composable
internal fun AirOptions(item: ItemConfig, set: (ItemConfig) -> Unit) {
    val online by Online.state.collectAsState()
    val on = Online.Service.OPEN_METEO in online.on
    val source = WeatherItem.source
    val city = WeatherRules.city(item)
    val here = WeatherRules.here(item)
    val by = "air-settings:" + item.id
    val setup by Setup.state.collectAsState()
    key(item.id) {
        var changing by remember { mutableStateOf(false) }
        val chooseHere: @Composable () -> Unit = {
            TextButton(onClick = { set(WeatherRules.useHere(item)); source.typed(by); changing = false; hereChosen() }, enabled = item.section != Section.OFF) {
                Text(stringResource(R.string.weather_use_here))
            }
        }
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text(when {
                here -> stringResource(R.string.weather_here_current)
                city != null -> stringResource(R.string.weather_city_current, city)
                else -> stringResource(R.string.weather_city_none)
            }, style = MaterialTheme.typography.labelLarge)
            Text(stringResource(R.string.weather_consent), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if ((!here && WeatherRules.place(item) == null) || changing) {
                CitySearch(item, by, initial = city ?: WeatherRules.cityOf(ZoneId.systemDefault().id), canSubmit = item.section != Section.OFF) { found, region ->
                    set(WeatherRules.picked(item, found, region))
                    source.typed(by)
                    changing = false
                }
                Row(Modifier.offset(x = (-12).dp)) {
                    if (!here) chooseHere()
                    if (changing) TextButton(onClick = { source.typed(by); changing = false }) { Text(stringResource(R.string.common_cancel)) }
                }
            } else Row(Modifier.offset(x = (-12).dp)) {
                TextButton(onClick = { changing = true }) { Text(stringResource(R.string.weather_change_city)) }
                if (!here) chooseHere()
                else if (city != null) TextButton(onClick = { set(WeatherRules.useCity(item)); source.typed(by) }) { Text(stringResource(R.string.weather_use_city, city)) }
                if (!on) TextButton(onClick = { Online.turnOn(Online.Service.OPEN_METEO); Ticker.refresh(AirItem.type) }) { Text(stringResource(R.string.weather_turn_on)) }
                if (here && !setup.location) TextButton(onClick = { askLocation() }) { Text(stringResource(R.string.weather_allow_location)) }
            }
        }
    }
    ChoiceRow(
        stringResource(R.string.air_show_label),
        listOf(
            "aqi" to stringResource(R.string.air_show_aqi),
            "both" to stringResource(R.string.air_show_aqi_uv),
            "uv" to stringResource(R.string.air_show_uv),
        ),
        item.opt("show", "aqi"),
    ) { set(item.with("show", it.takeIf { v -> v != "aqi" })) }
    ChoiceRow(
        stringResource(R.string.air_scale_label),
        listOf("us" to stringResource(R.string.air_scale_us), "eu" to stringResource(R.string.air_scale_eu)),
        item.opt("scale", "us"),
    ) { set(item.with("scale", it.takeIf { v -> v != "us" })) }
}
