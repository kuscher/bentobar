package io.github.kuscher.bentobar.ui

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.Switch
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.kuscher.bentobar.BuildConfig
import io.github.kuscher.bentobar.R
import io.github.kuscher.bentobar.data.ChipMode
import io.github.kuscher.bentobar.data.ColorMode
import io.github.kuscher.bentobar.data.Pill
import io.github.kuscher.bentobar.data.Position
import io.github.kuscher.bentobar.data.Section
import io.github.kuscher.bentobar.data.Store
import io.github.kuscher.bentobar.data.Uses
import io.github.kuscher.bentobar.data.TextSize
import io.github.kuscher.bentobar.items.Env
import io.github.kuscher.bentobar.items.Items
import io.github.kuscher.bentobar.items.Notify
import io.github.kuscher.bentobar.util.Sym
import io.github.kuscher.bentobar.util.SymIcon

@Composable
private fun Page(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp)) {
        Column(Modifier.widthIn(max = 820.dp)) { content() }
        Spacer(Modifier.height(24.dp))
    }
}

// ---- Add -----------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddPage(onAdded: (String) -> Unit) {
    val cfg by Store.config.collectAsState()
    val groups = listOf(
        R.string.add_group_system to listOf("cpu", "network", "memory", "battery", "storage"),
        R.string.add_group_time to listOf("calendar", "event", "clock", "timer", "countdown"),
        R.string.add_group_tools to listOf("caffeine", "sound", "tools", "folder", "app", "text", "spacer"),
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp)) {
        groups.forEach { (group, types) ->
            SectionLabel(stringResource(group))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                types.mapNotNull { Items.of(it) }.forEach { type ->
                    val count = cfg.items.count { it.type == type.type }
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.width(280.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center) {
                                    SymIcon(type.icon, size = 22.sp, filled = true, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(type.title, style = MaterialTheme.typography.titleMedium)
                                    if (count > 0) Text(pluralStringResource(R.plurals.add_in_your_bar, count, count), style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(type.blurb, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                minLines = 2)
                            Spacer(Modifier.height(10.dp))
                            Row {
                                FilledTonalButton(onClick = { onAdded(Store.add(type.type, Section.SHOWN, type.defaultOptions())) }) {
                                    SymIcon(Sym.ADD, size = 18.sp); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.add_add))
                                }
                                Spacer(Modifier.width(8.dp))
                                TextButton(onClick = { onAdded(Store.add(type.type, Section.HIDDEN, type.defaultOptions())) }) { Text(stringResource(R.string.add_add_hidden)) }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ---- Look ----------------------------------------------------------------------------------

@Composable
fun LookPage() {
    val cfg by Store.config.collectAsState()
    val context = LocalContext.current
    val res = androidx.compose.ui.platform.LocalResources.current
    Page {
        SectionLabel(stringResource(R.string.look_placement))
        ChoiceRow(stringResource(R.string.look_position), listOf(Position.RIGHT to stringResource(R.string.look_position_right),
            Position.CENTER to stringResource(R.string.look_position_center), Position.LEFT to stringResource(R.string.look_position_left)),
            cfg.position) { p -> Store.update { it.copy(position = p) } }
        SliderRow(stringResource(R.string.look_spacing), cfg.spacing, 0..24, { "$it dp" }) { v -> Store.update { it.copy(spacing = v) } }
        SwitchRow(stringResource(R.string.look_presenting), cfg.presenting, help = stringResource(R.string.look_presenting_help)) { on ->
            Store.update { it.copy(presenting = on) }
        }
        SectionLabel(stringResource(R.string.look_look))
        ChoiceRow(stringResource(R.string.look_text_size), listOf(TextSize.SMALL to stringResource(R.string.look_text_small),
            TextSize.DEFAULT to stringResource(R.string.option_like_system), TextSize.LARGE to stringResource(R.string.look_text_large)), cfg.textSize) { v ->
            Store.update { it.copy(textSize = v) }
        }
        ChoiceRow(stringResource(R.string.look_color), listOf(ColorMode.AUTO to stringResource(R.string.look_color_auto),
            ColorMode.LIGHT to stringResource(R.string.look_color_light), ColorMode.DARK to stringResource(R.string.look_color_dark)), cfg.color,
            help = stringResource(R.string.look_color_help)) { v -> Store.update { it.copy(color = v) } }
        ChoiceRow(stringResource(R.string.look_background), listOf(Pill.NONE to stringResource(R.string.look_pill_none),
            Pill.SUBTLE to stringResource(R.string.look_pill_subtle), Pill.SOLID to stringResource(R.string.look_pill_solid)), cfg.pill) { v ->
            Store.update { it.copy(pill = v) }
        }
        SectionLabel(stringResource(R.string.barmenu_hidden_items))
        SwitchRow(stringResource(R.string.look_chevron), cfg.chevron, help = stringResource(R.string.look_chevron_help)) { v ->
            Store.update { it.copy(chevron = v) }
        }
        ChoiceRow(stringResource(R.string.look_collapse), listOf(0 to stringResource(R.string.look_collapse_never),
            5 to pluralStringResource(R.plurals.look_collapse_after, 5, 5), 10 to pluralStringResource(R.plurals.look_collapse_after, 10, 10),
            30 to pluralStringResource(R.plurals.look_collapse_after, 30, 30)),
            if (cfg.autoCollapseSec in listOf(0, 5, 10, 30)) cfg.autoCollapseSec else 0) { v -> Store.update { it.copy(autoCollapseSec = v) } }
        SwitchRow(stringResource(R.string.look_reveal_hover), cfg.revealOnHover) { v -> Store.update { it.copy(revealOnHover = v) } }
        SectionLabel(stringResource(R.string.channel_live))
        ChoiceRow(stringResource(R.string.look_chip), listOf(ChipMode.OFF to stringResource(R.string.common_off),
            ChipMode.FALLBACK to stringResource(R.string.look_chip_fallback), ChipMode.ALWAYS to stringResource(R.string.look_chip_always)),
            cfg.chipMode, help = stringResource(R.string.look_chip_help)) { v ->
            Store.update { it.copy(chipMode = v) }
        }
        SectionLabel(stringResource(R.string.look_backup))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = {
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(res.getString(R.string.look_clip_label), Store.export()))
                Toast.makeText(context, res.getString(R.string.look_copied), Toast.LENGTH_SHORT).show()
            }) { Text(stringResource(R.string.look_copy)) }
            OutlinedButton(onClick = {
                val text = context.getSystemService(ClipboardManager::class.java)?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                Toast.makeText(context, res.getString(if (Store.import(text)) R.string.look_restored else R.string.look_not_settings), Toast.LENGTH_SHORT).show()
            }) { Text(stringResource(R.string.look_paste)) }
        }
    }
}

// ---- Setup ---------------------------------------------------------------------------------

@Composable
private fun Step(n: Int, title: String, done: Boolean, optional: Boolean = false, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(Modifier.padding(18.dp)) {
            Box(Modifier.size(32.dp).clip(CircleShape)
                .background(if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center) {
                if (done) SymIcon(Sym.CHECK, size = 18.sp, color = MaterialTheme.colorScheme.onPrimary)
                else Text("$n", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (optional) { Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.setup_optional), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline) }
                    if (done) { Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.setup_done), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                }
                Spacer(Modifier.height(4.dp))
                content()
            }
        }
    }
}

