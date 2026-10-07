package io.github.kuscher.bentobar.items

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import io.github.kuscher.bentobar.R
import io.github.kuscher.bentobar.data.BarConfig
import io.github.kuscher.bentobar.data.ItemConfig
import io.github.kuscher.bentobar.data.Section
import io.github.kuscher.bentobar.data.Store
import io.github.kuscher.bentobar.data.couldShow
import io.github.kuscher.bentobar.util.Now
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Every item type, in catalog order. */
object Items {
    val all: List<ItemType> = listOf(
        CpuItem, NetworkItem, BatteryItem, MemoryItem, StorageItem, HeatItem, DevicesItem,
        CalendarItem, EventItem, ClockItem, TimerItem, CountdownItem,
        CaffeineItem, MediaItem, SoundItem, ToolsItem, FolderItem, AppItem, TextItem, SpacerItem,
        WeatherItem, FlightItem, StocksItem,
    )
    private val byType = all.associateBy { it.type }

    fun of(type: String): ItemType? = byType[type]
}

/**
 * The one clock that drives item states: ticks once a second, on the second, while anything
 * that shows items is on screen (the bar, a menu, the settings preview), and not otherwise.
 */
object Ticker {
    private const val TAG = "BentoBar"
    private val main = Handler(Looper.getMainLooper())
    private val users = HashSet<String>()
    private val lastRun = HashMap<String, Long>()
    private val _states = MutableStateFlow<Map<String, ItemState>>(emptyMap())
    private val _tick = MutableStateFlow(0L)

    val states: StateFlow<Map<String, ItemState>> get() = _states
    val tick: StateFlow<Long> get() = _tick

    /**
     * Something that shows items is on screen (the bar, a menu, the settings preview). Read from any
     * thread: an online service is asked only while this holds (`Http.allowed`).
     */
    @Volatile var running = false; private set

    /** The types whose [ItemType.onLive] has run and whose [ItemType.onIdle] hasn't. Main thread. */
    private val liveTypes = HashSet<String>()

    /**
     * Tells the types which of them are being sampled now: [ItemType.onLive] for one that starts,
     * [ItemType.onIdle] for one that stops. A type's listeners and polls hang on these two, so none
     * exists while the bar is hidden, the screen is off, or no item of the type is outside Off.
     */
    private fun lifeCycle(types: Set<String>) {
        for (t in types) if (liveTypes.add(t)) hook(t, "onLive") { it.onLive() }
        for (t in liveTypes.filter { it !in types }) {
            liveTypes -= t
            hook(t, "onIdle") { it.onIdle() }
            // What such a type last showed (a track's title) is not kept while nobody shows it.
            if (Items.of(t)?.discreet == true) forgetStates(t)
        }
    }

    private fun forgetStates(type: String) {
        val ids = Store.config.value.items.filter { it.type == type }.mapTo(HashSet()) { it.id }
        if (ids.any { it in _states.value }) _states.value = _states.value - ids
        ids.forEach { lastRun -= it }
    }

    private inline fun hook(type: String, what: String, call: (ItemType) -> Unit) {
        val t = Items.of(type) ?: return
        try { call(t) } catch (e: Throwable) { failed(t, "$what of $type failed", e) }
    }

    /**
     * A type's own code failed: the tick goes on without it (also for a method this device's Android
     * doesn't have, which is no Exception). What went wrong is logged, but for a type that goes online
     * or shows the user's own things only its kind: an exception's message can quote what was being
     * read (a reply, a title).
     */
    private fun failed(type: ItemType, what: String, e: Throwable) {
        if (e is VirtualMachineError) throw e
        if (type.online != null || type.discreet) Log.w(TAG, "$what: ${e.javaClass.simpleName}") else Log.w(TAG, what, e)
    }

    /** Hidden items are on screen (bar expanded, or BentoBar's menu lists them): sample them too. */
    @Volatile var revealHidden = false
    /** The item whose menu is open, sampled even when it's hidden. */
    @Volatile var focusItem: String? = null

    /**
     * Items worth sampling now: what the strip could draw ([couldShow], the same rule it draws by),
     * revealed hidden items, an open menu, or everything while the settings preview is open. A
     * folded-away battery or memory item costs nothing.
     */
    private fun needed(cfg: BarConfig): List<ItemConfig> {
        val all = "settings" in users
        return cfg.items.filter {
            it.section != Section.OFF && (all || cfg.couldShow(it) || revealHidden || it.id == focusItem)
        }
    }

