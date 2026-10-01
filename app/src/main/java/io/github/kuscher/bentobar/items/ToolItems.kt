package io.github.kuscher.bentobar.items

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import android.view.KeyEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.produceState
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.annotation.StringRes
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.kuscher.bentobar.R
import io.github.kuscher.bentobar.data.ItemConfig
import io.github.kuscher.bentobar.ui.ActionTile
import io.github.kuscher.bentobar.ui.ChoiceRow
import io.github.kuscher.bentobar.ui.ChipRow
import io.github.kuscher.bentobar.ui.MenuCard
import io.github.kuscher.bentobar.ui.MenuDivider
import io.github.kuscher.bentobar.ui.MenuEntry
import io.github.kuscher.bentobar.ui.SectionLabel
import io.github.kuscher.bentobar.ui.SliderRow
import io.github.kuscher.bentobar.ui.SwitchRow
import io.github.kuscher.bentobar.ui.TextRow
import io.github.kuscher.bentobar.ui.TileGrid
import io.github.kuscher.bentobar.ui.rememberTick
import io.github.kuscher.bentobar.util.ClockAnchor
import io.github.kuscher.bentobar.util.Fmt
import io.github.kuscher.bentobar.util.Sym
import io.github.kuscher.bentobar.util.SymIcon
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale

/**
 * Keep-awake: while on, BarService keeps a tiny window with FLAG_KEEP_SCREEN_ON on screen. [until]
 * is wall time (it survives a restart); [anchor] keeps setting the clock from changing how long it lasts.
 */
object Caffeine {
    const val FOREVER = Long.MAX_VALUE
    val until = MutableStateFlow(0L)
    private var anchor = ClockAnchor(0, 0, -1)

    fun init(context: Context) {
        val p = context.getSharedPreferences("caffeine", Context.MODE_PRIVATE)
        anchor = ClockAnchor(p.getLong("anchorWall", 0), p.getLong("anchorElapsed", 0), p.getInt("anchorBoot", -1))
        until.value = p.getLong("until", 0)
        check()
    }

    fun on(minutes: Int?) = set(if (minutes == null) FOREVER else System.currentTimeMillis() + minutes * 60_000L)
    fun off() = set(0)
    fun toggle(minutes: Int?) = if (active()) off() else on(minutes)
    fun active(now: Long = System.currentTimeMillis()) = until.value > now

    /** Follows a clock change, and turns itself off when the time is up; called every tick. */
    fun check() {
        val v = until.value
        if (v == 0L || v == FOREVER) return
        val jump = anchor.jump(ClockAnchor.now(Env.app))
        if (jump != 0L) set(v + jump)
        if (!active()) off()
    }

    private fun set(v: Long) {
        until.value = v
        anchor = ClockAnchor.now(Env.app)
        Env.app.getSharedPreferences("caffeine", Context.MODE_PRIVATE).edit().putLong("until", v)
            .putLong("anchorWall", anchor.wall).putLong("anchorElapsed", anchor.elapsed).putInt("anchorBoot", anchor.boot).apply()
    }
}

object CaffeineItem : ItemType("caffeine", R.string.item_caffeine_title, Sym.COFFEE, R.string.item_caffeine_desc) {
    override val canBeActive = true
    override val trigger = Trigger(R.string.trigger_caffeine, R.string.trigger_caffeine_short)
    private val durations = listOf(15, 30, 60, 120, 240)

    override fun state(item: ItemConfig): ItemState {
        val on = Caffeine.active()
        val left = Caffeine.until.value - System.currentTimeMillis()
        val forever = Caffeine.until.value == Caffeine.FOREVER
        val text = when {
            !on -> null
            forever -> Env.str(R.string.common_on)
            else -> Fmt.duration(left)
        }
        return ItemState(icon = Sym.COFFEE, filled = on, text = text, active = on, widthKey = if (on && !forever) "left" else null,
            desc = Env.str(if (on) R.string.caffeine_on_desc else R.string.caffeine_off_desc))
    }

