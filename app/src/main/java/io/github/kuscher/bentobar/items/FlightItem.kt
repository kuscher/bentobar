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
 * Flight: counts down to one flight's departure and landing, with the gate, from AirLabs and with a
 * key of the user's own. One of the two item types that go online ([online]), and only once a key
 * was saved on this install. The key is kept by [Online], never in the layout.
 *
 * This is the first cut: the item, its rule and the words shown before anything is sent. The key's
 * settings, tracking a flight and the menu come next.
 */
object FlightItem : ItemType("flight", R.string.item_flight_title, Sym.FLIGHT, R.string.item_flight_desc) {
    override val refreshMs = 10_000L
    override val menuWidthDp = 340
    override val online = Online.Service.AIRLABS
    override val canBeActive = true

    /** How many hours before departure the item comes out. */
    private val before = Threshold("beforeHours", 24, 3..48, step = 3) { Env.plural(R.plurals.common_hours, it, it) }
    override val trigger = Trigger(R.string.trigger_flight, R.string.trigger_flight_short, before)

    // One plane while idle (it looks the same filled and outlined, so there is no "off" look).
    override fun state(item: ItemConfig) = ItemState(icon = Sym.FLIGHT, desc = Env.str(R.string.flight_not_set_up))

    override val menu: @Composable (ItemConfig, MenuHost) -> Unit = { _, _ ->
        MenuCard(Sym.FLIGHT, stringResource(R.string.item_flight_title), stringResource(R.string.flight_not_set_up)) {
            OnlineWords(Online.Service.AIRLABS)
        }
    }
}
