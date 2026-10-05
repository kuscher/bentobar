package io.github.kuscher.bentobar.items

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

/**
 * [Calendar]'s way of loading, as one class for everything else that is loaded: a weather reading, a
 * flight's answer, the batteries of the input devices. Loads run on a background thread, one per key
 * at a time. What they return is published on the main thread as a snapshot per key, which an item's
 * state and its menu read with [peek] at no cost. [forget] drops a snapshot together with whatever a
 * load that is still running returns.
 *
 * An item's state calls [want] every time it is asked (once a second, or as often as its type
 * refreshes): that loads when nothing was loaded yet, or when the snapshot is older than [every] says
 * it may be. So nothing is scheduled: while no item is sampled (the bar hidden, the screen off)
 * nothing loads, and when the bar returns, what has grown too old loads once.
 *
 * Pure Kotlin: the threads and the clock come in through [Wiring] (`Background` makes the app's),
 * and the tests run it on the calling thread.
 *
 * @param every how long a value stays fresh, in milliseconds since it was loaded; null: it is never
 *   loaded again by itself. Depends on the value: a failure is retried sooner, a flight about to
 *   leave is asked more often. Keep it pure and test it.
 * @param restore reads what was kept from an earlier run (and how old it is), on the background
 *   thread, the first time a key is wanted: a restart then shows the last value without asking.
 * @param load runs on the background thread and gets the last value. It returns the new one; a
 *   failure is a value too, carrying what went wrong and what was known before. Null means there is
 *   nothing to say: it was not asked after all (the request was refused because the switch went off
 *   or the bar hid under it), so what there is stays as it is. It should not throw (if it does, the
 *   key is left alone for [Wiring.afterThrowMs]).
 */
class Refresher<K : Any, V : Any>(
    private val wiring: Wiring,
    private val every: (key: K, value: V) -> Long?,
    private val restore: ((key: K) -> Restored<V>?)? = null,
    private val load: (key: K, last: V?) -> V?,
) {
    /**
     * Where work runs and what time it is. [elapsed] counts sleep (a night with the lid closed makes
     * a snapshot old). [changed] is told on the main thread after a snapshot changed. Whatever
     * [every] says, [want] never loads a key again within [minGapMs] of its last load. [mayLoad] is
     * asked on the main thread before anything is loaded: while it says no (nothing shows items, the
     * service is switched off), [want] and [refresh] do nothing.
     */
    class Wiring(
        val background: Executor,
        val main: (Runnable) -> Unit,
        val elapsed: () -> Long,
        val changed: () -> Unit = {},
        val minGapMs: Long = 10_000,
        val afterThrowMs: Long = 60_000,
        val mayLoad: () -> Boolean = { true },
    )

    /** A value from an earlier run, and how long ago it was loaded. */
    class Restored<V>(val value: V, val ageMs: Long)

    private class Slot<V>(val value: V, val at: Long)

    private val slots = ConcurrentHashMap<K, Slot<V>>()
    // Everything below is touched on the main thread only.
    private val loading = HashSet<K>()
    private val again = HashSet<K>()
    private val restored = HashSet<K>()
    private val threwAt = HashMap<K, Long>()
    /** When a load for the key last came back, whatever it brought. */
    private val triedAt = HashMap<K, Long>()
    /** Bumped by [forget]: a load that started before must not publish. One counter for all, one per key. */
    private var generation = 0
    private val keyGeneration = HashMap<K, Int>()

    /** The snapshot for [key], or null while there is none. Any thread; costs a map lookup. */
    fun peek(key: K): V? = slots[key]?.value

    /** How long ago the snapshot for [key] was loaded, in milliseconds; null while there is none. Any thread. */
    fun age(key: K): Long? = slots[key]?.let { wiring.elapsed() - it.at }

    /** A load for [key] is on its way. Main thread. */
    fun loading(key: K): Boolean = key in loading

    /** Loads [key] if it was never loaded or has grown older than [every] allows. Main thread; returns at once. */
    fun want(key: K) {
        if (key in loading || !wiring.mayLoad()) return
        val now = wiring.elapsed()
        threwAt[key]?.let { if (now - it < wiring.afterThrowMs) return }
        triedAt[key]?.let { if (now - it < wiring.minGapMs) return }
        val slot = slots[key]
        if (slot != null) {
            val keep = every(key, slot.value) ?: return
            if (now - slot.at < keep) return
        }
        start(key, tryRestore = slot == null && restore != null && key !in restored)
    }

    /**
     * Loads [key] now, whatever [every] says: the user pressed Refresh, or something changed that the
     * snapshot can't know of. Unless it was loaded less than [floorMs] ago, or nothing may be loaded
     * right now: then nothing happens and the answer is false. While a load is running none is added,
     * except with [afterRunning]: then one more follows the running one (what that one read may be
     * older than the reason for asking).
     */
    fun refresh(key: K, floorMs: Long = 0, afterRunning: Boolean = false): Boolean {
        if (!wiring.mayLoad()) return false
        if (key in loading) { if (afterRunning) again += key; return true }
        slots[key]?.let { if (wiring.elapsed() - it.at < floorMs) return false }
        threwAt -= key
        start(key, tryRestore = false)
        return true
    }

    /**
     * Drops the snapshot of [key], or of every key, and whatever a running load returns for it: the
     * source was switched off, access was taken back, the item is gone. Main thread.
     */
    fun forget(key: K? = null) {
        if (key == null) {
            generation++
            slots.clear(); again.clear(); restored.clear(); threwAt.clear(); triedAt.clear()
        } else {
            keyGeneration[key] = (keyGeneration[key] ?: 0) + 1
            slots.remove(key); again -= key; restored -= key; threwAt -= key; triedAt -= key
        }
    }

    /** Forgets every key that is not in [keys]: what the layout no longer has. Main thread. */
    fun keepOnly(keys: Set<K>) {
        for (k in slots.keys.toList()) if (k !in keys) forget(k)
    }

    private fun start(key: K, tryRestore: Boolean) {
        if (!loading.add(key)) return
        val all = generation
        val own = keyGeneration[key] ?: 0
        val last = slots[key]?.value
        val work = Runnable {
            // What was kept comes first, and is shown as it is; whether it is too old is the next want()'s question.
            val kept = if (tryRestore) runCatching { restore?.invoke(key) }.getOrNull() else null
            val loaded = if (kept != null) null else runCatching { load(key, last) }
            wiring.main(Runnable {
                loading -= key
                val current = all == generation && own == (keyGeneration[key] ?: 0)
                val now = wiring.elapsed()
                // Only an answer that counts: a key forgotten while this ran is looked up in what is kept again.
                if (tryRestore && current) restored += key
                when {
                    !current -> {}
                    kept != null -> { slots[key] = Slot(kept.value, now - kept.ageMs.coerceAtLeast(0)); wiring.changed() }
                    loaded != null && loaded.isSuccess -> {
                        threwAt -= key
                        triedAt[key] = now
                        // Nothing to say: what there is stays, as old as it is.
                        loaded.getOrNull()?.let { slots[key] = Slot(it, now); wiring.changed() }
                    }
                    else -> threwAt[key] = now
                }
                if (again.remove(key) && current) start(key, tryRestore = false)
            })
        }
        try {
            wiring.background.execute(work)
        } catch (e: RejectedExecutionException) {
            loading -= key
        }
    }
}
