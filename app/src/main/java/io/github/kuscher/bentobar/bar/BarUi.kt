package io.github.kuscher.bentobar.bar

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
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch
import io.github.kuscher.bentobar.ui.animatePlacement
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import io.github.kuscher.bentobar.util.Fmt
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kuscher.bentobar.data.Display
import io.github.kuscher.bentobar.data.ItemConfig
import io.github.kuscher.bentobar.data.Pill
import io.github.kuscher.bentobar.data.TextSize
import io.github.kuscher.bentobar.items.ItemState
import io.github.kuscher.bentobar.items.Tone
import io.github.kuscher.bentobar.util.Fonts
import io.github.kuscher.bentobar.util.Sym
import io.github.kuscher.bentobar.util.SymIcon

/** Colours and sizes for the strip, matched to the status bar. */
@androidx.compose.runtime.Immutable
data class StripLook(
    val fg: Color,
    /** True when [fg] is light, i.e. the bar behind it is dark. */
    val lightText: Boolean,
    val textSize: TextSize,
    val spacing: Dp,
    val pill: Pill,
    /** What the strip is drawn on: the sampled bar colour, or black/white for a dark/light bar. */
    val background: Color = if (lightText) Color.Black else Color.White,
) {
    val textSp: TextUnit get() = when (textSize) { TextSize.SMALL -> 12.5.sp; TextSize.DEFAULT -> 14.sp; TextSize.LARGE -> 15.5.sp }
    // Measured on the desktop bar: system glyphs are about 13 px wide and 14–16 px tall at 1.125x,
    // which a 15 sp Material Symbol matches.
    val iconSp: TextUnit get() = when (textSize) { TextSize.SMALL -> 13.5.sp; TextSize.DEFAULT -> 15.sp; TextSize.LARGE -> 17.sp }
    // Accent and warning colours keep 4.5:1 on the bar too; on a bar where they wouldn't (a mid-tone
    // colour), the text colour is used instead.
    val accent: Color get() = Contrast.orElse(if (lightText) Color(0xFFA8C7FA) else Color(0xFF0B57D0), background, fg)
    val warn: Color get() = Contrast.orElse(if (lightText) Color(0xFFFFD27A) else Color(0xFF8A5100), background, fg)
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
    /** The pointer is over [item] (or has left it), for its tooltip. */
    fun itemHover(item: ItemConfig, at: Rect, inside: Boolean) {}
    /** [item] dragged sideways by [dx] px; [done] on release, where it should land. */
    fun drag(item: ItemConfig, dx: Float, done: Boolean) {}
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
    /** Ids of the items that didn't fit, each time that changes. */
    onOverflow: (Set<String>) -> Unit = {},
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
        FitRow(ids = items.map { it.item.id }, maxWidthPx = maxWidthPx, keepEnd = chevronOnLeft, spacing = look.spacing, onDropped = onOverflow) {
            // key(): remembered state (hover, held width) belongs to the item, not to its position,
            // or an item popping in would inherit its neighbour's width.
            items.forEach { androidx.compose.runtime.key(it.item.id) { ItemView(it, look, events) } }
        }
        if (!chevronOnLeft) chevron()
    }
}

/**
 * A row that drops children which don't fit, from the start (or the end when !keepEnd), and
 * reports the dropped [ids] so they can be offered elsewhere instead of silently vanishing.
 */
