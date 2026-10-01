package io.github.kuscher.bentobar.ui

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarDuration
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.kuscher.bentobar.R
import io.github.kuscher.bentobar.bar.Strip
import io.github.kuscher.bentobar.bar.StripEntry
import io.github.kuscher.bentobar.bar.StripEvents
import io.github.kuscher.bentobar.bar.StripLook
import io.github.kuscher.bentobar.bar.Contrast
import io.github.kuscher.bentobar.data.ColorMode
import io.github.kuscher.bentobar.data.Display
import io.github.kuscher.bentobar.data.ItemConfig
import io.github.kuscher.bentobar.data.Position
import io.github.kuscher.bentobar.data.Section
import io.github.kuscher.bentobar.data.Store
import io.github.kuscher.bentobar.items.ItemState
import io.github.kuscher.bentobar.items.Items
import io.github.kuscher.bentobar.items.Ticker
import io.github.kuscher.bentobar.util.Dates
import io.github.kuscher.bentobar.util.Fonts
import io.github.kuscher.bentobar.util.Sym
import io.github.kuscher.bentobar.util.SymIcon
import java.time.ZonedDateTime

@Composable
fun BarPage(running: Boolean, selected: String?, onSelect: (String?) -> Unit, onSetup: () -> Unit) {
    val cfg by Store.config.collectAsState()
    val states by Ticker.states.collectAsState()
    val item = cfg.items.firstOrNull { it.id == selected }
    val snackbar = remember { SnackbarHostState() }
    val deleted by Undo.deleted.collectAsState()
    val res = androidx.compose.ui.platform.LocalResources.current
    LaunchedEffect(deleted) {
        val d = deleted ?: return@LaunchedEffect
        val name = Items.of(d.item.type)?.title ?: res.getString(R.string.bar_deleted_fallback)
        if (snackbar.showSnackbar(res.getString(R.string.bar_deleted, name), res.getString(R.string.bar_undo),
                duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) Undo.restore(d)
        Undo.deleted.value = null
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
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
                        TextButton(onClick = { onSelect(null) }) { SymIcon(Sym.ARROW_BACK, size = 18.sp); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.bar_all_items)) }
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
                Text(stringResource(R.string.bar_banner_title), style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(stringResource(R.string.bar_banner_text),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            FilledTonalButton(onClick = onSetup) { Text(stringResource(R.string.bar_banner_set_up)) }
        }
    }
}

@Composable
private fun Placeholder() {
    Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        SymIcon(Sym.TOUCH_APP, size = 40.sp, color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.bar_placeholder_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.bar_placeholder_text), style = MaterialTheme.typography.bodyMedium,
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
    // The preview uses the strip's real colours: the sampled bar when BentoBar is running and the
    // colour is automatic, else the forced Light or Dark choice.
    val live by io.github.kuscher.bentobar.bar.BarLook.current.collectAsState()
    val bar = when (cfg.color) {
        ColorMode.LIGHT -> Contrast.resolve(Color.White, null)
        ColorMode.DARK -> Contrast.resolve(Contrast.DARK_TEXT, null)
        ColorMode.AUTO -> live ?: Contrast.resolve(Color.White, null)
    }
    val look = StripLook(bar.fg, bar.barDark, cfg.textSize, cfg.spacing.dp, cfg.pill, bar.background)
    val barBrush = when {
        bar.opaque -> androidx.compose.ui.graphics.SolidColor(bar.background)
        bar.barDark -> Brush.horizontalGradient(listOf(Color(0xFF5B6F8A), Color(0xFF3F6F73), Color(0xFF4F7E78)))
        else -> Brush.horizontalGradient(listOf(Color(0xFFDDE6F3), Color(0xFFD5E8E6), Color(0xFFE3EEE9)))
    }
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
                .background(barBrush),
        ) {
            val now = remember(tick) { ZonedDateTime.now() }
            // The system's 12/24-hour setting and the locale's own short date, like the real status bar.
            val h24 = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
            Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(Dates.format(Dates.timeSkeleton(h24), now) + "   " + Dates.format("EEEMMMd", now), color = look.fg,
                    fontFamily = Fonts.bar, fontSize = 14.sp)
                if (cfg.position == Position.LEFT) Spacer(Modifier.width(16.dp)) else Spacer(Modifier.weight(1f))
                Strip(entries(visible), if (expanded) entries(hidden) else emptyList(), cfg.chevron && hidden.isNotEmpty(), expanded,
                    cfg.position != Position.LEFT, look, maxPx, 52.dp, events)
                if (cfg.position == Position.LEFT) Spacer(Modifier.weight(1f)) else if (cfg.position == Position.CENTER) Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("US", color = look.fg, fontFamily = Fonts.bar, fontSize = 13.sp)
                    SymIcon(Sym.NOTIFICATIONS, size = 18.sp, color = look.fg)
                    SymIcon(Sym.WIFI, size = 18.sp, color = look.fg)
                    SymIcon(Sym.BATTERY_FULL, size = 18.sp, color = look.fg)
                }
            }
        }
        Text(stringResource(if (hidden.isNotEmpty()) R.string.bar_preview_hint_hidden else R.string.bar_preview_hint),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp, top = 6.dp))
    }
}

