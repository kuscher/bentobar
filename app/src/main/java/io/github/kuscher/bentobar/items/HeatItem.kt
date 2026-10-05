package io.github.kuscher.bentobar.items

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.kuscher.bentobar.R
import io.github.kuscher.bentobar.data.ItemConfig
import io.github.kuscher.bentobar.ui.MenuCard
import io.github.kuscher.bentobar.ui.MenuNote
import io.github.kuscher.bentobar.util.Sym

/**
 * Heat: says when Android starts slowing a hot device, which it otherwise does without a word. From
 * Android's own thermal status; no permission is involved.
 *
 * This is the first cut: the item and its rule. Reading the status and the heat level comes next.
 */
object HeatItem : ItemType("heat", R.string.item_heat_title, Sym.DEVICE_THERMOSTAT, R.string.item_heat_desc) {
    override val canBeActive = true
    override val addsWhenActive = true

    /** From which of Android's levels on the item shows: 1 warm, 2 hot (when Android starts to slow the device), 3 very hot. */
    private val atLeast = Threshold("level", 2, 1..3) {
        Env.str(when (it) { 1 -> R.string.heat_level_warm; 3 -> R.string.heat_level_very_hot; else -> R.string.heat_level_hot })
    }
    override val trigger = Trigger(R.string.trigger_heat, R.string.trigger_heat_short, atLeast)

    // The icon alone while all is normal (it looks the same filled and outlined, so there is no "off" look).
    override fun state(item: ItemConfig) = ItemState(icon = Sym.DEVICE_THERMOSTAT, desc = Env.str(R.string.battery_thermal_normal))

    override val menu: @Composable (ItemConfig, MenuHost) -> Unit = { _, _ ->
        MenuCard(Sym.DEVICE_THERMOSTAT, stringResource(R.string.item_heat_title), stringResource(R.string.battery_thermal_normal)) {
            MenuNote(stringResource(R.string.heat_note))
        }
    }
}
