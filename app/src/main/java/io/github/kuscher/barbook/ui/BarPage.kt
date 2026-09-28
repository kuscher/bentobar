package io.github.kuscher.barbook.ui

import android.content.pm.PackageManager
import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kuscher.barbook.bar.Strip
import io.github.kuscher.barbook.bar.StripEntry
import io.github.kuscher.barbook.bar.StripEvents
import io.github.kuscher.barbook.bar.StripLook
import io.github.kuscher.barbook.data.Display
import io.github.kuscher.barbook.data.ItemConfig
import io.github.kuscher.barbook.data.Position
import io.github.kuscher.barbook.data.Section
import io.github.kuscher.barbook.data.Store
import io.github.kuscher.barbook.items.ItemState
import io.github.kuscher.barbook.items.Items
import io.github.kuscher.barbook.items.Ticker
import io.github.kuscher.barbook.util.Fonts
import io.github.kuscher.barbook.util.Sym
import io.github.kuscher.barbook.util.SymIcon
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun BarPage(running: Boolean, selected: String?, onSelect: (String?) -> Unit, onSetup: () -> Unit) {
    val cfg by Store.config.collectAsState()
    val states by Ticker.states.collectAsState()
    val item = cfg.items.firstOrNull { it.id == selected }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 900.dp
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp)) {
            if (!running) SetupBanner(onSetup)
            BarPreview(states, selected, onSelect)
            Spacer(Modifier.height(16.dp))
            if (wide) {
                Row(Modifier.weight(1f)) {
                    Column(Modifier.width(470.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                        Sections(cfg.items, states, selected, onSelect)
                    }
                    Spacer(Modifier.width(20.dp))
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        if (item != null) Column(Modifier.verticalScroll(rememberScrollState())) { ItemDetail(item, states[item.id], onSelect) }
                        else Placeholder()
                    }
                }
            } else {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    if (item != null) {
                        TextButton(onClick = { onSelect(null) }) { SymIcon(Sym.ARROW_BACK, size = 18.sp); Spacer(Modifier.width(6.dp)); Text("All items") }
                        ItemDetail(item, states[item.id], onSelect)
                    } else Sections(cfg.items, states, selected, onSelect)
                }
            }
        }
    }
}

@Composable
private fun SetupBanner(onSetup: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            SymIcon(Sym.INFO, size = 24.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("One step left: turn on BarBook's bar", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("Android only lets apps draw on the status bar through an accessibility service. Setup explains what that means.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            FilledTonalButton(onClick = onSetup) { Text("Set up") }
        }
    }
}

@Composable
private fun Placeholder() {
    Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        SymIcon(Sym.TOUCH_APP, size = 40.sp, color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(10.dp))
        Text("Pick an item to change it", style = MaterialTheme.typography.titleMedium)
        Text("Or right-click an item in the status bar.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A mock status bar with the real strip in it; clicking an item selects it. */
@Composable
private fun BarPreview(states: Map<String, ItemState>, selected: String?, onSelect: (String?) -> Unit) {
    val cfg by Store.config.collectAsState()
    val tick by Ticker.tick.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    val active = { it: ItemConfig -> it.whenActive && states[it.id]?.active == true }
    val visible = cfg.items.filter { it.section == Section.SHOWN || (it.section == Section.HIDDEN && active(it)) }
    val hidden = cfg.items.filter { it.section == Section.HIDDEN && !active(it) }
    val entries = { list: List<ItemConfig> -> list.map { StripEntry(it, states[it.id] ?: Ticker.stateOf(it)) } }
    val look = StripLook(Color.White, true, cfg.textSize, cfg.spacing.dp, cfg.pill)
    val events = object : StripEvents {
        override fun click(item: ItemConfig, at: Rect) = onSelect(item.id)
        override fun context(item: ItemConfig, at: Rect) = onSelect(item.id)
        override fun scroll(item: ItemConfig, steps: Int) { Items.of(item.type)?.onScroll(item, steps); Ticker.refresh() }
        override fun chevron(at: Rect) { expanded = !expanded }
        override fun chevronContext(at: Rect) { expanded = !expanded }
        override fun hover(inside: Boolean) {}
        override fun placed(id: String, at: Rect) {}
    }
    val maxPx = with(LocalDensity.current) { 900.dp.roundToPx() }
    Column {
        Box(
            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(18.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFF5B6F8A), Color(0xFF3F6F73), Color(0xFF4F7E78)))),
        ) {
            val now = remember(tick) { LocalDateTime.now() }
            Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(now.format(DateTimeFormatter.ofPattern("H:mm   EEE, MMM d", Locale.getDefault())), color = Color.White,
                    fontFamily = Fonts.bar, fontSize = 14.sp)
                if (cfg.position == Position.LEFT) Spacer(Modifier.width(16.dp)) else Spacer(Modifier.weight(1f))
                Strip(entries(visible), if (expanded) entries(hidden) else emptyList(), cfg.chevron && hidden.isNotEmpty(), expanded,
                    cfg.position != Position.LEFT, look, maxPx, 52.dp, events)
                if (cfg.position == Position.LEFT) Spacer(Modifier.weight(1f)) else if (cfg.position == Position.CENTER) Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("US", color = Color.White, fontFamily = Fonts.bar, fontSize = 13.sp)
                    SymIcon(Sym.NOTIFICATIONS, size = 18.sp, color = Color.White)
                    SymIcon(Sym.WIFI, size = 18.sp, color = Color.White)
                    SymIcon(Sym.BATTERY_FULL, size = 18.sp, color = Color.White)
                }
            }
        }
        Text("Preview. Click an item to edit it" + if (hidden.isNotEmpty()) ", and ‹ to show hidden items." else ".",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp, top = 6.dp))
    }
}

