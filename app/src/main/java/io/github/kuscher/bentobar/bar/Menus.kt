package io.github.kuscher.bentobar.bar

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import io.github.kuscher.bentobar.ui.GlassLook
import io.github.kuscher.bentobar.ui.LocalMenuGlass
import io.github.kuscher.bentobar.ui.MenuGlassState
import io.github.kuscher.bentobar.ui.MenuMotion
import io.github.kuscher.bentobar.ui.MenuRoom
import io.github.kuscher.bentobar.ui.ShownGlass
import io.github.kuscher.bentobar.ui.menuGlass
import io.github.kuscher.bentobar.ui.menuShadow
import io.github.kuscher.bentobar.ui.ChipRow
import io.github.kuscher.bentobar.ui.MenuCard
import io.github.kuscher.bentobar.ui.MenuDivider
import io.github.kuscher.bentobar.ui.MenuEntry
import io.github.kuscher.bentobar.ui.SectionLabel
import io.github.kuscher.bentobar.util.Sym

/**
 * The card every BentoBar popup sits on (docs/design/1.3): glass over a blur where the platform blurs
 * behind windows, a solid card where it doesn't, with a shadow drawn below it. [glass] is the popup's
 * state at this frame, from its window's clock: the card unfolds down from its top edge and its blocks
 * drop into place ([DropColumn]); null draws it at rest (a preview).
 */
@Composable
fun MenuSurface(width: Dp, maxHeight: Dp, glass: MenuGlassState?, content: @Composable () -> Unit) {
    BentoBarTheme {
        val g = glass ?: remember { MenuGlassState().apply { presence = 1f; shadow = 1f; clockMs = Float.POSITIVE_INFINITY; heightDp = Float.MAX_VALUE } }
        val dark = isSystemInDarkTheme()
        val scheme = MaterialTheme.colorScheme
        val look = GlassLook(
            veil = if (g.blur) scheme.surfaceContainerLowest.copy(alpha = if (dark) 0.62f else 0.56f) else scheme.surfaceContainerHigh,
            rim = Color.White.copy(alpha = if (dark) 0.44f else 0.80f),
            hairline = Color.Black.copy(alpha = if (dark) 0.28f else 0.20f),
            dark = dark,
        )
        val density = LocalDensity.current.density
        // The window is the card: its shadow is drawn in a window of its own (MenuShadow).
        Box {
            Box(
                Modifier.width(width)
                    .onSizeChanged {
                        g.fullHeightPx = it.height
                        // Open and at rest: the glass follows its contents when they change (a city added, a flight found).
                        if (g.clockMs.isInfinite() && glass != null) g.heightDp = it.height / density
                    }
                    .graphicsLayer {
                        alpha = g.alpha
                        clip = true
                        shape = ShownGlass(g.heightDp * density, MenuMotion.RADIUS_DP * density)
                    }
                    .menuGlass(g, look),
            ) {
                // Text with no colour of its own takes the glass's (Material's Surface did this; the glass is drawn here).
                CompositionLocalProvider(LocalMenuGlass provides glass, LocalContentColor provides scheme.onSurface) {
                    Column(
                        Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState())
                            .graphicsLayer { alpha = g.contents },
                    ) { content() }
                }
            }
        }
    }
}

/**
 * A popup's shadow, in a window of its own just below the popup's (`MenuWindow`): the popup's window is
 * framed to its glass for the blur, which clips whatever it draws outside the glass. Laid out exactly as
 * [MenuSurface], so the shadow lies under the card, and drawn from the same [glass] every frame.
 */
@Composable
fun MenuShadow(width: Dp, glass: MenuGlassState) {
    val dark = isSystemInDarkTheme()
    val density = LocalDensity.current.density
    Box(Modifier.padding(start = MenuRoom.side, end = MenuRoom.side, top = MenuRoom.top, bottom = MenuRoom.bottom)) {
        Box(Modifier.width(width).height((glass.fullHeightPx / density).dp).menuShadow(glass, dark))
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