    override fun onClick(item: ItemConfig): Boolean {
        if (Env.service == null) return false // menu explains why it can't work
        Caffeine.toggle(item.opt("minutes", "").toIntOrNull())
        return true
    }

    override val menu: @Composable (ItemConfig, MenuHost) -> Unit = { _, _ ->
        rememberTick()
        val on = Caffeine.active()
        MenuCard(Sym.COFFEE, stringResource(R.string.item_caffeine_title), when {
            Env.service == null -> stringResource(R.string.caffeine_needs_service)
            !on -> stringResource(R.string.caffeine_off_subtitle)
            Caffeine.until.value == Caffeine.FOREVER -> stringResource(R.string.caffeine_until_off_subtitle)
            else -> stringResource(R.string.caffeine_for_subtitle, Fmt.duration(Caffeine.until.value - System.currentTimeMillis()))
        }) {
            SectionLabel(stringResource(R.string.caffeine_keep_on_for))
            ChipRow(durations.map {
                if (it < 60) pluralStringResource(R.plurals.common_minutes_short, it, it)
                else pluralStringResource(R.plurals.common_hours_short, it / 60, it / 60)
            } + stringResource(R.string.caffeine_until_i_turn_off)) { i ->
                Caffeine.on(durations.getOrNull(i))
            }
            if (on) {
                MenuDivider()
                MenuEntry(Sym.CLOSE, stringResource(R.string.common_turn_off)) { Caffeine.off() }
            }
        }
    }

    override val options: @Composable (ItemConfig, (ItemConfig) -> Unit) -> Unit = { item, set ->
        ChoiceRow(stringResource(R.string.caffeine_click_keeps_on), listOf("" to stringResource(R.string.caffeine_until_turned_off),
            "30" to pluralStringResource(R.plurals.common_minutes_short, 30, 30), "60" to pluralStringResource(R.plurals.common_hours, 1, 1),
            "120" to pluralStringResource(R.plurals.common_hours, 2, 2)),
            item.opt("minutes", "")) { set(item.with("minutes", it.ifBlank { null })) }
    }
}

object SoundItem : ItemType("sound", R.string.item_sound_title, Sym.VOLUME_UP, R.string.item_sound_desc) {
    private fun am() = Env.app.getSystemService(AudioManager::class.java)

    override fun state(item: ItemConfig): ItemState {
        val am = am() ?: return ItemState(icon = Sym.VOLUME_UP)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val v = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val muted = am.isStreamMute(AudioManager.STREAM_MUSIC) || v == 0
        val pct = v * 100 / max
        return ItemState(icon = when { muted -> Sym.VOLUME_OFF; pct < 34 -> Sym.VOLUME_MUTE; pct < 67 -> Sym.VOLUME_DOWN; else -> Sym.VOLUME_UP },
            text = if (muted) Env.str(R.string.sound_muted) else "$pct%", widthKey = if (muted) null else "pct",
            desc = if (muted) Env.str(R.string.sound_muted_desc) else Env.plural(R.plurals.sound_volume_desc, pct))
    }

    override val usesWheel = true
    override fun onScroll(item: ItemConfig, steps: Int) {
        am()?.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (steps > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, 0)
    }

