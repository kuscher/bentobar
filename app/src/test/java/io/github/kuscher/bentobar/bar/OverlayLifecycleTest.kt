package io.github.kuscher.bentobar.bar

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.Event.ON_CREATE
import androidx.lifecycle.Lifecycle.Event.ON_DESTROY
import androidx.lifecycle.Lifecycle.Event.ON_PAUSE
import androidx.lifecycle.Lifecycle.Event.ON_RESUME
import androidx.lifecycle.Lifecycle.Event.ON_START
import androidx.lifecycle.Lifecycle.Event.ON_STOP
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** An overlay's lifecycle must survive every order of show, hide and destroy (a real LifecycleRegistry, no window). */
class OverlayLifecycleTest {
    private val owner = object : LifecycleOwner {
        // createUnsafe: no main-thread check, which would need a Looper.
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }
    private val events = ArrayList<Lifecycle.Event>()
    private val life = OverlayLifecycle(owner.registry).also {
        owner.registry.addObserver(LifecycleEventObserver { _, e -> events += e })
    }
    private val state get() = owner.registry.currentState

    /** The 0.5 crash: the service was unbound while the strip had never been shown. */
    @Test fun destroyingAnOverlayThatWasNeverShown() {
        life.destroy()
        assertEquals(Lifecycle.State.DESTROYED, state)
        assertEquals(listOf(ON_CREATE, ON_DESTROY), events)
    }

    @Test fun shownHiddenDestroyed() {
        assertTrue(life.shown())
        assertEquals(Lifecycle.State.RESUMED, state)
        life.hidden()
        assertEquals(Lifecycle.State.CREATED, state)
        life.destroy()
        assertEquals(listOf(ON_CREATE, ON_START, ON_RESUME, ON_PAUSE, ON_STOP, ON_DESTROY), events)
    }

    @Test fun destroyedWhileShown() {
        life.shown()
        life.destroy()
        assertEquals(Lifecycle.State.DESTROYED, state)
        assertEquals(ON_DESTROY, events.last())
    }

    @Test fun shownAgainAfterHiding() {
        life.shown(); life.hidden()
        assertTrue(life.shown())
        assertEquals(Lifecycle.State.RESUMED, state)
    }

    @Test fun destroyingTwiceIsOneDestroy() {
        life.shown()
        life.destroy(); life.destroy()
        assertEquals(1, events.count { it == ON_DESTROY })
        events.clear()
        OverlayLifecycle(owner.registry).destroy() // and through a second holder of the same registry
        assertEquals(emptyList<Lifecycle.Event>(), events)
    }

    @Test fun hidingAnOverlayThatWasNeverShownChangesNothing() {
        life.hidden()
        assertEquals(Lifecycle.State.INITIALIZED, state)
        assertEquals(emptyList<Lifecycle.Event>(), events)
    }

    @Test fun aDestroyedOverlayStaysDestroyed() {
        life.shown()
        life.destroy()
        events.clear()
        assertTrue(life.destroyed)
        assertFalse(life.shown())
        life.hidden()
        assertEquals(Lifecycle.State.DESTROYED, state)
        assertEquals(emptyList<Lifecycle.Event>(), events)
    }
}