@Composable
private fun Body(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun Bullet(text: String) = Row(Modifier.padding(start = 4.dp, top = 2.dp)) {
    Text("•  ", style = MaterialTheme.typography.bodyMedium)
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

/**
 * Setup's steps, from [setup] (observed, so they flip to done the moment something changes). One
 * rule for buttons: a step that isn't done has a filled action button; a done step only offers a
 * plain link to manage it.
 */
@Composable
fun SetupPage(activity: Activity, setup: SetupState) {
    val running = setup.serviceOn
    Page {
        if (setup.advancedProtection) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text(stringResource(R.string.setup_aap_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                    Text(stringResource(R.string.setup_aap_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
        Step(1, stringResource(R.string.setup_turn_on_title), running) {
            Body(stringResource(R.string.setup_turn_on_text))
            Spacer(Modifier.height(6.dp))
            Bullet(stringResource(R.string.setup_turn_on_reads))
            Bullet(stringResource(R.string.setup_turn_on_copies))
            Bullet(stringResource(R.string.setup_turn_on_runs))
            Spacer(Modifier.height(6.dp))
            Body(stringResource(R.string.setup_turn_on_doesnt))
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (running) TextButton(onClick = { MainActivity.openAccessibility(activity) }) { Text(stringResource(R.string.setup_accessibility_settings)) }
                else FilledTonalButton(onClick = { MainActivity.openAccessibility(activity) }, enabled = !setup.advancedProtection) { Text(stringResource(R.string.setup_turn_on)) }
                TextButton(onClick = { MainActivity.openAppInfo(activity) }) { Text(stringResource(R.string.setup_app_info)) }
            }
            if (!running) {
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.setup_restricted_intro), style = MaterialTheme.typography.bodyMedium)
                Bullet(stringResource(R.string.setup_restricted_1))
                Bullet(stringResource(R.string.setup_restricted_2))
                Bullet(stringResource(R.string.setup_restricted_3))
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.setup_review_note), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Step(2, stringResource(R.string.setup_notifications_title), setup.notifications) {
            UseSwitch(stringResource(R.string.setup_notifications_text), stringResource(R.string.setup_notifications_title), setup.notifications) { on ->
                setRuntimeUse(activity, Uses.NOTIFICATIONS, Manifest.permission.POST_NOTIFICATIONS, 3, R.string.setup_use_name_notifications, on)
            }
        }
        Step(3, stringResource(R.string.setup_live_title), setup.liveUpdates, optional = true) {
            Body(stringResource(R.string.setup_live_text))
            val open = {
                if (!Env.launch(Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)))
                    Env.launch(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName))
            }
            if (setup.liveUpdates) StepLink(stringResource(R.string.setup_open_setting)) { open() } else StepAction(stringResource(R.string.setup_turn_on)) { open() }
        }
        Step(4, stringResource(R.string.setup_calendar_title), setup.calendar, optional = true) {
            UseSwitch(stringResource(R.string.setup_calendar_text), stringResource(R.string.setup_calendar_title), setup.calendar) { on ->
                setRuntimeUse(activity, Uses.CALENDAR, Manifest.permission.READ_CALENDAR, 4, R.string.setup_use_name_calendar, on)
            }
        }
        Step(5, stringResource(R.string.setup_alarms_title), setup.exactAlarms, optional = true) {
            UseSwitch(stringResource(R.string.setup_alarms_text), stringResource(R.string.setup_alarms_title), setup.exactAlarms) { on ->
                setExactAlarmsUse(activity, on)
            }
        }
    }
}

/**
 * "BentoBar uses this": a switch beside the step's explanation. It reads as on only when Android
 * granted the permission and the user hasn't switched it off here, so a fresh install starts off.
 */
@Composable
private fun UseSwitch(text: String, label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(on, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { Body(text) }
        Spacer(Modifier.width(16.dp))
        Switch(checked = on, onCheckedChange = null, modifier = Modifier.semantics { contentDescription = label })
    }
}

/**
 * On: asks Android if needed. Off: BentoBar stops using it at once and gives the permission back
 * (revokeSelfPermissionOnKill), which Android completes the next time BentoBar's process restarts;
 * the accessibility service keeps it running, so in practice at the next update or reboot.
 */
private fun setRuntimeUse(activity: Activity, key: String, permission: String, request: Int, name: Int, on: Boolean) {
    val granted = activity.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
    if (on) {
        Store.update { it.copy(turnedOff = it.turnedOff - key) }
        if (!granted) activity.requestPermissions(arrayOf(permission), request)
    } else {
        Store.update { it.copy(turnedOff = it.turnedOff + key) }
        if (granted) activity.revokeSelfPermissionOnKill(permission)
        Notice.post(activity.getString(R.string.setup_use_off_runtime, activity.getString(name)))
    }
    Setup.refresh(activity)
}

/** Exact alarms are a special access an app can't give back itself: off stops using it, and says where to remove it. */
private fun setExactAlarmsUse(activity: Activity, on: Boolean) {
    val open = { Env.launch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + activity.packageName))) }
    val can = activity.getSystemService(android.app.AlarmManager::class.java)?.canScheduleExactAlarms() == true
    if (on) {
        Store.update { it.copy(turnedOff = it.turnedOff - Uses.EXACT_ALARMS) }
        if (!can) open()
    } else {
        Store.update { it.copy(turnedOff = it.turnedOff + Uses.EXACT_ALARMS) }
        if (can) Notice.post(activity.getString(R.string.setup_use_off_exact), activity.getString(R.string.common_open_settings)) { open() }
    }
    Setup.refresh(activity)
}