    private fun mediaKey(code: Int) {
        val am = am() ?: return
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    override val menu: @Composable (ItemConfig, MenuHost) -> Unit = { _, host ->
        rememberTick()
        val am = am()
        if (am != null) {
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            // Follows the volume keys and the scroll wheel while the menu is open, except mid-drag.
            val actual = am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
            var dragging by remember { mutableStateOf(false) }
            var v by remember { mutableFloatStateOf(actual) }
            LaunchedEffect(actual) { if (!dragging) v = actual }
            val muted = am.isStreamMute(AudioManager.STREAM_MUSIC)
            val volumeLabel = stringResource(R.string.sound_media_volume)
            MenuCard(Sym.VOLUME_UP, stringResource(R.string.item_sound_title),
                if (muted) stringResource(R.string.sound_muted) else stringResource(R.string.sound_media_volume_pct, (v * 100 / max).toInt())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = {
                        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0)
                    }) { SymIcon(if (muted) Sym.VOLUME_OFF else Sym.VOLUME_UP, size = 20.sp, contentDescription = stringResource(if (muted) R.string.sound_unmute else R.string.sound_mute)) }
                    Spacer(Modifier.width(8.dp))
                    Slider(value = v, onValueChange = {
                        dragging = true
                        v = it
                        am.setStreamVolume(AudioManager.STREAM_MUSIC, it.toInt(), 0)
                    }, onValueChangeFinished = { dragging = false }, valueRange = 0f..max.toFloat(), steps = (max - 1).coerceAtLeast(0),
                        modifier = Modifier.weight(1f).semantics { contentDescription = volumeLabel })
                }
                SectionLabel(stringResource(R.string.sound_media))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalIconButton(onClick = { mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }) { SymIcon(Sym.SKIP_PREVIOUS, size = 22.sp, contentDescription = stringResource(R.string.sound_previous)) }
                    FilledTonalIconButton(onClick = { mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }) {
                        SymIcon(if (am.isMusicActive) Sym.PAUSE else Sym.PLAY_ARROW, size = 22.sp, contentDescription = stringResource(if (am.isMusicActive) R.string.common_pause else R.string.sound_play))
                    }
                    FilledTonalIconButton(onClick = { mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT) }) { SymIcon(Sym.SKIP_NEXT, size = 22.sp, contentDescription = stringResource(R.string.sound_next)) }
                }
                MenuDivider()
                MenuEntry(Sym.TUNE, stringResource(R.string.sound_all_volumes)) { host.close(); Env.launch(Intent(Settings.Panel.ACTION_VOLUME)) }
                MenuEntry(Sym.SETTINGS, stringResource(R.string.sound_settings)) { host.close(); Env.launch(Intent(Settings.ACTION_SOUND_SETTINGS)) }
            }
        }
    }
}

object ToolsItem : ItemType("tools", R.string.item_tools_title, Sym.HANDYMAN, R.string.item_tools_desc) {
    override val menuWidthDp = 392
    private data class Tool(val icon: String, @StringRes val label: Int, val run: () -> Unit)

    private fun global(action: Int) = { Env.global(action); Unit }
    private fun settings(action: String) = { Env.launch(Intent(action)); Unit }

    private val system = listOf(
        Tool(Sym.SCREENSHOT_MONITOR, R.string.tools_screenshot, global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)),
        Tool(Sym.LOCK, R.string.tools_lock, global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)),
        Tool(Sym.DESKTOP_WINDOWS, R.string.tools_overview, global(AccessibilityService.GLOBAL_ACTION_RECENTS)),
        Tool(Sym.APPS, R.string.tools_all_apps, global(AccessibilityService.GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS)),
        Tool(Sym.NOTIFICATIONS, R.string.tools_notifications, global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)),
        Tool(Sym.TOGGLE_ON, R.string.tools_quick_settings, global(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)),
        Tool(Sym.POWER_SETTINGS_NEW, R.string.tools_power, global(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)),
    )
    private val shortcuts = listOf(
        Tool(Sym.WIFI, R.string.tools_wifi, settings(Settings.ACTION_WIFI_SETTINGS)),
        Tool(Sym.DESKTOP_WINDOWS, R.string.tools_display, settings(Settings.ACTION_DISPLAY_SETTINGS)),
        Tool(Sym.VOLUME_UP, R.string.tools_sound, settings(Settings.ACTION_SOUND_SETTINGS)),
        Tool(Sym.KEYBOARD, R.string.tools_keyboard, settings(Settings.ACTION_HARD_KEYBOARD_SETTINGS)),
        Tool(Sym.BOLT, R.string.tools_battery, settings(Intent.ACTION_POWER_USAGE_SUMMARY)),
        Tool(Sym.HARD_DRIVE, R.string.tools_storage, settings(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)),
        Tool(Sym.APPS, R.string.tools_apps, settings(Settings.ACTION_APPLICATION_SETTINGS)),
        Tool(Sym.TOUCH_APP, R.string.tools_accessibility, settings(Settings.ACTION_ACCESSIBILITY_SETTINGS)),
        Tool(Sym.BUILD, R.string.tools_developer, settings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)),
        Tool(Sym.SETTINGS, R.string.tools_settings, settings(Settings.ACTION_SETTINGS)),
    )

    override fun state(item: ItemConfig) = ItemState(icon = Sym.HANDYMAN, text = Env.str(R.string.item_tools_title), desc = Env.str(R.string.item_tools_title))

    override val menu: @Composable (ItemConfig, MenuHost) -> Unit = { item, host ->
        MenuCard(Sym.HANDYMAN, stringResource(R.string.item_tools_title)) {
            if (item.optBool("system", true)) {
                SectionLabel(stringResource(R.string.tools_section_system))
                TileGrid { system.forEach { t -> ActionTile(t.icon, stringResource(t.label)) { host.afterClose(t.run) } } }
            }
            if (item.optBool("settings", true)) {
                SectionLabel(stringResource(R.string.tools_settings))
                TileGrid { shortcuts.forEach { t -> ActionTile(t.icon, stringResource(t.label)) { host.close(); t.run() } } }
            }
        }
    }

    override val options: @Composable (ItemConfig, (ItemConfig) -> Unit) -> Unit = { item, set ->
        SwitchRow(stringResource(R.string.tools_system_actions), item.optBool("system", true), help = stringResource(R.string.tools_system_actions_help)) {
            set(item.with("system", it.toString()))
        }
        SwitchRow(stringResource(R.string.tools_settings_shortcuts), item.optBool("settings", true)) { set(item.with("settings", it.toString())) }
    }
}

