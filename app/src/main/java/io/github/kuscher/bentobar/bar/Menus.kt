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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.kuscher.bentobar.data.Display
import io.github.kuscher.bentobar.data.ItemConfig
import io.github.kuscher.bentobar.data.Section
import io.github.kuscher.bentobar.data.Store
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

/** Right-click on an item: where it lives and how it looks, without opening settings. */
@Composable
fun ItemContextMenu(itemId: String, host: MenuHost, openMenu: () -> Unit) {
    val cfg by Store.config.collectAsState()
    val item = cfg.items.firstOrNull { it.id == itemId } ?: return
    val type = Items.of(item.type) ?: return
    MenuCard(type.icon, type.title, when (item.section) {
        Section.SHOWN -> "Shown in the bar"
        Section.HIDDEN -> if (item.whenActive) "Hidden, shows when active" else "Hidden behind ‹"
        Section.OFF -> "Off"
    }) {
        if (type.menu != null) MenuEntry(Sym.OPEN_IN_NEW, "Open") { openMenu() }
        if (item.section != Section.SHOWN) MenuEntry(Sym.VISIBILITY, "Always show") { Store.updateItem(item.id) { it.copy(section = Section.SHOWN) } }
        if (item.section != Section.HIDDEN) MenuEntry(Sym.VISIBILITY_OFF, "Hide behind ‹") { Store.updateItem(item.id) { it.copy(section = Section.HIDDEN) } }
        if (type.canBeActive) MenuEntry(if (item.whenActive) Sym.CHECK_CIRCLE else Sym.UPDATE, "Show when active",
            detail = if (item.whenActive) "✓" else null) {
            Store.updateItem(item.id) { it.copy(whenActive = !it.whenActive, section = if (!it.whenActive && it.section == Section.SHOWN) Section.HIDDEN else it.section) }
        }
        SectionLabel("Show as")
        val displays = listOf(Display.ICON_AND_TEXT to "Icon and text", Display.TEXT to "Text", Display.ICON to "Icon")
        ChipRow(displays.map { (d, l) -> if (d == item.display) "✓ $l" else l }) { i ->
            Store.updateItem(item.id) { it.copy(display = displays[i].first) }
            Ticker.refresh()
        }
        MenuDivider()
        MenuEntry(Sym.TUNE, "Item settings…") { host.openItemSettings(item.id) }
        // It turns the item off (kept with its settings), so it says so; Delete is in settings, with an undo.
        MenuEntry(Sym.VISIBILITY_OFF, "Turn off") { host.close(); Store.updateItem(item.id) { it.copy(section = Section.OFF) } }
    }
}

/** Right-click on ‹, or the list view of hidden items. */
@Composable
fun BentoBarMenu(host: MenuHost, openItem: (ItemConfig) -> Unit, hideBar: () -> Unit) {
    val states by Ticker.states.collectAsState()
    // Worked out here, not when the menu opened, so an item popping out (or in) updates the list.
    val cfg by Store.config.collectAsState()
    val overflow by io.github.kuscher.bentobar.bar.BarOverflow.ids.collectAsState()
    val hidden = cfg.items.filter {
        (it.section == Section.HIDDEN && !(it.whenActive && states[it.id]?.active == true)) || it.id in overflow
    }
    MenuCard(Sym.WYSIWYG, "BentoBar", if (hidden.isEmpty()) "No hidden items" else "${hidden.size} hidden",
        iconRes = io.github.kuscher.bentobar.R.drawable.ic_bentobar) {
        if (hidden.isNotEmpty()) {
            SectionLabel("Hidden items")
            hidden.forEach { item ->
                val type = Items.of(item.type) ?: return@forEach
                val s = states[item.id]
                MenuEntry(s?.icon?.takeIf { it.isNotEmpty() } ?: type.icon, s?.text ?: type.title, detail = type.title.takeIf { s?.text != null }) { openItem(item) }
            }
            MenuDivider()
        }
        MenuEntry(Sym.EDIT, "Edit the bar…") { host.openItemSettings("") }
        MenuEntry(Sym.VISIBILITY_OFF, "Hide BentoBar", detail = "e.g. to present") { host.close(); hideBar() }
        Text("Bring it back from BentoBar's app or its Quick Settings tile.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 4.dp))
    }
}
