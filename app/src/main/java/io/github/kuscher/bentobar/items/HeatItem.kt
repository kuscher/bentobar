package io.github.kuscher.bentobar.items

import android.content.Intent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.kuscher.bentobar.R
import io.github.kuscher.bentobar.data.ItemConfig
import io.github.kuscher.bentobar.ui.ChoiceRow
import io.github.kuscher.bentobar.ui.InfoRow
import io.github.kuscher.bentobar.ui.MenuCard
import io.github.kuscher.bentobar.ui.MenuDivider
import io.github.kuscher.bentobar.ui.MenuEntry
import io.github.kuscher.bentobar.ui.MenuNote
import io.github.kuscher.bentobar.ui.Meter
import io.github.kuscher.bentobar.ui.Sparkline
import io.github.kuscher.bentobar.ui.rememberTick
import io.github.kuscher.bentobar.util.Fmt
import io.github.kuscher.bentobar.util.Now
import io.github.kuscher.bentobar.util.Sym
import io.github.kuscher.bentobar.util.Units

/**
 * Heat: says when Android starts slowing a hot device, which it otherwise does without a word. From
 * Android's own thermal status; no permission is involved, and nothing is guessed where Android says
 * nothing: a device that never leaves "none" is just calm, one that reports no heat level has no
 * such row in the menu, and a battery that reads 0 has no temperature.
 *
 * What it reads is in [Heat] (the status and the temperature from the battery's sampler, the heat
 * level asked every five seconds while an item is live); what it makes of it is in [HeatRules].
 */
object HeatItem : ItemType("heat", R.string.item_heat_title, Sym.DEVICE_THERMOSTAT, R.string.item_heat_desc) {
    override val canBeActive = true
    override val addsWhenActive = true
    // The battery's sampler reads the thermal status and the battery's temperature; the CPU's is for the menu's "CPU load" row.
    override val samples = setOf("battery", "cpu")

    /** From which of Android's levels on the item shows: 1 warm, 2 hot (when Android starts to slow the device), 3 very hot. */
    private val atLeast = Threshold("level", HeatRules.MODERATE, HeatRules.LIGHT..HeatRules.SEVERE) {
        Env.str(when (it) { 1 -> R.string.heat_rule_warm; 3 -> R.string.heat_rule_very_hot; else -> R.string.heat_rule_hot })
    }
    override val trigger = Trigger(R.string.trigger_heat, R.string.trigger_heat_short, atLeast)

    /** When the status was last at each of the rule's levels, for the minute the item stays after cooling. It runs out by itself. */
    private val hold = HeatHold()

    private val barWords = intArrayOf(R.string.heat_bar_warm, R.string.heat_bar_hot, R.string.heat_bar_very_hot, R.string.heat_bar_critical, R.string.heat_bar_too_hot)
    private val words = HeatRules.Words(
        bar = { step -> Env.str(barWords[step - 1]) },
        thermal = { step -> Env.str(Heat.words(step)) },
        tempWord = { temp, word -> Env.str(R.string.heat_bar_temp_word, temp, word) },
        desc = { thermal -> Env.str(R.string.heat_desc, thermal) },
        descTemp = { thermal, degrees -> Env.str(R.string.heat_desc_temp, thermal, degrees) },
    )

    override fun onIdle() = Heat.idle()

    override fun sample(now: Long) {
        hold.note(Heat.status(), now)
        Heat.sample()
    }

    // The thermometer in every state: it looks the same filled and outlined, so the words and the tone carry the state.
    override fun state(item: ItemConfig): ItemState {
        val level = atLeast.of(item)
        val showTemp = HeatRules.showsTemp(item)
        // Which unit is Android's to say (a regional preference): asked only where a temperature is shown.
        val shown = HeatRules.bar(Heat.status(), level, hold.since(level, Now.elapsed()), showTemp, Heat.tempC(),
            fahrenheit = showTemp && Units.fahrenheit(HeatRules.unit(item)), words = words)
        return ItemState(icon = Sym.DEVICE_THERMOSTAT, text = shown.text, tone = shown.tone, active = shown.active, desc = shown.desc,
            tooltip = shown.tooltip, textLimit = 20)
    }

    override val menu: @Composable (ItemConfig, MenuHost) -> Unit = { item, host ->
        rememberTick()
        MenuCard(Sym.DEVICE_THERMOSTAT, stringResource(R.string.item_heat_title), stringResource(Heat.words(Heat.status()))) {
            // A device that reports no heat level has no such row, meter or chart, and a note without the sentence about it.
            val level = Heat.level
            if (level != null) {
                val color = if (HeatRules.over(level)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                InfoRow(stringResource(R.string.heat_level), HeatRules.percentText(level), color)
                Meter(level, color)
                Spacer(Modifier.height(8.dp))
                val chart = Heat.chart()
                // Up to 100% the chart's top is 100%; beyond, its highest reading.
                Sparkline(chart, max = maxOf(1.0, chart.maxOrNull() ?: 0.0))
            }
            // A battery that says nothing reads 0.
            val temp = Heat.tempC()
            if (temp != 0.0 && temp.isFinite()) {
                val fahrenheit = Units.fahrenheit(HeatRules.unit(item))
                InfoRow(stringResource(R.string.heat_battery_temp),
                    stringResource(if (fahrenheit) R.string.heat_temp_f else R.string.heat_temp_c, HeatRules.oneDecimal(temp, fahrenheit)))
            }
            if (Env.cpu.available) InfoRow(stringResource(R.string.heat_cpu_load), Fmt.percent(Env.cpu.total))
            Spacer(Modifier.height(6.dp))
            MenuNote(stringResource(if (level != null) R.string.heat_note else R.string.heat_note_no_level))
            MenuDivider()
            MenuEntry(Sym.BOLT, stringResource(R.string.cpu_battery_usage)) { host.close(); Env.launch(Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) }
        }
    }

    override val options: @Composable (ItemConfig, (ItemConfig) -> Unit) -> Unit = { item, set ->
        ChoiceRow(stringResource(R.string.option_show), listOf("state" to stringResource(R.string.heat_show_state),
            "temp" to stringResource(R.string.heat_battery_temp)), if (HeatRules.showsTemp(item)) "temp" else "state") { set(item.with("show", it)) }
        ChoiceRow(stringResource(R.string.option_temperature_unit), listOf("system" to stringResource(R.string.option_like_system),
            "c" to stringResource(R.string.option_celsius), "f" to stringResource(R.string.option_fahrenheit)), HeatRules.unit(item)) { set(item.with("unit", it)) }
    }

    override fun debug(args: List<String>): String? = Heat.debug(args)
}