/** Launchable apps, for the app items. */
object Apps {
    data class App(val component: ComponentName, val label: String)

    private val icons = HashMap<String, Bitmap>()

    fun list(context: Context): List<App> {
        val pm = context.packageManager
        return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { App(ComponentName(it.activityInfo.packageName, it.activityInfo.name), it.loadLabel(pm).toString()) }
            .filter { it.component.packageName != context.packageName }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    fun label(context: Context, cn: ComponentName): String = runCatching {
        context.packageManager.getActivityInfo(cn, 0).loadLabel(context.packageManager).toString()
    }.getOrDefault(cn.packageName)

    /** [icon] if it's been drawn already: cheap enough to call while composing. */
    fun cachedIcon(cn: ComponentName, px: Int, mono: Boolean): Bitmap? = synchronized(icons) { icons["${cn.flattenToShortString()}/$px/$mono"] }

    /** The app's icon; [mono] uses the themed (monochrome) layer when the app has one. */
    fun icon(context: Context, cn: ComponentName, px: Int, mono: Boolean): Bitmap? = synchronized(icons) {
        val key = "${cn.flattenToShortString()}/$px/$mono"
        icons[key] ?: runCatching {
            val d: Drawable = context.packageManager.getActivityIcon(cn)
            val drawable = if (mono && d is AdaptiveIconDrawable && d.monochrome != null) d.monochrome!! else d
            val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            if (mono && d is AdaptiveIconDrawable && d.monochrome != null) {
                // The monochrome layer is drawn on the adaptive canvas (108 dp with a 72 dp safe zone).
                val pad = (px * 0.25f).toInt()
                drawable.setBounds(-pad, -pad, px + pad, px + pad)
            } else drawable.setBounds(0, 0, px, px)
            drawable.draw(Canvas(b))
            b
        }.getOrNull()?.also { icons[key] = it }
    }

    fun hasMono(context: Context, cn: ComponentName) = runCatching {
        (context.packageManager.getActivityIcon(cn) as? AdaptiveIconDrawable)?.monochrome != null
    }.getOrDefault(false)

    fun launch(cn: ComponentName) = Env.launch(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(cn))

    fun parse(list: String): List<ComponentName> = list.split(',').mapNotNull { ComponentName.unflattenFromString(it.trim()) }
}

@Composable
fun AppPicker(selected: List<ComponentName>, multi: Boolean, onChange: (List<ComponentName>) -> Unit) {
    var query by remember { mutableStateOf("") }
    // Every launchable app with its label, and their icons, are read off the main thread: on a
    // Googlebook with many apps, doing it while composing stalled the settings window.
    val all by produceState<List<Apps.App>?>(null) { value = withContext(Dispatchers.IO) { Apps.list(Env.app) } }
    TextRow(stringResource(R.string.apps_find), query, placeholder = stringResource(R.string.apps_search)) { query = it }
    val shown = all.orEmpty().filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }.take(40)
    Column {
        if (all == null) CircularProgressIndicator(Modifier.padding(8.dp).size(20.dp), strokeWidth = 2.dp)
        shown.forEach { app ->
            val on = app.component in selected
            val toggle = { onChange(if (multi) (if (on) selected - app.component else selected + app.component) else listOf(app.component)) }
            // One control per app, named by it: TalkBack reads "Gmail, checkbox, checked", not a bare checkbox.
            Row(Modifier.fillMaxWidth().toggleable(on, role = Role.Checkbox, onValueChange = { toggle() }).padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically) {
                val icon by produceState(Apps.cachedIcon(app.component, 64, false), app.component) {
                    if (value == null) value = withContext(Dispatchers.IO) { Apps.icon(Env.app, app.component, 64, false) }
                }
                icon?.let { Image(it.asImageBitmap(), null, Modifier.size(28.dp)) } ?: Spacer(Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text(app.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Checkbox(checked = on, onCheckedChange = null)
            }
        }
    }
}

object AppItem : ItemType("app", R.string.item_app_title, Sym.ROCKET_LAUNCH, R.string.item_app_desc) {
    override val refreshMs = 60_000L

    private fun cn(item: ItemConfig) = item.options["app"]?.let { ComponentName.unflattenFromString(it) }

    override fun state(item: ItemConfig): ItemState {
        val cn = cn(item) ?: return ItemState(icon = Sym.ROCKET_LAUNCH, text = Env.str(R.string.app_pick), desc = Env.str(R.string.app_no_app_desc))
        val label = Apps.label(Env.app, cn)
        return ItemState(image = Apps.icon(Env.app, cn, 64, item.optBool("mono", true)), text = label, desc = Env.str(R.string.app_open_desc, label))
    }

    override fun onClick(item: ItemConfig): Boolean {
        val cn = cn(item) ?: return false
        Apps.launch(cn)
        return true
    }

    override val options: @Composable (ItemConfig, (ItemConfig) -> Unit) -> Unit = { item, set ->
        SwitchRow(stringResource(R.string.app_match_color), item.optBool("mono", true), help = stringResource(R.string.app_match_color_help)) {
            set(item.with("mono", it.toString()))
        }
        AppPicker(listOfNotNull(cn(item)), multi = false) { set(item.with("app", it.firstOrNull()?.flattenToString())) }
    }
}

object FolderItem : ItemType("folder", R.string.item_folder_title, Sym.GRID_VIEW, R.string.item_folder_desc) {
    override val menuWidthDp = 340
    override fun state(item: ItemConfig) = ItemState(icon = Sym.GRID_VIEW, text = item.opt("label", Env.str(R.string.common_apps)), desc = Env.str(R.string.item_folder_title))

    override val menu: @Composable (ItemConfig, MenuHost) -> Unit = { item, host ->
        val apps = Apps.parse(item.opt("apps", ""))
        MenuCard(Sym.GRID_VIEW, item.opt("label", stringResource(R.string.common_apps))) {
            if (apps.isEmpty()) Text(stringResource(R.string.folder_no_apps), style = MaterialTheme.typography.bodyMedium)
            TileGrid {
                apps.forEach { cn ->
                    Column(Modifier.width(76.dp).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Apps.icon(Env.app, cn, 96, false)?.let {
                            Image(it.asImageBitmap(), Apps.label(Env.app, cn),
                                Modifier.size(44.dp).clickableHand { host.close(); Apps.launch(cn) })
                        }
                        Text(Apps.label(Env.app, cn), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
            MenuDivider()
            MenuEntry(Sym.EDIT, stringResource(R.string.folder_choose_apps)) { host.openItemSettings(item.id) }
        }
    }

    override val options: @Composable (ItemConfig, (ItemConfig) -> Unit) -> Unit = { item, set ->
        TextRow(stringResource(R.string.folder_name), item.opt("label", stringResource(R.string.common_apps))) { set(item.with("label", it.take(16).ifBlank { null })) }
        AppPicker(Apps.parse(item.opt("apps", "")), multi = true) { list ->
            set(item.with("apps", list.joinToString(",") { it.flattenToString() }.ifBlank { null }))
        }
    }
}

object TextItem : ItemType("text", R.string.item_text_title, Sym.TEXT_FIELDS, R.string.item_text_desc) {
    override fun state(item: ItemConfig) = ItemState(
        icon = item.options["icon"]?.let { iconChoices.toMap()[it] },
        text = item.opt("text", Env.str(R.string.text_default)), desc = item.opt("text", Env.str(R.string.text_default)),
    )

    val iconChoices = listOf("none" to "", "star" to Sym.STAR, "heart" to Sym.FAVORITE, "bookmark" to Sym.BOOKMARK,
        "label" to Sym.LABEL, "pin" to Sym.PUSH_PIN, "rocket" to Sym.ROCKET_LAUNCH, "idea" to Sym.EMOJI_OBJECTS, "link" to Sym.LINK)

    /** The icon choices' names (keys above are stored, these are shown). */
    private val iconNames = mapOf("none" to R.string.option_none, "star" to R.string.text_icon_star, "heart" to R.string.text_icon_heart,
        "bookmark" to R.string.text_icon_bookmark, "label" to R.string.text_icon_label, "pin" to R.string.text_icon_pin,
        "rocket" to R.string.text_icon_rocket, "idea" to R.string.text_icon_idea, "link" to R.string.text_icon_link)

    override fun onClick(item: ItemConfig): Boolean {
        val link = item.options["link"]?.takeIf { it.isNotBlank() } ?: return true
        Env.launch(Intent(Intent.ACTION_VIEW, Uri.parse(if (link.contains("://")) link else "https://$link")))
        return true
    }

    override val options: @Composable (ItemConfig, (ItemConfig) -> Unit) -> Unit = { item, set ->
        TextRow(stringResource(R.string.text_text), item.opt("text", stringResource(R.string.text_default)), help = stringResource(R.string.text_text_help)) {
            set(item.with("text", it.take(40)))
        }
        ChoiceRow(stringResource(R.string.text_icon), iconChoices.map { it.first to stringResource(iconNames.getValue(it.first)) },
            item.opt("icon", "none")) { set(item.with("icon", if (it == "none") null else it)) }
        TextRow(stringResource(R.string.text_opens), item.opt("link", ""), placeholder = "https://…", help = stringResource(R.string.text_opens_help)) {
            set(item.with("link", it.trim().ifBlank { null }))
        }
    }

    override fun defaultOptions() = mapOf("text" to Env.str(R.string.text_default))
}

object SpacerItem : ItemType("spacer", R.string.item_spacer_title, Sym.SPACE_BAR, R.string.item_spacer_desc) {
    override val refreshMs = 60_000L

    override fun state(item: ItemConfig) = ItemState(gapDp = item.optInt("width", 12), divider = item.optBool("line", false), desc = Env.str(R.string.spacer_desc))

    override fun onClick(item: ItemConfig) = true

    override val options: @Composable (ItemConfig, (ItemConfig) -> Unit) -> Unit = { item, set ->
        SliderRow(stringResource(R.string.spacer_width), item.optInt("width", 12), 4..64, { "$it dp" }) { set(item.with("width", it.toString())) }
        SwitchRow(stringResource(R.string.spacer_line), item.optBool("line", false)) { set(item.with("line", it.toString())) }
    }
}

/** A clickable with the hand pointer, for small image buttons. */
fun Modifier.clickableHand(onClick: () -> Unit): Modifier =
    clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand)
