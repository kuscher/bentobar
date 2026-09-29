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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import io.github.kuscher.bentobar.util.Fmt
import io.github.kuscher.bentobar.util.Sym
import io.github.kuscher.bentobar.util.SymIcon
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale

/** Keep-awake: while on, BarService keeps a tiny window with FLAG_KEEP_SCREEN_ON on screen. */
object Caffeine {
    const val FOREVER = Long.MAX_VALUE
    val until = MutableStateFlow(0L)

    fun init(context: Context) {
        val p = context.getSharedPreferences("caffeine", Context.MODE_PRIVATE)
        until.value = p.getLong("until", 0).takeIf { it > System.currentTimeMillis() } ?: 0L
    }

    fun on(minutes: Int?) = set(if (minutes == null) FOREVER else System.currentTimeMillis() + minutes * 60_000L)
    fun off() = set(0)
    fun toggle(minutes: Int?) = if (active()) off() else on(minutes)
    fun active(now: Long = System.currentTimeMillis()) = until.value > now

    /** Turns itself off when the time is up; called every tick. */
    fun check() { if (until.value != 0L && !active()) off() }

    private fun set(v: Long) {
        until.value = v
        Env.app.getSharedPreferences("caffeine", Context.MODE_PRIVATE).edit().putLong("until", v).apply()
    }
}

object CaffeineItem : ItemType("caffeine", "Keep awake", Sym.COFFEE, "Stops the screen from turning off; click to switch") {
    override val canBeActive = true
    private val durations = listOf(15, 30, 60, 120, 240)

    override fun state(item: ItemConfig): ItemState {
        val on = Caffeine.active()
        val left = Caffeine.until.value - System.currentTimeMillis()
        val text = when {
            !on -> null
            Caffeine.until.value == Caffeine.FOREVER -> "On"
            else -> Fmt.duration(left)
        }
        return ItemState(icon = Sym.COFFEE, filled = on, text = text, active = on, widthKey = if (text != null && text != "On") "left" else null,
            desc = if (on) "Keeping the screen on" else "Keep awake is off")
    }

    override fun onClick(item: ItemConfig): Boolean {
        if (Env.service == null) return false // menu explains why it can't work
        Caffeine.toggle(item.opt("minutes", "").toIntOrNull())
        return true
    }

    override val menu: @Composable (ItemConfig, MenuHost) -> Unit = { _, _ ->
        rememberTick()
        val on = Caffeine.active()
        MenuCard(Sym.COFFEE, "Keep awake", when {
            Env.service == null -> "Turn on BentoBar (accessibility) to use this"
            !on -> "The screen turns off as usual"
            Caffeine.until.value == Caffeine.FOREVER -> "Screen stays on until you turn this off"
            else -> "Screen stays on for ${Fmt.duration(Caffeine.until.value - System.currentTimeMillis())}"
        }) {
            SectionLabel("Keep the screen on for")
            ChipRow(durations.map { if (it < 60) "$it min" else "${it / 60} h" } + "Until I turn it off") { i ->
                Caffeine.on(durations.getOrNull(i))
            }
            if (on) {
                MenuDivider()
                MenuEntry(Sym.CLOSE, "Turn off") { Caffeine.off() }
            }
        }
    }

    override val options: @Composable (ItemConfig, (ItemConfig) -> Unit) -> Unit = { item, set ->
        ChoiceRow("A click keeps the screen on", listOf("" to "Until turned off", "30" to "30 min", "60" to "1 hour", "120" to "2 hours"),
            item.opt("minutes", "")) { set(item.with("minutes", it.ifBlank { null })) }
    }
}

object SoundItem : ItemType("sound", "Sound", Sym.VOLUME_UP, "Volume and media controls; scroll to change the volume") {
    private fun am() = Env.app.getSystemService(AudioManager::class.java)

    override fun state(item: ItemConfig): ItemState {
        val am = am() ?: return ItemState(icon = Sym.VOLUME_UP)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val v = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val muted = am.isStreamMute(AudioManager.STREAM_MUSIC) || v == 0
        val pct = v * 100 / max
        return ItemState(icon = when { muted -> Sym.VOLUME_OFF; pct < 34 -> Sym.VOLUME_MUTE; pct < 67 -> Sym.VOLUME_DOWN; else -> Sym.VOLUME_UP },
            text = if (muted) "Muted" else "$pct%", widthKey = if (muted) null else "pct",
            desc = if (muted) "Sound muted" else "Volume $pct percent")
    }

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
            var v by remember { mutableFloatStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
            val muted = am.isStreamMute(AudioManager.STREAM_MUSIC)
            MenuCard(Sym.VOLUME_UP, "Sound", if (muted) "Muted" else "Media volume ${(v * 100 / max).toInt()}%") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = {
                        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0)
                    }) { SymIcon(if (muted) Sym.VOLUME_OFF else Sym.VOLUME_UP, size = 20.sp) }
                    Spacer(Modifier.width(8.dp))
                    Slider(value = v, onValueChange = {
                        v = it
                        am.setStreamVolume(AudioManager.STREAM_MUSIC, it.toInt(), 0)
                    }, valueRange = 0f..max.toFloat(), steps = (max - 1).coerceAtLeast(0), modifier = Modifier.weight(1f))
                }
                SectionLabel("Media")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalIconButton(onClick = { mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }) { SymIcon(Sym.SKIP_PREVIOUS, size = 22.sp) }
                    FilledTonalIconButton(onClick = { mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }) {
                        SymIcon(if (am.isMusicActive) Sym.PAUSE else Sym.PLAY_ARROW, size = 22.sp)
                    }
                    FilledTonalIconButton(onClick = { mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT) }) { SymIcon(Sym.SKIP_NEXT, size = 22.sp) }
                }
                MenuDivider()
                MenuEntry(Sym.TUNE, "All volumes") { host.close(); Env.launch(Intent(Settings.Panel.ACTION_VOLUME)) }
                MenuEntry(Sym.SETTINGS, "Sound settings") { host.close(); Env.launch(Intent(Settings.ACTION_SOUND_SETTINGS)) }
            }
        }
    }
}

