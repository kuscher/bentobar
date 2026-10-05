package io.github.kuscher.bentobar.items

import android.os.Handler
import android.os.Looper
import io.github.kuscher.bentobar.data.Online
import io.github.kuscher.bentobar.util.Now
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * The app's wiring for [Refresher] and [Ask]: each gets a thread of its own that exists only while
 * there is work, results arrive on the main thread, the clock is [Now], and a new snapshot makes the
 * ticker compute the items again, so the bar shows it at once.
 */
object Background {
    private val main = Handler(Looper.getMainLooper())
    /** What to forget when a service is switched off: the snapshots of the refreshers made for it. */
    private val forServices = ArrayList<Pair<Online.Service, () -> Unit>>()

    /** One thread named "BentoBar-[name]", lowest priority, started with the first task and gone after half a minute without one. */
    private fun thread(name: String): Executor =
        ThreadPoolExecutor(0, 1, 30, TimeUnit.SECONDS, LinkedBlockingQueue()) { r ->
            Thread(r, "BentoBar-$name").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }
        }

    fun wiring(name: String) = Refresher.Wiring(thread(name), { main.post(it) }, Now::elapsed, changed = { Ticker.refresh() })

    /**
     * A [Refresher] for an item type. With [service], its snapshots are forgotten the moment that
     * service is switched off or loses its key (before the type's own `forgetFetched` runs).
     */
    fun <K : Any, V : Any> refresher(
        name: String,
        service: Online.Service? = null,
        every: (key: K, value: V) -> Long?,
        restore: ((key: K) -> Refresher.Restored<V>?)? = null,
        load: (key: K, last: V?) -> V,
    ): Refresher<K, V> = Refresher(wiring(name), every, restore, load).also { r ->
        if (service != null) synchronized(forServices) { forServices += service to { r.forget() } }
    }

    fun <Q : Any, A : Any> ask(name: String, work: (Q) -> A): Ask<Q, A> = Ask(wiring(name), work)

    /** [service] was switched off: every refresher made for it forgets what it has. Main thread. */
    internal fun forget(service: Online.Service) {
        val mine = synchronized(forServices) { forServices.filter { it.first == service }.map { it.second } }
        mine.forEach { it() }
    }

    /** Runs [block] on the main thread: now if this is it, else posted. */
    fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }
}
