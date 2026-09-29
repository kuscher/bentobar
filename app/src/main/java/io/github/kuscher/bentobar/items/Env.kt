package io.github.kuscher.bentobar.items

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import io.github.kuscher.bentobar.data.Store

/**
 * Process-wide context for items: the app context, the accessibility service while it runs,
 * and the shared samplers. [tick] is called once a second by [Ticker] before item states are read.
 */
@SuppressLint("StaticFieldLeak") // application context only
object Env {
    const val TAG = "BentoBar"
    lateinit var app: Context private set

    /** The running accessibility service (global actions, overlay windows), or null. */
    @Volatile var service: AccessibilityService? = null

    val net = NetSampler()
    val cpu = CpuSampler()
    val mem = MemSampler()
    val battery = BatterySampler()
    val storage = StorageSampler()

    @Synchronized
    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        Store.init(app)
        io.github.kuscher.bentobar.util.Fonts.init(app)
        Timers.init(app)
        Caffeine.init(app)
        Calendar.init(app)
    }

    /** Samples only what the configured items use ([types]); menus ask for theirs while open. */
    fun tick(now: Long, types: Set<String>) {
        if ("network" in types) net.sample(now)
        if ("cpu" in types) cpu.sample(now)
        if ("memory" in types) mem.sample(app, now)
        if ("battery" in types) battery.sample(app, now)
        if ("storage" in types) storage.sample(now)
    }

    /** Starts an activity from a non-activity context. */
    fun launch(intent: Intent): Boolean = try {
        app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "no activity for $intent")
        Toast.makeText(app, "Nothing on this device can open that", Toast.LENGTH_SHORT).show()
        false
    } catch (e: SecurityException) {
        Log.w(TAG, "not allowed: $intent", e)
        false
    }

    fun global(action: Int): Boolean = service?.performGlobalAction(action) ?: false

    /**
     * Advanced Protection (Android 17) turns off accessibility services that aren't assistive
     * tools, BentoBar's included; its chip and tiles keep working.
     */
    fun advancedProtection(): Boolean = android.os.Build.VERSION.SDK_INT >= 36 && runCatching {
        app.getSystemService(android.security.advancedprotection.AdvancedProtectionManager::class.java)?.isAdvancedProtectionEnabled == true
    }.getOrDefault(false)

    private var powerSaveAt = 0L
    private var powerSave = false

    /** Battery saver, re-read at most every 30 s. Items then refresh every 2 s instead of 1. */
    fun powerSave(now: Long): Boolean {
        if (now - powerSaveAt > 30_000) {
            powerSaveAt = now
            powerSave = app.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
        }
        return powerSave
    }
}

/** A fixed-size history of samples for the small charts in menus. */
class History(val size: Int = 60) {
    private val values = DoubleArray(size)
    private var count = 0
    private var head = 0
    fun add(v: Double) { values[head] = v; head = (head + 1) % size; if (count < size) count++ }
    fun toList(): List<Double> = List(count) { values[(head - count + it + size) % size] }
    fun last(): Double = if (count == 0) 0.0 else values[(head - 1 + size) % size]
    fun max(): Double = toList().maxOrNull() ?: 0.0
}

/** Device-wide network throughput from TrafficStats (all interfaces since boot). */
class NetSampler {
    private var lastRx = -1L
    private var lastTx = -1L
    private var lastAt = 0L
    var down = 0.0; private set
    var up = 0.0; private set
    val downHistory = History()
    val upHistory = History()
    var rxTotal = 0L; private set
    var txTotal = 0L; private set

    fun sample(now: Long) {
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        if (rx == TrafficStats.UNSUPPORTED.toLong() || tx == TrafficStats.UNSUPPORTED.toLong()) return
        rxTotal = rx; txTotal = tx
        if (lastRx >= 0 && now > lastAt) {
            val dt = (now - lastAt) / 1000.0
            // Counters can reset (interface restarts): treat drops as zero.
            down = ((rx - lastRx).coerceAtLeast(0)) / dt
            up = ((tx - lastTx).coerceAtLeast(0)) / dt
            // A gap (screen was off) would average over minutes; start fresh instead.
            if (dt > 5) { down = 0.0; up = 0.0 }
            downHistory.add(down); upHistory.add(up)
        }
        lastRx = rx; lastTx = tx; lastAt = now
    }

    /** "Wi-Fi", "Ethernet", … plus metered state, from the default network. */
    fun describe(context: Context): String = runCatching { describeOrThrow(context) }.getOrDefault("Network")

