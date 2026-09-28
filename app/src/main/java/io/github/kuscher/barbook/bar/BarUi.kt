package io.github.kuscher.barbook.bar

import android.graphics.Rect
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kuscher.barbook.data.Display
import io.github.kuscher.barbook.data.ItemConfig
import io.github.kuscher.barbook.data.Pill
import io.github.kuscher.barbook.data.TextSize
import io.github.kuscher.barbook.items.ItemState
import io.github.kuscher.barbook.items.Tone
import io.github.kuscher.barbook.util.Fonts
import io.github.kuscher.barbook.util.Sym
import io.github.kuscher.barbook.util.SymIcon

/** Colours and sizes for the strip, matched to the status bar. */
data class StripLook(
    val fg: Color,
    /** True when [fg] is light, i.e. the bar behind it is dark. */
    val lightText: Boolean,
    val textSize: TextSize,
    val spacing: Dp,
    val pill: Pill,
) {
    val textSp: TextUnit get() = when (textSize) { TextSize.SMALL -> 12.5.sp; TextSize.DEFAULT -> 14.sp; TextSize.LARGE -> 15.5.sp }
    val iconSp: TextUnit get() = when (textSize) { TextSize.SMALL -> 16.sp; TextSize.DEFAULT -> 18.sp; TextSize.LARGE -> 20.sp }
    val accent: Color get() = if (lightText) Color(0xFFA8C7FA) else Color(0xFF0B57D0)
    val warn: Color get() = if (lightText) Color(0xFFFFD27A) else Color(0xFF8A5100)
    val alertBg: Color get() = if (lightText) Color(0xFFFFB4AB) else Color(0xFFB3261E)
    val alertFg: Color get() = if (lightText) Color(0xFF690005) else Color.White
}

data class StripEntry(val item: ItemConfig, val state: ItemState)

/** What the strip reports back to [BarController]. Rects are in window coordinates. */
interface StripEvents {
    fun click(item: ItemConfig, at: Rect)
    fun context(item: ItemConfig, at: Rect)
    fun scroll(item: ItemConfig, steps: Int)
    fun chevron(at: Rect)
    fun chevronContext(at: Rect)
    fun hover(inside: Boolean)
    /** Where an item (or "chevron") was drawn, in window coordinates. */
    fun placed(id: String, at: Rect)
}

/**
 * The strip: optional ‹ button, revealed hidden items, then the visible items. With
 * [keepEnd], items at the end (next to the system icons) win when space runs out.
 */
@Composable
fun Strip(
    visible: List<StripEntry>,
    revealed: List<StripEntry>,
    showChevron: Boolean,
    expanded: Boolean,
    chevronOnLeft: Boolean,
    look: StripLook,
    maxWidthPx: Int,
    heightDp: Dp,
    events: StripEvents,
) {
    val pillBg = when (look.pill) {
        Pill.NONE -> Color.Transparent
        Pill.SUBTLE -> look.fg.copy(alpha = 0.12f)
        Pill.SOLID -> if (look.lightText) Color(0xE6202124) else Color(0xE6F1F3F4)
    }
    Row(
        Modifier.height(heightDp)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        when (e.type) {
                            PointerEventType.Enter -> events.hover(true)
                            PointerEventType.Exit -> events.hover(false)
                        }
                    }
                }
            }
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(50))
            .background(pillBg)
            .padding(horizontal = if (look.pill == Pill.NONE) 0.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val chevron: @Composable () -> Unit = {
            if (showChevron) Chevron(expanded, chevronOnLeft, look, events)
        }
        val items = if (chevronOnLeft) revealed + visible else visible + revealed
        if (chevronOnLeft) chevron()
        FitRow(maxWidthPx = maxWidthPx, keepEnd = chevronOnLeft, spacing = look.spacing) {
            items.forEach { ItemView(it, look, events) }
        }
        if (!chevronOnLeft) chevron()
    }
}

/** A row that drops children which don't fit, from the start (or the end when !keepEnd). */
@Composable
private fun FitRow(maxWidthPx: Int, keepEnd: Boolean, spacing: Dp, content: @Composable () -> Unit) {
    Layout(content) { measurables, constraints ->
        val gap = spacing.roundToPx()
        val placeables = measurables.map { it.measure(Constraints(maxHeight = constraints.maxHeight)) }
        val keep = BooleanArray(placeables.size)
        var used = 0
        val order = if (keepEnd) placeables.indices.reversed() else placeables.indices
        for (i in order) {
            val w = placeables[i].width + if (used > 0) gap else 0
            if (used + w > maxWidthPx.coerceAtLeast(0)) break
            keep[i] = true
            used += w
        }
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(used, height) {
            var x = 0
            placeables.forEachIndexed { i, p ->
                if (!keep[i]) return@forEachIndexed
                p.placeRelative(x, (height - p.height) / 2)
                x += p.width + gap
            }
        }
    }
}