@Composable
private fun Sections(items: List<ItemConfig>, states: Map<String, ItemState>, selected: String?, onSelect: (String?) -> Unit) {
    SectionCard(Section.SHOWN, "In the bar", "Left to right, as they appear", items, states, selected, onSelect)
    SectionCard(Section.HIDDEN, "Hidden behind ‹", "Revealed by the ‹ button or on hover. Items set to show when active pop out on their own.", items, states, selected, onSelect)
    SectionCard(Section.OFF, "Off", "Kept with their settings, not shown", items, states, selected, onSelect)
}

@Composable
private fun SectionCard(section: Section, title: String, help: String, all: List<ItemConfig>, states: Map<String, ItemState>,
                        selected: String?, onSelect: (String?) -> Unit) {
    val items = all.filter { it.section == section }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
            Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(6.dp))
            if (items.isEmpty()) Text("Nothing here", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            items.forEachIndexed { i, item -> ItemRow(item, states[item.id], i, items.size, item.id == selected, onSelect) }
        }
    }
}

@Composable
private fun ItemRow(item: ItemConfig, state: ItemState?, index: Int, count: Int, selected: Boolean, onSelect: (String?) -> Unit) {
    val type = Items.of(item.type) ?: return
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(14.dp))
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable { onSelect(item.id) }.pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center) { SymIcon(type.icon, size = 20.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(type.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val detail = listOfNotNull(state?.text?.takeIf { it.isNotBlank() },
                if (item.whenActive && item.section == Section.HIDDEN) "shows when active" else null,
                when (item.display) { Display.ICON -> "icon only"; Display.TEXT -> "text only"; else -> null }).joinToString(" · ")
            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = { Store.move(item.id, item.section, index - 1) }, enabled = index > 0) { SymIcon(Sym.ARROW_UPWARD, size = 18.sp) }
        IconButton(onClick = { Store.move(item.id, item.section, index + 1) }, enabled = index < count - 1) { SymIcon(Sym.ARROW_DOWNWARD, size = 18.sp) }
        Box {
            IconButton(onClick = { menu = true }) { SymIcon(Sym.MENU, size = 18.sp) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (item.section != Section.SHOWN) DropdownMenuItem(text = { Text("Show in the bar") }, onClick = { menu = false; Store.move(item.id, Section.SHOWN, 999) },
                    leadingIcon = { SymIcon(Sym.VISIBILITY, size = 18.sp) })
                if (item.section != Section.HIDDEN) DropdownMenuItem(text = { Text("Hide behind ‹") }, onClick = { menu = false; Store.move(item.id, Section.HIDDEN, 999) },
                    leadingIcon = { SymIcon(Sym.VISIBILITY_OFF, size = 18.sp) })
                if (item.section != Section.OFF) DropdownMenuItem(text = { Text("Turn off") }, onClick = { menu = false; Store.move(item.id, Section.OFF, 999) },
                    leadingIcon = { SymIcon(Sym.REMOVE, size = 18.sp) })
                DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; if (selected) onSelect(null); Store.remove(item.id) },
                    leadingIcon = { SymIcon(Sym.DELETE, size = 18.sp) })
            }
        }
    }
}

@Composable
private fun ItemDetail(item: ItemConfig, state: ItemState?, onSelect: (String?) -> Unit) {
    val type = Items.of(item.type) ?: return
    val context = androidx.compose.ui.platform.LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center) {
                    SymIcon(type.icon, size = 26.sp, filled = true, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(type.title, style = MaterialTheme.typography.titleLarge)
                    Text(type.blurb, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state?.text != null) Text(state.text, style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.primary, maxLines = 1)
            }
            Spacer(Modifier.height(12.dp))
            val missing = type.permissions.filter { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
            if (missing.isNotEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("This item needs access to your calendar. It stays on this device.", style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onTertiaryContainer)
                        FilledTonalButton(onClick = { (context as? android.app.Activity)?.requestPermissions(missing.toTypedArray(), 2) }) { Text("Allow") }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            ChoiceRow("Where", listOf(Section.SHOWN to "In the bar", Section.HIDDEN to "Hidden behind ‹", Section.OFF to "Off"), item.section) { s ->
                Store.move(item.id, s, 999)
            }
            if (type.canBeActive) SwitchRow("Show itself when active", item.whenActive,
                help = "Stays hidden until it has something to say, like a running timer or a meeting about to start") { on ->
                Store.updateItem(item.id) { it.copy(whenActive = on, section = if (on && it.section == Section.SHOWN) Section.HIDDEN else it.section) }
            }
            ChoiceRow("Show as", listOf(Display.ICON_AND_TEXT to "Icon and text", Display.TEXT to "Text", Display.ICON to "Icon"), item.display) { d ->
                Store.updateItem(item.id) { it.copy(display = d) }; Ticker.refresh()
            }
            type.options?.let { opts ->
                Spacer(Modifier.height(4.dp))
                SectionLabel("Options")
                opts(item) { changed -> Store.updateItem(item.id) { changed }; Ticker.refresh() }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { onSelect(Store.add(item.type, item.section, item.options)) }) { Text("Duplicate") }
                TextButton(onClick = { onSelect(null); Store.remove(item.id) }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