    private fun describeOrThrow(context: Context): String {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return "Unknown"
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "Offline"
        val kind = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth tethering"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_USB) -> "USB tethering"
            else -> "Connected"
        }
        val vpn = if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) " · VPN" else ""
        val metered = if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) " · metered" else ""
        return kind + vpn + metered
    }
}

/**
 * Device-wide CPU load. Apps can't read /proc/stat (Android 8+) and SystemHealthManager's CPU
 * headroom is unsupported on Googlebooks, but each core's idle-state residency in sysfs
 * (cpuN/cpuidle/stateM/time, microseconds) is readable: load = 1 − idle time / wall time.
 * Also reads each cluster's clock (cpufreq) and, on Qualcomm, the GPU's busy counter.
 */
class CpuSampler {
    private val root = java.io.File("/sys/devices/system/cpu")
    private var cores = IntArray(0)
    /** Idle-state residency files, kept open: sysfs regenerates a value on each read from offset 0. */
    private var idleFiles: Array<Array<java.io.RandomAccessFile>> = emptyArray()
    private var policies: List<Pair<String, java.io.File>> = emptyList()
    private var maxKhz: List<Long> = emptyList()
    private var lastIdle = LongArray(0)
    private var lastAt = 0L
    private var initialised = false
    private val buf = ByteArray(32)
    /** False when this device doesn't expose idle-state counters. */
    var available = true; private set
    var total = 0.0; private set
    var perCore = DoubleArray(0); private set
    val history = History()
    val gpuHistory = History()
    /** GPU busy fraction, or null when the device doesn't expose it. */
    var gpu: Double? = null; private set
    /** Clocks and GPU are read only while someone looks at them (the CPU menu). */
    @Volatile var detail = 0

    data class Cluster(val cores: String, val curKhz: Long, val maxKhz: Long)
    var clusters: List<Cluster> = emptyList(); private set

    private fun readLong(f: java.io.File): Long? = try {
        f.bufferedReader().use { it.readLine() }?.trim()?.toLongOrNull()
    } catch (e: Exception) { null }

    private fun readLong(f: java.io.RandomAccessFile): Long {
        f.seek(0)
        val n = f.read(buf)
        var v = 0L
        for (i in 0 until n) { val c = buf[i].toInt(); if (c in 48..57) v = v * 10 + (c - 48) else if (v > 0 || c == 10) break }
        return v
    }

    private fun init() {
        initialised = true
        val present = runCatching { java.io.File(root, "present").readText().trim() }.getOrDefault("0")
        cores = present.split(',').flatMap { part ->
            val (a, b) = if ('-' in part) part.split('-').map { it.trim().toInt() } else listOf(part.trim().toInt(), part.trim().toInt())
            (a..b).toList()
        }.toIntArray()
        idleFiles = Array(cores.size) { i ->
            (0 until 8).mapNotNull { st ->
                runCatching { java.io.RandomAccessFile(java.io.File(root, "cpu${cores[i]}/cpuidle/state$st/time"), "r") }.getOrNull()
            }.toTypedArray()
        }
        available = idleFiles.isNotEmpty() && idleFiles.all { it.isNotEmpty() }
        // Clock domains (policies) are fixed: find them once.
        policies = (0 until 32).mapNotNull { p ->
            val dir = java.io.File(root, "cpufreq/policy$p")
            if (!java.io.File(dir, "scaling_cur_freq").exists()) return@mapNotNull null
            val related = runCatching { java.io.File(dir, "related_cpus").readText().trim().split(' ').mapNotNull { it.toIntOrNull() } }
                .getOrDefault(listOf(p))
            val span = if (related.size > 1) "${related.first()}–${related.last()}" else "${related.firstOrNull() ?: p}"
            span to java.io.File(dir, "scaling_cur_freq")
        }
        maxKhz = policies.map { (_, f) -> readLong(java.io.File(f.parentFile, "cpuinfo_max_freq")) ?: 0L }
    }

