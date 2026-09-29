package io.github.kuscher.bentobar.items

import android.Manifest
import android.content.Intent
import android.provider.AlarmClock
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kuscher.bentobar.data.ItemConfig
import io.github.kuscher.bentobar.data.Store
import io.github.kuscher.bentobar.ui.ChipRow
import io.github.kuscher.bentobar.ui.ChoiceRow
import io.github.kuscher.bentobar.ui.InfoRow
import io.github.kuscher.bentobar.ui.MainActivity
import io.github.kuscher.bentobar.ui.MenuCard
import io.github.kuscher.bentobar.ui.MenuDivider
import io.github.kuscher.bentobar.ui.MenuEntry
import io.github.kuscher.bentobar.ui.Meter
import io.github.kuscher.bentobar.ui.SectionLabel
import io.github.kuscher.bentobar.ui.SliderRow
import io.github.kuscher.bentobar.ui.SwitchRow
import io.github.kuscher.bentobar.ui.TextRow
import io.github.kuscher.bentobar.ui.rememberTick
import io.github.kuscher.bentobar.util.Fmt
import io.github.kuscher.bentobar.util.Sym
import io.github.kuscher.bentobar.util.SymIcon
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

private fun is24(item: ItemConfig) = when (item.opt("hours", "system")) {
    "24" -> true
    "12" -> false
    else -> DateFormat.is24HourFormat(Env.app)
}

private fun timeFormatter(h24: Boolean, seconds: Boolean): DateTimeFormatter =
    DateTimeFormatter.ofPattern(if (h24) (if (seconds) "HH:mm:ss" else "HH:mm") else (if (seconds) "h:mm:ss a" else "h:mm a"), Locale.getDefault())

private fun zoneOf(item: ItemConfig): ZoneId =
    item.options["zone"]?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()

/** "Tokyo" from "Asia/Tokyo". */
private fun cityOf(zone: ZoneId) = zone.id.substringAfterLast('/').replace('_', ' ')

object ClockItem : ItemType("clock", "Clock", Sym.SCHEDULE, "A second clock: seconds, 24-hour or another time zone") {
    override fun state(item: ItemConfig): ItemState {
        val zone = zoneOf(item)
        val time = ZonedDateTime.now(zone).format(timeFormatter(is24(item), item.optBool("seconds", true)))
        val label = item.opt("label", "")
        return ItemState(icon = Sym.SCHEDULE, text = if (label.isBlank()) time else "$label $time", widthKey = "clock",
            desc = "${label.ifBlank { cityOf(zone) }} $time")
    }

    override val menu: @Composable (ItemConfig, MenuHost) -> Unit = { item, host ->
        rememberTick()
        val zones = (listOf(ZoneId.systemDefault()) + Store.config.value.items.filter { it.type == "clock" }.map { zoneOf(it) })
            .distinctBy { it.id }
        val local = ZonedDateTime.now()
        MenuCard(Sym.PUBLIC, "World clock", local.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()))) {
            zones.forEach { z ->
                val t = ZonedDateTime.now(z)
                val diffH = (t.offset.totalSeconds - local.offset.totalSeconds) / 3600.0
                val day = when (t.toLocalDate().compareTo(local.toLocalDate())) { 0 -> ""; 1 -> "Tomorrow · "; else -> if (t.toLocalDate() > local.toLocalDate()) "Tomorrow · " else "Yesterday · " }
                val off = if (diffH == 0.0) "Local" else (if (diffH > 0) "+" else "−") + Fmt.oneDecimal(kotlin.math.abs(diffH)).removeSuffix(".0") + "h"
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (z == ZoneId.systemDefault()) "Here (${cityOf(z)})" else cityOf(z), style = MaterialTheme.typography.bodyLarge)
                        Text(day + off, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(t.format(timeFormatter(is24(item), false)), style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"))
                }
            }
            MenuDivider()
            MenuEntry(Sym.ALARM, "Alarms and timers") { host.close(); Env.launch(Intent(AlarmClock.ACTION_SHOW_ALARMS)) }
            MenuEntry(Sym.SETTINGS, "Date and time settings") { host.close(); Env.launch(Intent(Settings.ACTION_DATE_SETTINGS)) }
        }
    }

    override val options: @Composable (ItemConfig, (ItemConfig) -> Unit) -> Unit = { item, set ->
        ZonePicker(item.options["zone"]) { set(item.with("zone", it)) }
        TextRow("Label", item.opt("label", ""), help = "Shown before the time, e.g. NYC", placeholder = "None") {
            set(item.with("label", it.take(12).ifBlank { null }))
        }
        ChoiceRow("Hours", listOf("system" to "Like the system", "24" to "24-hour", "12" to "12-hour"), item.opt("hours", "system")) {
            set(item.with("hours", it))
        }
        SwitchRow("Seconds", item.optBool("seconds", true)) { set(item.with("seconds", it.toString())) }
    }
}

