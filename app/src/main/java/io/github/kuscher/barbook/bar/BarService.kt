package io.github.kuscher.barbook.bar

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import io.github.kuscher.barbook.data.ColorMode
import io.github.kuscher.barbook.data.ItemConfig
import io.github.kuscher.barbook.data.Pill
import io.github.kuscher.barbook.data.Position
import io.github.kuscher.barbook.data.Section
import io.github.kuscher.barbook.data.Store
import io.github.kuscher.barbook.data.TextSize
import io.github.kuscher.barbook.items.Caffeine
import io.github.kuscher.barbook.items.Chips
import io.github.kuscher.barbook.items.Env
import io.github.kuscher.barbook.items.Items
import io.github.kuscher.barbook.items.MenuHost
import io.github.kuscher.barbook.items.Ticker
import io.github.kuscher.barbook.ui.MainActivity
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * BarBook's accessibility service. It reads only SystemUI's status bar window (layout and text
 * colour), draws BarBook's items in overlay windows on top of it, and performs the global
 * actions of the Tools menu when asked. It doesn't observe keys, touches or other apps' windows.
 */
class BarService : AccessibilityService() {
    private var bar: BarController? = null

    override fun onServiceConnected() {
        Env.init(this)
        Env.service = this
        bar = BarController(this).also { it.start() }
        Chips.update(this)
        Log.i(TAG, "service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) { bar?.onEvent(event) }
    override fun onInterrupt() {}

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        bar?.onConfigChanged()
    }

    override fun onUnbind(intent: Intent?): Boolean { shutdown(); return super.onUnbind(intent) }
    override fun onDestroy() { shutdown(); super.onDestroy() }

    private fun shutdown() {
        bar?.stop()
        bar = null
        if (Env.service === this) Env.service = null
        Chips.update(this)
    }

    fun controller(): BarController? = bar

    companion object { const val TAG = "BarBook" }
}

class BarController(private val service: AccessibilityService) {
    private val tag = BarService.TAG
    private val main = Handler(Looper.getMainLooper())
    private val scope = MainScope()
    private val wm = service.getSystemService(WindowManager::class.java)
    private val pm = service.getSystemService(PowerManager::class.java)
    private val km = service.getSystemService(KeyguardManager::class.java)
    private val io = Executors.newSingleThreadExecutor()
    private val density get() = service.resources.displayMetrics.density

    // Read by the strip's composition.
    private val expanded = mutableStateOf(false)
    private val look = mutableStateOf(StripLook(Color.White, true, TextSize.DEFAULT, 10.dp, Pill.NONE))
    private val maxWidth = mutableIntStateOf(0)
    private val heightDp = mutableStateOf(36.dp)

    private val strip = Overlay(service, "BarBook")
    private var menu: Overlay? = null
    private var menuKey: String? = null
    private var menuClosedKey: String? = null
    private var menuClosedAt = 0L
    private var awake: Overlay? = null
    private var snap: BarSnapshot? = null
    private var sampled: Color? = null
    private var overlayIds = emptySet<Int>()
    private var hovering = false
    private var pinned = false
    private var started = false
    /** Where each item was last drawn (window coordinates), for anchoring menus from tests. */
    val placed = HashMap<String, Rect>()

    private val scanNow = Runnable { scan() }
    private val poll = object : Runnable {
        override fun run() { scan(); main.postDelayed(this, 5_000) }
    }
    private val expandOnHover = Runnable { if (hovering) expanded.value = true }
    private val collapse = Runnable { if (!hovering && menuKey == null) { expanded.value = false; pinned = false } }
    private val awakeExpiry = Runnable { Caffeine.check() }
    private val sample = Runnable { sampleColor() }