@Composable
private fun FitRow(ids: List<String>, maxWidthPx: Int, keepEnd: Boolean, spacing: Dp, onDropped: (Set<String>) -> Unit,
                   content: @Composable () -> Unit) {
    val last = remember { arrayOf<Set<String>?>(null) }
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
        val dropped = ids.filterIndexed { i, _ -> i < keep.size && !keep[i] }.toSet()
        if (dropped != last[0]) { last[0] = dropped; onDropped(dropped) }
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
    val label = androidx.compose.ui.res.stringResource(if (expanded) io.github.kuscher.bentobar.R.string.chevron_hide else io.github.kuscher.bentobar.R.string.chevron_show)
    val menuLabel = androidx.compose.ui.res.stringResource(io.github.kuscher.bentobar.R.string.chevron_menu)
    Box(
        Modifier.fillMaxHeight()
            .onGloballyPositioned { bounds = it.boundsInWindow().toRect(); events.placed("chevron", bounds) }
            .clip(RoundedCornerShape(10.dp))
            .background(if (hovered) look.fg.copy(alpha = 0.14f) else Color.Transparent)
            .hoverable(source)
            .clicks({ events.chevron(bounds) }, { events.chevronContext(bounds) }, null)
            .semantics {
                contentDescription = label; role = Role.Button
                onClick { events.chevron(bounds); true }
                onLongClick(menuLabel) { events.chevronContext(bounds); true }
            }
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
    val itemMenuLabel = androidx.compose.ui.res.stringResource(io.github.kuscher.bentobar.R.string.strip_item_menu)
    LaunchedEffect(hovered) { events.itemHover(entry.item, bounds, hovered) }
    // Dragging sideways: the item lifts and follows the pointer while the others slide aside
    // (animatePlacement, as the controller previews the new order), then it settles into its slot.
    // Positions are measured against the item's untransformed slot: pointer positions relative to
    // the moving item itself would feed its own movement back in and make it flicker.
    var slotX by remember { mutableStateOf(0f) }
    var grab by remember { mutableStateOf<Float?>(null) } // where in the item it was picked up
    var startSlot by remember { mutableStateOf(0f) }
    var dragOffset by remember { mutableStateOf(0f) }
    val settle = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val lifted = grab != null
    val liftScale by androidx.compose.animation.core.animateFloatAsState(if (lifted) 1.08f else 1f,
        androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMedium), label = "lift")
    // Live numbers (speeds, percentages, clocks) sit in a fixed slot sized for their widest
    // reading (Fmt.widthTemplate), right-aligned, so nothing next to them moves as they change.
    val density = androidx.compose.ui.platform.LocalDensity.current
    val measurer = rememberTextMeasurer()
    val textStyle = TextStyle(fontFamily = Fonts.bar, fontSize = look.textSp, fontFeatureSettings = "tnum", lineHeight = look.textSp)
    // Appearing (the strip starting, an item popping out, ‹ revealing hidden items) fades and slides
    // in instead of popping. Drawn in the graphics layer only: no relayout, so the window doesn't
    // resize per frame.
    val appear = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, androidx.compose.animation.core.tween(220)) }
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
            .onGloballyPositioned { slotX = it.positionInWindow().x; bounds = it.boundsInWindow().toRect(); events.placed(entry.item.id, bounds) }
            .clicks({ events.click(entry.item, bounds) }, { events.context(entry.item, bounds) }, { events.scroll(entry.item, it) },
                onDrag = { localX, downX, done ->
                    if (grab == null) { grab = downX; startSlot = slotX; scope.launch { settle.snapTo(0f) } }
                    val left = slotX + localX - (grab ?: downX) // where the item's left edge follows the pointer
                    dragOffset = left - slotX
                    events.drag(entry.item, left - startSlot, done)
                    if (done) {
                        // The previewed order is now the real one; spring from where it was let go into its slot.
                        val from = dragOffset
                        grab = null
                        dragOffset = 0f
                        scope.launch {
                            settle.snapTo(from)
                            settle.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.8f,
                                stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow))
                        }
                    }
                })
            // Neighbors slide to their new places like magnets; the lifted item follows the pointer instead.
            .then(if (lifted || settle.isRunning) Modifier else Modifier.animatePlacement())
            .zIndex(if (lifted || settle.isRunning) 1f else 0f)
            .graphicsLayer {
                alpha = appear.value
                translationX = (1f - appear.value) * 6.dp.toPx() + dragOffset + settle.value
                scaleX = liftScale; scaleY = liftScale
            }
            .clip(RoundedCornerShape(10.dp))
            .background(when {
                alert -> look.alertBg
                // Lifted, it's a solid tile in the bar's own colour, so text it passes over doesn't show through.
                lifted -> look.fg.copy(alpha = 0.18f).compositeOver(look.background)
                hovered -> look.fg.copy(alpha = 0.14f)
                else -> Color.Transparent
            })
            .hoverable(source)
            .semantics(mergeDescendants = true) {
                contentDescription = s.desc.ifEmpty { s.text.orEmpty() }; role = Role.Button
                // The clicks come from raw pointer input, so tell assistive tech how to press it.
                onClick { events.click(entry.item, bounds); true }
                onLongClick(itemMenuLabel) { events.context(entry.item, bounds); true }
            }
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
            val template = if (s.widthKey != null) Fmt.widthTemplate(s.text!!) else null
            val slot = template?.let { tpl -> remember(tpl, textStyle) { measurer.measure(tpl, textStyle, maxLines = 1).size.width } }
            // With an icon, the number stays next to it and the spare room trails; text alone sits at the end.
            val iconShown = showIcon && (s.image != null || !s.icon.isNullOrEmpty())
            Text(s.text!!, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = if (iconShown) TextAlign.Start else TextAlign.End,
                modifier = if (slot != null) Modifier.widthIn(min = with(density) { slot.toDp() }) else Modifier,
                style = textStyle)
        }
    }
}