/** Search-as-you-type over the IANA zones. */
@Composable
fun ZonePicker(current: String?, onPick: (String?) -> Unit) {
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text("Time zone: " + (current?.let { cityOf(ZoneId.of(it)) + " ($it)" } ?: "this device's"), style = MaterialTheme.typography.labelLarge)
        TextRow("Find a city or zone", query, placeholder = "e.g. Tokyo, London, New York") { query = it }
        if (query.length >= 2) {
            val q = query.trim().replace(' ', '_').lowercase(Locale.ROOT)
            val hits = ZoneId.getAvailableZoneIds().filter { it.lowercase(Locale.ROOT).contains(q) && it.contains('/') }.sorted().take(8)
            hits.forEach { id -> MenuEntry(Sym.PUBLIC, cityOf(ZoneId.of(id)), id) { onPick(id); query = "" } }
            if (hits.isEmpty()) Text("No zone matches", style = MaterialTheme.typography.bodySmall)
        }
        if (current != null) TextButton(onClick = { onPick(null) }) { Text("Use this device's time zone") }
    }
}

object CalendarItem : ItemType("calendar", "Date and calendar", Sym.CALENDAR_MONTH, "Today's date; click for a month view with your events") {
    override val refreshMs = 10_000L

    override fun state(item: ItemConfig): ItemState {
        val today = LocalDate.now()
        val text = runCatching { today.format(DateTimeFormatter.ofPattern(item.opt("format", "EEE d MMM"), Locale.getDefault())) }
            .getOrDefault(today.toString())
        return ItemState(icon = Sym.CALENDAR_MONTH, text = text,
            desc = today.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.getDefault())))
    }

    override val menu: @Composable (ItemConfig, MenuHost) -> Unit = { item, host -> MonthMenu(item, host) }

    override val options: @Composable (ItemConfig, (ItemConfig) -> Unit) -> Unit = { item, set ->
        ChoiceRow("Date in the bar", listOf("EEE d MMM" to "Sun 28 Sep", "d MMM" to "28 Sep", "EEEE" to "Sunday",
            "'W'w" to "Week number", "d.M." to "28.9."), item.opt("format", "EEE d MMM")) { set(item.with("format", it)) }
        TextRow("Or your own pattern", item.opt("format", "EEE d MMM"),
            help = "Java date pattern, e.g. EEE d MMM yyyy") { if (it.isNotBlank()) set(item.with("format", it)) }
        SwitchRow("Week numbers in the month view", item.optBool("weeks", true)) { set(item.with("weeks", it.toString())) }
    }
}

