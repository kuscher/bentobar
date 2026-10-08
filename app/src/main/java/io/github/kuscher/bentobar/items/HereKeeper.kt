package io.github.kuscher.bentobar.items

import io.github.kuscher.bentobar.data.ItemConfig

/**
 * What My location needs of Android, so that what is done with its answers is pure and tested:
 * [WeatherHere] is the app's one, on Android's location service.
 */
interface Locator {
    /** Whether the approximate location is allowed. */
    fun allowed(): Boolean

    /** Whether location is switched on (a device without a location service has it off). */
    fun on(): Boolean

    /** Android's last known location, which asks for no new fix; null when it has none. */
    fun last(): Located?

    /**
     * Asks Android for a new fix; [answer] gets it, or null, on the main thread. Returns what calls the
     * ask off, or null when none could be made (no provider): then no answer comes.
     */
    fun current(answer: (Located?) -> Unit): Cancel?

    fun interface Cancel { fun cancel() }
}

/** A fix as Android gave it, and how old it was then, in milliseconds (counted from when it was taken). */
class Located(val fix: Fix, val age: Long)

/**
 * Where the device is, for the Weather items of My location: what [HereRules] say, done with a
 * [Locator]. It lives in memory only; nothing here is logged or written. [clock] is the time since
 * boot; [changed] is called when what the items show changed (the rounded place, or why there is
 * none). Main thread, except [fix] and [why], which any thread reads.
 */
class HereKeeper(private val android: Locator, private val clock: () -> Long, private val changed: () -> Unit) {
    @Volatile private var here = Here()
    private var lookedAt: Long? = null
    /** The ask on its way: its answer counts only while this is still it. */
    private var waiting: Any? = null
    private var callOff: Locator.Cancel? = null

    /** Where the device is, as Android said it; null until it did, and once that is past its time ([HereRules.FIX_MS]). */
    val fix: Fix? get() = HereRules.fresh(here, clock()).fix

    /** Why there is no [fix], while there is none. */
    val why: Locate get() = HereRules.fresh(here, clock()).why

    /** Everything held, a fix past its time included: for tests, which check that forgetting leaves nothing. */
    internal val held: Here get() = here

    /** Whether an ask is on its way. */
    val asking: Boolean get() = waiting != null

    /**
     * An item of My location is live: keeps [fix] up to date, looking at the permission and the
     * location switch every few seconds and asking Android when that is due ([HereRules.look]).
     */
    fun keepUp() {
        val now = clock()
        if (!HereRules.looks(now, lookedAt)) return
        lookedAt = now
        val allowed = android.allowed()
        val on = allowed && android.on()
        if (!on) callOff()
        val look = HereRules.look(here, now, allowed, on, asking = waiting != null)
        set(look.here)
        if (look.ask) ask(now)
    }

    private fun ask(now: Long) {
        set(HereRules.asked(here, now))
        // A fix some app asked for in the last half hour is as good as a new one, and costs nothing.
        val last = android.last()
        if (last != null && last.age < HereRules.EVERY_MS) return found(last)
        val token = Any()
        waiting = token
        val cancel = android.current { located ->
            if (waiting !== token) return@current
            waiting = null
            callOff = null
            if (located != null) found(located) else if (last != null) found(last) else none()
        }
        if (waiting !== token) return // answered at once
        if (cancel == null) {
            waiting = null
            if (last != null) found(last) else none()
        } else callOff = cancel
    }

    private fun found(located: Located) {
        if (WeatherRules.nearby(located.fix.lat, located.fix.lon) == null) return none()
        val now = clock()
        set(HereRules.found(here, located.fix, at = now - located.age, now = now))
    }

    private fun none() = set(HereRules.none(here))

    private fun callOff() {
        callOff?.cancel()
        callOff = null
        waiting = null
    }

    private fun set(next: Here) {
        val before = here
        here = next
        fun near(h: Here) = h.fix?.let { WeatherRules.nearby(it.lat, it.lon) }
        if (near(next) != near(before) || (next.fix == null && next.why != before.why)) changed()
    }

    /** Something changed that could let a place be found now: the permission was answered, an item chose My location. */
    fun wake() {
        lookedAt = null
        here = HereRules.woken(here)
    }

    /**
     * The layout's [items] and the Weather switch ([on]) as they are now. True while they want where the
     * device is ([HereRules.wanted]); else it is forgotten ([forget]), a fix past its time and an ask on
     * its way too.
     */
    fun keepFor(items: List<ItemConfig>, on: Boolean): Boolean {
        if (HereRules.wanted(items, on)) return true
        forget()
        return false
    }

    /** Forgets where the device is, and calls off an ask on its way: its answer is dropped. */
    fun forget() {
        callOff()
        here = Here()
        lookedAt = null
    }
}
