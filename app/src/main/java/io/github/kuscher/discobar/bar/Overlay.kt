package io.github.kuscher.discobar.bar

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * A Compose UI in a TYPE_ACCESSIBILITY_OVERLAY window: the only window type an app may put on
 * top of the status bar. It brings its own lifecycle, saved-state and view-model owners because
 * there's no Activity. [onOutside] fires for touches outside the window (menus close on it) and
 * [onKey] gets key events while the window has focus.
 */
class Overlay(
    private val context: Context,
    title: String,
    focusable: Boolean = false,
    touchable: Boolean = true,
    private val onOutside: (() -> Unit)? = null,
    private val onKey: ((KeyEvent) -> Boolean)? = null,
) : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val wm = context.getSystemService(WindowManager::class.java)
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this).apply { performRestore(null) }
    override val viewModelStore = ViewModelStore()
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    private var root: FrameLayout? = null
    private var compose: ComposeView? = null
    val shown get() = root != null

    val params = WindowManager.LayoutParams().apply {
        type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        format = PixelFormat.TRANSLUCENT
        width = WindowManager.LayoutParams.WRAP_CONTENT
        height = WindowManager.LayoutParams.WRAP_CONTENT
        gravity = Gravity.TOP or Gravity.START
        flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
            (if (focusable) WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) or
            (if (onOutside != null) WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH else 0) or
            (if (!touchable) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else 0)
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        setTitle(title)
    }

    fun show(content: @Composable () -> Unit) {
        val existing = compose
        if (existing != null) { existing.setContent(content); return }
        val cv = ComposeView(context).apply { setContent(content) }
        val frame = Frame(context).apply {
            setViewTreeLifecycleOwner(this@Overlay)
            setViewTreeViewModelStoreOwner(this@Overlay)
            setViewTreeSavedStateRegistryOwner(this@Overlay)
            addView(cv)
        }
        compose = cv
        root = frame
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        wm.addView(frame, params)
    }

    /** Applies changed [params] (position, size, flags). */
    fun relayout() { root?.let { runCatching { wm.updateViewLayout(it, params) } } }

    fun hide() {
        val r = root ?: return
        runCatching { wm.removeViewImmediate(r) }
        root = null
        compose = null
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun destroy() {
        hide()
        viewModelStore.clear()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }

    /** Screen position of the window's top-left corner. */
    fun locationOnScreen(): IntArray = IntArray(2).also { root?.getLocationOnScreen(it) }

    @SuppressLint("ViewConstructor")
    private inner class Frame(context: Context) : FrameLayout(context) {
        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) { onOutside?.invoke(); return true }
            return super.dispatchTouchEvent(ev)
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (onKey?.invoke(event) == true) return true
            return super.dispatchKeyEvent(event)
        }
    }
}
