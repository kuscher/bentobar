package io.github.kuscher.bentobar.items

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.SystemClock
import io.github.kuscher.bentobar.util.Now

/**
 * Where the device is, for the Weather items of My location: Android's approximate location (the
 * only location permission BentoBar has), as Android said it. [WeatherLoad.place] rounds it to about
 * ten kilometers ([WeatherRules.nearby]) before anything is asked or held under it.
 *
 * It lives in memory only. The layout holds the choice and never the place, and nothing here is
 * logged or written, nor is the reading of the rounded place: that too is held in memory only.
 * Android is asked while an item of My location is outside Off and the Weather switch is on
 * ([keepUp], from the item's tick), as [HereRules] says: when a fix is half an hour old, and without
 * one less and less often, up to every half hour. Its last known location is taken first, which asks
 * for no new fix at all. This object asks Android and does what the rules say; the rules are tested.
 */
object WeatherHere {
    /** What is known: the fix and why there is none. Written on the main thread, read on any. */
    @Volatile private var here = Here()
    private var lookedAt: Long? = null
    private var pending: CancellationSignal? = null

    /** Where the device is, as Android said it; null until it did, and once that is past its time ([HereRules.FIX_MS]). Any thread. */
    val fix: Fix? get() = HereRules.fresh(here, Now.elapsed()).fix

    /** Why there is no [fix], while there is none. Any thread. */
    val why: Locate get() = HereRules.fresh(here, Now.elapsed()).why

    fun allowed(): Boolean =
        Env.app.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /**
     * An item of My location is live: keeps [fix] up to date, looking at the permission and the
     * location switch every few seconds and asking Android when that is due ([HereRules.look]).
     * Without the permission there is no place (it was taken back); with location switched off a fix
     * stays for its time. Main thread.
     */
    fun keepUp() {
        val now = Now.elapsed()
        if (!HereRules.looks(now, lookedAt)) return
        lookedAt = now
        val allowed = allowed()
        val lm = if (allowed) Env.app.getSystemService(LocationManager::class.java) else null
        // A device without a location service at all is one with location off.
        val on = lm != null && lm.isLocationEnabled
        if (!on) cancel()
        val look = HereRules.look(here, now, allowed, on, asking = pending != null)
        set(look.here)
        if (look.ask && lm != null) ask(lm, now)
    }

    private fun ask(lm: LocationManager, now: Long) {
        set(HereRules.asked(here, now))
        // A fix some app asked for in the last half hour is as good as a new one, and costs nothing.
        val last = providers(lm).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.elapsedRealtimeNanos }
        if (last != null && age(last) < HereRules.EVERY_MS) return found(last)
        val provider = providers(lm).firstOrNull() ?: return none()
        val signal = CancellationSignal()
        pending = signal
        try {
            lm.getCurrentLocation(provider, signal, Env.app.mainExecutor) { location ->
                if (pending !== signal) return@getCurrentLocation
                pending = null
                if (location != null) found(location) else if (last != null) found(last) else none()
            }
        } catch (e: Exception) {
            pending = null
            if (last != null) found(last) else none()
        }
    }

    /** The location providers an approximate location comes from, best first: Android's fused one, then the network's. */
    private fun providers(lm: LocationManager): List<String> =
        listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }

    /** How old [location] is, in milliseconds: what it is good for is counted from when it was taken, not from when it came. */
    private fun age(location: Location): Long = ((SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000).coerceAtLeast(0)

    private fun found(location: Location) {
        if (WeatherRules.nearby(location.latitude, location.longitude) == null) return none()
        val now = Now.elapsed()
        set(HereRules.found(here, Fix(location.latitude, location.longitude), at = now - age(location), now = now))
    }

    private fun none() = set(HereRules.none(here))

    private fun cancel() {
        pending?.cancel()
        pending = null
    }

    /**
     * A new state. The Weather items are worked out again, and nothing else (no sampler runs: this can
     * come from the middle of a tick), when what they show changed: the rounded place, or why there is none.
     */
    private fun set(next: Here) {
        val before = here
        here = next
        fun near(h: Here) = h.fix?.let { WeatherRules.nearby(it.lat, it.lon) }
        if (near(next) != near(before) || (next.fix == null && next.why != before.why)) Ticker.refresh(WeatherItem.type)
    }

    /** Something changed that could let a place be found now: the permission was answered, an item chose My location. Main thread. */
    fun wake() {
        lookedAt = null
        here = HereRules.woken(here)
    }

    /**
     * Forgets where the device is: the switch went off, the permission was taken back, or no item of
     * My location is left ([HereRules.keeps]). Main thread.
     */
    fun forget() {
        cancel()
        here = Here()
        lookedAt = null
    }
}
