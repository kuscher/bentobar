package io.github.kuscher.bentobar.bar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.kuscher.bentobar.R
import io.github.kuscher.bentobar.data.Display
import io.github.kuscher.bentobar.data.ItemConfig
import io.github.kuscher.bentobar.data.Section
import io.github.kuscher.bentobar.data.Store
import io.github.kuscher.bentobar.data.notDrawn
import io.github.kuscher.bentobar.items.Items
import io.github.kuscher.bentobar.items.MenuHost
import io.github.kuscher.bentobar.items.Ticker
import io.github.kuscher.bentobar.ui.BentoBarTheme
import io.github.kuscher.bentobar.ui.ChipRow
import io.github.kuscher.bentobar.ui.MenuCard
import io.github.kuscher.bentobar.ui.MenuDivider
import io.github.kuscher.bentobar.ui.MenuEntry
import io.github.kuscher.bentobar.ui.SectionLabel
import io.github.kuscher.bentobar.util.Sym

/** Room around a menu card inside its window, for the shadow. */
val MENU_MARGIN = 12.dp

/** The card every BentoBar menu sits on, with a short drop-in animation. */
@Composable
fun MenuSurface(width: Dp, maxHeight: Dp, content: @Composable () -> Unit) {
    BentoBarTheme {
        val anim = remember { Animatable(0f) }
        LaunchedEffect(Unit) { anim.animateTo(1f, tween(140)) }
        Box(Modifier.padding(MENU_MARGIN)) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.width(width).graphicsLayer {
                    alpha = anim.value
                    translationY = (1f - anim.value) * -8.dp.toPx()
                },
            ) {
                Column(Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState())) { content() }
            }
        }
    }
}

/**
 * An item's own menu, drawn for the item as the layout has it now: a menu that changes its item (a
 * city added, a flight tracked) is drawn again with the new options. An item that is deleted or
 * turned off while its menu is open closes it.
 */
@Composable
fun ItemMenu(itemId: String, host: MenuHost) {
    val cfg by Store.config.collectAsState()
    val item = cfg.items.firstOrNull { it.id == itemId && it.section != Section.OFF }
    val menu = item?.let { Items.of(it.type)?.menu }
    if (item == null || menu == null) { LaunchedEffect(itemId) { host.close() }; return }
    menu(item, host)
}

/** Right-click on an item: where it lives and how it looks, without opening settings. */
@Composable
fun ItemContextMenu(itemId: String, host: MenuHost, openMenu: () -> Unit) {
    val cfg by Store.config.collectAsState()
    val item = cfg.items.firstOrNull { it.id == itemId } ?: return
    val type = Items.of(item.type) ?: return
    MenuCard(type.icon, type.title, when (item.section) {
        Section.SHOWN -> stringResource(R.string.context_shown)
        Section.HIDDEN -> stringResource(if (item.whenActive) R.string.context_hidden_when_active else R.string.section_hidden)
        Section.OFF -> stringResource(R.string.common_off)
    }) {
        if (type.menu != null) MenuEntry(Sym.OPEN_IN_NEW, stringResource(R.string.common_open)) { openMenu() }
        if (item.section != Section.SHOWN) MenuEntry(Sym.VISIBILITY, stringResource(R.string.context_always_show)) { Store.updateItem(item.id) { it.copy(section = Section.SHOWN) } }
        if (item.section != Section.HIDDEN) MenuEntry(Sym.VISIBILITY_OFF, stringResource(R.string.section_hidden)) { Store.updateItem(item.id) { it.copy(section = Section.HIDDEN) } }
        if (type.canBeActive) MenuEntry(if (item.whenActive) Sym.CHECK_CIRCLE else Sym.UPDATE, stringResource(R.string.context_show_when_active),
            detail = if (item.whenActive) "✓" else null) {
            Store.updateItem(item.id) { it.copy(whenActive = !it.whenActive, section = if (!it.whenActive && it.section == Section.SHOWN) Section.HIDDEN else it.section) }
        }
        SectionLabel(stringResource(R.string.display_show_as))
        val displays = listOf(Display.ICON_AND_TEXT to stringResource(R.string.display_icon_and_text), Display.TEXT to stringResource(R.string.display_text),
            Display.ICON to stringResource(R.string.display_icon))
        ChipRow(displays.map { it.second }, selected = displays.indexOfFirst { it.first == item.display }) { i ->
            Store.updateItem(item.id) { it.copy(display = displays[i].first) }
            Ticker.refresh()
        }
        MenuDivider()
        MenuEntry(Sym.TUNE, stringResource(R.string.context_item_settings)) { host.openItemSettings(item.id) }
        // It turns the item off (kept with its settings), so it says so; Delete is in settings, with an undo.
        // Its own icon: the crossed-out eye two rows up means Hidden, which keeps the item behind ‹.
        MenuEntry(Sym.DO_NOT_DISTURB_ON, stringResource(R.string.common_turn_off)) { host.close(); Store.updateItem(item.id) { it.copy(section = Section.OFF) } }
    }
}

