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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Every item type, in catalog order. */
object Items {
    val all: List<ItemType> = listOf(
        CpuItem, NetworkItem, BatteryItem, MemoryItem, StorageItem,
        CalendarItem, EventItem, ClockItem, TimerItem, CountdownItem,
        CaffeineItem, SoundItem, ToolsItem, FolderItem, AppItem, TextItem, SpacerItem,
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
        if (wasIdle) { main.removeCallbacksAndMessages(TAG); lastRun.clear(); loop.run() }
    }

    fun stop(who: String) {
        users -= who
        if (users.isEmpty()) main.removeCallbacksAndMessages(TAG)
    }

    /** Recomputes now (after a settings change or a click), without waiting for the next second. */
    fun refresh() { lastRun.clear(); runOnce() }

    private fun runOnce() {
        val now = SystemClock.elapsedRealtime()
        val cfg = Store.config.value
        val live = needed(cfg)
        try {
            Env.tick(now, live.mapTo(HashSet()) { it.type })
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
            if (item.id in next && now - last < type.refreshMs) continue
            next[item.id] = try { type.state(item) } catch (e: Exception) {
                Log.w(TAG, "item ${item.type} failed", e); ItemState(icon = type.icon, text = "!", desc = Env.str(R.string.item_failed, type.title))
            }
            lastRun[item.id] = now
        }
        next.keys.retainAll(ids)
        _states.value = next
        _tick.value = System.currentTimeMillis()
    }

    fun stateOf(item: ItemConfig): ItemState = _states.value[item.id]
        ?: Items.of(item.type)?.let { runCatching { it.state(item) }.getOrNull() } ?: ItemState()
}