    fun sample(now: Long) {
        if (!initialised) init()
        if (!available) return
        val t = android.os.SystemClock.elapsedRealtimeNanos() / 1000
        val idle = LongArray(cores.size) { i -> var sum = 0L; for (f in idleFiles[i]) sum += runCatching { readLong(f) }.getOrDefault(0L); sum }
        if (lastAt > 0) {
            val dt = (t - lastAt).toDouble()
            if (dt in 200_000.0..10_000_000.0) {
                perCore = DoubleArray(cores.size) { i -> (1 - (idle[i] - lastIdle[i]) / dt).coerceIn(0.0, 1.0) }
                total = perCore.average()
                history.add(total)
            }
        }
        lastIdle = idle
        lastAt = t
        if (detail > 0) {
            clusters = policies.mapIndexed { i, (span, f) -> Cluster(span, readLong(f) ?: 0L, maxKhz[i]) }
            gpu = runCatching {
                val parts = java.io.File("/sys/class/kgsl/kgsl-3d0/gpubusy").readText().trim().split(Regex("\\s+")).map { it.toDouble() }
                // "busy total" over the last window; an idle GPU reports "0 0".
                if (parts.size < 2) null else if (parts[1] > 0) (parts[0] / parts[1]).coerceIn(0.0, 1.0) else 0.0
            }.getOrNull()
            gpu?.let { gpuHistory.add(it) }
        }
    }
}

class MemSampler {
    var total = 0L; private set
    var avail = 0L; private set
    var low = false; private set
    val usedHistory = History()
    val used get() = if (total == 0L) 0.0 else (total - avail).toDouble() / total
    private val info = ActivityManager.MemoryInfo()

    fun sample(context: Context, now: Long) {
        val am = context.getSystemService(ActivityManager::class.java) ?: return
        am.getMemoryInfo(info)
        total = info.totalMem; avail = info.availMem; low = info.lowMemory
        usedHistory.add(used)
    }
}

class BatterySampler {
    var level = 0.0; private set
    var charging = false; private set
    var plugged = 0; private set
    var status = BatteryManager.BATTERY_STATUS_UNKNOWN; private set
    var voltageMv = 0; private set
    var currentUa = 0L; private set
    var tempC = 0.0; private set
    var health = BatteryManager.BATTERY_HEALTH_UNKNOWN; private set
    var cycles = -1; private set
    var chargeTimeMs = -1L; private set
    var thermal = PowerManager.THERMAL_STATUS_NONE; private set
    val wattHistory = History()
    /** Watts into (+) or out of (−) the battery. */
    var watts = 0.0; private set

    fun sample(context: Context, now: Long) {
        val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
        val lvl = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        level = if (lvl >= 0 && scale > 0) lvl.toDouble() / scale else 0.0
        status = i.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        voltageMv = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        tempC = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0
        health = i.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
        cycles = i.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1)
        val bm = context.getSystemService(BatteryManager::class.java)
        currentUa = bm?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0L
        if (currentUa == Long.MIN_VALUE) currentUa = 0
        chargeTimeMs = bm?.computeChargeTimeRemaining() ?: -1
        // Sign conventions differ between devices; trust the charging status for the direction.
        val w = kotlin.math.abs(currentUa) / 1e6 * (voltageMv / 1000.0)
        watts = if (plugged != 0 && charging) w else -w
        wattHistory.add(kotlin.math.abs(watts))
        thermal = context.getSystemService(PowerManager::class.java)?.currentThermalStatus ?: thermal
    }

    fun statusText(): String = when (status) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "On battery"
        BatteryManager.BATTERY_STATUS_FULL -> "Full"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Plugged in, not charging"
        else -> "Unknown"
    }

    fun sourceText(): String = when (plugged) {
        BatteryManager.BATTERY_PLUGGED_AC -> "Charger"
        BatteryManager.BATTERY_PLUGGED_USB -> "USB"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
        BatteryManager.BATTERY_PLUGGED_DOCK -> "Dock"
        0 -> "Battery"
        else -> "Power"
    }

    fun healthText(): String = when (health) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
        BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
        else -> "Unknown"
    }

    fun thermalText(): String = when (thermal) {
        PowerManager.THERMAL_STATUS_NONE -> "Normal"
        PowerManager.THERMAL_STATUS_LIGHT -> "Warm"
        PowerManager.THERMAL_STATUS_MODERATE -> "Hot, slowing a little"
        PowerManager.THERMAL_STATUS_SEVERE -> "Hot, slowing down"
        PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
        PowerManager.THERMAL_STATUS_EMERGENCY, PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutting down soon"
        else -> "Unknown"
    }
}

class StorageSampler {
    var total = 0L; private set
    var free = 0L; private set
    private var lastAt = 0L

    fun sample(now: Long) {
        if (lastAt != 0L && now - lastAt < 30_000) return
        lastAt = now
        runCatching {
            val fs = StatFs(Environment.getDataDirectory().path)
            total = fs.totalBytes; free = fs.availableBytes
        }
    }

    fun refresh() { lastAt = 0; sample(SystemClock.elapsedRealtime()) }
}
