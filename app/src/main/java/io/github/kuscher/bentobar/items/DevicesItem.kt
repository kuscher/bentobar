package io.github.kuscher.bentobar.items

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.kuscher.bentobar.R
import io.github.kuscher.bentobar.data.ItemConfig
import io.github.kuscher.bentobar.ui.MenuCard
import io.github.kuscher.bentobar.ui.MenuNote
import io.github.kuscher.bentobar.util.Sym

/**
 * Device batteries: the battery of a mouse, keyboard, stylus or game controller that tells Android
 * its level. No permission is involved: Android's input devices report it themselves.
 *
 * This is the first cut: the item, its rule and its empty state. Reading the devices comes next.
 */
object DevicesItem : ItemType("devices", R.string.item_devices_title, Sym.MOUSE, R.string.item_devices_desc) {
    override val refreshMs = 5_000L
    override val canBeActive = true
    override val addsWhenActive = true

    /** The same rule as the Battery item's, so the two behave alike. */
    private val below = Threshold("lowPct", 20, 5..50, step = 5) { "$it%" }
    override val trigger = Trigger(R.string.trigger_devices, R.string.trigger_devices_short, below)

    // Outlined: no device reports a battery.
    override fun state(item: ItemConfig) = ItemState(icon = Sym.MOUSE, filled = false, desc = Env.str(R.string.devices_none))

    override val menu: @Composable (ItemConfig, MenuHost) -> Unit = { _, _ ->
        MenuCard(Sym.MOUSE, stringResource(R.string.item_devices_title), stringResource(R.string.devices_none)) {
            MenuNote(stringResource(R.string.devices_note))
        }
    }
}
