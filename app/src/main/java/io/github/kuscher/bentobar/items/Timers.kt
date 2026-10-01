package io.github.kuscher.bentobar.items

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.github.kuscher.bentobar.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The one shared timer: countdown, stopwatch or Pomodoro. State is persisted (wall-clock times)
 * so a timer survives BentoBar's process being restarted, and the end is also scheduled with
 * AlarmManager so it fires even when the bar isn't running.
 */
object Timers {
    enum class Mode { TIMER, STOPWATCH, POMODORO }
    enum class Phase { WORK, BREAK, LONG_BREAK }

    @Serializable
    data class State(
        val mode: Mode,
        val running: Boolean,
        /** TIMER/POMODORO: wall time it ends (while running). STOPWATCH: wall time it started. */
        val at: Long,
        /** While paused: remaining (TIMER/POMODORO) or elapsed (STOPWATCH) milliseconds. */
        val pausedMs: Long = 0,
        /** Full length, for progress (TIMER/POMODORO). */
        val lengthMs: Long = 0,
        val phase: Phase = Phase.WORK,
        /** Completed work rounds (POMODORO). */
        val round: Int = 0,
        val label: String = "",
    )

    const val POMO_WORK = 25 * 60_000L
    const val POMO_BREAK = 5 * 60_000L
    const val POMO_LONG = 15 * 60_000L

    private const val TAG = "BentoBar"
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var app: Context
    private lateinit var prefs: SharedPreferences
    private val main = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> get() = _state

    /** Wall time the last timer finished; the bar flashes the item for a minute after. */
    @Volatile var finishedAt = 0L; private set

    fun init(context: Context) {
        app = context.applicationContext
        prefs = app.getSharedPreferences("timers", Context.MODE_PRIVATE)
        _state.value = prefs.getString("state", null)?.let { runCatching { json.decodeFromString(State.serializer(), it) }.getOrNull() }
        finishedAt = prefs.getLong("finishedAt", 0)
        check()
    }

    fun now() = System.currentTimeMillis()

    fun remaining(s: State, now: Long = now()): Long = when {
        s.mode == Mode.STOPWATCH -> 0
        s.running -> (s.at - now).coerceAtLeast(0)
        else -> s.pausedMs
    }

    fun elapsed(s: State, now: Long = now()): Long = when {
        s.mode != Mode.STOPWATCH -> s.lengthMs - remaining(s, now)
        s.running -> (now - s.at).coerceAtLeast(0)
        else -> s.pausedMs
    }

    fun startTimer(ms: Long, label: String = "") =
        set(State(Mode.TIMER, true, now() + ms, lengthMs = ms, label = label))

    fun startStopwatch() = set(State(Mode.STOPWATCH, true, now()))

    fun startPomodoro() = set(State(Mode.POMODORO, true, now() + POMO_WORK, lengthMs = POMO_WORK, phase = Phase.WORK))

    fun pause() {
        val s = _state.value ?: return
        if (!s.running) return
        set(s.copy(running = false, pausedMs = if (s.mode == Mode.STOPWATCH) elapsed(s) else remaining(s)))
    }

    fun resume() {
        val s = _state.value ?: return
        if (s.running) return
        set(s.copy(running = true, at = if (s.mode == Mode.STOPWATCH) now() - s.pausedMs else now() + s.pausedMs))
    }

    fun toggle() { val s = _state.value ?: return; if (s.running) pause() else resume() }

    /** Adds (or with negative [ms], removes) time from a countdown. */
    fun add(ms: Long) {
        val s = _state.value ?: return
        if (s.mode == Mode.STOPWATCH) return
        if (s.running) {
            val end = (s.at + ms).coerceAtLeast(now() + 1000)
            set(s.copy(at = end, lengthMs = (s.lengthMs + ms).coerceAtLeast(end - now())))
        } else {
            val left = (s.pausedMs + ms).coerceAtLeast(1000)
            set(s.copy(pausedMs = left, lengthMs = (s.lengthMs + ms).coerceAtLeast(left)))
        }
    }

    fun stop() { set(null); finishedAt = 0; prefs.edit().putLong("finishedAt", 0).apply() }

    /** Called every tick and by the alarm: finishes a countdown whose time is up. */
    fun check() {
        val s = _state.value ?: return
        if (s.mode == Mode.STOPWATCH || !s.running || now() < s.at) return
        finish(s)
    }

    private fun finish(s: State) {
        finishedAt = now()
        prefs.edit().putLong("finishedAt", finishedAt).apply()
        if (s.mode == Mode.POMODORO) {
            val round = if (s.phase == Phase.WORK) s.round + 1 else s.round
            val next = when {
                s.phase != Phase.WORK -> Phase.WORK
                round % 4 == 0 -> Phase.LONG_BREAK
                else -> Phase.BREAK
            }
            val len = when (next) { Phase.WORK -> POMO_WORK; Phase.BREAK -> POMO_BREAK; Phase.LONG_BREAK -> POMO_LONG }
            val minutes = (len / 60_000).toInt()
            Notify.timerDone(app, app.getString(if (s.phase == Phase.WORK) R.string.timer_alert_focus_done else R.string.timer_alert_break_over),
                if (next == Phase.WORK) app.getString(R.string.timer_alert_next_focus)
                else app.resources.getQuantityString(R.plurals.timer_alert_next_break, minutes, minutes))
            set(State(Mode.POMODORO, true, now() + len, lengthMs = len, phase = next, round = round))
        } else {
            Notify.timerDone(app, if (s.label.isNotBlank()) s.label else app.getString(R.string.timer_alert_done),
                app.getString(R.string.timer_alert_finished, io.github.kuscher.bentobar.util.Fmt.duration(s.lengthMs)))
            set(null)
        }
    }

    private fun set(s: State?) {
        _state.value = s
        prefs.edit().putString("state", s?.let { json.encodeToString(State.serializer(), it) }).apply()
        schedule(s)
        Chips.update(app)
    }

    private fun schedule(s: State?) {
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(app, 0, Intent(app, TimerAlarm::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        am.cancel(pi)
        main.removeCallbacksAndMessages(this)
        if (s == null || !s.running || s.mode == Mode.STOPWATCH) return
        // Exact when the user allowed it (Alarms & reminders); otherwise Android may run it a bit late
        // while the device sleeps. The in-process handler covers the awake case to the second.
        try {
            if (am.canScheduleExactAlarms() && io.github.kuscher.bentobar.data.Uses.on(io.github.kuscher.bentobar.data.Uses.EXACT_ALARMS))
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, s.at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, s.at, pi)
        } catch (e: SecurityException) {
            Log.w(TAG, "alarm not allowed", e)
        }
        main.postAtTime({ check() }, this, android.os.SystemClock.uptimeMillis() + (s.at - now()).coerceAtLeast(0) + 50)
    }
}

/** AlarmManager lands here when a countdown ends. */
class TimerAlarm : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Env.init(context)
        Timers.check()
    }
}
