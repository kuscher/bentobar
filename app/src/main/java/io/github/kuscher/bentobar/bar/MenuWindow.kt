package io.github.kuscher.bentobar.bar

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Choreographer
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import io.github.kuscher.bentobar.R
import io.github.kuscher.bentobar.ui.MenuGlassState
import io.github.kuscher.bentobar.ui.MenuMotion
import io.github.kuscher.bentobar.ui.MenuRoom
import java.util.function.Consumer
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A popup's window (docs/design/1.3/engineering.md). Unlike the strip's [Overlay] it is a dialog's
 * window: only a `Window` can blur what is behind it, and a dialog of type TYPE_ACCESSIBILITY_OVERLAY
 * made with the service's context gets the service's token from its WindowManager, as `addView` does.
 *
 * The window is the card plus room for its shadow ([MenuRoom]), added at its full size and never
 * resized while it moves: the card unfolds inside it. Before each frame its root view is framed to the
 * glass as far as it shows, because the platform blurs exactly the root view's rectangle (Booklight's
 * "the blur follows the glass"); what is in it is moved back so it stays where it is on screen.
 *
 * One clock drives everything ([MenuMotion]), from the popup's first drawn frame: the glass, its blur
 * and shadow (read by the Compose content from [glass]), and its blocks. Closing folds the glass back
 * into its item; a timer takes the window away regardless, so a stalled clock (the screen going off)
 * can never leave one up.
 */
