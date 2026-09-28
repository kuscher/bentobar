package io.github.kuscher.discobar.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * The bar layout and settings, as one JSON document in SharedPreferences. The accessibility
 * service and the settings screens run in the same process and share this [StateFlow], so every
 * edit shows up in the status bar right away.
 */
object Store {
    private const val TAG = "DiscoBar"
    private const val KEY = "bar"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var prefs: SharedPreferences
    private val state = MutableStateFlow(BarConfig())
    val config: StateFlow<BarConfig> get() = state.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("discobar", Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, null)
        state.value = raw?.let {
            runCatching { json.decodeFromString(BarConfig.serializer(), it) }
                .onFailure { e -> Log.w(TAG, "settings unreadable, starting fresh", e) }
                .getOrNull()
        } ?: Defaults.config()
        if (raw == null) save(state.value)
    }

    fun update(change: (BarConfig) -> BarConfig) {
        val next = synchronized(this) {
            val n = change(state.value)
            if (n == state.value) return
            state.value = n
            n
        }
        save(next)
    }

    fun updateItem(id: String, change: (ItemConfig) -> ItemConfig) =
        update { c -> c.copy(items = c.items.map { if (it.id == id) change(it) else it }) }

    fun add(type: String, section: Section = Section.SHOWN, options: Map<String, String> = emptyMap()): String {
        val id = newId()
        update { c -> c.copy(items = c.items + ItemConfig(id, type, section, options = options)) }
        return id
    }

    fun remove(id: String) = update { c -> c.copy(items = c.items.filterNot { it.id == id }) }

    /** Moves [id] into [section] at [index] within that section (end if out of range). */
    fun move(id: String, section: Section, index: Int) = update { c ->
        val item = c.items.firstOrNull { it.id == id } ?: return@update c
        val rest = c.items.filterNot { it.id == id }
        val inSection = rest.withIndex().filter { it.value.section == section }
        val at = when {
            inSection.isEmpty() -> rest.size
            index >= inSection.size -> inSection.last().index + 1
            else -> inSection[index.coerceAtLeast(0)].index
        }
        c.copy(items = rest.toMutableList().apply { add(at, item.copy(section = section)) })
    }

    fun newId(): String = UUID.randomUUID().toString().substring(0, 8)

    fun export(): String = json.encodeToString(BarConfig.serializer(), state.value)

    fun import(text: String): Boolean = runCatching {
        val c = json.decodeFromString(BarConfig.serializer(), text)
        update { c }
    }.isSuccess

    private fun save(c: BarConfig) {
        prefs.edit().putString(KEY, json.encodeToString(BarConfig.serializer(), c)).apply()
    }
}

/** What a fresh install shows: a useful bar that still leaves room. */
object Defaults {
    fun config() = BarConfig(
        items = listOf(
            ItemConfig(Store.newId(), "timer", Section.HIDDEN, whenActive = true),
            ItemConfig(Store.newId(), "event", Section.HIDDEN, whenActive = true),
            ItemConfig(Store.newId(), "network", Section.SHOWN, display = Display.TEXT),
            ItemConfig(Store.newId(), "battery", Section.HIDDEN),
            ItemConfig(Store.newId(), "memory", Section.HIDDEN),
            ItemConfig(Store.newId(), "caffeine", Section.SHOWN, display = Display.ICON),
            ItemConfig(Store.newId(), "calendar", Section.SHOWN, display = Display.ICON),
            ItemConfig(Store.newId(), "tools", Section.SHOWN, display = Display.ICON),
        ),
    )
}
