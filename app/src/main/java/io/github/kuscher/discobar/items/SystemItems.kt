package io.github.kuscher.discobar.items

import android.content.Intent
import android.os.PowerManager
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import io.github.kuscher.discobar.data.ItemConfig
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kuscher.discobar.ui.ChoiceRow
import io.github.kuscher.discobar.ui.CoreBars
import io.github.kuscher.discobar.ui.SectionLabel
import io.github.kuscher.discobar.ui.InfoRow
import io.github.kuscher.discobar.ui.MenuCard
import io.github.kuscher.discobar.ui.MenuDivider
import io.github.kuscher.discobar.ui.MenuEntry
import io.github.kuscher.discobar.ui.Meter
import io.github.kuscher.discobar.ui.SliderRow
import io.github.kuscher.discobar.ui.Sparkline
import io.github.kuscher.discobar.ui.rememberTick
import io.github.kuscher.discobar.util.Fmt
import io.github.kuscher.discobar.util.Sym
import java.util.Locale

object NetworkItem : ItemType("network", "Network speed", Sym.SWAP_VERT, "Download and upload speed, with a chart") {
    override val canBeActive = true

    override fun state(item: ItemConfig): ItemState {
        val n = Env.net
        val down = "↓" + Fmt.rate(n.down)
        val up = "↑" + Fmt.rate(n.up)
        val text = when (item.opt("show", "both")) {
            "down" -> down
            "up" -> up
            "total" -> Fmt.rate(n.down + n.up) + "/s"
            else -> "$down $up"
        }
        val threshold = item.optInt("activeKBs", 500) * 1000.0
        return ItemState(icon = Sym.SWAP_VERT, text = text, active = maxOf(n.down, n.up) >= threshold, widthKey = "net",
            desc = "Network: ${Fmt.bytes(n.down)}/s down, ${Fmt.bytes(n.up)}/s up")
    }

    override val menu: @Composable (ItemConfig, MenuHost) -> Unit = { _, host ->
        rememberTick()
        val n = Env.net
        MenuCard(Sym.SWAP_VERT, "Network", Env.net.describe(Env.app)) {
            InfoRow("Download", "${Fmt.bytes(n.down)}/s", MaterialTheme.colorScheme.primary)
            InfoRow("Upload", "${Fmt.bytes(n.up)}/s", MaterialTheme.colorScheme.tertiary)
            Sparkline(n.downHistory.toList(), second = n.upHistory.toList())
            InfoRow("Received since start-up", Fmt.bytes(n.rxTotal.toDouble()))
            InfoRow("Sent since start-up", Fmt.bytes(n.txTotal.toDouble()))
            MenuDivider()
            MenuEntry(Sym.WIFI, "Internet settings") { host.close(); Env.launch(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)) }
            MenuEntry(Sym.DATA_USAGE, "Data usage") { host.close(); Env.launch(Intent(Settings.ACTION_DATA_USAGE_SETTINGS)) }
        }
    }

    override val options: @Composable (ItemConfig, (ItemConfig) -> Unit) -> Unit = { item, set ->
        ChoiceRow("Show", listOf("both" to "Down and up", "down" to "Download", "up" to "Upload", "total" to "Total"),
            item.opt("show", "both")) { set(item.with("show", it)) }
        SliderRow("Counts as active above", item.optInt("activeKBs", 500), 50..5000,
            { "$it KB/s" }) { set(item.with("activeKBs", it.toString())) }
    }
}

object CpuItem : ItemType("cpu", "CPU load", Sym.MEMORY, "How busy the processor is, per core, with clock speeds") {
    override val canBeActive = true

    override fun state(item: ItemConfig): ItemState {
        val c = Env.cpu
        if (!c.available) return ItemState(icon = Sym.MEMORY, text = "–", desc = "This device doesn't share CPU load with apps")
        val limit = item.optInt("activePct", 80) / 100.0
        return ItemState(icon = Sym.MEMORY, text = Fmt.percent(c.total), active = c.total >= limit,
            tone = if (c.total >= 0.9) Tone.WARN else Tone.NORMAL, widthKey = "cpu", desc = "CPU ${Fmt.percent(c.total)} busy")
    }

