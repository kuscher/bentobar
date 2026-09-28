package io.github.kuscher.discobar.bar

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.composed
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
import io.github.kuscher.discobar.data.Display
import io.github.kuscher.discobar.data.ItemConfig
import io.github.kuscher.discobar.data.Pill
import io.github.kuscher.discobar.data.TextSize
import io.github.kuscher.discobar.items.ItemState
import io.github.kuscher.discobar.items.Tone
import io.github.kuscher.discobar.util.Fonts
import io.github.kuscher.discobar.util.Sym
import io.github.kuscher.discobar.util.SymIcon

/** Colours and sizes for the strip, matched to the status bar. */
@androidx.compose.runtime.Immutable
data class StripLook(
    val fg: Color,
    /** True when [fg] is light, i.e. the bar behind it is dark. */
    val lightText: Boolean,
    val textSize: TextSize,
    val spacing: Dp,
    val pill: Pill,
) {
    val textSp: TextUnit get() = when (textSize) { TextSize.SMALL -> 12.5.sp; TextSize.DEFAULT -> 14.sp; TextSize.LARGE -> 15.5.sp }
    // Measured on the desktop bar: system glyphs are about 13 px wide and 14–16 px tall at 1.125x,
    // which a 15 sp Material Symbol matches.
    val iconSp: TextUnit get() = when (textSize) { TextSize.SMALL -> 13.5.sp; TextSize.DEFAULT -> 15.sp; TextSize.LARGE -> 17.sp }
    val accent: Color get() = if (lightText) Color(0xFFA8C7FA) else Color(0xFF0B57D0)
    val warn: Color get() = if (lightText) Color(0xFFFFD27A) else Color(0xFF8A5100)
    val alertBg: Color get() = if (lightText) Color(0xFFFFB4AB) else Color(0xFFB3261E)
    val alertFg: Color get() = if (lightText) Color(0xFF690005) else Color.White
}

@androidx.compose.runtime.Immutable
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
            // key(): remembered state (hover, held width) belongs to the item, not to its position,
            // or an item popping in would inherit its neighbour's width.
            items.forEach { androidx.compose.runtime.key(it.item.id) { ItemView(it, look, events) } }
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
        val limit = minOf(maxWidthPx, constraints.maxWidth).coerceAtLeast(0)
        for (i in order) {
            val w = placeables[i].width + if (used > 0) gap else 0
            if (used + w > limit) break
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
            .semantics { contentDescription = if (expanded) "Hide DiscoBar's hidden items" else "Show DiscoBar's hidden items"; role = Role.Button }
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
    // (and the window doesn't resize every tick), then ease back to the natural width.
    val density = androidx.compose.ui.platform.LocalDensity.current
    var natural by remember(s.widthKey) { mutableStateOf(0) }
    var widest by remember(s.widthKey) { mutableStateOf(0) }
    LaunchedEffect(widest) {
        if (widest > natural) { kotlinx.coroutines.delay(5_000); widest = natural }
    }
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
                modifier = if (s.widthKey != null) Modifier.widthIn(min = with(density) { widest.toDp() }) else Modifier,
                onTextLayout = { r ->
                    if (s.widthKey == null) return@Text
                    val w = kotlin.math.ceil(r.getLineRight(0) - r.getLineLeft(0)).toInt()
                    natural = w
                    if (w > widest) widest = w
                },
                style = TextStyle(fontFamily = Fonts.bar, fontSize = look.textSp, fontFeatureSettings = "tnum", lineHeight = look.textSp))
        }
    }
}

/** Primary click, secondary click (right button or touch long-press) and mouse-wheel steps. */
private fun Modifier.clicks(onClick: () -> Unit, onContext: () -> Unit, onScroll: ((Int) -> Unit)?): Modifier = composed {
    // Keep the gesture coroutine running across recompositions; call the latest callbacks.
    val click by rememberUpdatedState(onClick)
    val context by rememberUpdatedState(onContext)
    val scroll by rememberUpdatedState(onScroll)
    pointerInput(Unit) {
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
                        if (inside) { if (secondary || longPress) context() else click() }
                        e.changes.forEach { it.consume() }
                    }
                    PointerEventType.Scroll -> scroll?.let { onWheel ->
                        val dy = e.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                        if (dy != 0f) onWheel(if (dy < 0) 1 else -1)
                        e.changes.forEach { it.consume() }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.ui.geometry.Rect.toRect() = Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())

/**
 * Measures [content] at its natural width and reports it, so the window can be sized exactly.
 * (A WRAP_CONTENT window is first measured at the system's dialog width, 580 dp here, and Compose
 * doesn't ask for more, which cut the strip off and slid items under the chevron.) While the
 * window catches up, the content is aligned by [bias]: -1 start, 0 centre, 1 end.
 */
@Composable
fun MeasuredStrip(bias: Float, onWidth: (Int) -> Unit, content: @Composable () -> Unit) {
    Layout(content) { measurables, constraints ->
        val p = measurables.first().measure(Constraints(minHeight = constraints.minHeight, maxHeight = constraints.maxHeight))
        onWidth(p.width)
        val w = if (constraints.hasBoundedWidth) constraints.maxWidth else p.width
        layout(w, p.height) { p.place((((w - p.width) * (1 + bias)) / 2).toInt(), 0) }
    }
}