@Composable
private fun MonthMenu(item: ItemConfig, host: MenuHost) {
    rememberTick()
    val today = LocalDate.now()
    var offset by remember { mutableIntStateOf(0) }
    var picked by remember { mutableStateOf(today) }
    val month = YearMonth.from(today).plusMonths(offset.toLong())
    val zone = ZoneId.systemDefault()
    val firstDow = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val lead = ((month.atDay(1).dayOfWeek.value - firstDow.value) + 7) % 7
    val allowed = Calendar.allowed()
    if (allowed) Calendar.refresh()
    fun eventsOn(d: LocalDate): List<Calendar.Event> {
        val s = d.atStartOfDay(zone).toInstant().toEpochMilli()
        val e = d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return Calendar.on(s, e, d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    }
    val weekOf = WeekFields.of(Locale.getDefault()).weekOfWeekBasedYear()
    MenuCard(Sym.CALENDAR_MONTH, today.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())),
        "Week ${today.get(weekOf)}") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + month.year,
                style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            SmallIconButton(Sym.CHEVRON_LEFT) { offset-- }
            if (offset != 0) TextButton(onClick = { offset = 0; picked = today }) { Text("Today") }
            SmallIconButton(Sym.CHEVRON_RIGHT) { offset++ }
        }
        val weeks = item.optBool("weeks", true)
        Row(Modifier.fillMaxWidth()) {
            if (weeks) Text("", Modifier.width(24.dp))
            for (i in 0 until 7) {
                val dow = firstDow.plus(i.toLong())
                Text(dow.getDisplayName(TextStyle.NARROW, Locale.getDefault()), Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val cells = lead + month.lengthOfMonth()
        val rows = (cells + 6) / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val firstDay = month.atDay(1).plusDays((r * 7 - lead).toLong())
                if (weeks) Text(firstDay.get(weekOf).toString(), Modifier.width(24.dp),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                for (c in 0 until 7) {
                    val i = r * 7 + c - lead
                    Box(Modifier.weight(1f).height(30.dp), contentAlignment = Alignment.Center) {
                        if (i in 0 until month.lengthOfMonth()) {
                            val d = month.atDay(i + 1)
                            val isToday = d == today
                            val isPicked = d == picked
                            val has = allowed && eventsOn(d).isNotEmpty()
                            Column(
                                Modifier.size(28.dp).clip(CircleShape)
                                    .background(when { isToday -> MaterialTheme.colorScheme.primary; isPicked -> MaterialTheme.colorScheme.secondaryContainer; else -> Color.Transparent })
                                    .clickable { picked = d }.pointerHoverIcon(PointerIcon.Hand),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                            ) {
                                Text("${i + 1}", style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                                    color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
                                if (has) Box(Modifier.size(4.dp).clip(CircleShape).background(if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary))
                            }
                        }
                    }
                }
            }
        }
        MenuDivider()
        if (!allowed) {
            Text("Allow calendar access to see your events here.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            MenuEntry(Sym.EVENT, "Allow calendar access") { host.close(); MainActivity.requestPermission(Env.app, Manifest.permission.READ_CALENDAR) }
        } else {
            SectionLabel(if (picked == today) "Today" else picked.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())))
            val list = eventsOn(picked)
            if (list.isEmpty()) Text("No events", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            list.take(6).forEach { e -> EventRow(e) { host.close(); Calendar.open(e) } }
        }
        MenuEntry(Sym.OPEN_IN_NEW, "Open Calendar") {
            host.close(); Calendar.openDay(picked.atTime(9, 0).atZone(zone).toInstant().toEpochMilli())
        }
    }
}

@Composable
private fun SmallIconButton(sym: String, onClick: () -> Unit) {
    Box(Modifier.size(28.dp).clip(CircleShape).clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center) { SymIcon(sym, size = 18.sp) }
}

private val shortTime get() = DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(Env.app)) "HH:mm" else "h:mm a", Locale.getDefault())

@Composable
fun EventRow(e: Calendar.Event, onClick: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val range = if (e.allDay) "All day" else
        Instant.ofEpochMilli(e.begin).atZone(zone).format(shortTime) + " – " + Instant.ofEpochMilli(e.end).atZone(zone).format(shortTime)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand)
        .padding(vertical = 6.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 4.dp, height = 30.dp).clip(RoundedCornerShape(2.dp)).background(Color(e.color or 0xFF000000.toInt())))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(range + if (e.location.isNotBlank() && e.link == null) " · ${e.location}" else "",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
        if (e.link != null) SymIcon(Sym.VIDEOCAM, size = 18.sp, color = MaterialTheme.colorScheme.primary)
    }
}

object EventItem : ItemType("event", "Next meeting", Sym.EVENT_UPCOMING, "Your next calendar event and a countdown, with a Join button") {
    override val refreshMs = 5_000L
    override val canBeActive = true
    override val permissions = listOf(Manifest.permission.READ_CALENDAR)