    override val menu: @Composable (ItemConfig, MenuHost) -> Unit = { _, host ->
        rememberTick()
        val c = Env.cpu
        androidx.compose.runtime.DisposableEffect(Unit) { c.detail++; onDispose { c.detail-- } }
        MenuCard(Sym.MEMORY, "CPU", if (c.available) "${Fmt.percent(c.total)} busy · ${c.perCore.size} cores" else "Not available on this device") {
            if (c.available) {
                Sparkline(c.history.toList(), max = 1.0)
                Spacer(Modifier.height(8.dp))
                SectionLabel("Cores")
                CoreBars(c.perCore)
                Spacer(Modifier.height(6.dp))
                c.clusters.forEach { cl ->
                    InfoRow("Cores ${cl.cores}", String.format(Locale.ROOT, "%.2f of %.2f GHz", cl.curKhz / 1e6, cl.maxKhz / 1e6))
                }
                c.gpu?.let { g ->
                    MenuDivider()
                    InfoRow("Graphics (GPU)", Fmt.percent(g), MaterialTheme.colorScheme.tertiary)
                    Sparkline(c.gpuHistory.toList(), color = MaterialTheme.colorScheme.tertiary, max = 1.0)
                }
            }
            MenuDivider()
            MenuEntry(Sym.BOLT, "Battery usage by app") { host.close(); Env.launch(Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) }
        }
    }

    override val options: @Composable (ItemConfig, (ItemConfig) -> Unit) -> Unit = { item, set ->
        SliderRow("Counts as active above", item.optInt("activePct", 80), 30..99, { "$it%" }) { set(item.with("activePct", it.toString())) }
    }
}

object MemoryItem : ItemType("memory", "Memory", Sym.MEMORY_ALT, "How much RAM is in use") {
    override val canBeActive = true

    override fun state(item: ItemConfig): ItemState {
        val m = Env.mem
        val limit = item.optInt("activePct", 85) / 100.0
        return ItemState(icon = Sym.MEMORY_ALT, text = Fmt.percent(m.used), active = m.used >= limit || m.low, widthKey = "mem",
            tone = if (m.low) Tone.WARN else Tone.NORMAL, desc = "Memory ${Fmt.percent(m.used)} used")
    }

    override val menu: @Composable (ItemConfig, MenuHost) -> Unit = { _, host ->
        rememberTick()
        val m = Env.mem
        MenuCard(Sym.MEMORY_ALT, "Memory", "${Fmt.percent(m.used)} in use") {
            Meter(m.used.toFloat(), if (m.low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            InfoRow("Used", Fmt.bytes((m.total - m.avail).toDouble()))
            InfoRow("Available", Fmt.bytes(m.avail.toDouble()))
            InfoRow("Installed", Fmt.bytes(m.total.toDouble()))
            if (m.low) InfoRow("State", "Low memory", MaterialTheme.colorScheme.error)
            Sparkline(m.usedHistory.toList(), max = 1.0)
            MenuDivider()
            MenuEntry(Sym.APPS, "Apps") { host.close(); Env.launch(Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)) }
        }
    }

    override val options: @Composable (ItemConfig, (ItemConfig) -> Unit) -> Unit = { item, set ->
        SliderRow("Counts as active above", item.optInt("activePct", 85), 50..99, { "$it%" }) { set(item.with("activePct", it.toString())) }
    }
}

object BatteryItem : ItemType("battery", "Battery details", Sym.BOLT, "Power draw in watts, temperature, time to full") {
    override val canBeActive = true

    override fun state(item: ItemConfig): ItemState {
        val b = Env.battery
        val hot = b.tempC >= 42 || b.thermal >= PowerManager.THERMAL_STATUS_MODERATE
        val low = !b.charging && b.level < 0.2
        val text = when (item.opt("show", "watts")) {
            "percent" -> Fmt.percent(b.level)
            "temp" -> String.format(Locale.ROOT, "%.0f°", b.tempC)
            "time" -> if (b.chargeTimeMs > 0) Fmt.duration(b.chargeTimeMs) else Fmt.percent(b.level)
            else -> Fmt.oneDecimal(kotlin.math.abs(b.watts)) + "W"
        }
        return ItemState(
            icon = if (b.charging) Sym.BOLT else if (b.level < 0.1) Sym.BATTERY_0_BAR else Sym.BATTERY_FULL,
            filled = b.charging, text = text, active = low || hot, widthKey = "bat",
            tone = when { !b.charging && b.level < 0.1 -> Tone.ALERT; low || hot -> Tone.WARN; else -> Tone.NORMAL },
            desc = "Battery ${Fmt.percent(b.level)}, ${b.statusText()}, ${Fmt.oneDecimal(kotlin.math.abs(b.watts))} watts",
        )
    }

