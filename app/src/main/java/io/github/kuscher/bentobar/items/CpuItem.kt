package io.github.kuscher.bentobar.items

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.kuscher.bentobar.R
import io.github.kuscher.bentobar.data.ItemConfig
import io.github.kuscher.bentobar.ui.CoreBars
import io.github.kuscher.bentobar.ui.InfoRow
import io.github.kuscher.bentobar.ui.MenuCard
import io.github.kuscher.bentobar.ui.MenuDivider
import io.github.kuscher.bentobar.ui.MenuEntry
import io.github.kuscher.bentobar.ui.MenuNote
import io.github.kuscher.bentobar.ui.SectionLabel
import io.github.kuscher.bentobar.ui.Sparkline
import io.github.kuscher.bentobar.ui.rememberTick
import io.github.kuscher.bentobar.util.Fmt
import io.github.kuscher.bentobar.util.Sym
import java.util.Locale

// The CPU item, in a file of its own (it was in SystemItems.kt).

object CpuItem : ItemType("cpu", R.string.item_cpu_title, Sym.MEMORY, R.string.item_cpu_desc) {
    override val canBeActive = true
    private val above = Threshold("activePct", 80, 30..99) { "$it%" }
    override val trigger = Trigger(R.string.trigger_cpu, R.string.trigger_cpu_short, above)

    override fun state(item: ItemConfig): ItemState {
        val c = Env.cpu
        if (!c.available) return ItemState(icon = Sym.MEMORY, text = "–", desc = Env.str(R.string.cpu_unavailable_desc))
        val limit = above.of(item) / 100.0
        return ItemState(icon = Sym.MEMORY, text = Fmt.percent(c.total), active = c.total >= limit,
            tone = if (c.total >= 0.9) Tone.WARN else Tone.NORMAL, widthKey = "cpu",
            desc = Env.str(if (c.total >= 0.9) R.string.cpu_state_desc_high else R.string.cpu_state_desc, Fmt.percent(c.total)))
    }

    override val menu: @Composable (ItemConfig, MenuHost) -> Unit = { _, host ->
        rememberTick()
        val c = Env.cpu
        androidx.compose.runtime.DisposableEffect(Unit) { c.detail++; onDispose { c.detail-- } }
        MenuCard(Sym.MEMORY, stringResource(R.string.cpu_menu_title),
            if (c.available) pluralStringResource(R.plurals.cpu_menu_subtitle, c.perCore.size, Fmt.percent(c.total), c.perCore.size)
            else stringResource(R.string.cpu_menu_unavailable)) {
            if (c.available) {
                InfoRow(stringResource(R.string.cpu_overall), Fmt.percent(c.total), MaterialTheme.colorScheme.primary)
                Sparkline(c.history.toList(), max = 1.0)
                Spacer(Modifier.height(8.dp))
                SectionLabel(stringResource(R.string.cpu_cores))
                CoreBars(c.perCore)
                Spacer(Modifier.height(6.dp))
                c.clusters.forEach { cl ->
                    // "Cores 0–3" for a group; a core with its own clock speed (each one, on some Intel
                    // chips) is "Core 0".
                    InfoRow(stringResource(if (cl.cores.all { it.isDigit() }) R.string.cpu_core_one else R.string.cpu_cluster, cl.cores), stringResource(R.string.cpu_cluster_clock,
                        String.format(Locale.ROOT, "%.2f", cl.curKhz / 1e6), String.format(Locale.ROOT, "%.2f", cl.maxKhz / 1e6)))
                }
                c.gpu?.let { g ->
                    MenuDivider()
                    InfoRow(stringResource(R.string.cpu_gpu), Fmt.percent(g), MaterialTheme.colorScheme.tertiary)
                    Sparkline(c.gpuHistory.toList(), color = MaterialTheme.colorScheme.tertiary, max = 1.0)
                }
            }
            Spacer(Modifier.height(6.dp))
            MenuNote(stringResource(R.string.cpu_per_app_note))
            MenuDivider()
            MenuEntry(Sym.BOLT, stringResource(R.string.cpu_battery_usage)) { host.close(); Env.launch(Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) }
            MenuEntry(Sym.APPS, stringResource(R.string.common_apps)) { host.close(); Env.launch(Intent(Settings.ACTION_APPLICATION_SETTINGS)) }
            // Developer options only when the user has turned them on.
            val dev = androidx.compose.runtime.remember { developerOptionsOn() }
            if (dev) MenuEntry(Sym.BUILD, stringResource(R.string.cpu_developer_options)) {
                host.close(); Env.launch(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            }
        }
    }
}

/** Whether the user has turned on Developer options (a global setting any app may read). */
private fun developerOptionsOn(): Boolean = runCatching {
    Settings.Global.getInt(Env.app.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
}.getOrDefault(false)
