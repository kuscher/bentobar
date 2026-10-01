package io.github.kuscher.bentobar.data

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
    private const val TAG = "BentoBar"
    private const val KEY = "bar"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var prefs: SharedPreferences
    private val state = MutableStateFlow(BarConfig())
    val config: StateFlow<BarConfig> get() = state.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("bentobar", Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, null)
        state.value = raw?.let {
            runCatching { json.decodeFromString(BarConfig.serializer(), it) }
                .onFailure { e -> Log.w(TAG, "settings unreadable, starting fresh", e) }
                .getOrNull()
        } ?: Defaults.config()
        // v1 → v2: timed hiding of revealed items became opt-in; 8 s was only the old default.
        if (state.value.version < 2) {
            val c = state.value
            state.value = c.copy(version = 2, autoCollapseSec = if (c.autoCollapseSec == 8) 0 else c.autoCollapseSec)
            save(state.value)
        }
        if (state.value.version < 3) { state.value = state.value.migrateToV3(); save(state.value) }
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
    fun move(id: String, section: Section, index: Int) = update { c -> c.copy(items = c.items.moved(id, section, index)) }

    fun newId(): String = UUID.randomUUID().toString().substring(0, 8)

    fun export(): String = json.encodeToString(BarConfig.serializer(), state.value)

    fun import(text: String): Boolean = runCatching {
        // An older layout (Copy settings from an earlier version) comes through the same migrations.
        val c = json.decodeFromString(BarConfig.serializer(), text).let {
            if (it.version < 2) it.copy(version = 2, autoCollapseSec = if (it.autoCollapseSec == 8) 0 else it.autoCollapseSec) else it
        }.migrateToV3()
        update { c }
    }.isSuccess

    private fun save(c: BarConfig) {
        prefs.edit().putString(KEY, json.encodeToString(BarConfig.serializer(), c)).apply()
    }
}

/** What a fresh install shows: a useful bar that still leaves room. */
object Defaults {
    /**
     * A calm first bar, after the most-used Mac menu bar tools: the calendar (Fantastical,
     * Itsycal), a timer, and keep awake (Amphetamine), plus the next meeting (MeetingBar), which
     * shows only when one is near. Everything else is one click away in Add.
     */
    fun config() = BarConfig(
        items = listOf(
            ItemConfig(Store.newId(), "event", Section.HIDDEN, whenActive = true),
            ItemConfig(Store.newId(), "calendar", Section.SHOWN, display = Display.ICON),
            ItemConfig(Store.newId(), "timer", Section.SHOWN, display = Display.ICON),
            ItemConfig(Store.newId(), "caffeine", Section.SHOWN, display = Display.ICON),
        ),
    )
}