/**
 * Right-click on ‹, or the list view of hidden items. From the keyboard ([everything], the "BentoBar
 * menu" shortcut) it lists every item in the bar, focused on the first, so arrows and Enter reach
 * any item's menu.
 */
@Composable
fun BentoBarMenu(host: MenuHost, openItem: (ItemConfig) -> Unit, hideBar: () -> Unit, everything: Boolean = false) {
    val states by Ticker.states.collectAsState()
    // Worked out here, not when the menu opened, so an item popping out (or in) updates the list.
    val cfg by Store.config.collectAsState()
    val overflow by io.github.kuscher.bentobar.bar.BarOverflow.ids.collectAsState()
    val hidden = if (everything) cfg.items.filter { it.section != Section.OFF }
    else cfg.notDrawn { states[it.id]?.active == true }.let { waiting -> cfg.items.filter { it in waiting || it.id in overflow } }
    val first = remember { androidx.compose.ui.focus.FocusRequester() }
    if (everything) androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    MenuCard(Sym.WYSIWYG, stringResource(R.string.app_name),
        when {
            everything -> null // it lists every item, not just hidden ones
            hidden.isEmpty() -> stringResource(R.string.barmenu_no_hidden)
            else -> pluralStringResource(R.plurals.barmenu_hidden_count, hidden.size, hidden.size)
        },
        iconRes = io.github.kuscher.bentobar.R.drawable.ic_bentobar) {
        if (hidden.isNotEmpty()) {
            SectionLabel(stringResource(if (everything) R.string.barmenu_all_items else R.string.barmenu_hidden_items))
            hidden.forEachIndexed { i, item ->
                val type = Items.of(item.type) ?: return@forEachIndexed
                val s = states[item.id]
                val label = s?.label ?: s?.text
                MenuEntry(s?.icon?.takeIf { it.isNotEmpty() } ?: type.icon, label ?: type.title, detail = type.title.takeIf { label != null && label != it },
                    modifier = if (i == 0) Modifier.focusRequester(first) else Modifier) { openItem(item) }
            }
            MenuDivider()
        }
        if (cfg.presenting) MenuEntry(Sym.DESKTOP_WINDOWS, stringResource(R.string.barmenu_presenting_stop)) {
            host.close(); Store.update { it.copy(presenting = false) }
        } else MenuEntry(Sym.DESKTOP_WINDOWS, stringResource(R.string.barmenu_presenting), detail = stringResource(R.string.barmenu_presenting_detail)) {
            host.close(); Store.update { it.copy(presenting = true) }
        }
        MenuEntry(Sym.EDIT, stringResource(R.string.barmenu_edit)) { host.openItemSettings("") }
        MenuEntry(Sym.VISIBILITY_OFF, stringResource(R.string.barmenu_hide), detail = stringResource(R.string.barmenu_hide_detail)) { host.close(); hideBar() }
        Text(stringResource(R.string.barmenu_bring_back), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 4.dp))
    }
}
