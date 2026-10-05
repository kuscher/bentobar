package io.github.kuscher.bentobar.items

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.kuscher.bentobar.R
import io.github.kuscher.bentobar.ui.CopyEntry
import io.github.kuscher.bentobar.ui.MenuEntry
import io.github.kuscher.bentobar.ui.SectionLabel
import io.github.kuscher.bentobar.ui.SmallIconButton
import io.github.kuscher.bentobar.util.Dates
import io.github.kuscher.bentobar.util.Sym
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * Plan a time's arithmetic, apart from its drawing so that it is unit-tested on the JVM
 * (`PlanATimeTest`): the slider's quarter hours, the days it reaches, and which moment a chosen time
 * of a chosen day is, also on the two days a year when the clocks change. No Android in it.
 */
object PlanATime {
    /** The slider's last stop: the day in quarter hours runs from 0 (00:00) to this (23:45). */
    const val LAST_QUARTER = 95
    /** How far the day can be stepped: from yesterday to two weeks ahead. */
    const val DAYS_BACK = 1L
    const val DAYS_AHEAD = 14L

    /** A moment being planned: a day of the device's calendar and a quarter hour of that day. */
    @androidx.compose.runtime.Immutable
    data class Plan(val date: LocalDate, val quarter: Int)

    /** The quarter hour [time] is in: where the slider stands at rest. */
    fun quarter(time: LocalTime): Int = (time.hour * 60 + time.minute) / 15

    /** The slider was moved to [quarter]: that time of the planned day, or of today when nothing was planned yet. */
    fun slid(plan: Plan?, now: ZonedDateTime, quarter: Int): Plan = Plan(plan?.date ?: now.toLocalDate(), quarter.coerceIn(0, LAST_QUARTER))

    /**
     * The day was stepped by [days]: the planned time on that day, or the present quarter hour when
     * nothing was planned yet. Never further than yesterday or [DAYS_AHEAD] days ahead of today.
     */
    fun stepped(plan: Plan?, now: ZonedDateTime, days: Int): Plan {
        val today = now.toLocalDate()
        val date = (plan?.date ?: today).plusDays(days.toLong())
        return Plan(date.coerceIn(today.minusDays(DAYS_BACK), today.plusDays(DAYS_AHEAD)), plan?.quarter ?: quarter(now.toLocalTime()))
    }

    /** Whether a step by [days] stays within yesterday and [DAYS_AHEAD] days ahead: else its button is dimmed. */
    fun canStep(plan: Plan?, today: LocalDate, days: Int): Boolean =
        ChronoUnit.DAYS.between(today, (plan?.date ?: today).plusDays(days.toLong())) in -DAYS_BACK..DAYS_AHEAD

    /**
     * The moment a plan stands for in [zone], the device's. On the day the clocks go forward an hour
     * of the day doesn't exist: a time in it is the next one that does (2:30 is 3:00, as is every
     * stop from 2:00 on), which is not what `ZonedDateTime.of` would make of it (3:30). On the day
     * they go back an hour comes twice: a time in it is its first.
     */
    fun moment(plan: Plan, zone: ZoneId): ZonedDateTime {
        val quarter = plan.quarter.coerceIn(0, LAST_QUARTER)
        val wall = LocalDateTime.of(plan.date, LocalTime.of(quarter / 4, quarter % 4 * 15))
        val gap = zone.rules.getTransition(wall)?.takeIf { it.isGap }
        return if (gap != null) gap.instant.atZone(zone) else ZonedDateTime.of(wall, zone)
    }

    /** How a day is called beside the day buttons. */
    enum class Day { YESTERDAY, TODAY, TOMORROW, OTHER }

    fun day(date: LocalDate, today: LocalDate): Day = when (ChronoUnit.DAYS.between(today, date)) {
        -1L -> Day.YESTERDAY
        0L -> Day.TODAY
        1L -> Day.TOMORROW
        else -> Day.OTHER
    }
}