    private val loop = object : Runnable {
        override fun run() {
            runOnce()
            val now = System.currentTimeMillis()
            // On the second boundary; every other second under battery saver.
            val period = if (Env.powerSave(SystemClock.elapsedRealtime())) 2000 else 1000
            main.postAtTime(this, TAG, SystemClock.uptimeMillis() + (period - now % period) + 5)
        }
    }

    /** [who] wants ticks (e.g. "bar", "menu", "settings"); idempotent. */
    fun start(who: String) {
        val wasIdle = users.isEmpty()
        users += who
        running = true
        if (wasIdle) { main.removeCallbacksAndMessages(TAG); lastRun.clear(); loop.run() }
    }

    fun stop(who: String) {
        users -= who
        if (users.isEmpty()) {
            main.removeCallbacksAndMessages(TAG)
            running = false
            // Nothing shows items any more: every type lets go of what it listens to.
            lifeCycle(emptySet())
        }
    }

    /** Recomputes now (after a settings change or a click), without waiting for the next second. */
    fun refresh() { lastRun.clear(); runOnce() }

    /**
     * Recomputes one item now, and nothing else: no sampler runs. For what only that item shows and
     * that changes many times a second (its slider being dragged), where [refresh] would feed every
     * sampler a reading per pointer move.
     */
    fun refresh(item: ItemConfig) {
        val current = Store.config.value.items.firstOrNull { it.id == item.id } ?: return
        val type = Items.of(current.type) ?: return
        _states.value = _states.value + (current.id to compute(type, current))
        lastRun[current.id] = SystemClock.elapsedRealtime()
    }

    /**
     * Recomputes the items of one type now, and nothing else: what was loaded for it has just come
     * in. No sampler runs, so an answer that arrives between two ticks puts no extra reading into
     * the network or CPU chart. Only the items that are being sampled anyway are computed, so an item
     * that isn't on screen asks for nothing; menus that redraw with the tick redraw now. Main thread.
     */
    fun refresh(type: String) {
        val t = Items.of(type) ?: return
        if (users.isEmpty()) return
        val mine = needed(Store.config.value).filter { it.type == type }
        if (mine.isEmpty()) return
        val now = SystemClock.elapsedRealtime()
        _states.value = _states.value + mine.associate { it.id to compute(t, it) }
        mine.forEach { lastRun[it.id] = now }
        _tick.value = System.currentTimeMillis()
    }

    private fun compute(type: ItemType, item: ItemConfig): ItemState = try { type.state(item) } catch (e: Throwable) {
        failed(type, "item ${item.type} failed", e)
        ItemState(icon = type.icon, text = "!", desc = Env.str(R.string.item_failed, type.title))
    }

    private fun runOnce() {
        val now = SystemClock.elapsedRealtime()
        val cfg = Store.config.value
        val live = needed(cfg)
        val types = live.mapTo(HashSet()) { it.type }
        // Only while something shows items: a refresh() with nothing on screen must not wake a type that nobody would put back to sleep.
        lifeCycle(if (users.isEmpty()) emptySet() else types)
        try {
            // A type may read another type's sampler (Heat reads the battery's temperature): those run for it too.
            val sampled = HashSet(types)
            for (t in types) Items.of(t)?.samples?.let { sampled += it }
            Env.tick(now, sampled)
            if (users.isNotEmpty()) for (t in types) hook(t, "sample") { it.sample(Now.elapsed()) }
            Timers.check()
            Caffeine.check()
        } catch (e: Exception) {
            Log.w(TAG, "sampling failed", e)
        }
        val next = HashMap(_states.value)
        val ids = HashSet<String>()
        cfg.items.forEach { ids += it.id }
        for (item in live) {
            val type = Items.of(item.type) ?: continue
            val last = lastRun[item.id] ?: 0L
            if (item.id in next && !TickRules.due(now, last, type.refreshMs)) continue
            next[item.id] = compute(type, item)
            lastRun[item.id] = now
        }
        next.keys.retainAll(ids)
        _states.value = next
        _tick.value = System.currentTimeMillis()
    }

    fun stateOf(item: ItemConfig): ItemState = _states.value[item.id]
        ?: Items.of(item.type)?.let { runCatching { it.state(item) }.getOrNull() } ?: ItemState()
}
