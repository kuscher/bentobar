package io.github.kuscher.bentobar.bar

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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import io.github.kuscher.bentobar.data.ColorMode
import io.github.kuscher.bentobar.data.ItemConfig
import io.github.kuscher.bentobar.data.Pill
import io.github.kuscher.bentobar.data.Position
import io.github.kuscher.bentobar.data.Section
import io.github.kuscher.bentobar.data.Store
import io.github.kuscher.bentobar.data.TextSize
import io.github.kuscher.bentobar.items.Caffeine
import io.github.kuscher.bentobar.items.Chips
import io.github.kuscher.bentobar.items.Env
import io.github.kuscher.bentobar.items.Items
import io.github.kuscher.bentobar.items.MenuHost
import io.github.kuscher.bentobar.items.Ticker
import io.github.kuscher.bentobar.ui.MainActivity
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * BentoBar's accessibility service. It reads only SystemUI's status bar window (layout and text
 * colour), draws BentoBar's items in overlay windows on top of it, and performs the global
 * actions of the Tools menu when asked. It doesn't observe keys, touches or other apps' windows.
 */
class BarService : AccessibilityService() {
    private var bar: BarController? = null

    override fun onServiceConnected() {
        Env.init(this)
        Env.service = this
        bar = BarController(this).also { it.start() }
        Chips.update(this)
        io.github.kuscher.bentobar.ui.Setup.refresh(this)
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
        io.github.kuscher.bentobar.ui.Setup.refresh(this)
    }

    fun controller(): BarController? = bar

    companion object { const val TAG = "BentoBar" }
}

