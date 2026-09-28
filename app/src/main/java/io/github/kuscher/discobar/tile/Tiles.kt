package io.github.kuscher.discobar.tile

import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.kuscher.discobar.data.Store
import io.github.kuscher.discobar.items.Caffeine
import io.github.kuscher.discobar.items.Env
import io.github.kuscher.discobar.items.Timers
import io.github.kuscher.discobar.ui.MainActivity
import io.github.kuscher.discobar.util.Fmt

private fun TileService.openApp() {
    startActivityAndCollapse(PendingIntent.getActivity(this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE))
}

/** Shows or hides DiscoBar's items, e.g. before presenting. */
class BarTile : TileService() {
    override fun onStartListening() { Env.init(this); refresh() }

    override fun onClick() {
        Env.init(this)
        if (Env.service == null) { openApp(); return }
        Store.update { it.copy(enabled = !it.enabled) }
        refresh()
    }

    private fun refresh() {
        val t = qsTile ?: return
        val running = Env.service != null
        val on = Store.config.value.enabled
        t.state = if (running && on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.subtitle = when { !running -> "Set up"; on -> "Shown"; else -> "Hidden" }
        t.updateTile()
    }
}

/** Keeps the screen on until switched off (needs DiscoBar, which owns the window). */
class AwakeTile : TileService() {
    override fun onStartListening() { Env.init(this); refresh() }

    override fun onClick() {
        Env.init(this)
        if (Env.service == null) { openApp(); return }
        Caffeine.toggle(null)
        refresh()
    }

    private fun refresh() {
        val t = qsTile ?: return
        val on = Caffeine.active()
        t.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.subtitle = when {
            Env.service == null -> "Set up DiscoBar"
            !on -> "Off"
            Caffeine.until.value == Caffeine.FOREVER -> "On"
            else -> Fmt.duration(Caffeine.until.value - System.currentTimeMillis())
        }
        t.updateTile()
    }
}

/** Starts, pauses and resumes DiscoBar's timer (25 minutes unless one is running). */
class TimerTile : TileService() {
    override fun onStartListening() { Env.init(this); refresh() }

    override fun onClick() {
        Env.init(this)
        if (Timers.state.value == null) Timers.startTimer(25 * 60_000L) else Timers.toggle()
        refresh()
    }

    private fun refresh() {
        val t = qsTile ?: return
        val s = Timers.state.value
        t.state = if (s?.running == true) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.subtitle = when {
            s == null -> "25 min"
            s.mode == Timers.Mode.STOPWATCH -> Fmt.clock(Timers.elapsed(s)) + if (s.running) "" else " · paused"
            else -> Fmt.clock(Timers.remaining(s)) + if (s.running) " left" else " · paused"
        }
        t.updateTile()
    }
}