    override fun state(item: ItemConfig): ItemState {
        if (!Calendar.allowed()) return ItemState(icon = Sym.EVENT_UPCOMING, text = "Allow calendar", desc = "Calendar access needed")
        Calendar.refresh()
        val now = System.currentTimeMillis()
        val max = item.optInt("chars", 20)
        fun short(t: String) = if (t.length <= max) t else t.take(max - 1).trimEnd() + "…"
        val soon = item.optInt("soonMin", 15) * 60_000L
        Calendar.current(now)?.let { e ->
            return ItemState(icon = Sym.EVENT, filled = true, text = "${short(e.title)} · ${Fmt.duration(e.end - now)} left",
                active = true, tone = Tone.ACCENT, desc = "Now: ${e.title}, ends in ${Fmt.duration(e.end - now)}")
        }
        val next = Calendar.next(now) ?: return ItemState(icon = Sym.EVENT_UPCOMING, text = "No more events", desc = "No upcoming events")
        val until = next.begin - now
        val sameDay = LocalDate.now() == Instant.ofEpochMilli(next.begin).atZone(ZoneId.systemDefault()).toLocalDate()
        val whenText = when {
            until < 60 * 60_000L -> "in ${Fmt.duration(until.coerceAtLeast(60_000))}"
            sameDay -> Instant.ofEpochMilli(next.begin).atZone(ZoneId.systemDefault()).format(shortTime)
            else -> Instant.ofEpochMilli(next.begin).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))
        }
        return ItemState(icon = Sym.EVENT_UPCOMING, text = "${short(next.title)} $whenText", active = until <= soon,
            tone = if (until <= 5 * 60_000L) Tone.ACCENT else Tone.NORMAL, desc = "Next: ${next.title} $whenText")
    }

    override val menu: @Composable (ItemConfig, MenuHost) -> Unit = { _, host ->
        rememberTick()
        val now = System.currentTimeMillis()
        if (!Calendar.allowed()) {
            MenuCard(Sym.EVENT_UPCOMING, "Next meeting", "BentoBar needs to read your calendar for this") {
                MenuEntry(Sym.EVENT, "Allow calendar access") { host.close(); MainActivity.requestPermission(Env.app, Manifest.permission.READ_CALENDAR) }
            }
        } else {
            val cur = Calendar.current(now)
            val next = Calendar.next(now)
            val focus = cur ?: next
            MenuCard(Sym.EVENT_UPCOMING, focus?.title ?: "Nothing coming up",
                when {
                    cur != null -> "Now · ends in ${Fmt.duration(cur.end - now)}"
                    next != null -> "Starts in ${Fmt.duration(next.begin - now)}"
                    else -> null
                }) {
                if (focus != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (focus.link != null) FilledTonalButton(onClick = { host.close(); Calendar.join(focus) }) {
                        SymIcon(Sym.VIDEOCAM, size = 18.sp); Spacer(Modifier.width(8.dp)); Text("Join")
                    }
                    OutlinedButton(onClick = { host.close(); Calendar.open(focus) }) { Text("Open") }
                }
                MenuDivider()
                SectionLabel("Coming up")
                val upcoming = Calendar.timed(now).filter { it != focus }.take(6)
                if (upcoming.isEmpty()) Text("Nothing else in the next weeks", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                var lastDay: LocalDate? = null
                upcoming.forEach { e ->
                    val d = Instant.ofEpochMilli(e.begin).atZone(ZoneId.systemDefault()).toLocalDate()
                    if (d != lastDay && d != LocalDate.now()) {
                        Text(d.format(DateTimeFormatter.ofPattern("EEEE d MMM", Locale.getDefault())), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                    }
                    lastDay = d
                    EventRow(e) { host.close(); Calendar.open(e) }
                }
            }
        }
    }

    override val options: @Composable (ItemConfig, (ItemConfig) -> Unit) -> Unit = { item, set ->
        SliderRow("Counts as active from", item.optInt("soonMin", 15), 1..60, { "$it min before" }) { set(item.with("soonMin", it.toString())) }
        SliderRow("Longest title", item.optInt("chars", 20), 8..40, { "$it characters" }) { set(item.with("chars", it.toString())) }
    }
}

object TimerItem : ItemType("timer", "Timer", Sym.TIMER, "Countdown, stopwatch and Pomodoro; scroll to add minutes") {
    override val canBeActive = true

    override fun state(item: ItemConfig): ItemState {
        val s = Timers.state.value
        val now = Timers.now()
        if (s == null) {
            val done = Timers.finishedAt != 0L && now - Timers.finishedAt < 60_000
            return if (done) ItemState(icon = Sym.TIMER, filled = true, text = "Done", active = true, tone = Tone.ALERT, desc = "Timer finished")
            else ItemState(icon = Sym.TIMER, desc = "Timer")
        }
        return when (s.mode) {
            Timers.Mode.STOPWATCH -> ItemState(icon = if (s.running) Sym.AVG_PACE else Sym.PAUSE, filled = true,
                text = Fmt.clock(Timers.elapsed(s, now)), active = true, widthKey = "stopwatch",
                desc = "Stopwatch ${Fmt.clock(Timers.elapsed(s, now))}")
            else -> {
                val left = Timers.remaining(s, now)
                val onBreak = s.mode == Timers.Mode.POMODORO && s.phase != Timers.Phase.WORK
                ItemState(icon = if (!s.running) Sym.PAUSE else if (onBreak) Sym.COFFEE else Sym.TIMER, filled = true,
                    text = Fmt.clock(left), active = true, tone = if (left < 60_000 && s.running) Tone.ACCENT else Tone.NORMAL,
                    widthKey = "countdown",
                    desc = "${if (onBreak) "Break" else "Timer"} ${Fmt.clock(left)} left")
            }
        }
    }

    override fun onScroll(item: ItemConfig, steps: Int) {
        val s = Timers.state.value
        if (s == null) Timers.startTimer(steps.coerceAtLeast(1) * 60_000L) else Timers.add(steps * 60_000L)
    }

    override val menu: @Composable (ItemConfig, MenuHost) -> Unit = { item, host ->
        rememberTick()
        val s = Timers.state.collectAsStateValue()
        if (s == null) {
            var custom by remember { mutableStateOf("") }
            MenuCard(Sym.TIMER, "Timer", "Scroll over the timer in the bar to add minutes") {
                SectionLabel("Countdown")
                val presets = listOf(1, 3, 5, 10, 15, 25, 45, 60)
                ChipRow(presets.map { if (it < 60) "$it min" else "1 hour" }) { i -> Timers.startTimer(presets[i] * 60_000L, item.opt("label", "")) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { TextRow("Minutes", custom, numeric = true) { custom = it.filter(Char::isDigit).take(4) } }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(onClick = { custom.toIntOrNull()?.takeIf { it > 0 }?.let { Timers.startTimer(it * 60_000L, item.opt("label", "")) } },
                        enabled = (custom.toIntOrNull() ?: 0) > 0) { Text("Start") }
                }
                MenuDivider()
                MenuEntry(Sym.AVG_PACE, "Stopwatch") { Timers.startStopwatch() }
                MenuEntry(Sym.COFFEE, "Pomodoro", "25 + 5 min") { Timers.startPomodoro() }
            }
        } else {
            val now = Timers.now()
            val title = when (s.mode) {
                Timers.Mode.STOPWATCH -> "Stopwatch"
                Timers.Mode.POMODORO -> if (s.phase == Timers.Phase.WORK) "Focus" else "Break"
                Timers.Mode.TIMER -> s.label.ifBlank { "Timer" }
            }
            val sub = when (s.mode) {
                Timers.Mode.POMODORO -> "Round ${s.round + if (s.phase == Timers.Phase.WORK) 1 else 0} · long break every 4"
                Timers.Mode.STOPWATCH -> if (s.running) "Running" else "Paused"
                Timers.Mode.TIMER -> if (s.running) "Ends at " + Instant.ofEpochMilli(s.at).atZone(ZoneId.systemDefault()).format(shortTime) else "Paused"
            }
            MenuCard(if (s.mode == Timers.Mode.STOPWATCH) Sym.AVG_PACE else Sym.TIMER, title, sub) {
                val face = if (s.mode == Timers.Mode.STOPWATCH) Timers.elapsed(s, now) else Timers.remaining(s, now)
                Text(Fmt.clock(face), style = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = "tnum"),
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                if (s.mode != Timers.Mode.STOPWATCH && s.lengthMs > 0) {
                    Meter(1f - Timers.remaining(s, now).toFloat() / s.lengthMs)
                    Spacer(Modifier.height(10.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(onClick = { Timers.toggle() }, modifier = Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) {
                        SymIcon(if (s.running) Sym.PAUSE else Sym.PLAY_ARROW, size = 18.sp); Spacer(Modifier.width(6.dp))
                        Text(if (s.running) "Pause" else "Resume", maxLines = 1)
                    }
                    if (s.mode != Timers.Mode.STOPWATCH) OutlinedButton(onClick = { Timers.add(60_000) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) { Text("+1 min", maxLines = 1) }
                    androidx.compose.material3.OutlinedIconButton(onClick = { Timers.stop() }) {
                        SymIcon(if (s.mode == Timers.Mode.STOPWATCH) Sym.RESTART_ALT else Sym.STOP, size = 20.sp)
                    }
                }
            }
        }
    }

    override val options: @Composable (ItemConfig, (ItemConfig) -> Unit) -> Unit = { item, set ->
        TextRow("Name for countdowns", item.opt("label", ""), placeholder = "Timer", help = "Shown in the alert when it ends") {
            set(item.with("label", it.take(30).ifBlank { null }))
        }
    }
}

object CountdownItem : ItemType("countdown", "Countdown", Sym.HOURGLASS_TOP, "Days and hours until a date you pick") {
    override val refreshMs = 10_000L
    override val canBeActive = true

    fun target(item: ItemConfig): LocalDateTime? = runCatching {
        LocalDateTime.parse(item.opt("at", ""), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    }.getOrNull()

    override fun state(item: ItemConfig): ItemState {
        val t = target(item) ?: return ItemState(icon = Sym.HOURGLASS_TOP, text = "Set a date", desc = "Countdown without a date")
        val left = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() - System.currentTimeMillis()
        val label = item.opt("label", "")
        val text = if (left <= 0) "${label.ifBlank { "Now" }} ✓" else (if (label.isBlank()) "" else "$label ") + Fmt.duration(left)
        return ItemState(icon = Sym.HOURGLASS_TOP, text = text.trim(), active = left in 0..86_400_000L,
            desc = "${label.ifBlank { "Countdown" }}: ${Fmt.duration(left.coerceAtLeast(0))} left")
    }

    override val menu: @Composable (ItemConfig, MenuHost) -> Unit = { item, host ->
        rememberTick()
        val t = target(item)
        MenuCard(Sym.HOURGLASS_TOP, item.opt("label", "Countdown").ifBlank { "Countdown" },
            t?.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, HH:mm", Locale.getDefault()))) {
            if (t != null) {
                val left = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() - System.currentTimeMillis()
                InfoRow("Time left", if (left > 0) Fmt.duration(left) else "Done")
                InfoRow("Days", (left / 86_400_000L).coerceAtLeast(0).toString())
            }
            MenuDivider()
            MenuEntry(Sym.EDIT, "Change date") { host.openItemSettings(item.id) }
        }
    }

    override val options: @Composable (ItemConfig, (ItemConfig) -> Unit) -> Unit = { item, set ->
        TextRow("Label", item.opt("label", ""), placeholder = "e.g. Launch") { set(item.with("label", it.take(16).ifBlank { null })) }
        TextRow("Date and time", item.opt("at", ""), placeholder = "2026-12-24 18:00", help = "Year-month-day hour:minute") {
            set(item.with("at", it.trim().ifBlank { null }))
        }
    }

    override fun defaultOptions() = mapOf("at" to LocalDate.now().plusDays(30).atTime(9, 0)
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
}

@Composable
private fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectAsStateValue(): T = collectAsState().value