    private val screen = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action == Intent.ACTION_SCREEN_OFF) closeMenu()
            main.post(scanNow)
        }
    }
    private val wallpaper = WallpaperManager.OnColorsChangedListener { _, _ -> main.postDelayed(sample, 300) }

    fun start() {
        started = true
        service.registerReceiver(screen, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT)
        })
        runCatching { WallpaperManager.getInstance(service).addOnColorsChangedListener(wallpaper, main) }
        scope.launch { Store.config.collect { onConfig() } }
        scope.launch {
            Caffeine.until.collect { until ->
                updateAwake()
                main.removeCallbacks(awakeExpiry)
                if (until != 0L && until != Caffeine.FOREVER) main.postDelayed(awakeExpiry, (until - System.currentTimeMillis()).coerceAtLeast(0) + 200)
            }
        }
        scan()
        main.postDelayed(poll, 5_000)
    }

    fun stop() {
        started = false
        main.removeCallbacksAndMessages(null)
        runCatching { service.unregisterReceiver(screen) }
        runCatching { WallpaperManager.getInstance(service).removeOnColorsChangedListener(wallpaper) }
        closeMenu()
        strip.destroy()
        awake?.destroy(); awake = null
        Ticker.stop("bar")
        scope.cancel()
        io.shutdown()
    }

    fun onEvent(e: AccessibilityEvent) {
        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                if (e.windowId in overlayIds) return // our own windows resizing
                main.removeCallbacks(scanNow); main.postDelayed(scanNow, 120)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val s = snap
                if (e.packageName == SYSTEMUI && (s == null || e.windowId == s.windowId)) {
                    main.removeCallbacks(scanNow); main.postDelayed(scanNow, 300)
                }
            }
        }
    }

    fun onConfigChanged() { main.postDelayed(scanNow, 200); main.postDelayed(sample, 500) }

    private fun onConfig() {
        if (!started) return
        applyLook()
        scan()
        Chips.update(service)
    }

    // ---- where the status bar is -------------------------------------------------------------

    private fun scan() {
        if (!started) return
        val metrics = wm.currentWindowMetrics
        val screenW = metrics.bounds.width()
        val inset = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top
        val maxH = (if (inset > 0) inset else (48 * density).toInt()) + 4
        val windows = runCatching { service.windows }.getOrDefault(emptyList())
        overlayIds = windows.filter { it.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY }.map { it.id }.toSet()
        val s = runCatching { StatusBarScan.scan(service, screenW, maxH) }.onFailure { Log.w(tag, "scan failed", it) }.getOrNull()
        val newWindow = s?.windowId != snap?.windowId
        snap = s
        val cfg = Store.config.value
        val show = s != null && cfg.enabled && pm.isInteractive && !km.isKeyguardLocked
        if (show) {
            place(s!!, screenW)
            if (newWindow || sampled == null) { main.removeCallbacks(sample); main.postDelayed(sample, 250) }
        } else {
            if (strip.shown) Log.i(tag, "bar hidden (statusBar=${s != null} enabled=${cfg.enabled} locked=${km.isKeyguardLocked})")
            closeMenu()
            strip.hide()
            Ticker.stop("bar")
        }
    }

    private fun place(s: BarSnapshot, screenW: Int) {
        val cfg = Store.config.value
        val gap = (6 * density).toInt()
        val p = strip.params
        p.height = s.bar.height()
        p.y = s.bar.top
        when (cfg.position) {
            Position.RIGHT -> { p.gravity = Gravity.TOP or Gravity.RIGHT; p.x = screenW - s.free.right + gap }
            Position.LEFT -> { p.gravity = Gravity.TOP or Gravity.LEFT; p.x = s.free.left + gap }
            Position.CENTER -> { p.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; p.x = s.free.centerX() - screenW / 2 }
        }
        // Keep room for the chevron (~26 dp) inside the free area.
        maxWidth.intValue = (s.free.width() - 2 * gap - (30 * density).toInt()).coerceAtLeast(0)
        heightDp.value = (s.bar.height() / density).dp
        if (!strip.shown) {
            strip.show { StripHost() }
            Ticker.start("bar")
            Log.i(tag, "bar shown: bar=${s.bar.toShortString()} free=${s.free.toShortString()} [${s.summary}]")
        } else strip.relayout()
    }

    // ---- colour ------------------------------------------------------------------------------

    /**
     * Copies the status bar's text colour: a screenshot of SystemUI's status bar WINDOW only
     * (its own surface: glyphs on transparent, no wallpaper or app content), read at the clock.
     */
    private fun sampleColor() {
        val s = snap ?: return applyLook()
        if (Store.config.value.color != ColorMode.AUTO) return applyLook()
        service.takeScreenshotOfWindow(s.windowId, io, object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(r: AccessibilityService.ScreenshotResult) {
                val c = runCatching { textColor(r, s) }.getOrNull()
                main.post { sampled = c; applyLook() }
            }
            override fun onFailure(code: Int) {
                Log.i(tag, "status bar colour sample failed ($code), using wallpaper hints")
                main.post { applyLook() }
            }
        })
    }

    private fun textColor(r: AccessibilityService.ScreenshotResult, s: BarSnapshot): Color? {
        val hb = r.hardwareBuffer
        val bmp = Bitmap.wrapHardwareBuffer(hb, r.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
        hb.close()
        bmp ?: return null
        val area = Rect(s.clock ?: s.bar).apply { offset(-s.bar.left, -s.bar.top) }
        area.intersect(0, 0, bmp.width, bmp.height)
        var rr = 0L; var gg = 0L; var bb = 0L; var n = 0
        for (y in area.top until area.bottom) for (x in area.left until area.right) {
            val px = bmp.getPixel(x, y)
            if ((px ushr 24) >= 230) { rr += (px shr 16) and 0xFF; gg += (px shr 8) and 0xFF; bb += px and 0xFF; n++ }
        }
        bmp.recycle()
        if (n < 12) return null
        return Color((rr / n).toInt(), (gg / n).toInt(), (bb / n).toInt())
    }

    private fun wallpaperGuess(): Color {
        val hints = runCatching { WallpaperManager.getInstance(service).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)?.colorHints }.getOrNull() ?: 0
        return if (hints and android.app.WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0) Color(0xFF1F1F1F) else Color.White
    }

    private fun applyLook() {
        val cfg = Store.config.value
        val fg = when (cfg.color) {
            ColorMode.LIGHT -> Color.White
            ColorMode.DARK -> Color(0xFF1F1F1F)
            ColorMode.AUTO -> sampled ?: wallpaperGuess()
        }
        look.value = StripLook(fg, fg.luminance() > 0.5f, cfg.textSize, cfg.spacing.dp, cfg.pill)
    }

    // ---- the strip ---------------------------------------------------------------------------

    private fun isActive(item: ItemConfig, states: Map<String, io.github.kuscher.barbook.items.ItemState>) =
        item.whenActive && states[item.id]?.active == true

    fun hiddenItems(): List<ItemConfig> {
        val states = Ticker.states.value
        return Store.config.value.items.filter { it.section == Section.HIDDEN && !isActive(it, states) }
    }

    @Composable
    private fun StripHost() {
        val cfg by Store.config.collectAsState()
        val states by Ticker.states.collectAsState()
        val entries = { list: List<ItemConfig> -> list.map { StripEntry(it, states[it.id] ?: Ticker.stateOf(it)) } }
        val visible = cfg.items.filter { it.section == Section.SHOWN || (it.section == Section.HIDDEN && isActive(it, states)) }
        val hidden = cfg.items.filter { it.section == Section.HIDDEN && !isActive(it, states) }
        Strip(
            visible = entries(visible),
            revealed = if (expanded.value) entries(hidden) else emptyList(),
            showChevron = cfg.chevron && hidden.isNotEmpty(),
            expanded = expanded.value,
            chevronOnLeft = cfg.position != Position.LEFT,
            look = look.value,
            maxWidthPx = maxWidth.intValue,
            heightDp = heightDp.value,
            events = events,
        )
    }

    private val events = object : StripEvents {
        override fun placed(id: String, at: Rect) { placed[id] = at }

        override fun click(item: ItemConfig, at: Rect) {
            placed[item.id] = at
            val type = Items.of(item.type) ?: return
            if (type.onClick(item)) { Ticker.refresh(); return }
            val menuUi = type.menu ?: return
            toggleMenu("item:${item.id}", at, type.menuWidthDp) { host -> menuUi(item, host) }
        }

        override fun context(item: ItemConfig, at: Rect) {
            placed[item.id] = at
            toggleMenu("ctx:${item.id}", at, 320) { host ->
                ItemContextMenu(item.id, host) {
                    val type = Items.of(item.type)
                    val menuUi = type?.menu
                    if (menuUi != null) { closeMenu(); menuClosedKey = null; toggleMenu("item:${item.id}", at, type.menuWidthDp) { h -> menuUi(item, h) } }
                }
            }
        }

        override fun scroll(item: ItemConfig, steps: Int) {
            Items.of(item.type)?.onScroll(item, steps)
            Ticker.refresh()
        }

        override fun chevron(at: Rect) {
            val next = !expanded.value
            expanded.value = next
            pinned = next
            main.removeCallbacks(collapse)
            val secs = Store.config.value.autoCollapseSec
            if (next && secs > 0) main.postDelayed(collapse, secs * 1000L)
        }

        override fun chevronContext(at: Rect) = toggleMenu("barbook", at, 320) { host ->
            BarBookMenu(host, hiddenItems(), openItem = { item ->
                val type = Items.of(item.type)
                val menuUi = type?.menu
                closeMenu(); menuClosedKey = null
                if (type != null && type.onClick(item)) Ticker.refresh()
                else if (menuUi != null) toggleMenu("item:${item.id}", at, type.menuWidthDp) { h -> menuUi(item, h) }
            }, hideBar = { Store.update { it.copy(enabled = false) } })
        }

        override fun hover(inside: Boolean) {
            hovering = inside
            main.removeCallbacks(expandOnHover); main.removeCallbacks(collapse)
            if (inside) {
                if (Store.config.value.revealOnHover && !expanded.value) main.postDelayed(expandOnHover, 350)
            } else if (!pinned) main.postDelayed(collapse, 900)
            else {
                val secs = Store.config.value.autoCollapseSec
                if (secs > 0) main.postDelayed(collapse, secs * 1000L)
            }
        }
    }

    // ---- menus -------------------------------------------------------------------------------

    private val host = object : MenuHost {
        override fun close() = closeMenu()
        override fun openItemSettings(id: String) { closeMenu(); MainActivity.open(service, id.ifEmpty { null }) }
        override fun afterClose(action: () -> Unit) { closeMenu(); main.postDelayed(action, 250) }
    }

    /** Opens the menu [key] below [anchor] (strip window coordinates), or closes it if it's open. */
    fun toggleMenu(key: String, anchor: Rect, widthDp: Int, content: @Composable (MenuHost) -> Unit) {
        val now = SystemClock.uptimeMillis()
        if (menuKey == key) { closeMenu(); return }
        // The press that closed this very menu (outside touch) shouldn't reopen it.
        if (menuClosedKey == key && now - menuClosedAt < 350) return
        closeMenu()
        val s = snap ?: return
        val loc = strip.locationOnScreen()
        val a = Rect(anchor).apply { offset(loc[0], loc[1]) }
        val bounds = wm.currentWindowMetrics.bounds
        val margin = (MENU_MARGIN.value * density).toInt()
        val w = (widthDp * density).toInt() + 2 * margin
        val x = if (a.centerX() > bounds.width() / 2) a.right + margin - w else a.left - margin
        val maxH = ((bounds.height() - s.bar.bottom) / density - 170).toInt().coerceAtLeast(200)
        val o = Overlay(service, "BarBook menu", focusable = true, onOutside = { closeMenu() },
            onKey = { e ->
                if (e.keyCode == KeyEvent.KEYCODE_ESCAPE && e.action == KeyEvent.ACTION_UP) { closeMenu(); true } else false
            })
        o.params.gravity = Gravity.TOP or Gravity.LEFT
        o.params.x = x.coerceIn(0, (bounds.width() - w).coerceAtLeast(0))
        o.params.y = s.bar.bottom + (4 * density).toInt() - margin
        menu = o
        menuKey = key
        Ticker.start("menu")
        o.show { MenuSurface(widthDp.dp, maxH.dp) { content(host) } }
    }

    fun closeMenu() {
        val o = menu ?: return
        menu = null
        menuClosedKey = menuKey
        menuClosedAt = SystemClock.uptimeMillis()
        menuKey = null
        o.destroy()
        Ticker.stop("menu")
        if (!hovering && !pinned) { main.removeCallbacks(collapse); main.postDelayed(collapse, 600) }
    }

    // ---- keep awake --------------------------------------------------------------------------

    private fun updateAwake() {
        val on = Caffeine.active()
        if (on && awake == null) {
            awake = Overlay(service, "BarBook keep awake", touchable = false).apply {
                params.width = 1; params.height = 1
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                show { }
            }
            Log.i(tag, "keep awake on")
        } else if (!on && awake != null) {
            awake?.destroy(); awake = null
            Log.i(tag, "keep awake off")
        }
    }

    // ---- tests (adb only) --------------------------------------------------------------------

    fun debug(cmd: List<String>): String {
        fun first(type: String) = Store.config.value.items.firstOrNull { it.type == type || it.id == type }
        return when (cmd.firstOrNull()) {
            "dump" -> "snap=$snap look=${look.value} expanded=${expanded.value} menu=$menuKey shown=${strip.shown} placed=$placed"
            "open", "ctx" -> {
                val item = first(cmd.getOrElse(1) { "" }) ?: return "no such item"
                val at = placed[item.id] ?: return "item not drawn yet"
                if (cmd[0] == "open") events.click(item, at) else events.context(item, at); "ok"
            }
            "chevron" -> { events.chevron(Rect()); "expanded=${expanded.value}" }
            "barmenu" -> { events.chevronContext(placed["chevron"] ?: Rect(0, 0, 40, 40)); "ok" }
            "hover" -> { events.hover(cmd.getOrNull(1) == "on"); "ok" }
            "close" -> { closeMenu(); "ok" }
            "scan" -> { scan(); "snap=$snap" }
            "sample" -> { sampleColor(); "sampling" }
            else -> "unknown"
        }
    }

    companion object { const val SYSTEMUI = "com.android.systemui" }
}
