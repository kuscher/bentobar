package io.github.kuscher.discobar.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.kuscher.discobar.bar.BarService
import io.github.kuscher.discobar.data.Defaults
import io.github.kuscher.discobar.data.Section
import io.github.kuscher.discobar.data.Store
import io.github.kuscher.discobar.items.Caffeine
import io.github.kuscher.discobar.items.Env
import io.github.kuscher.discobar.items.Ticker
import io.github.kuscher.discobar.items.Timers

/**
 * Test hooks for development over adb. The receiver requires android.permission.DUMP, which only
 * the shell (adb) and the system hold, so apps on the device can't use it.
 *
 *   adb shell am broadcast -a io.github.kuscher.discobar.DEBUG -p io.github.kuscher.discobar --es c 'dump'
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
                        "display" -> Store.updateItem(item.id) { it.copy(display = io.github.kuscher.discobar.data.Display.valueOf(v.uppercase())) }
                        else -> Store.updateItem(item.id) { it.with(k, v.ifEmpty { null }) }
                    }
                    Ticker.refresh(); "ok"
                }
                "cpuprobe" -> { // which CPU-load sources this app may read
                    val out = StringBuilder()
                    val shm = context.getSystemService(android.os.health.SystemHealthManager::class.java)
                    runCatching {
                        out.append("minInterval=${shm.cpuHeadroomMinIntervalMillis} window=${shm.cpuHeadroomCalculationWindowRange} ")
                        out.append("cpuHeadroom=${shm.getCpuHeadroom(android.os.CpuHeadroomParams.Builder().build())} ")
                    }.onFailure { out.append("cpuHeadroom=$it ") }
                    runCatching { out.append("gpuHeadroom=${shm.getGpuHeadroom(android.os.GpuHeadroomParams.Builder().build())} ") }
                        .onFailure { out.append("gpuHeadroom=$it ") }
                    val extra = (args.getOrNull(1)?.split(",") ?: emptyList())
                    for (f in listOf("/proc/stat", "/proc/loadavg", "/proc/uptime", "/proc/pressure/cpu", "/sys/devices/system/cpu/present",
                        "/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq", "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq",
                        "/sys/devices/system/cpu/cpu0/cpuidle/state0/time", "/sys/devices/system/cpu/cpu0/cpuidle/state1/time",
                        "/sys/devices/system/cpu/cpu0/cpuidle/state0/name", "/sys/devices/system/cpu/cpu5/cpuidle/state2/time",
                        "/sys/devices/system/cpu/cpufreq/policy0/stats/time_in_state", "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
                        "/sys/class/kgsl/kgsl-3d0/gpubusy", "/proc/self/stat") + extra) {
                        out.append("| $f: ").append(runCatching { java.io.File(f).bufferedReader().use { it.readLine() }?.take(60) }
                            .getOrElse { "DENIED ${it.javaClass.simpleName}" })
                    }
                    out.toString()
                }
                "state" -> { // what an item shows right now
                    val item = Store.config.value.items.firstOrNull { it.type == args[1] || it.id == args[1] } ?: error("no item")
                    Ticker.stateOf(item).let { "${it.text} active=${it.active}" }
                }
                "finish" -> { io.github.kuscher.discobar.ui.MainActivity.current?.finish(); "ok" }
                "bar" -> { Store.update { it.copy(enabled = args.getOrNull(1) != "off") }; "ok" }
                "look" -> {
                    Store.update { c ->
                        when (args[1]) {
                            "position" -> c.copy(position = io.github.kuscher.discobar.data.Position.valueOf(args[2].uppercase()))
                            "pill" -> c.copy(pill = io.github.kuscher.discobar.data.Pill.valueOf(args[2].uppercase()))
                            "color" -> c.copy(color = io.github.kuscher.discobar.data.ColorMode.valueOf(args[2].uppercase()))
                            "size" -> c.copy(textSize = io.github.kuscher.discobar.data.TextSize.valueOf(args[2].uppercase()))
                            "chips" -> c.copy(chipMode = io.github.kuscher.discobar.data.ChipMode.valueOf(args[2].uppercase()))
                            "chevron" -> c.copy(chevron = args[2] == "on")
                            "hover" -> c.copy(revealOnHover = args[2] == "on")
                            "collapse" -> c.copy(autoCollapseSec = args[2].toInt())
                            "spacing" -> c.copy(spacing = args[2].toInt())
                            else -> c
                        }
                    }; "ok"
                }
                "timer" -> { // minutes, or seconds with an "s" suffix
                    val a = args.getOrElse(1) { "1" }
                    Timers.startTimer(if (a.endsWith("s")) a.dropLast(1).toLong() * 1000 else a.toLong() * 60_000L); "ok"
                }
                "stopwatch" -> { Timers.startStopwatch(); "ok" }
                "pomodoro" -> { Timers.startPomodoro(); "ok" }
                "stop" -> { Timers.stop(); "ok" }
                "awake" -> { if (args.getOrNull(1) == "off") Caffeine.off() else Caffeine.on(args.getOrNull(1)?.toIntOrNull()); "ok" }
                else -> bar?.debug(args) ?: "bar not running"
            }
        } catch (e: Exception) {
            "failed: $e"
        }
        Log.i("DiscoBar", "debug ${args.joinToString(" ")} -> $out")
    }
}