    override val menu: @Composable (ItemConfig, MenuHost) -> Unit = { _, host ->
        rememberTick()
        val b = Env.battery
        MenuCard(if (b.charging) Sym.BOLT else Sym.BATTERY_FULL, "Battery", "${Fmt.percent(b.level)} · ${b.statusText()}") {
            Meter(b.level.toFloat(), if (!b.charging && b.level < 0.2) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            InfoRow(if (b.watts >= 0) "Charging at" else "Using", String.format(Locale.ROOT, "%.1f W", kotlin.math.abs(b.watts)))
            Sparkline(b.wattHistory.toList())
            InfoRow("Power source", b.sourceText())
            InfoRow("Voltage", String.format(Locale.ROOT, "%.2f V", b.voltageMv / 1000.0))
            InfoRow("Current", String.format(Locale.ROOT, "%d mA", kotlin.math.abs(b.currentUa) / 1000))
            InfoRow("Temperature", String.format(Locale.ROOT, "%.1f °C", b.tempC),
                if (b.tempC >= 42) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            InfoRow("Health", b.healthText())
            if (b.cycles >= 0) InfoRow("Charge cycles", b.cycles.toString())
            if (b.chargeTimeMs > 0) InfoRow("Full in", Fmt.duration(b.chargeTimeMs))
            InfoRow("Thermal state", b.thermalText())
            MenuDivider()
            MenuEntry(Sym.BOLT, "Battery usage") { host.close(); Env.launch(Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) }
            MenuEntry(Sym.SETTINGS, "Battery saver") { host.close(); Env.launch(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)) }
        }
    }

    override val options: @Composable (ItemConfig, (ItemConfig) -> Unit) -> Unit = { item, set ->
        ChoiceRow("Show", listOf("watts" to "Watts", "percent" to "Percent", "temp" to "Temperature", "time" to "Time to full"),
            item.opt("show", "watts")) { set(item.with("show", it)) }
    }
}

object StorageItem : ItemType("storage", "Storage", Sym.HARD_DRIVE, "Free space on the device") {
    override val refreshMs = 30_000L
    override val canBeActive = true

    override fun state(item: ItemConfig): ItemState {
        val s = Env.storage
        val freeFrac = if (s.total == 0L) 1.0 else s.free.toDouble() / s.total
        return ItemState(icon = Sym.HARD_DRIVE, text = Fmt.bytes(s.free.toDouble()).replace(" ", ""),
            active = freeFrac < 0.1, tone = if (freeFrac < 0.05) Tone.ALERT else if (freeFrac < 0.1) Tone.WARN else Tone.NORMAL,
            desc = "${Fmt.bytes(s.free.toDouble())} free")
    }

    override val menu: @Composable (ItemConfig, MenuHost) -> Unit = { _, host ->
        val s = Env.storage
        val used = if (s.total == 0L) 0.0 else (s.total - s.free).toDouble() / s.total
        MenuCard(Sym.HARD_DRIVE, "Storage", "${Fmt.bytes(s.free.toDouble())} free of ${Fmt.bytes(s.total.toDouble())}") {
            Meter(used.toFloat())
            InfoRow("Used", Fmt.bytes((s.total - s.free).toDouble()))
            InfoRow("Free", Fmt.bytes(s.free.toDouble()))
            MenuDivider()
            MenuEntry(Sym.HARD_DRIVE, "Storage settings") { host.close(); Env.launch(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) }
            MenuEntry(Sym.DELETE, "Free up space") { host.close(); Env.launch(Intent(StorageManager.ACTION_MANAGE_STORAGE)) }
        }
    }
}