/**
 * Plan a time, under World clock's places: a slider over the device's day in quarter hours, and
 * under it the day with a button to each side. At rest ([plan] is null) the slider stands on the
 * present quarter hour and the word beside the heading is "Now". Moving the slider or stepping the
 * day starts a plan ([onPlan]); the places above then show that moment, and two entries appear here:
 * "Copy times" ([line] is what it copies) and "New event at 9:00 AM" ([onNewEvent], with the moment
 * in milliseconds). "Now" ends the plan.
 *
 * [now] is the present moment in the device's zone; [time] writes a moment as the menu's rows do.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanTime(plan: PlanATime.Plan?, now: ZonedDateTime, time: (moment: Long) -> String, line: () -> String,
             onPlan: (PlanATime.Plan?) -> Unit, onNewEvent: (moment: Long) -> Unit) {
    val today = now.toLocalDate()
    val planned = plan?.let { PlanATime.moment(it, now.zone).toInstant().toEpochMilli() }
    // A time the clocks skip that day reads as the next one that exists, here as in the rows.
    val chosen = planned?.let(time)
    val word = stringResource(R.string.clock_plan_now)
    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Box(Modifier.weight(1f).alignByBaseline()) { SectionLabel(stringResource(R.string.clock_plan)) }
        Text(chosen ?: word, Modifier.alignByBaseline(), style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            color = if (chosen != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
    val sliderLabel = stringResource(R.string.clock_plan_slider)
    val sliderState = if (planned != null && chosen != null) stringResource(R.string.clock_plan_state, chosen, Dates.format("EEEEMMMMd", planned, now.zone)) else word
    Slider(
        value = (plan?.quarter ?: PlanATime.quarter(now.toLocalTime())).toFloat(),
        onValueChange = { onPlan(PlanATime.slid(plan, now, it.roundToInt())) },
        valueRange = 0f..PlanATime.LAST_QUARTER.toFloat(),
        // A stop every quarter hour, so that the arrow keys and a screen reader move 15 minutes; 94 tick marks would only clutter.
        steps = PlanATime.LAST_QUARTER - 1,
        track = { SliderDefaults.Track(it, drawTick = { _, _ -> }) },
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = sliderLabel; stateDescription = sliderState },
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        val date = plan?.date ?: today
        Text(when (PlanATime.day(date, today)) {
            PlanATime.Day.TODAY -> stringResource(R.string.calendar_today)
            PlanATime.Day.TOMORROW -> stringResource(R.string.clock_tomorrow)
            PlanATime.Day.YESTERDAY -> stringResource(R.string.clock_yesterday)
            PlanATime.Day.OTHER -> Dates.format("EEEMMMd", date)
        }, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        SmallIconButton(Sym.CHEVRON_LEFT, stringResource(R.string.clock_plan_previous_day), enabled = PlanATime.canStep(plan, today, -1)) {
            onPlan(PlanATime.stepped(plan, now, -1))
        }
        // "Now" is only there while a time is planned, but its room is kept at rest: the first step back would
        // otherwise put it under the pointer, where the next click would undo the step.
        TextButton(onClick = { onPlan(null) }, enabled = plan != null,
            modifier = if (plan != null) Modifier else Modifier.alpha(0f).clearAndSetSemantics { }) { Text(word, maxLines = 1) }
        SmallIconButton(Sym.CHEVRON_RIGHT, stringResource(R.string.clock_plan_next_day), enabled = PlanATime.canStep(plan, today, 1)) {
            onPlan(PlanATime.stepped(plan, now, 1))
        }
    }
    if (planned != null && chosen != null) {
        CopyEntry(stringResource(R.string.clock_plan_copy), text = line)
        MenuEntry(Sym.CALENDAR_ADD_ON, stringResource(R.string.clock_plan_new_event, chosen)) { onNewEvent(planned) }
    }
}
