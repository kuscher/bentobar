package io.github.kuscher.bentobar.items

import android.content.Intent
import android.provider.AlarmClock
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.kuscher.bentobar.R
import io.github.kuscher.bentobar.data.ItemConfig
import io.github.kuscher.bentobar.data.Store
import io.github.kuscher.bentobar.ui.ChoiceRow
import io.github.kuscher.bentobar.ui.MenuCard
import io.github.kuscher.bentobar.ui.MenuDivider
import io.github.kuscher.bentobar.ui.MenuEntry
import io.github.kuscher.bentobar.ui.SwitchRow
import io.github.kuscher.bentobar.ui.TextRow
import io.github.kuscher.bentobar.ui.rememberTick
import io.github.kuscher.bentobar.util.Dates
import io.github.kuscher.bentobar.util.Fmt
import io.github.kuscher.bentobar.util.Sym
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

// The Clock item and its World clock menu, in a file of their own (they were in TimeItems.kt).

private fun is24(item: ItemConfig) = when (item.opt("hours", "system")) {
    "24" -> true
    "12" -> false
    else -> DateFormat.is24HourFormat(Env.app)
}

/** The time in [t]'s zone, 24-hour or 12-hour as asked, laid out the locale's way. */
private fun formatTime(t: ZonedDateTime, h24: Boolean, seconds: Boolean): String = Dates.format(Dates.timeSkeleton(h24, seconds), t)

private fun zoneOf(item: ItemConfig): ZoneId =
    item.options["zone"]?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()

/** "Tokyo" from "Asia/Tokyo". */
private fun cityOf(zone: ZoneId) = cityOf(zone.id)
private fun cityOf(zoneId: String) = zoneId.substringAfterLast('/').replace('_', ' ')

object ClockItem : ItemType("clock", R.string.item_clock_title, Sym.SCHEDULE, R.string.item_clock_desc) {
    override fun state(item: ItemConfig): ItemState {
        val zone = zoneOf(item)
        val time = formatTime(ZonedDateTime.now(zone), is24(item), item.optBool("seconds", true))
        val label = item.opt("label", "")
        return ItemState(icon = Sym.SCHEDULE, text = if (label.isBlank()) time else "$label $time", widthKey = "clock",
            desc = "${label.ifBlank { cityOf(zone) }} $time")
    }

    override val menu: @Composable (ItemConfig, MenuHost) -> Unit = { item, host ->
        rememberTick()
        val zones = (listOf(ZoneId.systemDefault()) + Store.config.value.items.filter { it.type == "clock" }.map { zoneOf(it) })
            .distinctBy { it.id }
        val local = ZonedDateTime.now()
        MenuCard(Sym.PUBLIC, stringResource(R.string.clock_world), Dates.format("EEEEdMMMM", local)) {
            zones.forEach { z ->
                val t = ZonedDateTime.now(z)
                val diffH = (t.offset.totalSeconds - local.offset.totalSeconds) / 3600.0
                val day = when (t.toLocalDate().compareTo(local.toLocalDate())) {
                    0 -> null
                    1 -> stringResource(R.string.clock_tomorrow)
                    else -> stringResource(if (t.toLocalDate() > local.toLocalDate()) R.string.clock_tomorrow else R.string.clock_yesterday)
                }
                val off = if (diffH == 0.0) stringResource(R.string.clock_local) else (if (diffH > 0) "+" else "−") + Fmt.oneDecimal(kotlin.math.abs(diffH)).removeSuffix(".0") + "h"
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (z == ZoneId.systemDefault()) stringResource(R.string.clock_here, cityOf(z)) else cityOf(z), style = MaterialTheme.typography.bodyLarge)
                        Text(listOfNotNull(day, off).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(formatTime(t, is24(item), false), style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"))
                }
            }
            MenuDivider()
            MenuEntry(Sym.ALARM, stringResource(R.string.clock_alarms)) { host.close(); Env.launch(Intent(AlarmClock.ACTION_SHOW_ALARMS)) }
            MenuEntry(Sym.SETTINGS, stringResource(R.string.clock_date_settings)) { host.close(); Env.launch(Intent(Settings.ACTION_DATE_SETTINGS)) }
        }
    }

    override val options: @Composable (ItemConfig, (ItemConfig) -> Unit) -> Unit = { item, set ->
        ZonePicker(item.options["zone"]) { set(item.with("zone", it)) }
        TextRow(stringResource(R.string.option_label), item.opt("label", ""), help = stringResource(R.string.clock_label_help),
            placeholder = stringResource(R.string.option_none)) {
            set(item.with("label", it.take(12).ifBlank { null }))
        }
        ChoiceRow(stringResource(R.string.clock_hours), listOf("system" to stringResource(R.string.option_like_system),
            "24" to stringResource(R.string.clock_hours_24), "12" to stringResource(R.string.clock_hours_12)), item.opt("hours", "system")) {
            set(item.with("hours", it))
        }
        SwitchRow(stringResource(R.string.clock_seconds), item.optBool("seconds", true)) { set(item.with("seconds", it.toString())) }
    }
}

/** Search-as-you-type over the IANA zones. */
@Composable
fun ZonePicker(current: String?, onPick: (String?) -> Unit) {
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(current?.let { stringResource(R.string.clock_zone_named, cityOf(it), it) } ?: stringResource(R.string.clock_zone_device),
            style = MaterialTheme.typography.labelLarge)
        TextRow(stringResource(R.string.clock_zone_find), query, placeholder = stringResource(R.string.clock_zone_find_hint)) { query = it }
        if (query.length >= 2) {
            val q = query.trim().replace(' ', '_').lowercase(Locale.ROOT)
            val hits = ZoneId.getAvailableZoneIds().filter { it.lowercase(Locale.ROOT).contains(q) && it.contains('/') }.sorted().take(8)
            hits.forEach { id -> MenuEntry(Sym.PUBLIC, cityOf(ZoneId.of(id)), id) { onPick(id); query = "" } }
            if (hits.isEmpty()) Text(stringResource(R.string.clock_zone_none), style = MaterialTheme.typography.bodySmall)
        }
        if (current != null) TextButton(onClick = { onPick(null) }) { Text(stringResource(R.string.clock_zone_use_device)) }
    }
}