/**
 * Primary click, secondary click (right button or touch long-press), mouse-wheel steps and, with
 * [onDrag], a sideways drag: a primary press that moves past the touch slop (mouse, touchpad press
 * or touch) becomes a drag instead of a click.
 */
private fun Modifier.clicks(onClick: () -> Unit, onContext: () -> Unit, onScroll: ((Int) -> Unit)?,
                            onDrag: ((localX: Float, downX: Float, done: Boolean) -> Unit)? = null): Modifier = composed {
    // Keep the gesture coroutine running across recompositions; call the latest callbacks.
    val click by rememberUpdatedState(onClick)
    val context by rememberUpdatedState(onContext)
    val scroll by rememberUpdatedState(onScroll)
    val drag by rememberUpdatedState(onDrag)
    pointerInput(Unit) {
        awaitPointerEventScope {
            var down = 0L
            var secondary = false
            var armed = false
            var downX = 0f
            var dx = 0f
            var dragging = false
            while (true) {
                val e = awaitPointerEvent()
                when (e.type) {
                    PointerEventType.Press -> {
                        armed = true
                        secondary = e.buttons.isSecondaryPressed
                        down = e.changes.firstOrNull()?.uptimeMillis ?: 0L
                        downX = e.changes.firstOrNull()?.position?.x ?: 0f
                        dx = 0f
                        dragging = false
                        e.changes.forEach { it.consume() }
                    }
                    PointerEventType.Move -> if (armed && !secondary && drag != null) {
                        val c = e.changes.firstOrNull()
                        if (c != null && c.pressed) {
                            dx = c.position.x - downX
                            if (!dragging && kotlin.math.abs(dx) > viewConfiguration.touchSlop) dragging = true
                            if (dragging) { drag?.invoke(c.position.x, downX, false); c.consume() }
                        }
                    }
                    PointerEventType.Release -> if (armed && dragging) {
                        armed = false
                        dragging = false
                        // Where it was let go: the pointer can travel past the last move event.
                        drag?.invoke(e.changes.firstOrNull()?.position?.x ?: (downX + dx), downX, true)
                        e.changes.forEach { it.consume() }
                    } else if (armed) {
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

/** A strip item's tooltip: dark, high-contrast (white on #303134 is over 12:1) on any bar. */
@Composable
fun Tooltip(label: String) {
    Box(Modifier.fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(Color(0xF0303134)).padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center) {
        Text(label, color = Color.White, fontSize = 13.sp, maxLines = 1)
    }
}