object ToolsItem : ItemType("tools", "Tools", Sym.HANDYMAN, "Screenshot, lock, overview, all apps and settings shortcuts") {
    override val menuWidthDp = 392
    private data class Tool(val icon: String, val label: String, val run: () -> Unit)

    private fun global(action: Int) = { Env.global(action); Unit }
    private fun settings(action: String) = { Env.launch(Intent(action)); Unit }

    private val system = listOf(
        Tool(Sym.SCREENSHOT_MONITOR, "Screenshot", global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)),
        Tool(Sym.LOCK, "Lock", global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)),
        Tool(Sym.DESKTOP_WINDOWS, "Overview", global(AccessibilityService.GLOBAL_ACTION_RECENTS)),
        Tool(Sym.APPS, "All apps", global(AccessibilityService.GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS)),
        Tool(Sym.NOTIFICATIONS, "Notifications", global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)),
        Tool(Sym.TOGGLE_ON, "Quick settings", global(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)),
        Tool(Sym.POWER_SETTINGS_NEW, "Power", global(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)),
    )
    private val shortcuts = listOf(
        Tool(Sym.WIFI, "Wi-Fi", settings(Settings.ACTION_WIFI_SETTINGS)),
        Tool(Sym.DESKTOP_WINDOWS, "Display", settings(Settings.ACTION_DISPLAY_SETTINGS)),
        Tool(Sym.VOLUME_UP, "Sound", settings(Settings.ACTION_SOUND_SETTINGS)),
        Tool(Sym.KEYBOARD, "Keyboard", settings(Settings.ACTION_HARD_KEYBOARD_SETTINGS)),
        Tool(Sym.BOLT, "Battery", settings(Intent.ACTION_POWER_USAGE_SUMMARY)),
        Tool(Sym.HARD_DRIVE, "Storage", settings(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)),
        Tool(Sym.APPS, "Apps", settings(Settings.ACTION_APPLICATION_SETTINGS)),
        Tool(Sym.TOUCH_APP, "Accessibility", settings(Settings.ACTION_ACCESSIBILITY_SETTINGS)),
        Tool(Sym.BUILD, "Developer", settings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)),
        Tool(Sym.SETTINGS, "Settings", settings(Settings.ACTION_SETTINGS)),
    )

    override fun state(item: ItemConfig) = ItemState(icon = Sym.HANDYMAN, text = "Tools", desc = "Tools")

    override val menu: @Composable (ItemConfig, MenuHost) -> Unit = { item, host ->
        MenuCard(Sym.HANDYMAN, "Tools") {
            if (item.optBool("system", true)) {
                SectionLabel("System")
                TileGrid { system.forEach { t -> ActionTile(t.icon, t.label) { host.afterClose(t.run) } } }
            }
            if (item.optBool("settings", true)) {
                SectionLabel("Settings")
                TileGrid { shortcuts.forEach { t -> ActionTile(t.icon, t.label) { host.close(); t.run() } } }
            }
        }
    }

    override val options: @Composable (ItemConfig, (ItemConfig) -> Unit) -> Unit = { item, set ->
        SwitchRow("System actions", item.optBool("system", true), help = "Screenshot, lock, overview, all apps, power") {
            set(item.with("system", it.toString()))
        }
        SwitchRow("Settings shortcuts", item.optBool("settings", true)) { set(item.with("settings", it.toString())) }
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
    val all = remember { Apps.list(Env.app) }
    TextRow("Find an app", query, placeholder = "Search") { query = it }
    val shown = all.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }.take(40)
    Column {
        shown.forEach { app ->
            val on = app.component in selected
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Apps.icon(Env.app, app.component, 64, false)?.let {
                    Image(it.asImageBitmap(), null, Modifier.size(28.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(app.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Checkbox(checked = on, onCheckedChange = {
                    onChange(if (multi) (if (on) selected - app.component else selected + app.component) else listOf(app.component))
                })
            }
        }
    }
}

object AppItem : ItemType("app", "App shortcut", Sym.ROCKET_LAUNCH, "One app, one click away") {
    override val refreshMs = 60_000L

    private fun cn(item: ItemConfig) = item.options["app"]?.let { ComponentName.unflattenFromString(it) }

    override fun state(item: ItemConfig): ItemState {
        val cn = cn(item) ?: return ItemState(icon = Sym.ROCKET_LAUNCH, text = "Pick an app", desc = "App shortcut without an app")
        val label = Apps.label(Env.app, cn)
        return ItemState(image = Apps.icon(Env.app, cn, 64, item.optBool("mono", true)), text = label, desc = "Open $label")
    }

    override fun onClick(item: ItemConfig): Boolean {
        val cn = cn(item) ?: return false
        Apps.launch(cn)
        return true
    }

    override val options: @Composable (ItemConfig, (ItemConfig) -> Unit) -> Unit = { item, set ->
        SwitchRow("Match the bar's colour", item.optBool("mono", true), help = "Uses the app's themed icon when it has one") {
            set(item.with("mono", it.toString()))
        }
        AppPicker(listOfNotNull(cn(item)), multi = false) { set(item.with("app", it.firstOrNull()?.flattenToString())) }
    }
}

object FolderItem : ItemType("folder", "App folder", Sym.GRID_VIEW, "A drop-down of your favourite apps") {
    override val menuWidthDp = 340
    override fun state(item: ItemConfig) = ItemState(icon = Sym.GRID_VIEW, text = item.opt("label", "Apps"), desc = "App folder")

    override val menu: @Composable (ItemConfig, MenuHost) -> Unit = { item, host ->
        val apps = Apps.parse(item.opt("apps", ""))
        MenuCard(Sym.GRID_VIEW, item.opt("label", "Apps")) {
            if (apps.isEmpty()) Text("No apps yet", style = MaterialTheme.typography.bodyMedium)
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
            MenuEntry(Sym.EDIT, "Choose apps") { host.openItemSettings(item.id) }
        }
    }

    override val options: @Composable (ItemConfig, (ItemConfig) -> Unit) -> Unit = { item, set ->
        TextRow("Name", item.opt("label", "Apps")) { set(item.with("label", it.take(16).ifBlank { null })) }
        AppPicker(Apps.parse(item.opt("apps", "")), multi = true) { list ->
            set(item.with("apps", list.joinToString(",") { it.flattenToString() }.ifBlank { null }))
        }
    }
}

object TextItem : ItemType("text", "Text or emoji", Sym.TEXT_FIELDS, "Your own label, optionally opening a link") {
    override fun state(item: ItemConfig) = ItemState(
        icon = item.options["icon"]?.let { iconChoices.toMap()[it] },
        text = item.opt("text", "Hello"), desc = item.opt("text", "Hello"),
    )

    val iconChoices = listOf("none" to "", "star" to Sym.STAR, "heart" to Sym.FAVORITE, "bookmark" to Sym.BOOKMARK,
        "label" to Sym.LABEL, "pin" to Sym.PUSH_PIN, "rocket" to Sym.ROCKET_LAUNCH, "idea" to Sym.EMOJI_OBJECTS, "link" to Sym.LINK)

    override fun onClick(item: ItemConfig): Boolean {
        val link = item.options["link"]?.takeIf { it.isNotBlank() } ?: return true
        Env.launch(Intent(Intent.ACTION_VIEW, Uri.parse(if (link.contains("://")) link else "https://$link")))
        return true
    }

    override val options: @Composable (ItemConfig, (ItemConfig) -> Unit) -> Unit = { item, set ->
        TextRow("Text", item.opt("text", "Hello"), help = "Emoji work too") { set(item.with("text", it.take(40))) }
        ChoiceRow("Icon", iconChoices.map { it.first to (if (it.first == "none") "None" else it.first.replaceFirstChar(Char::titlecase)) },
            item.opt("icon", "none")) { set(item.with("icon", if (it == "none") null else it)) }
        TextRow("Opens (optional)", item.opt("link", ""), placeholder = "https://…", help = "A web address to open on click") {
            set(item.with("link", it.trim().ifBlank { null }))
        }
    }

    override fun defaultOptions() = mapOf("text" to "Hello")
}

object SpacerItem : ItemType("spacer", "Spacer or divider", Sym.SPACE_BAR, "Room between items, or a thin line to group them") {
    override val refreshMs = 60_000L

    override fun state(item: ItemConfig) = ItemState(gapDp = item.optInt("width", 12), divider = item.optBool("line", false), desc = "Spacer")

    override fun onClick(item: ItemConfig) = true

    override val options: @Composable (ItemConfig, (ItemConfig) -> Unit) -> Unit = { item, set ->
        SliderRow("Width", item.optInt("width", 12), 4..64, { "$it dp" }) { set(item.with("width", it.toString())) }
        SwitchRow("Draw a divider line", item.optBool("line", false)) { set(item.with("line", it.toString())) }
    }
}

/** A clickable with the hand pointer, for small image buttons. */
fun Modifier.clickableHand(onClick: () -> Unit): Modifier =
    clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand)
