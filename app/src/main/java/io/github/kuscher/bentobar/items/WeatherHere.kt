package io.github.kuscher.bentobar.items

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.SystemClock
import io.github.kuscher.bentobar.data.ItemConfig
import io.github.kuscher.bentobar.util.Now

/**
 * Where the device is, for the Weather items of My location: Android's approximate location (the
 * only location permission BentoBar has), as Android said it. [WeatherLoad.place] rounds it to about
 * ten kilometers ([WeatherRules.nearby]) before anything is asked or held under it.
 *
 * It lives in memory only. The layout holds the choice and never the place, and nothing here is
 * logged or written, nor is the reading of the rounded place: that too is held in memory only.
 * What is done is [HereKeeper]'s, by [HereRules] (both pure and tested); this object is the app's
 * keeper, on Android's location service, and recomputes the Weather items alone when what they show
 * changed (`Ticker.refresh("weather")`: no sampler runs, since this can come from the middle of a tick).
 */
object WeatherHere {
    private val keeper = HereKeeper(Android, Now::elapsed) { Ticker.refresh(WeatherItem.type) }

    /** Where the device is, as Android said it; null until it did, and once that is past its time ([HereRules.FIX_MS]). Any thread. */
    val fix: Fix? get() = keeper.fix

    /** Why there is no [fix], while there is none. Any thread. */
    val why: Locate get() = keeper.why

    fun allowed(): Boolean = Android.allowed()

    /** An item of My location is live: see [HereKeeper.keepUp]. Main thread. */
    fun keepUp() = keeper.keepUp()

    /** Something changed that could let a place be found now: the permission was answered, an item chose My location. Main thread. */
    fun wake() = keeper.wake()

    /** The layout's items and the switch as they are now: see [HereKeeper.keepFor]. Main thread. */
    fun keepFor(items: List<ItemConfig>, on: Boolean) = keeper.keepFor(items, on)

    /**
     * Forgets where the device is, and calls off an ask on its way: the switch went off, the permission
     * was taken back, or no item of My location is left ([HereRules.keeps]). Main thread.
     */
    fun forget() = keeper.forget()

    /** Android's location service, as [HereKeeper] uses it. */
    private object Android : Locator {
        override fun allowed(): Boolean =
            Env.app.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        // A device without a location service at all is one with location off.
        private fun service(): LocationManager? = Env.app.getSystemService(LocationManager::class.java)

        override fun on(): Boolean = service()?.isLocationEnabled == true

        override fun last(): Located? {
            val lm = service() ?: return null
            return providers(lm).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
                .maxByOrNull { it.elapsedRealtimeNanos }?.let(::located)
        }

        override fun current(answer: (Located?) -> Unit): Locator.Cancel? {
            val lm = service() ?: return null
            val provider = providers(lm).firstOrNull() ?: return null
            val signal = CancellationSignal()
            return try {
                lm.getCurrentLocation(provider, signal, Env.app.mainExecutor) { answer(it?.let(::located)) }
                Locator.Cancel { signal.cancel() }
            } catch (e: Exception) {
                null
            }
        }

        /** The location providers an approximate location comes from, best first: Android's fused one, then the network's. */
        private fun providers(lm: LocationManager): List<String> =
            listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }

        /** How old it is is counted from when it was taken, not from when it came, by Android's own clock (a debug build's moved clock aside). */
        private fun located(location: Location) = Located(Fix(location.latitude, location.longitude),
            age = ((SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000).coerceAtLeast(0))
    }
}
