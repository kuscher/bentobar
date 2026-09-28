package io.github.kuscher.barbook.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.kuscher.barbook.bar.BarService
import io.github.kuscher.barbook.data.Defaults
import io.github.kuscher.barbook.data.Section
import io.github.kuscher.barbook.data.Store
import io.github.kuscher.barbook.items.Caffeine
import io.github.kuscher.barbook.items.Env
import io.github.kuscher.barbook.items.Ticker
import io.github.kuscher.barbook.items.Timers

/**
 * Test hooks for development over adb. The receiver requires android.permission.DUMP, which only
 * the shell (adb) and the system hold, so apps on the device can't use it.
 *
 *   adb shell am broadcast -a io.github.kuscher.barbook.DEBUG -p io.github.kuscher.barbook --es c 'dump'
 */
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Env.init(context)
        val args = intent.getStringExtra("c").orEmpty().trim().split(Regex("\\s+"))
        val bar = (Env.service as? BarService)?.controller()
        val out = try {
            when (args[0]) {
                "cfg" -> Store.export()
                "reset" -> { Store.update { Defaults.config().copy(onboarded = it.onboarded) }; "reset" }
                "add" -> Store.add(args[1], args.getOrNull(2)?.let { Section.valueOf(it.uppercase()) } ?: Section.SHOWN)
                "set" -> {
                    val (k, v) = args[2].split('=', limit = 2)
                    val item = Store.config.value.items.firstOrNull { it.id == args[1] || it.type == args[1] } ?: error("no item")
                    when (k) {
                        "section" -> Store.updateItem(item.id) { it.copy(section = Section.valueOf(v.uppercase())) }
                        "whenActive" -> Store.updateItem(item.id) { it.copy(whenActive = v.toBoolean()) }
                        "display" -> Store.updateItem(item.id) { it.copy(display = io.github.kuscher.barbook.data.Display.valueOf(v.uppercase())) }
                        else -> Store.updateItem(item.id) { it.with(k, v.ifEmpty { null }) }
                    }
                    Ticker.refresh(); "ok"
                }
                "bar" -> { Store.update { it.copy(enabled = args.getOrNull(1) != "off") }; "ok" }
                "look" -> {
                    Store.update { c ->
                        when (args[1]) {
                            "position" -> c.copy(position = io.github.kuscher.barbook.data.Position.valueOf(args[2].uppercase()))
                            "pill" -> c.copy(pill = io.github.kuscher.barbook.data.Pill.valueOf(args[2].uppercase()))
                            "color" -> c.copy(color = io.github.kuscher.barbook.data.ColorMode.valueOf(args[2].uppercase()))
                            "size" -> c.copy(textSize = io.github.kuscher.barbook.data.TextSize.valueOf(args[2].uppercase()))
                            "chips" -> c.copy(chipMode = io.github.kuscher.barbook.data.ChipMode.valueOf(args[2].uppercase()))
                            else -> c
                        }
                    }; "ok"
                }
                "timer" -> { Timers.startTimer((args.getOrNull(1)?.toLongOrNull() ?: 1) * 60_000L); "ok" }
                "stopwatch" -> { Timers.startStopwatch(); "ok" }
                "pomodoro" -> { Timers.startPomodoro(); "ok" }
                "stop" -> { Timers.stop(); "ok" }
                "awake" -> { if (args.getOrNull(1) == "off") Caffeine.off() else Caffeine.on(args.getOrNull(1)?.toIntOrNull()); "ok" }
                else -> bar?.debug(args) ?: "bar not running"
            }
        } catch (e: Exception) {
            "failed: $e"
        }
        Log.i("BarBook", "debug ${args.joinToString(" ")} -> $out")
    }
}