@Composable
private fun Chevron(expanded: Boolean, onLeft: Boolean, look: StripLook, events: StripEvents) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    var bounds by remember { mutableStateOf(Rect()) }
    // Hidden items sit on the chevron's far side; the arrow points where they'll appear.
    val sym = if (onLeft != expanded) Sym.CHEVRON_LEFT else Sym.CHEVRON_RIGHT
    Box(
        Modifier.fillMaxHeight()
            .onGloballyPositioned { bounds = it.boundsInWindow().toRect(); events.placed("chevron", bounds) }
            .clip(RoundedCornerShape(10.dp))
            .background(if (hovered) look.fg.copy(alpha = 0.14f) else Color.Transparent)
            .hoverable(source)
            .clicks({ events.chevron(bounds) }, { events.chevronContext(bounds) }, null)
            .semantics { contentDescription = if (expanded) "Hide BarBook's hidden items" else "Show BarBook's hidden items"; role = Role.Button }
            .padding(horizontal = 3.dp),
        contentAlignment = Alignment.Center,
    ) { SymIcon(sym, size = look.iconSp, color = look.fg.copy(alpha = 0.85f)) }
}

@Composable
private fun ItemView(entry: StripEntry, look: StripLook, events: StripEvents) {
    val s = entry.state
    if (entry.item.type == "spacer") {
        Box(Modifier.width(s.gapDp.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
            if (s.divider) Box(Modifier.width(1.dp).height(16.dp).background(look.fg.copy(alpha = 0.45f)))
        }
        return
    }
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    var bounds by remember { mutableStateOf(Rect()) }
    // Numbers change width every second; hold the widest size for a while so neighbours don't jump
    // (and the window doesn't resize every tick).
    val density = androidx.compose.ui.platform.LocalDensity.current
    var widest by remember { mutableStateOf(0) }
    var widestAt by remember { mutableStateOf(0L) }
    val alert = s.tone == Tone.ALERT
    val color = when (s.tone) {
        Tone.ALERT -> look.alertFg
        Tone.WARN -> look.warn
        Tone.ACCENT -> look.accent
        Tone.NORMAL -> look.fg
    }
    val display = entry.item.display
    val showIcon = display != Display.TEXT || s.text.isNullOrEmpty()
    val showText = display != Display.ICON && !s.text.isNullOrEmpty()
    Row(
        Modifier.fillMaxHeight()
            .widthIn(min = with(density) { widest.toDp() })
            .onSizeChanged { size ->
                val now = android.os.SystemClock.uptimeMillis()
                if (size.width > widest || now - widestAt > 15_000) { widest = size.width; widestAt = now }
            }
            .onGloballyPositioned { bounds = it.boundsInWindow().toRect(); events.placed(entry.item.id, bounds) }
            .clip(RoundedCornerShape(10.dp))
            .background(when {
                alert -> look.alertBg
                hovered -> look.fg.copy(alpha = 0.14f)
                else -> Color.Transparent
            })
            .hoverable(source)
            .clicks({ events.click(entry.item, bounds) }, { events.context(entry.item, bounds) }, { events.scroll(entry.item, it) })
            .semantics(mergeDescendants = true) { contentDescription = s.desc.ifEmpty { s.text.orEmpty() }; role = Role.Button }
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (showIcon) {
            val img = s.image
            if (img != null) {
                Image(img.asImageBitmap(), null, Modifier.size(look.iconSp.value.dp),
                    colorFilter = if (entry.item.optBool("mono", true) && entry.item.type == "app") ColorFilter.tint(color) else null)
            } else if (s.icon != null && s.icon.isNotEmpty()) {
                SymIcon(s.icon, size = look.iconSp, filled = s.filled, color = color)
            }
        }
        if (showIcon && showText && (s.image != null || !s.icon.isNullOrEmpty())) Spacer(Modifier.width(5.dp))
        if (showText) {
            Text(s.text!!, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = Fonts.bar, fontSize = look.textSp, fontFeatureSettings = "tnum", lineHeight = look.textSp))
        }
    }
}

/** Primary click, secondary click (right button or touch long-press) and mouse-wheel steps. */
private fun Modifier.clicks(onClick: () -> Unit, onContext: () -> Unit, onScroll: ((Int) -> Unit)?): Modifier =
    pointerInput(onClick, onContext, onScroll) {
        awaitPointerEventScope {
            var down = 0L
            var secondary = false
            var armed = false
            while (true) {
                val e = awaitPointerEvent()
                when (e.type) {
                    PointerEventType.Press -> {
                        armed = true
                        secondary = e.buttons.isSecondaryPressed
                        down = e.changes.firstOrNull()?.uptimeMillis ?: 0L
                        e.changes.forEach { it.consume() }
                    }
                    PointerEventType.Release -> if (armed) {
                        armed = false
                        val c = e.changes.firstOrNull()
                        val inside = c != null && c.position.x >= 0 && c.position.y >= 0 &&
                            c.position.x <= size.width && c.position.y <= size.height
                        val longPress = c != null && c.uptimeMillis - down > 550 && c.type == androidx.compose.ui.input.pointer.PointerType.Touch
                        if (inside) { if (secondary || longPress) onContext() else onClick() }
                        e.changes.forEach { it.consume() }
                    }
                    PointerEventType.Scroll -> if (onScroll != null) {
                        val dy = e.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                        if (dy != 0f) onScroll(if (dy < 0) 1 else -1)
                        e.changes.forEach { it.consume() }
                    }
                }
            }
        }
    }

private fun androidx.compose.ui.geometry.Rect.toRect() = Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
