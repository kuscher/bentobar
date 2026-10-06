package io.github.kuscher.bentobar.items

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import io.github.kuscher.bentobar.util.Now

/**
 * Where the device is, for the Weather items of My location: Android's approximate location (the
 * only location permission BentoBar has), rounded to about ten kilometers ([WeatherRules.nearby]).
 *
 * It lives in memory only. The layout holds the choice and never the place, and nothing here is
 * logged or written: what is kept on the device is the reading of the rounded place, like a city's.
 * Android is asked while an item of My location is outside Off and the Weather switch is on
 * ([keepUp], from the item's tick), at most every half an hour, and its last known location is
 * taken first, which asks for no new fix at all.
 */
object WeatherHere {
    /** A place is looked for again after this long, as a reading is asked for again. */
    private const val EVERY_MS = WeatherRules.FRESH_MS
    /** No place and Android had none: tried again after a minute. */
    private const val RETRY_MS = 60_000L
    /** Whether location is allowed and on is looked at no more often than this (and at once after [wake]). */
    private const val LOOK_MS = 5_000L

    /** Where the device is, rounded; null until Android said so. Any thread. */
    @Volatile var place: Place? = null
        private set

    /** Why there is no [place], while there is none. */
    @Volatile var why: Locate = Locate.FINDING
        private set

    private var askedAt = 0L
    private var lookedAt = 0L
    private var pending: CancellationSignal? = null

    fun allowed(): Boolean =
        Env.app.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /**
     * An item of My location is live: keeps [place] up to date. Without the permission there is no
     * place (it was taken back). With location switched off a place found before stays: the weather of
     * where the device was a while ago is still the right weather. Main thread.
     */
    fun keepUp() {
        val up = Now.elapsed()
        if (lookedAt != 0L && up - lookedAt < LOOK_MS) return
        lookedAt = up
        if (!allowed()) { if (place != null || why != Locate.NOT_ALLOWED) { forget(); why = Locate.NOT_ALLOWED; Ticker.refresh() }; return }
        val lm = Env.app.getSystemService(LocationManager::class.java) ?: return none()
        if (!lm.isLocationEnabled) { cancel(); if (place == null && why != Locate.OFF) { why = Locate.OFF; Ticker.refresh() }; return }
        if (pending != null) return
        val known = place
        if (askedAt != 0L && up - askedAt < (if (known != null) EVERY_MS else if (why == Locate.NONE) RETRY_MS else 0)) return
        askedAt = up
        // A fix some app asked for in the last half hour is as good as a new one, and costs nothing.
        val last = providers(lm).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.elapsedRealtimeNanos }
        if (last != null && android.os.SystemClock.elapsedRealtimeNanos() - last.elapsedRealtimeNanos < EVERY_MS * 1_000_000) return found(last)
        val provider = providers(lm).firstOrNull() ?: return none()
        if (known == null) why = Locate.FINDING
        val signal = CancellationSignal()
        pending = signal
        try {
            lm.getCurrentLocation(provider, signal, Env.app.mainExecutor) { fix ->
                if (pending !== signal) return@getCurrentLocation
                pending = null
                if (fix != null) found(fix) else if (last != null) found(last) else none()
            }
        } catch (e: Exception) {
            pending = null
            if (last != null) found(last) else none()
        }
    }

    /** The location providers an approximate location comes from, best first: Android's fused one, then the network's. */
    private fun providers(lm: LocationManager): List<String> =
        listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }

    private fun found(fix: Location) {
        val near = WeatherRules.nearby(fix.latitude, fix.longitude) ?: return none()
        if (near != place) { place = near; Ticker.refresh() }
    }

    private fun none() {
        if (place == null) { why = Locate.NONE; Ticker.refresh() }
    }

    private fun cancel() {
        pending?.cancel()
        pending = null
    }

    /** Something changed that could let a place be found now: the permission was answered, an item chose My location. Main thread. */
    fun wake() {
        lookedAt = 0L
        if (place == null) askedAt = 0L
    }

    /**
     * Forgets where the device is: the switch went off, the permission was taken back, or no item of
     * My location is left. Main thread.
     */
    fun forget() {
        cancel()
        place = null
        why = Locate.FINDING
        askedAt = 0L
    }
}