/** A step's main action, while it isn't done. */
@Composable
private fun StepAction(label: String, onClick: () -> Unit) =
    FilledTonalButton(onClick = onClick, modifier = Modifier.padding(top = 8.dp)) { Text(label) }

/** A done step's way to change it. */
@Composable
private fun StepLink(label: String, onClick: () -> Unit) =
    TextButton(onClick = onClick, modifier = Modifier.padding(top = 4.dp)) { Text(label) }

// ---- About ---------------------------------------------------------------------------------

@Composable
fun AboutPage() {
    Page {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(io.github.kuscher.bentobar.R.drawable.ic_bentobar),
                    contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
                Text(stringResource(R.string.about_tagline), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.about_formerly), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SectionLabel(stringResource(R.string.about_privacy))
        Body(stringResource(R.string.about_privacy_text))
        SectionLabel(stringResource(R.string.about_how))
        Body(stringResource(R.string.about_how_text))
        SectionLabel(stringResource(R.string.about_open_source))
        Body(stringResource(R.string.about_open_source_text))
        Spacer(Modifier.height(4.dp))
        Bullet(stringResource(R.string.about_credit_symbols))
        Bullet(stringResource(R.string.about_credit_compose))
        Bullet(stringResource(R.string.about_credit_kotlin))
        SectionLabel(stringResource(R.string.about_who))
        Body(stringResource(R.string.about_who_text))
        Spacer(Modifier.height(6.dp))
        Body(stringResource(R.string.about_employer_text))
        SectionLabel(stringResource(R.string.about_not_affiliated))
        Body(stringResource(R.string.about_not_affiliated_text))
    }
}