class MenuWindow(
    private val service: Context,
    private val title: String,
    private val onOutside: () -> Unit,
    private val onEscape: () -> Unit,
) {
    val glass = MenuGlassState()

    private val wm = service.getSystemService(WindowManager::class.java)
    private val density = service.resources.displayMetrics.density
    private val main = Handler(Looper.getMainLooper())
    private val outline = GlassOutline()
    private val models = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
    /** The shadow's window: never touched, never focused, just below the popup's. */
    private val shade = Overlay(service, "$title shadow", touchable = false)

    private val dialog = object : ComponentDialog(service, R.style.Theme_BentoBar_Menu) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_ESCAPE) {
                if (event.action == KeyEvent.ACTION_UP) onEscape()
                return true
            }
            return super.dispatchKeyEvent(event)
        }

        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) { onOutside(); return true }
            // The room around the card is part of the window but not of the popup: a press there is outside too.
            if (ev.actionMasked == MotionEvent.ACTION_DOWN && !onCard(ev)) { onOutside(); return true }
            return super.dispatchTouchEvent(ev)
        }
    }

    /** Where the card sits in the window, in px. */
    private val cardLeft = (MenuRoom.side.value * density).roundToInt()
    private val cardTop = (MenuRoom.top.value * density).roundToInt()
    private var cardWidth = 0
    private var windowX = 0
    private var windowY = 0
    private val blurPx = (BLUR_DP * density).roundToInt()

    private enum class Phase { OPENING, OPEN, CLOSING, REOPENING, DISSOLVING, GONE }

    private var phase = Phase.OPENING
    /** The frame time the phase began, in ns; -1 until its first frame. */
    private var phaseStart = -1L
    private var closeFrom = 0f
    private var closePresence = 1f
    private var reopenFrom = 0f
    private var reopenSpeed = 0f
    private var reopenPresence = 1f
    private var reopenContents = 1f
    /** The glass's height a frame ago and when, for its speed when it turns round. */
    private var lastDp = 0f
    private var lastNanos = 0L
    private var framed = false
    private var lastBlur = -1
    private var onGone: (() -> Unit)? = null
    private var shown = false

    private val blurListener = Consumer<Boolean> { on -> glass.blur = on && !solid; applyBlur() }
    private val frameGlass = ViewTreeObserver.OnPreDrawListener { frame(); true }
    private val tick = Choreographer.FrameCallback { step(it) }
    private val removal = Runnable { remove() }

    /** True while the popup is up or still leaving. */
    val isShowing get() = shown

    /**
     * Shows the popup with its card's top-left corner at ([x], [y]) on screen, [width] px wide. False if
     * Android refused the window (the service is going): nothing is up then.
     */
    fun show(x: Int, y: Int, width: Int, shadow: @Composable () -> Unit, content: @Composable () -> Unit): Boolean {
        val window = dialog.window ?: return false
        cardWidth = width
        windowX = x - cardLeft
        windowY = y - cardTop
        glass.blur = wm.isCrossWindowBlurEnabled && !solid
        dialog.setCancelable(false)
        dialog.setTitle(title)
        dialog.setContentView(ComposeView(dialog.context).apply { setContent(content) })
        window.decorView.setViewTreeViewModelStoreOwner(models)
        (window.decorView as? ViewGroup)?.clipChildren = false // the shadow lies outside the framed root view
        window.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
        window.setBackgroundDrawable(outline)
        window.setWindowAnimations(0)
        window.setGravity(Gravity.TOP or Gravity.LEFT)
        window.setDecorFitsSystemWindows(false)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
        )
        window.attributes = window.attributes.apply {
            this.x = windowX
            this.y = windowY
            this.width = width + 2 * cardLeft
            height = WindowManager.LayoutParams.WRAP_CONTENT
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            setTitle(title)
        }
        // Asked for now, not with the first frame: the platform turns the blur on in a message of its own, which would
        // wait behind the opening's frames. Never 0 on the way in, or the platform lets the blur layer go.
        applyBlur()
        window.decorView.viewTreeObserver.addOnPreDrawListener(frameGlass)
        // The shadow's window first, so the popup's own is above it (later windows of one type are higher).
        shade.params.gravity = Gravity.TOP or Gravity.LEFT
        shade.params.x = windowX
        shade.params.y = windowY
        shade.params.width = width + 2 * cardLeft
        shade.show(shadow)
        try {
            dialog.show()
        } catch (e: RuntimeException) {
            Log.w("BentoBar", "can't add the window $title: ${e.javaClass.simpleName}")
            window.decorView.viewTreeObserver.removeOnPreDrawListener(frameGlass)
            shade.destroy()
            return false
        }
        shown = true
        wm.addCrossWindowBlurEnabledListener(service.mainExecutor, blurListener)
        phase = Phase.OPENING
        phaseStart = -1L
        if (scale() <= 0f) settle() else Choreographer.getInstance().postFrameCallback(tick)
        return true
    }

    /**
     * Closes the popup: it takes no more touches or keys from this moment (the next click goes where it
     * points), folds back into its item and is taken away; [gone] runs then. [dissolve]: it was left for
     * another popup, and dissolves in place instead of folding. [now]: away at once (the service stopping,
     * the display changing).
     */
    fun close(dissolve: Boolean = false, now: Boolean = false, gone: (() -> Unit)? = null) {
        if (!shown) { gone?.invoke(); return }
        onGone = gone
        if (now || scale() <= 0f) { remove(); return }
        if (phase == Phase.CLOSING || phase == Phase.DISSOLVING) return
        passThrough()
        closeFrom = glass.heightDp
        closePresence = glass.presence
        phase = if (dissolve) Phase.DISSOLVING else Phase.CLOSING
        phaseStart = -1L
        val ms = if (dissolve) MenuMotion.DISSOLVE_MS else MenuMotion.closeMs(closeFrom, fullDp())
        main.removeCallbacks(removal)
        main.postDelayed(removal, (ms * scale()).toLong() + REMOVAL_GRACE_MS)
        Choreographer.getInstance().removeFrameCallback(tick)
        Choreographer.getInstance().postFrameCallback(tick)
    }

    /** Asked to open again while it folds away: it turns round from where it is, keeping its speed. False if it can't (dissolving, gone). */
    fun reopen(): Boolean {
        if (!shown || phase != Phase.CLOSING) return false
        main.removeCallbacks(removal)
        onGone = null
        takeTouches()
        reopenFrom = glass.heightDp
        reopenSpeed = speed()
        reopenPresence = glass.presence
        reopenContents = glass.contents
        phase = Phase.REOPENING
        phaseStart = -1L
        return true
    }

    private fun step(nanos: Long) {
        if (!shown || phase == Phase.GONE || phase == Phase.OPEN) return
        val full = fullDp()
        if (full <= 0f) { Choreographer.getInstance().postFrameCallback(tick); return } // not measured yet
        if (phaseStart < 0) phaseStart = nanos
        val ms = MenuMotion.scaled((nanos - phaseStart) / 1e6f, scale())
        when (phase) {
            Phase.OPENING -> {
                val g = MenuMotion.opening(ms, full)
                put(g, nanos)
                glass.clockMs = ms
                if (g.done && ms >= MenuMotion.blocksDoneMs(9)) { settle(); return }
            }
            Phase.REOPENING -> {
                val g = MenuMotion.reopening(ms, reopenFrom, reopenSpeed, full, reopenPresence, reopenContents)
                put(g, nanos)
                if (g.done) { settle(); return }
            }
            Phase.CLOSING -> {
                val g = MenuMotion.closing(ms, closeFrom, full, closePresence)
                put(g, nanos)
                if (g.done) { remove(); return }
            }
            Phase.DISSOLVING -> {
                glass.alpha = MenuMotion.dissolve(ms)
                applyBlur()
                if (ms >= MenuMotion.DISSOLVE_MS) { remove(); return }
            }
            else -> return
        }
        Choreographer.getInstance().postFrameCallback(tick)
    }

    private fun put(g: MenuMotion.Glass, nanos: Long) {
        lastDp = glass.heightDp; lastNanos = nanos
        glass.heightDp = g.heightDp
        glass.presence = g.presence
        glass.shadow = g.shadow
        glass.contents = g.contents
        applyBlur()
    }

    /** At rest, open: the glass whole, the blocks in place, the clock stopped. */
    private fun settle() {
        phase = Phase.OPEN
        glass.heightDp = fullDp().coerceAtLeast(MenuMotion.LIP_DP)
        glass.presence = 1f; glass.shadow = 1f; glass.contents = 1f; glass.alpha = 1f
        glass.clockMs = Float.POSITIVE_INFINITY
        applyBlur()
    }

    /** The glass's speed as it was last moving, in dp per ms (negative: folding up). */
    private fun speed(): Float {
        val dt = (System.nanoTime() - lastNanos) / 1e6f
        return if (lastNanos == 0L || dt <= 0f || dt > 50f) 0f else (glass.heightDp - lastDp) / dt.coerceAtLeast(4f)
    }

    private fun fullDp(): Float = glass.fullHeightPx / density

    /** Before each frame: the root view framed to the glass as it shows, the blur's corners with it. */
    private fun frame() {
        val root = dialog.window?.decorView as? ViewGroup ?: return
        val content = root.getChildAt(0) ?: return
        val w = content.width
        val h = content.height
        if (w == 0 || h == 0) return
        val shownPx = min((glass.heightDp * density).roundToInt(), glass.fullHeightPx.takeIf { it > 0 } ?: Int.MAX_VALUE)
        val follow = glass.blur && glass.fullHeightPx > 0
        val left = if (follow) cardLeft else 0
        val top = if (follow) cardTop else 0
        val right = if (follow) cardLeft + cardWidth else w
        val bottom = if (follow) cardTop + shownPx.coerceAtLeast(1) else h
        outline.corner = min(MenuMotion.RADIUS_DP * density, (bottom - top) / 2f)
        if (follow || framed) {
            // A layout pass gives the root view the whole window again, so the frame is set anew every time.
            framed = follow
            root.setLeftTopRightBottom(left, top, right, bottom)
            for (i in 0 until root.childCount) root.getChildAt(i).apply { translationX = -left.toFloat(); translationY = -top.toFloat() }
            root.invalidateOutline()
        }
    }

    private fun applyBlur() {
        val window = dialog.window ?: return
        val r = if (!glass.blur) 0
            else (blurPx * glass.presence * glass.alpha).roundToInt().coerceAtLeast(if (phase == Phase.CLOSING || phase == Phase.DISSOLVING) 0 else 1)
        if (r != lastBlur) { lastBlur = r; window.setBackgroundBlurRadius(r) }
    }

    private fun onCard(ev: MotionEvent): Boolean {
        val x = ev.rawX - windowX
        val y = ev.rawY - windowY
        return x >= cardLeft && x < cardLeft + cardWidth && y >= cardTop && y < cardTop + glass.fullHeightPx
    }

    /** Leaving: touches and keys go to whatever is under it from now on. */
    private fun passThrough() = setFlags(
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
    )

    private fun takeTouches() = setFlags(
        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
    )

    private fun setFlags(add: Int, clear: Int) {
        val window = dialog.window ?: return
        runCatching { window.addFlags(add); window.clearFlags(clear) }
    }

    private fun remove() {
        if (!shown) return
        shown = false
        phase = Phase.GONE
        main.removeCallbacks(removal)
        Choreographer.getInstance().removeFrameCallback(tick)
        runCatching { wm.removeCrossWindowBlurEnabledListener(blurListener) }
        dialog.window?.decorView?.viewTreeObserver?.let { if (it.isAlive) it.removeOnPreDrawListener(frameGlass) }
        runCatching { dialog.dismiss() }
        shade.destroy()
        models.viewModelStore.clear()
        onGone?.invoke(); onGone = null
    }

    /** The system's animator duration scale: 1 normally, 0 with "Remove animations" (then nothing moves). */
    private fun scale(): Float = runCatching {
        Settings.Global.getFloat(service.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f)

    companion object {
        /** Debug builds (`./bento debug solid on`): popups as if the platform had no blur, to see the solid card. */
        @Volatile var solid = false

        /** The glass's blur (visual.md: 24 dp, a little more than Booklight's 22 for small text under it). */
        const val BLUR_DP = 24f
        /** How long after its fold should have ended a closing window is taken away regardless. */
        private const val REMOVAL_GRACE_MS = 250L
    }
}

/**
 * A popup window's background: it paints nothing (the Compose content paints the glass), but its
 * outline is the glass's rounded rectangle, from which the platform takes the blur's corners before
 * each frame. The bounds are the root view's, framed to the glass as far as it shows.
 */
private class GlassOutline : Drawable() {
    var corner = 0f
    override fun draw(canvas: Canvas) {}
    override fun getOutline(outline: Outline) {
        outline.setRoundRect(bounds, min(corner, min(bounds.width(), bounds.height()) / 2f))
        outline.alpha = 1f
    }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
