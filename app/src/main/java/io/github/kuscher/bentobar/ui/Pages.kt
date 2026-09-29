package io.github.kuscher.bentobar.ui

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kuscher.bentobar.BuildConfig
import io.github.kuscher.bentobar.data.ChipMode
import io.github.kuscher.bentobar.data.ColorMode
import io.github.kuscher.bentobar.data.Pill
import io.github.kuscher.bentobar.data.Position
import io.github.kuscher.bentobar.data.Section
import io.github.kuscher.bentobar.data.Store
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
        "System" to listOf("cpu", "network", "memory", "battery", "storage"),
        "Time" to listOf("calendar", "event", "clock", "timer", "countdown"),
        "Tools" to listOf("caffeine", "sound", "tools", "folder", "app", "text", "spacer"),
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp)) {
        groups.forEach { (group, types) ->
            SectionLabel(group)
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
                                    if (count > 0) Text(if (count == 1) "In your bar" else "$count in your bar", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(type.blurb, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                minLines = 2)
                            Spacer(Modifier.height(10.dp))
                            Row {
                                FilledTonalButton(onClick = { onAdded(Store.add(type.type, Section.SHOWN, type.defaultOptions())) }) {
                                    SymIcon(Sym.ADD, size = 18.sp); Spacer(Modifier.width(6.dp)); Text("Add")
                                }
                                Spacer(Modifier.width(8.dp))
                                TextButton(onClick = { onAdded(Store.add(type.type, Section.HIDDEN, type.defaultOptions())) }) { Text("Add hidden") }
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
    Page {
        SectionLabel("Placement")
        ChoiceRow("Where BentoBar sits", listOf(Position.RIGHT to "Next to the system icons", Position.CENTER to "Centre", Position.LEFT to "After the clock"),
            cfg.position) { p -> Store.update { it.copy(position = p) } }
        SliderRow("Space between items", cfg.spacing, 0..24, { "$it dp" }) { v -> Store.update { it.copy(spacing = v) } }
        SectionLabel("Look")
        ChoiceRow("Text size", listOf(TextSize.SMALL to "Small", TextSize.DEFAULT to "Like the system", TextSize.LARGE to "Large"), cfg.textSize) { v ->
            Store.update { it.copy(textSize = v) }
        }
        ChoiceRow("Colour", listOf(ColorMode.AUTO to "Match the status bar", ColorMode.LIGHT to "Light", ColorMode.DARK to "Dark"), cfg.color,
            help = "Matching reads the colour of the status bar clock") { v -> Store.update { it.copy(color = v) } }
        ChoiceRow("Background", listOf(Pill.NONE to "None, like the system", Pill.SUBTLE to "Soft pill", Pill.SOLID to "Solid pill"), cfg.pill) { v ->
            Store.update { it.copy(pill = v) }
        }
        SectionLabel("Hidden items")
        SwitchRow("Show the ‹ button", cfg.chevron,
            help = "Click it to show or hide your hidden items. Without it, hidden items only appear while active.") { v ->
            Store.update { it.copy(chevron = v) }
        }
        ChoiceRow("After revealing, hide them again", listOf(0 to "Only when I click ‹", 5 to "After 5 s", 10 to "After 10 s", 30 to "After 30 s"),
            if (cfg.autoCollapseSec in listOf(0, 5, 10, 30)) cfg.autoCollapseSec else 0) { v -> Store.update { it.copy(autoCollapseSec = v) } }
        SwitchRow("Reveal them when the pointer rests on BentoBar", cfg.revealOnHover) { v -> Store.update { it.copy(revealOnHover = v) } }
        SectionLabel("Live Update chip")
        ChoiceRow("Timer or meeting chip", listOf(ChipMode.OFF to "Off", ChipMode.FALLBACK to "When BentoBar is off", ChipMode.ALWAYS to "Always"),
            cfg.chipMode, help = "Android shows one chip per app next to the system icons, and hides it while that app's own window is open. BentoBar uses it for a running timer, or a meeting that starts within 15 minutes.") { v ->
            Store.update { it.copy(chipMode = v) }
        }
        SectionLabel("Backup")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = {
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("BentoBar settings", Store.export()))
                Toast.makeText(context, "Settings copied", Toast.LENGTH_SHORT).show()
            }) { Text("Copy settings") }
            OutlinedButton(onClick = {
                val text = context.getSystemService(ClipboardManager::class.java)?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                Toast.makeText(context, if (Store.import(text)) "Settings restored" else "The clipboard doesn't hold BentoBar settings", Toast.LENGTH_SHORT).show()
            }) { Text("Paste settings") }
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
                    if (optional) Text("  optional", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    if (done) Text("  done", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
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

@Composable
fun SetupPage(activity: Activity, running: Boolean) {
    val granted = { p: String -> activity.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED }
    if (running && !Store.config.value.onboarded) Store.update { it.copy(onboarded = true) }
    Page {
        if (Env.advancedProtection()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text("Advanced Protection is on", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                    Text("It allows only assistive accessibility services, so BentoBar can't run. The Live Update chip " +
                        "and the Quick Settings tiles still work.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
        Step(1, "Turn on BentoBar", running) {
            Body("Android lets apps draw on the status bar only through an accessibility service, so turning BentoBar on " +
                "happens in Accessibility settings. Android will say BentoBar can \"view and control your screen\"; here is what it actually does with that:")
            Spacer(Modifier.height(6.dp))
            Bullet("reads the layout of the status bar, and nothing else, to find free space for your items")
            Bullet("copies the colour of the status bar clock so your items match it")
            Bullet("runs system actions (screenshot, lock, overview…) when you pick them in the Tools menu")
            Spacer(Modifier.height(6.dp))
            Body("It doesn't read other apps' windows, doesn't watch your keyboard, mouse or touches, and has no internet access: nothing leaves your device.")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = { MainActivity.openAccessibility(activity) }) { Text(if (running) "Accessibility settings" else "Turn on") }
                TextButton(onClick = { MainActivity.openAppInfo(activity) }) { Text("App info") }
            }
            if (!running) {
                Spacer(Modifier.height(10.dp))
                Text("Installed BentoBar from a download? Android guards this switch for such apps:", style = MaterialTheme.typography.bodyMedium)
                Bullet("In Accessibility, open BentoBar and tap the switch. Android says \"Restricted setting\"; tap OK.")
                Bullet("Tap App info above, then ⋮ (top right) › Allow restricted settings, and confirm with your PIN.")
                Bullet("Come back to Accessibility › BentoBar and turn it on.")
            }
            Spacer(Modifier.height(8.dp))
            Text("About a day later, Android asks you to review apps with full device access. That's a standard check for every app " +
                "like this; keep BentoBar if you're happy with what it does.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Step(2, "Notifications", Notify.allowed(activity)) {
            Body("For timer alerts, and the Live Update chip that shows a running timer when BentoBar is off.")
            if (!Notify.allowed(activity)) FilledTonalButton(onClick = { activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 3) },
                modifier = Modifier.padding(top = 8.dp)) { Text("Allow") }
        }
        val nm = activity.getSystemService(NotificationManager::class.java)
        val promoted = runCatching { nm?.canPostPromotedNotifications() == true }.getOrDefault(false)
        Step(3, "Live Updates", promoted, optional = true) {
            Body("Lets Android show BentoBar's chip in the status bar. On by default; you can turn it off in Android's settings.")
            TextButton(onClick = {
                if (!Env.launch(Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)))
                    Env.launch(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName))
            }) { Text("Open setting") }
        }
        Step(4, "Calendar", granted(Manifest.permission.READ_CALENDAR), optional = true) {
            Body("For the Next meeting item and the events in the month view. Read on this device only.")
            if (!granted(Manifest.permission.READ_CALENDAR)) FilledTonalButton(onClick = { activity.requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), 4) },
                modifier = Modifier.padding(top = 8.dp)) { Text("Allow") }
        }
        val am = activity.getSystemService(AlarmManager::class.java)
        val exact = am?.canScheduleExactAlarms() == true
        Step(5, "Alarms and reminders", exact, optional = true) {
            Body("Lets timers ring on the second even while the Googlebook sleeps. Without it they may be a little late.")
            if (!exact) TextButton(onClick = {
                Env.launch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + activity.packageName)))
            }) { Text("Allow") }
        }
    }
}

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
                Text("BentoBar ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
                Text("Add, hide and organise items in your Googlebook's status bar.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Called DiscoBar until version 0.4.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SectionLabel("Privacy")
        Body("BentoBar has no internet permission. Your settings, calendar events and everything it measures stay on this device. " +
            "Its accessibility service reads only the status bar (its layout, and the colour of its clock), never other apps.")
        SectionLabel("How it works")
        Body("Android doesn't let apps change the system's own status bar icons. BentoBar draws its items in the empty part of the " +
            "status bar, in windows of its own, and moves them out of the way when the system's icons change or an app goes full screen. " +
            "Your running timer can also appear as an official Android Live Update chip.")
        SectionLabel("Open source")
        Body("BentoBar is free software under the MIT License.")
        Spacer(Modifier.height(4.dp))
        Bullet("Material Symbols, © Google, Apache License 2.0")
        Bullet("Jetpack Compose and AndroidX, © The Android Open Source Project, Apache License 2.0")
        Bullet("Kotlin and kotlinx.serialization, © JetBrains, Apache License 2.0")
        SectionLabel("Who made this")
        Body("BentoBar is a personal hobby project by Alexander Kuscher (github.com/kuscher), proudly developed entirely on a Googlebook.")
        Spacer(Modifier.height(6.dp))
        Body("It isn't affiliated with the author's employer: that employer didn't make, sponsor or endorse it, and BentoBar doesn't " +
            "endorse that employer or its products either. The views and choices in it are the author's own.")
        SectionLabel("Not affiliated")
        Body("BentoBar is an independent project, not made by or affiliated with Google, or with Surtees Studios (Bartender) or Bjango (iStat Menus).")
    }
}