@Composable
private fun Sections(items: List<ItemConfig>, states: Map<String, ItemState>, selected: String?, onSelect: (String?) -> Unit) {
    SectionCard(Section.SHOWN, stringResource(R.string.section_shown), stringResource(R.string.section_shown_help), items, states, selected, onSelect)
    val cfg by Store.config.collectAsState()
    // Says how hidden items come back with the current settings (hover reveal is off by default).
    val reveal = stringResource(when {
        cfg.chevron && cfg.revealOnHover -> R.string.section_hidden_reveal_both
        cfg.chevron -> R.string.section_hidden_reveal_chevron
        cfg.revealOnHover -> R.string.section_hidden_reveal_hover
        else -> R.string.section_hidden_reveal_none
    })
    SectionCard(Section.HIDDEN, stringResource(R.string.section_hidden), stringResource(R.string.section_hidden_help, reveal), items, states, selected, onSelect)
    SectionCard(Section.OFF, stringResource(R.string.common_off), stringResource(R.string.section_off_help), items, states, selected, onSelect)
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
            if (items.isEmpty()) Text(stringResource(R.string.section_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline,
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
            val overflow by io.github.kuscher.bentobar.bar.BarOverflow.ids.collectAsState()
            val detail = listOfNotNull(if (item.id in overflow) stringResource(R.string.row_overflow) else null,
                state?.text?.takeIf { it.isNotBlank() },
                if (item.whenActive && item.section == Section.HIDDEN) stringResource(R.string.row_when_active) else null,
                when (item.display) { Display.ICON -> stringResource(R.string.row_icon_only); Display.TEXT -> stringResource(R.string.row_text_only); else -> null }).joinToString(" · ")
            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = { Store.move(item.id, item.section, index - 1) }, enabled = index > 0) { SymIcon(Sym.ARROW_UPWARD, size = 18.sp, contentDescription = stringResource(R.string.row_move_up, type.title)) }
        IconButton(onClick = { Store.move(item.id, item.section, index + 1) }, enabled = index < count - 1) { SymIcon(Sym.ARROW_DOWNWARD, size = 18.sp, contentDescription = stringResource(R.string.row_move_down, type.title)) }
        Box {
            IconButton(onClick = { menu = true }) { SymIcon(Sym.MENU, size = 18.sp, contentDescription = stringResource(R.string.row_more, type.title)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (item.section != Section.SHOWN) DropdownMenuItem(text = { Text(stringResource(R.string.row_show_in_bar)) }, onClick = { menu = false; Store.move(item.id, Section.SHOWN, 999) },
                    leadingIcon = { SymIcon(Sym.VISIBILITY, size = 18.sp) })
                if (item.section != Section.HIDDEN) DropdownMenuItem(text = { Text(stringResource(R.string.section_hidden)) }, onClick = { menu = false; Store.move(item.id, Section.HIDDEN, 999) },
                    leadingIcon = { SymIcon(Sym.VISIBILITY_OFF, size = 18.sp) })
                if (item.section != Section.OFF) DropdownMenuItem(text = { Text(stringResource(R.string.common_turn_off)) }, onClick = { menu = false; Store.move(item.id, Section.OFF, 999) },
                    leadingIcon = { SymIcon(Sym.REMOVE, size = 18.sp) })
                DropdownMenuItem(text = { Text(stringResource(R.string.common_delete)) }, onClick = { menu = false; if (selected) onSelect(null); Undo.delete(item) },
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
                        Text(stringResource(R.string.detail_needs_calendar), style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onTertiaryContainer)
                        FilledTonalButton(onClick = { (context as? android.app.Activity)?.requestPermissions(missing.toTypedArray(), 2) }) { Text(stringResource(R.string.common_allow)) }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            ChoiceRow(stringResource(R.string.detail_where), listOf(Section.SHOWN to stringResource(R.string.section_shown),
                Section.HIDDEN to stringResource(R.string.section_hidden), Section.OFF to stringResource(R.string.common_off)), item.section) { s ->
                Store.move(item.id, s, 999)
            }
            if (type.canBeActive) SwitchRow(stringResource(R.string.detail_when_active), item.whenActive,
                help = stringResource(R.string.detail_when_active_help)) { on ->
                Store.updateItem(item.id) { it.copy(whenActive = on, section = if (on && it.section == Section.SHOWN) Section.HIDDEN else it.section) }
            }
            ChoiceRow(stringResource(R.string.display_show_as), listOf(Display.ICON_AND_TEXT to stringResource(R.string.display_icon_and_text),
                Display.TEXT to stringResource(R.string.display_text), Display.ICON to stringResource(R.string.display_icon)), item.display) { d ->
                Store.updateItem(item.id) { it.copy(display = d) }; Ticker.refresh()
            }
            type.options?.let { opts ->
                Spacer(Modifier.height(4.dp))
                SectionLabel(stringResource(R.string.detail_options))
                opts(item) { changed -> Store.updateItem(item.id) { changed }; Ticker.refresh() }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { onSelect(Store.add(item.type, item.section, item.options)) }) { Text(stringResource(R.string.detail_duplicate)) }
                TextButton(onClick = { onSelect(null); Undo.delete(item) }) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

/** Delete with an undo: the item and where it was, until the snackbar goes. */
object Undo {
    data class Deleted(val item: ItemConfig, val index: Int)
    val deleted = kotlinx.coroutines.flow.MutableStateFlow<Deleted?>(null)

    fun delete(item: ItemConfig) {
        deleted.value = Deleted(item, Store.config.value.items.indexOfFirst { it.id == item.id })
        Store.remove(item.id)
    }

    fun restore(d: Deleted) = Store.update { c ->
        if (c.items.any { it.id == d.item.id }) c
        else c.copy(items = c.items.toMutableList().apply { add(d.index.coerceIn(0, size), d.item) })
    }
}
