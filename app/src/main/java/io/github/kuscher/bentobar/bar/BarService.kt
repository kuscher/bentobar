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
import io.github.kuscher.bentobar.data.HiddenMode
import io.github.kuscher.bentobar.data.shows
import io.github.kuscher.bentobar.data.behindChevron
import io.github.kuscher.bentobar.data.moved
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
        bar?.stop() // connected again without an unbind: the old strip's windows mustn't stay up
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

    /**
     * Runs on unbind and again on destroy; a rebind makes a new controller in [onServiceConnected].
     * (UiAutomation, e.g. `uiautomator dump`, unbinds every accessibility service while it runs.)
     */
    private fun shutdown() {
        val stopping = bar
        bar = null // first: whatever stop() does, it runs once per controller
        stopping?.stop()
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
    /** [io] for screenshot results; one arriving after stop() shut it down is dropped, not thrown. */
    private val callbacks = java.util.concurrent.Executor { r -> runCatching { io.execute(r) } }
    private val density get() = service.resources.displayMetrics.density

    // Read by the strip's composition.
    private val expanded = object {
        private val state = mutableStateOf(false)
        var value: Boolean
            get() = state.value
            set(v) {
                state.value = v; Ticker.revealHidden = v || menuKey == "bentobar"; if (v) Ticker.refresh()
                // Out: a click anywhere else folds them away again (see [outsideClick]).
                strip.watchOutside(v)
            }
    }
    private val look = mutableStateOf(StripLook(Color.White, true, TextSize.DEFAULT, 10.dp, Pill.NONE))
    private val maxWidth = mutableIntStateOf(0)
    private val heightDp = mutableStateOf(36.dp)

    private val strip = Overlay(service, "BentoBar", onOutside = { outsideClick() }).apply { params.width = 1; watchOutside(false) }

    /** A click outside the strip while hidden items are out folds them away, unless a menu is open (it closes itself). */
    private fun outsideClick() {
        if (menuKey != null || !expanded.value) return
        expanded.value = false; pinned = false
        if (Store.config.value.pinnedOpen) Store.update { it.copy(pinnedOpen = false) }
    }
    private var menu: Overlay? = null
    private var menuKey: String? = null
    private var menuClosedKey: String? = null
    private var menuClosedAt = 0L
    private var snap: BarSnapshot? = null
    private var sampled: BarColors? = null
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
    /** The app windows sitting against the bar's lower edge, as their left and right edges ([check]). */
    private var againstBar = ""

    private fun lightCheck() {
        val s = snap ?: return scan()
        val node = s.spacerNode
        // A bar without the DesktopStatusBarSpacer node has nothing cheap to re-check: a full scan
        // (with clearCache) every 2 s is costly, so between full scans only the window list is read.
        if (node == null) return if (SystemClock.uptimeMillis() - lastFullScan > 10_000) scan() else check(full = false)
        if (SystemClock.uptimeMillis() - lastFullScan > 30_000 || !node.refresh()) return scan()
        // Against the spacer's own last bounds: with the spacer squeezed to nothing, the free area is
        // the widest gap instead, and comparing with that would run a full scan every time.
        val r = Rect().also { node.getBoundsInScreen(it) }
        if (r != s.spacer) scan()
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
            updateAwake()
            main.post(scanNow)
            if (i.action == Intent.ACTION_SCREEN_ON) {
                main.removeCallbacks(poll); main.postDelayed(poll, 2_000)
                // The chip's next change is timed on a clock that stops while the device sleeps: catch up.
                Chips.update(service)
            }
        }
    }
    private val wallpaper = WallpaperManager.OnColorsChangedListener { _, _ -> requestSample(300) }

    fun start() {
        started = true
        // Pinned open with ‹ before a restart: open again.
        Store.config.value.let { if (it.pinnedOpen && it.hiddenMode != HiddenMode.SHOW_ALL) { expanded.value = true; pinned = true } }
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
        if (!started) return // stopped already: the status and the ticker may be a newer controller's by now
        started = false
        BarLook.current.value = null
        BarStatus.current.value = BarStatus.STOPPED
        BarOverflow.ids.value = emptySet()
        runCatching { service.unregisterReceiver(screen) }
        runCatching { WallpaperManager.getInstance(service).removeOnColorsChangedListener(wallpaper) }
        closeMenu()
        hideTip()
        main.removeCallbacksAndMessages(null) // after closeMenu, which posts a collapse
        strip.destroy()
        if (screenLock.isHeld) screenLock.release()
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

    /** Rotation, density or resolution: a menu or tooltip placed for the old display would be off, so close them. */
    fun onConfigChanged() {
        closeMenu(); hideTip()
        main.postDelayed(scanNow, 200); requestSample(500)
    }

    private var lastColor: ColorMode? = null

    private fun onConfig() {
        if (!started) return
        // Colour back to "Match the status bar": read the bar again (nothing else would until it changes).
        Store.config.value.color.let { if (it != lastColor) { if (it == ColorMode.AUTO && lastColor != null) requestSample(100); lastColor = it } }
        // Switched to Show everything (or presenting) with hidden items out: nothing can fold them back.
        Store.config.value.let { if ((it.hiddenMode == HiddenMode.SHOW_ALL || it.presenting) && expanded.value) { expanded.value = false; pinned = false } }
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
                    // No node tree (SystemUI mid-change): the whole bar would cover its icons. Keep the last
                    // reading of this same bar; with none, stay hidden until a scan can read it.
                    ?.let { if (it.summary != StatusBarScan.NO_TREE) it else prev?.takeIf { p -> p.windowId == it.windowId && p.bar == it.bar } }
            }
        }
        val newWindow = s?.windowId != snap?.windowId
        snap = s
        val cfg = Store.config.value
        // On tablets the shade and Quick Settings slide over the status bar; our overlay would sit
        // on top of them. Only a big system window counts: on the desktop bar the panels open as
        // popups below the bar, and small system UI near the top shouldn't blink BentoBar away.
        // The screenshot UI is a big, mostly see-through system window too ("Screenshot preview",
        // full screen while it animates), but it doesn't cover the bar: hiding for it made the strip
        // vanish for the seconds the preview shows.
        val cover = if (s == null) null else windows.firstOrNull { w ->
            val r = Rect().also { w.getBoundsInScreen(it) }
            w.type == AccessibilityWindowInfo.TYPE_SYSTEM && w.id != s.windowId && Rect.intersects(r, s.free) &&
                r.height() >= s.bar.height() * 4 && w.title?.contains("Screenshot", ignoreCase = true) != true
        }
        val covered = cover != null
        val show = s != null && !covered && cfg.enabled && pm.isInteractive && !km.isKeyguardLocked
        if (!show) BarStatus.current.value = when {
            !pm.isInteractive || km.isKeyguardLocked -> BarStatus.ASLEEP
            !cfg.enabled -> BarStatus.HIDDEN_BY_USER
            s == null -> BarStatus.NO_BAR
            else -> BarStatus.COVERED
        }
        // An app window settled against the bar's lower edge (maximized, or snapped to a side): SystemUI
        // then gives the bar a background of its own, and takes it away when the window leaves, without
        // an event of the bar's own. Only the windows' bounds are looked at, from the list at hand.
        val against = if (s == null) "" else windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .map { w -> Rect().also { w.getBoundsInScreen(it) } }
            .filter { kotlin.math.abs(it.top - s.bar.bottom) <= 2 && it.width() >= s.bar.width() / 4 }
            .sortedBy { it.left }.joinToString(" ") { "${it.left}-${it.right}" }
        val moved = against != againstBar
        againstBar = against
        if (show) {
            place(s!!, screenW)
            // A new status bar window can look different (see requestSample for the other triggers).
            if (newWindow) requestSample(250) else if (moved) requestSample(300)
        } else {
            if (strip.shown) Log.i(tag, "bar hidden (statusBar=${s != null} covered=${cover?.let { "${it.title} " + Rect().also { r -> it.getBoundsInScreen(r) }.toShortString() }} " +
                "enabled=${cfg.enabled} interactive=${pm.isInteractive} locked=${km.isKeyguardLocked})")
            closeMenu()
            hideTip()
            cancelDrag() // the strip's window goes, and with it the release that would end a drag
            strip.hide()
            updateAwake()
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
        // The whole strip fits the free area: items, the pill's own padding and (decided by the strip,
        // from what fits) room for ‹.
        val pillPad = if (cfg.pill == Pill.NONE) 0 else (2 * STRIP_PILL_PADDING.value * density).toInt()
        maxWidth.intValue = (s.free.width() - 2 * gap - pillPad).coerceAtLeast(0)
        heightDp.value = (s.bar.height() / density).dp
        val where = "free=${s.free.toShortString()} x=${p.x} maxW=${maxWidth.intValue} [${s.summary}]"
        if (where != lastPlace) { trace("place $where"); lastPlace = where }
        BarStatus.current.value = if (maxWidth.intValue == 0) BarStatus.NO_ROOM else BarStatus.SHOWN
        if (!strip.shown) {
            stripEmpty = false // a new window starts visible; its first measure says whether it has content
            strip.show { StripHost() }
            updateAwake()
            // Back on screen (after a full-screen app, the lock screen, a covering panel): the bar may
            // look different now, and a reading owed while hidden is taken here.
            requestSample(250)
            Ticker.start("bar")
            Log.i(tag, "bar shown: bar=${s.bar.toShortString()} free=${s.free.toShortString()} [${s.summary}]")
        } else strip.relayout()
    }

    // ---- colour ------------------------------------------------------------------------------

    /**
     * Asks for one colour reading: a screenshot of SystemUI's status bar WINDOW only (its own surface,
     * no app content), read at the clock ([BarPixels]). On some devices that surface is glyphs on
     * transparent; on others it's opaque while an app is maximized (white glyphs on black) and
     * see-through over the wallpaper otherwise.
     *
     * A reading is taken only while the strip is on screen; asked for while it's hidden, it waits
     * until the strip shows ([place] asks again then). The triggers are the moments the bar can
     * change: a new status bar window, the strip coming back on screen, a window settling against the
     * bar's lower edge or leaving it ([check]), a theme, display or wallpaper change, and switching
     * back to "Match the status bar". Never on a timer: an accessibility service taking screenshots
     * every 30 s looks like screen capture to Android's threat detection.
     */
    private fun requestSample(delayMs: Long) {
        if (!started || !strip.shown) return
        confirmOwed = true
        main.removeCallbacks(sample)
        main.postDelayed(sample, delayMs)
    }

    private var sampleFailures = 0
    /** A reading that differs from the last one is taken once more ([onSample]). */
    private var confirmOwed = false

    private fun sampleColor() {
        if (!started || !strip.shown || !pm.isInteractive) return
        val s = snap ?: return applyLook()
        if (Store.config.value.color != ColorMode.AUTO) return applyLook()
        service.takeScreenshotOfWindow(s.windowId, callbacks, object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(r: AccessibilityService.ScreenshotResult) {
                val c = runCatching { barColors(r, s) }.getOrNull()
                // A result can arrive after stop(): it mustn't paint a strip that's gone.
                main.post { if (started) onSample(c) }
            }
            override fun onFailure(code: Int) {
                main.post { if (started) { Log.i(tag, "status bar colour sample failed ($code)"); onSample(null) } }
            }
        })
    }

    /**
     * A reading, or null for none (the screenshot failed, or the bar was caught fading and had nothing
     * opaque to read). No reading keeps the colours the strip has and tries twice more, then uses the
     * theme and wallpaper hints rather than a reading of how the bar looked before.
     */
    private fun onSample(c: BarColors?) {
        if (c == null) {
            main.removeCallbacks(sample)
            if (++sampleFailures <= 2) main.postDelayed(sample, 1_500)
            else { Log.i(tag, "no status bar colour reading, using theme and wallpaper hints"); sampleFailures = 0; sampled = null; applyLook() }
            return
        }
        sampleFailures = 0
        if (c == sampled) return
        sampled = c
        applyLook()
        // The bar fades from one look to the other: a reading that differs from the last may have
        // caught it halfway, so it's read once more when it has settled.
        if (confirmOwed) { confirmOwed = false; main.removeCallbacks(sample); main.postDelayed(sample, 800) }
    }

    /** The pixels around the clock (the whole bar when no clock was found), as [BarPixels] reads them. */
    private fun barColors(r: AccessibilityService.ScreenshotResult, s: BarSnapshot): BarColors? {
        val hb = r.hardwareBuffer
        val bmp = try {
            Bitmap.wrapHardwareBuffer(hb, r.colorSpace)?.let { wrapped -> wrapped.copy(Bitmap.Config.ARGB_8888, false).also { wrapped.recycle() } }
        } finally { hb.close() }
        bmp ?: return null
        try {
            val area = Rect(s.clock ?: s.bar).apply { offset(-s.bar.left, -s.bar.top) }
            // A clock box outside the picture (the bar changed size since the scan): read all of it.
            if (!area.intersect(0, 0, bmp.width, bmp.height)) area.set(0, 0, bmp.width, bmp.height)
            val px = IntArray(area.width() * area.height())
            bmp.getPixels(px, 0, area.width(), area.left, area.top, area.width(), area.height())
            return BarPixels.colors(px, area.width())
        } finally { bmp.recycle() }
    }

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

    fun hiddenItems(): List<ItemConfig> {
        val states = Ticker.states.value
        return Store.config.value.behindChevron { states[it.id]?.active == true }
    }

    @Composable
    private fun StripHost() {
        val cfg by Store.config.collectAsState()
        val states by Ticker.states.collectAsState()
        val entries = { list: List<ItemConfig> -> list.map { StripEntry(it, states[it.id] ?: Ticker.stateOf(it)) } }
        // Presenting (screen sharing): only what matters on stage, whatever its section.
        // While dragging, the bar shows the order the item would land in.
        val items = dragPreview.value?.let { (id, at) -> cfg.items.moved(id, Section.SHOWN, at) } ?: cfg.items
        val visible = (if (cfg.presenting) items.filter { it.section != Section.OFF && it.type in PRESENTING_TYPES && states[it.id]?.active == true }
        else items.filter { cfg.shows(it, states[it.id]?.active == true) })
            // Next meeting stays at the far left: its text changes width most, and there it moves nothing else.
            .sortedBy { if (it.type == Store.PINNED_LEFT) 0 else 1 }
        // Behind ‹ (click or hover modes): hidden items not out on their own. Show everything: none.
        val hidden = if (cfg.presenting) emptyList() else cfg.behindChevron { states[it.id]?.active == true }
        val overflow by BarOverflow.ids.collectAsState()
        // Without the ‹ button there's no way to fold hidden items back, so they stay folded.
        val open = expanded.value && cfg.hiddenMode != HiddenMode.SHOW_ALL && !cfg.presenting
        MeasuredStrip(bias = when (cfg.position) { Position.RIGHT -> 1f; Position.CENTER -> 0f; Position.LEFT -> -1f },
            onWidth = { w -> if (w != strip.params.width || (w <= 0) != stripEmpty) main.post { applyWidth(w) } }) {
            Strip(
                visible = entries(visible),
                revealed = if (open) entries(hidden) else emptyList(),
                // Items that don't fit are offered in the ‹ menu, so ‹ shows for them too.
                showChevron = cfg.presenting || overflow.isNotEmpty() || hidden.isNotEmpty(),
                chevronAlways = cfg.presenting || hidden.isNotEmpty(),
                chevronReservePx = (30 * density).toInt(),
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
    /**
     * Nothing to draw (every item off, or only rule items that don't apply now): the strip's window
     * is made invisible rather than left up empty and a pixel wide, which is what Android's threat
     * detection looks for ("imperceptible content" from an accessibility service). Its content keeps
     * being measured, so it shows again as soon as there's something to draw.
     */
    private var stripEmpty = false

    private fun applyWidth(w: Int) {
        val empty = w <= 0
        if (empty != stripEmpty) { stripEmpty = empty; strip.setContentVisible(!empty); trace("strip ${if (empty) "empty" else "has content"}"); updateAwake() }
        val width = w.coerceAtLeast(1)
        if (strip.params.width == width) return
        trace("width ${strip.params.width} -> $width")
        strip.params.width = width
        strip.relayout()
    }

    private var dragFrom: Pair<String, Rect>? = null
    private var dragCenters: Map<String, Float> = emptyMap()
    /** While an item is dragged: its id and the index it would land at, for the strip's live order. */
    private val dragPreview = mutableStateOf<Pair<String, Int>?>(null)

    /** Ends a drag without moving anything: the order drawn is the saved one again. */
    private fun cancelDrag() { dragPreview.value = null; dragFrom = null; dragCenters = emptyMap() }

    private val events = object : StripEvents {
        override fun placed(id: String, at: Rect) { placed[id] = at }

        override fun click(item: ItemConfig, at: Rect) {
            placed[item.id] = at
            val type = Items.of(item.type) ?: return
            if (type.onClick(item)) { Ticker.refresh(); return }
            if (type.menu == null) return
            toggleMenu("item:${item.id}", at, type.menuWidthDp) { host -> ItemMenu(item.id, host) }
        }

        override fun context(item: ItemConfig, at: Rect) {
            placed[item.id] = at
            toggleMenu("ctx:${item.id}", at, 280) { host ->
                ItemContextMenu(item.id, host) {
                    val type = Items.of(item.type)
                    if (type?.menu != null) { closeMenu(); menuClosedKey = null; toggleMenu("item:${item.id}", at, type.menuWidthDp) { h -> ItemMenu(item.id, h) } }
                }
            }
        }

        override fun scroll(item: ItemConfig, steps: Int) {
            Items.of(item.type)?.onScroll(item, steps)
            Ticker.refresh()
        }

        /** The slider in the bar: the item sets what it stands for, and only that item is drawn again (no sampler runs for a drag). */
        override fun slide(item: ItemConfig, level: Float, done: Boolean) {
            hideTip()
            Items.of(item.type)?.onSlide(item, level, done)
            Ticker.refresh(item)
        }

        override fun chevron(at: Rect) {
            // While presenting, or with nothing behind it (show everything, or ‹ only for items that
            // don't fit), ‹ opens the menu.
            Store.config.value.let { if (it.presenting || it.hiddenMode == HiddenMode.SHOW_ALL || (!expanded.value && hiddenItems().isEmpty())) return chevronContext(at) }
            val next = !expanded.value
            expanded.value = next
            pinned = next
            if (Store.config.value.pinnedOpen != next) Store.update { it.copy(pinnedOpen = next) }
            main.removeCallbacks(collapse)
            val secs = Store.config.value.autoCollapseSec
            if (next && secs > 0) main.postDelayed(collapse, secs * 1000L)
        }

        override fun chevronContext(at: Rect) = barMenu(at, everything = false)

        /**
         * A drag in the bar. While it moves, the strip previews the new order ([dragPreview]), so
         * the other items slide aside; on release that order is saved. The landing place is judged
         * against where the items were when the drag began (a snapshot), not their moving slots,
         * so it can't flip back and forth at a boundary. Only items in the bar move this way;
         * ones out on their own or revealed from ‹ keep their section.
         */
        override fun drag(item: ItemConfig, dx: Float, done: Boolean) {
            hideTip()
            if (dragFrom?.first != item.id) {
                val r = placed[item.id]
                dragFrom = r?.let { item.id to Rect(it) }
                dragCenters = placed.filterKeys { it != item.id }.mapValues { it.value.exactCenterX() }
            }
            val from = dragFrom?.second
            val cfg = Store.config.value
            val index = if (item.section != Section.SHOWN || from == null) null else {
                val center = from.exactCenterX() + dx
                val overflow = BarOverflow.ids.value
                val others = cfg.items.filter { it.section == Section.SHOWN && it.id != item.id }
                val drawn = others.filter { it.id !in overflow && dragCenters.containsKey(it.id) }
                val after = drawn.firstOrNull { dragCenters.getValue(it.id) > center }
                if (after == null) others.size else others.indexOfFirst { it.id == after.id }
            }
            if (!done) {
                if (index != null && dragPreview.value != (item.id to index)) dragPreview.value = item.id to index
                return
            }
            trace("drag ${item.type} dx=${dx.toInt()} -> index $index")
            if (index != null) Store.move(item.id, Section.SHOWN, index)
            cancelDrag()
        }

        override fun wheel(up: Boolean) {
            val cfg = Store.config.value
            if (cfg.presenting || cfg.hiddenMode == HiddenMode.SHOW_ALL || hiddenItems().isEmpty()) return
            if (up != expanded.value) { expanded.value = up; pinned = false }
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
                if (Store.config.value.hiddenMode == HiddenMode.HOVER && !expanded.value && hiddenItems().isNotEmpty()) main.postDelayed(expandOnHover, 350)
            } else if (!pinned) main.postDelayed(collapse, 900)
            else {
                val secs = Store.config.value.autoCollapseSec
                if (secs > 0) main.postDelayed(collapse, secs * 1000L)
            }
        }
    }

    private fun barMenu(at: Rect, everything: Boolean) = toggleMenu("bentobar", at, 290) { host ->
        BentoBarMenu(host, openItem = { item ->
            val type = Items.of(item.type)
            closeMenu(); menuClosedKey = null
            // From the list of every item, an item's menu opens under the item itself.
            val anchor = placed[item.id]?.takeIf { everything } ?: at
            if (type != null && type.onClick(item)) Ticker.refresh()
            else if (type?.menu != null) toggleMenu("item:${item.id}", anchor, type.menuWidthDp) { h -> ItemMenu(item.id, h) }
        }, hideBar = { Store.update { it.copy(enabled = false) } }, everything = everything)
    }

    /**
     * The "BentoBar menu" entry (a keyboard shortcut the user binds in the system's shortcut
     * settings): BentoBar's menu listing every item, focused for arrows and Enter. Again closes it.
     * False when the strip isn't showing.
     */
    fun openBarMenu(): Boolean {
        if (!started || !strip.shown) return false
        // Under the strip's end next to the system icons (its start when the strip is on the left).
        val w = strip.params.width
        val h = strip.params.height
        val at = if (Store.config.value.position == Position.LEFT) Rect(0, 0, 1, h) else Rect(w - 1, 0, w, h)
        barMenu(at, everything = true)
        return true
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
        // The press that closed this very menu (outside touch) shouldn't reopen it.
        if (menuClosedKey == key && now - menuClosedAt < 350) return
        closeMenu() // resets the sampling demand, so the new menu's is set after it
        val s = snap ?: return
        Ticker.focusItem = key.removePrefix("item:").takeIf { key.startsWith("item:") }
        Ticker.revealHidden = expanded.value || key == "bentobar"
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
     * nothing to go on. An item whose state has a tooltip of its own (a track's whole title and
     * artist, which flight this is) shows that instead. A no-touch window, so it never takes a click.
     */
    private fun showTooltip(item: ItemConfig, anchor: Rect) {
        hideTip()
        val s = snap ?: return
        if (menu != null || !strip.shown) return
        val label = Ticker.states.value[item.id]?.tooltip?.takeIf { it.isNotBlank() } ?: Items.of(item.type)?.title ?: return
        val loc = strip.locationOnScreen()
        val a = Rect(anchor).apply { offset(loc[0], loc[1]) }
        val paint = android.text.TextPaint().apply { textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 13f, service.resources.displayMetrics) }
        // A long tip ends in an ellipsis: the window is never wider than this.
        val w = ((paint.measureText(label) + 2 * 10 * density).toInt() + 2).coerceAtMost((TOOLTIP_MAX_DP * density).toInt())
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

    /**
     * Keep awake holds the screen on through the strip's own window (FLAG_KEEP_SCREEN_ON on a
     * window that's visible), not a window of its own. It used to be a separate 1×1 see-through
     * overlay: Play Protect's live threat detection flags an accessibility service that keeps
     * "imperceptible content" on screen ("App displays over other apps"). The catch: while the strip
     * is hidden (a full-screen app, the screen locked), keep awake waits; full-screen video players
     * keep the screen on themselves.
     */
    private fun updateAwake() {
        val on = Caffeine.active()
        val f = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        val flags = if (on) strip.params.flags or f else strip.params.flags and f.inv()
        if (flags != strip.params.flags) {
            strip.params.flags = flags
            strip.relayout()
            Log.i(tag, if (on) "keep awake on" else "keep awake off")
        }
        // While the strip is hidden (a full-screen app, BentoBar hidden from its tile) or has nothing
        // to draw, no visible window of ours carries the flag: a screen wake lock holds it instead,
        // with no window. Released when the strip is back, keep awake ends, the screen goes off (the
        // power key or the lid) or the lock screen is up.
        val lock = on && (!strip.shown || stripEmpty) && pm.isInteractive && !km.isKeyguardLocked
        if (lock && !screenLock.isHeld) { screenLock.acquire(); Log.i(tag, "keep awake: wake lock while the bar is hidden") }
        else if (!lock && screenLock.isHeld) screenLock.release()
    }

    /** See [updateAwake]. Deprecated in favor of FLAG_KEEP_SCREEN_ON, which needs a visible window. */
    @Suppress("DEPRECATION")
    private val screenLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "BentoBar:keepAwake").apply { setReferenceCounted(false) }

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
        private const val TOOLTIP_MAX_DP = 360
        private const val RELEVANT = AccessibilityEvent.WINDOWS_CHANGE_ADDED or AccessibilityEvent.WINDOWS_CHANGE_REMOVED or
            AccessibilityEvent.WINDOWS_CHANGE_BOUNDS
    }
}