/** Item types still shown while presenting (when active): a running timer, a meeting about to start. */
private val PRESENTING_TYPES = setOf("timer", "countdown", "event")

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
    private val expanded = object {
        private val state = mutableStateOf(false)
        var value: Boolean
            get() = state.value
            set(v) { state.value = v; Ticker.revealHidden = v || menuKey == "bentobar"; if (v) Ticker.refresh() }
    }
    private val look = mutableStateOf(StripLook(Color.White, true, TextSize.DEFAULT, 10.dp, Pill.NONE))
    private val maxWidth = mutableIntStateOf(0)
    private val heightDp = mutableStateOf(36.dp)

    private val strip = Overlay(service, "BentoBar").apply { params.width = 1 }
    private var menu: Overlay? = null
    private var menuKey: String? = null
    private var menuClosedKey: String? = null
    private var menuClosedAt = 0L
    private var awake: Overlay? = null
    private var snap: BarSnapshot? = null
    private var sampled: BarColors? = null
    private var sampledAt = 0L
    private var overlayIds = emptySet<Int>()
    private var hovering = false
    private var pinned = false
    private var started = false
    /** Where each item was last drawn (window coordinates), for anchoring menus from tests. */
    val placed = HashMap<String, Rect>()

    private val scanNow = Runnable { scan() }
    private val quickCheck = Runnable { check(full = false) }
    /**
     * Status bar icons come and go without window changes. Every 2 s while shown, refresh just the
     * spacer node (one call) and read the whole bar again only if it moved, or every 30 s.
     */
    private val poll = object : Runnable {
        override fun run() {
            if (!pm.isInteractive) return // screen off: nothing to show; SCREEN_ON restarts this
            if (strip.shown) lightCheck() else check(full = false)
            main.postDelayed(this, if (strip.shown) 2_000 else 5_000)
        }
    }
    private var lastFullScan = 0L

    private fun lightCheck() {
        val s = snap ?: return scan()
        val node = s.spacerNode
        // A bar without the DesktopStatusBarSpacer node has nothing cheap to re-check: a full scan
        // (with clearCache) every 2 s is costly, so between full scans only the window list is read.
        if (node == null) return if (SystemClock.uptimeMillis() - lastFullScan > 10_000) scan() else check(full = false)
        if (SystemClock.uptimeMillis() - lastFullScan > 30_000 || !node.refresh()) return scan()
        val r = Rect().also { node.getBoundsInScreen(it) }
        if (r != s.free) scan()
    }
    private val expandOnHover = Runnable { if (hovering) expanded.value = true }
    private val collapse = Runnable {
        if (!hovering && menuKey == null && expanded.value) {
            Log.i(tag, "collapse (pinned=$pinned autoCollapse=${Store.config.value.autoCollapseSec}s)")
            expanded.value = false; pinned = false
            if (Store.config.value.pinnedOpen) Store.update { it.copy(pinnedOpen = false) }
        }
    }
    private val awakeExpiry = Runnable { Caffeine.check() }
    private val sample = Runnable { sampleColor() }

    private val screen = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action == Intent.ACTION_SCREEN_OFF) closeMenu()
            main.post(scanNow)
            if (i.action == Intent.ACTION_SCREEN_ON) { main.removeCallbacks(poll); main.postDelayed(poll, 2_000) }
        }
    }
    private val wallpaper = WallpaperManager.OnColorsChangedListener { _, _ -> main.postDelayed(sample, 300) }

    fun start() {
        started = true
        // Pinned open with ‹ before a restart: open again.
        Store.config.value.let { if (it.pinnedOpen && it.chevron) { expanded.value = true; pinned = true } }
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
        main.postDelayed(poll, 2_000)
    }

    fun stop() {
        started = false
        BarLook.current.value = null
        BarStatus.current.value = BarStatus.STOPPED
        BarOverflow.ids.value = emptySet()
        main.removeCallbacksAndMessages(null)
        runCatching { service.unregisterReceiver(screen) }
        runCatching { WallpaperManager.getInstance(service).removeOnColorsChangedListener(wallpaper) }
        closeMenu()
        hideTip()
        strip.destroy()
        awake?.destroy(); awake = null
        Ticker.stop("bar")
        scope.cancel()
        io.shutdown()
    }

    fun onEvent(e: AccessibilityEvent) {
        if (tracing) trace("event windows id=${e.windowId} changes=0x${Integer.toHexString(e.windowChanges)} ours=${e.windowId in overlayIds}")
        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                if (Log.isLoggable("BentoBarEvents", Log.DEBUG)) Log.d(tag, "windows changed id=${e.windowId} changes=0x${Integer.toHexString(e.windowChanges)} ours=${e.windowId in overlayIds}")
                if (e.windowId in overlayIds) return // our own windows resizing
                // Only windows appearing, going or moving can hide or cover the bar; titles, focus
                // and stacking changes (every page load, every terminal command) can't.
                if (e.windowChanges and RELEVANT == 0) return
                // The status bar animates in and out: look again as the animation settles.
                main.removeCallbacks(quickCheck)
                for (delay in longArrayOf(100, 450, 900)) main.postDelayed(quickCheck, delay)
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

    private fun scan() = check(full = true)

    /**
     * Where the status bar is and whether to show the strip. A cheap pass reads just the window list;
     * the status bar's node tree is read only when [full] or the bar window itself changed.
     */
    private fun check(full: Boolean) {
        if (!started) return
        val metrics = wm.currentWindowMetrics
        val screenW = metrics.bounds.width()
        val inset = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top
        val maxH = (if (inset > 0) inset else (48 * density).toInt()) + 4
        val windows = runCatching { service.windows }.getOrDefault(emptyList())
        overlayIds = windows.filter { it.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY }.map { it.id }.toSet()
        val barWindow = StatusBarScan.findWindow(windows, screenW, maxH)
        val barBounds = barWindow?.let { Rect().also { r -> it.getBoundsInScreen(r) } }
        val prev = snap
        val s = when {
            barWindow == null -> null
            !full && prev != null && prev.windowId == barWindow.id && prev.bar == barBounds -> prev
            else -> {
                lastFullScan = SystemClock.uptimeMillis()
                // Without content-change events the node cache can go stale: read the status bar fresh.
                service.clearCache()
                runCatching { StatusBarScan.scan(barWindow) }.onFailure { Log.w(tag, "scan failed", it) }.getOrNull()
            }
        }
        val newWindow = s?.windowId != snap?.windowId
        snap = s
        val cfg = Store.config.value
        // On tablets the shade and Quick Settings slide over the status bar; our overlay would sit
        // on top of them. Only a big system window counts: on the desktop bar the panels open as
        // popups below the bar, and small system UI near the top shouldn't blink BentoBar away.
        val cover = if (s == null) null else windows.firstOrNull { w ->
            val r = Rect().also { w.getBoundsInScreen(it) }
            w.type == AccessibilityWindowInfo.TYPE_SYSTEM && w.id != s.windowId && Rect.intersects(r, s.free) &&
                r.height() >= s.bar.height() * 4
        }
        val covered = cover != null
        val show = s != null && !covered && cfg.enabled && pm.isInteractive && !km.isKeyguardLocked
        if (!show) BarStatus.current.value = when {
            !pm.isInteractive || km.isKeyguardLocked -> BarStatus.ASLEEP
            !cfg.enabled -> BarStatus.HIDDEN_BY_USER
            s == null -> BarStatus.NO_BAR
            else -> BarStatus.COVERED
        }
        if (show) {
            place(s!!, screenW)
            val now = SystemClock.uptimeMillis()
            // Re-sample every 30 s too: a bar can turn opaque or change colour without a new window.
            if (newWindow || now - sampledAt > 30_000) {
                sampledAt = now; main.removeCallbacks(sample); main.postDelayed(sample, 250)
            }
        } else {
            if (strip.shown) Log.i(tag, "bar hidden (statusBar=${s != null} covered=${cover?.let { "${it.title} " + Rect().also { r -> it.getBoundsInScreen(r) }.toShortString() }} " +
                "enabled=${cfg.enabled} interactive=${pm.isInteractive} locked=${km.isKeyguardLocked})")
            closeMenu()
            hideTip()
            strip.hide()
            Ticker.stop("bar")
        }
    }

    /** adb `debug trace on|off`: logs every move and resize with its cause, to chase jitter. */
    private var tracing = false
    private var lastPlace = ""
    private fun trace(what: String) { if (tracing) Log.i(tag, "trace ${SystemClock.uptimeMillis() % 100_000} $what") }

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
        // Keep room for the chevron (~26 dp) inside the free area, when it shows.
        val chevron = cfg.presenting || (cfg.chevron && (hiddenItems().isNotEmpty() || BarOverflow.ids.value.isNotEmpty()))
        maxWidth.intValue = (s.free.width() - 2 * gap - if (chevron) (30 * density).toInt() else 0).coerceAtLeast(0)
        heightDp.value = (s.bar.height() / density).dp
        val where = "free=${s.free.toShortString()} x=${p.x} maxW=${maxWidth.intValue} [${s.summary}]"
        if (where != lastPlace) { trace("place $where"); lastPlace = where }
        BarStatus.current.value = if (maxWidth.intValue == 0) BarStatus.NO_ROOM else BarStatus.SHOWN
        if (!strip.shown) {
            strip.show { StripHost() }
            Ticker.start("bar")
            Log.i(tag, "bar shown: bar=${s.bar.toShortString()} free=${s.free.toShortString()} [${s.summary}]")
        } else strip.relayout()
    }

    // ---- colour ------------------------------------------------------------------------------

    /**
     * Copies the status bar's text colour: a screenshot of SystemUI's status bar WINDOW only (its
     * own surface, no app content), read at the clock. On some devices that surface is glyphs on
     * transparent (the HP); on others it's opaque, e.g. white glyphs on a black bar (the Acer).
     */
    private fun sampleColor() {
        val s = snap ?: return applyLook()
        if (Store.config.value.color != ColorMode.AUTO) return applyLook()
        service.takeScreenshotOfWindow(s.windowId, io, object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(r: AccessibilityService.ScreenshotResult) {
                val c = runCatching { barColors(r, s) }.getOrNull()
                main.post { sampled = c; applyLook() }
            }
            override fun onFailure(code: Int) {
                Log.i(tag, "status bar colour sample failed ($code), using wallpaper hints")
                main.post { applyLook() }
            }
        })
    }

    /**
     * The clock's text colour and, when the bar is opaque there, the bar's own colour. Averaging
     * every opaque pixel (as before) only works on a transparent bar: on an opaque one it blends
     * glyphs and background into a grey (#474747 on the Acer's black bar, 2.3:1).
     */
    private fun barColors(r: AccessibilityService.ScreenshotResult, s: BarSnapshot): BarColors? {
        val hb = r.hardwareBuffer
        val bmp = Bitmap.wrapHardwareBuffer(hb, r.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
        hb.close()
        bmp ?: return null
        val area = Rect(s.clock ?: s.bar).apply { offset(-s.bar.left, -s.bar.top) }
        area.intersect(0, 0, bmp.width, bmp.height)
        val opaque = ArrayList<Int>()
        var total = 0
        for (y in area.top until area.bottom) for (x in area.left until area.right) {
            val px = bmp.getPixel(x, y)
            total++
            if ((px ushr 24) >= 230) opaque.add(px)
        }
        bmp.recycle()
        if (opaque.size < 12) return null
        // Transparent bar: only the glyphs are opaque, so they are the text colour.
        if (opaque.size < total * 0.8) return BarColors(average(opaque), null)
        // Opaque bar: the commonest colour is the background; the text is what stands out most from it.
        val bg = opaque.groupingBy { it and 0xFFFFFF }.eachCount().maxBy { it.value }.key
        val dist = opaque.map { distance(it, bg) }
        val far = dist.max()
        val background = Color(0xFF000000.toInt() or bg)
        if (far < 60) return BarColors(null, background) // nothing readable in the box
        return BarColors(average(opaque.filterIndexed { i, _ -> dist[i] >= far * 0.6 }), background)
    }

    private fun average(px: List<Int>): Color {
        var rr = 0L; var gg = 0L; var bb = 0L
        for (p in px) { rr += (p shr 16) and 0xFF; gg += (p shr 8) and 0xFF; bb += p and 0xFF }
        return Color((rr / px.size).toInt(), (gg / px.size).toInt(), (bb / px.size).toInt())
    }

    private fun distance(a: Int, b: Int): Int =
        kotlin.math.abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) +
            kotlin.math.abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) + kotlin.math.abs((a and 0xFF) - (b and 0xFF))

    /** No sample: a dark system theme means a dark bar on Googlebooks; otherwise the wallpaper decides. */
    private fun fallbackText(): Color {
        val night = (service.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        if (night) return Color.White
        val hints = runCatching { WallpaperManager.getInstance(service).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)?.colorHints }.getOrNull() ?: 0
        return if (hints and android.app.WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0) Contrast.DARK_TEXT else Color.White
    }

    private fun applyLook() {
        val cfg = Store.config.value
        val bg = if (cfg.color == ColorMode.AUTO) sampled?.background else null
        val text = when (cfg.color) {
            ColorMode.LIGHT -> Color.White
            ColorMode.DARK -> Contrast.DARK_TEXT
            ColorMode.AUTO -> sampled?.text ?: bg?.let { Contrast.readableOn(it) } ?: fallbackText()
        }
        val live = Contrast.resolve(text, bg)
        BarLook.current.value = live
        look.value = StripLook(live.fg, live.barDark, cfg.textSize, cfg.spacing.dp, cfg.pill, live.background)
        Log.i(tag, "look fg=${hex(live.fg)} bg=${bg?.let { hex(it) } ?: "transparent"} dark=${live.barDark} " +
            "contrast=${"%.1f".format(java.util.Locale.ROOT, Contrast.ratio(live.fg, live.background))}")
    }

    private fun hex(c: Color) = "#%06X".format(java.util.Locale.ROOT, c.toArgb() and 0xFFFFFF)

    // ---- the strip ---------------------------------------------------------------------------

    private fun isActive(item: ItemConfig, states: Map<String, io.github.kuscher.bentobar.items.ItemState>) =
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
        // Presenting (screen sharing): only what matters on stage, whatever its section.
        val visible = if (cfg.presenting) cfg.items.filter { it.section != Section.OFF && it.type in PRESENTING_TYPES && states[it.id]?.active == true }
        else cfg.items.filter { it.section == Section.SHOWN || (it.section == Section.HIDDEN && isActive(it, states)) }
        val hidden = if (cfg.presenting) emptyList() else cfg.items.filter { it.section == Section.HIDDEN && !isActive(it, states) }
        val overflow by BarOverflow.ids.collectAsState()
        // Without the ‹ button there's no way to fold hidden items back, so they stay folded.
        val open = expanded.value && cfg.chevron && !cfg.presenting
        MeasuredStrip(bias = when (cfg.position) { Position.RIGHT -> 1f; Position.CENTER -> 0f; Position.LEFT -> -1f },
            onWidth = { w -> if (w != strip.params.width) main.post { applyWidth(w) } }) {
            Strip(
                visible = entries(visible),
                revealed = if (open) entries(hidden) else emptyList(),
                // Items that don't fit are offered in the ‹ menu, so ‹ shows for them too.
                showChevron = cfg.presenting || (cfg.chevron && (hidden.isNotEmpty() || overflow.isNotEmpty())),
                expanded = open,
                chevronOnLeft = cfg.position != Position.LEFT,
                look = look.value,
                maxWidthPx = maxWidth.intValue,
                heightDp = heightDp.value,
                events = events,
                onOverflow = { ids -> main.post { if (BarOverflow.ids.value != ids) { BarOverflow.ids.value = ids; main.post(scanNow) } } },
            )
        }
    }

    /** Sizes the strip window to its content (an exact width, never WRAP_CONTENT). */
    private fun applyWidth(w: Int) {
        val width = w.coerceAtLeast(1)
        if (strip.params.width == width) return
        trace("width ${strip.params.width} -> $width")
        strip.params.width = width
        strip.relayout()
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
            toggleMenu("ctx:${item.id}", at, 280) { host ->
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
            // While presenting, ‹ is only the way back to the menu (and out of presenting).
            if (Store.config.value.presenting) return chevronContext(at)
            val next = !expanded.value
            expanded.value = next
            pinned = next
            if (Store.config.value.pinnedOpen != next) Store.update { it.copy(pinnedOpen = next) }
            main.removeCallbacks(collapse)
            val secs = Store.config.value.autoCollapseSec
            if (next && secs > 0) main.postDelayed(collapse, secs * 1000L)
        }

        override fun chevronContext(at: Rect) = toggleMenu("bentobar", at, 290) { host ->
            BentoBarMenu(host, openItem = { item ->
                val type = Items.of(item.type)
                val menuUi = type?.menu
                closeMenu(); menuClosedKey = null
                if (type != null && type.onClick(item)) Ticker.refresh()
                else if (menuUi != null) toggleMenu("item:${item.id}", at, type.menuWidthDp) { h -> menuUi(item, h) }
            }, hideBar = { Store.update { it.copy(enabled = false) } })
        }

        override fun itemHover(item: ItemConfig, at: Rect, inside: Boolean) {
            main.removeCallbacks(showTip)
            if (inside && menu == null) { tipFor = item to Rect(at); main.postDelayed(showTip, 600) } else hideTip()
        }

        override fun hover(inside: Boolean) {
            trace("hover $inside")
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
        hideTip()
        if (menuKey == key) { closeMenu(); return }
        Ticker.focusItem = key.removePrefix("item:").takeIf { key.startsWith("item:") }
        Ticker.revealHidden = expanded.value || key == "bentobar"
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
        val o = Overlay(service, "BentoBar menu", focusable = true, onOutside = { closeMenu() },
            onKey = { e ->
                if (e.keyCode == KeyEvent.KEYCODE_ESCAPE && e.action == KeyEvent.ACTION_UP) { closeMenu(); true } else false
            })
        o.params.gravity = Gravity.TOP or Gravity.LEFT
        o.params.width = w // exact: a WRAP_CONTENT window would be capped at the dialog width
        o.params.x = x.coerceIn(0, (bounds.width() - w).coerceAtLeast(0))
        o.params.y = s.bar.bottom + (4 * density).toInt() - margin
        menu = o
        menuKey = key
        Ticker.start("menu")
        o.show { MenuSurface(widthDp.dp, maxH.dp) { content(host) } }
    }

    // ---- tooltips ----------------------------------------------------------------------------

    private var tip: Overlay? = null
    private var tipFor: Pair<ItemConfig, Rect>? = null
    private val showTip = Runnable { tipFor?.let { (item, at) -> showTooltip(item, at) } }

    /**
     * The item's name below it, after a short hover: icon-only items otherwise give a mouse user
     * nothing to go on. A no-touch window, so it never takes a click.
     */
    private fun showTooltip(item: ItemConfig, anchor: Rect) {
        hideTip()
        val s = snap ?: return
        if (menu != null || !strip.shown) return
        val label = Items.of(item.type)?.title ?: return
        val loc = strip.locationOnScreen()
        val a = Rect(anchor).apply { offset(loc[0], loc[1]) }
        val paint = android.text.TextPaint().apply { textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 13f, service.resources.displayMetrics) }
        val w = (paint.measureText(label) + 2 * 10 * density).toInt() + 2
        val bounds = wm.currentWindowMetrics.bounds
        val o = Overlay(service, "BentoBar tooltip", touchable = false)
        o.params.gravity = Gravity.TOP or Gravity.LEFT
        o.params.width = w // exact, as for menus
        o.params.height = (28 * density).toInt()
        o.params.x = (a.centerX() - w / 2).coerceIn(0, (bounds.width() - w).coerceAtLeast(0))
        o.params.y = s.bar.bottom + (4 * density).toInt()
        tip = o
        o.show { Tooltip(label) }
    }

    private fun hideTip() {
        main.removeCallbacks(showTip)
        tip?.destroy(); tip = null
    }

    fun closeMenu() {
        val o = menu ?: return
        menu = null
        menuClosedKey = menuKey
        menuClosedAt = SystemClock.uptimeMillis()
        menuKey = null
        o.destroy()
        Ticker.focusItem = null
        Ticker.revealHidden = expanded.value
        Ticker.stop("menu")
        if (!hovering && !pinned) { main.removeCallbacks(collapse); main.postDelayed(collapse, 600) }
    }

    // ---- keep awake --------------------------------------------------------------------------

    private fun updateAwake() {
        val on = Caffeine.active()
        if (on && awake == null) {
            awake = Overlay(service, "BentoBar keep awake", touchable = false).apply {
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
            "scroll" -> {
                val item = first(cmd.getOrElse(1) { "" }) ?: return "no such item"
                events.scroll(item, cmd.getOrNull(2)?.toIntOrNull() ?: 1); "ok"
            }
            "barmenu" -> { events.chevronContext(placed["chevron"] ?: Rect(0, 0, 40, 40)); "ok" }
            "hover" -> { events.hover(cmd.getOrNull(1) == "on"); "ok" }
            "close" -> { closeMenu(); "ok" }
            "scan" -> { scan(); "snap=$snap" }
            "windows" -> service.windows.joinToString(" | ") { w ->
                val r = Rect().also { w.getBoundsInScreen(it) }
                "${w.id}:t${w.type}:${w.title}:${r.toShortString()}"
            }
            "sample" -> { sampleColor(); "sampling" }
            "trace" -> { tracing = cmd.getOrNull(1) != "off"; "trace=$tracing" }
            else -> "unknown"
        }
    }

    companion object {
        const val SYSTEMUI = "com.android.systemui"
        private const val RELEVANT = AccessibilityEvent.WINDOWS_CHANGE_ADDED or AccessibilityEvent.WINDOWS_CHANGE_REMOVED or
            AccessibilityEvent.WINDOWS_CHANGE_BOUNDS
    }
}
