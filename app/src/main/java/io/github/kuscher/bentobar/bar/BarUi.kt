package io.github.kuscher.bentobar.bar

import android.graphics.Rect
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onPlaced
import kotlinx.coroutines.launch
import io.github.kuscher.bentobar.ui.animatePlacement
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.TextStyle
import io.github.kuscher.bentobar.util.Fmt
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kuscher.bentobar.data.Display
import io.github.kuscher.bentobar.data.ItemConfig
import io.github.kuscher.bentobar.data.Pill
import io.github.kuscher.bentobar.data.TextSize
import io.github.kuscher.bentobar.items.BarSlider
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

    /**
     * How strongly the text color is drawn for the slider's empty track: 0.38 on a dark bar and 0.50
     * on a light one (3.4:1 and 3.2:1 against black and white), raised in steps of 0.1 on a bar of
     * another color until the track stands out from it at 3:1, the contrast a control's parts need.
     * It stops at 0.7: any stronger and the track is no longer told from the filled part, which is
     * what shows the level. On the few mid-tone bars where the text itself only just reaches 4.5:1
     * the track then ends a little under 3:1 (2.8:1 at the least, see SliderMathTest).
     */
    val sliderTrackAlpha: Float get() {
        var a = if (lightText) 0.38f else 0.50f
        while (a < 0.7f && Contrast.ratio(fg.copy(alpha = a).compositeOver(background), background) < 3f) a = (a + 0.1f).coerceAtMost(0.7f)
        return a
    }
    /** The slider's empty track. */
    val sliderTrack: Color get() = fg.copy(alpha = sliderTrackAlpha)
    /**
     * The slider's filled part while muted: between the track and the text color, so the kept level
     * still shows (0.62 on a dark bar, 0.74 on a light one; stronger where the track had to be).
     */
    val sliderMuted: Color get() = fg.copy(alpha = (sliderTrackAlpha + 0.24f).coerceAtMost(1f))
}

/** The slider in the bar: the track, the zone that takes the pointer for it (the track and the item's end), its handle. */
val SLIDER_TRACK = 64.dp
private val SLIDER_ZONE = 70.dp
private val SLIDER_HEIGHT = 4.dp
private val SLIDER_HANDLE_WIDTH = 4.dp
private val SLIDER_HANDLE_HEIGHT = 14.dp
/** A finger held this long without moving asks for the item's menu (the same time the items use). */
private const val LONG_PRESS_MS = 550L

/** The pill's padding at each end (none without a pill); the controller counts it in the width budget. */
val STRIP_PILL_PADDING = 6.dp

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
    /** The wheel over the strip, where no item uses it: [up] reveals hidden items, down folds them. */
    fun wheel(up: Boolean) {}
    /** [item] dragged sideways by [dx] px; [done] on release, where it should land. */
    fun drag(item: ItemConfig, dx: Float, done: Boolean) {}
    /** [item]'s slider was set to [level] (0 to 1, on one of its steps); [done] when the pointer let go. */
    fun slide(item: ItemConfig, level: Float, done: Boolean) {}
    /** Whether a slider in this strip is a control. In the settings preview it is only drawn: a click there selects the item. */
    val slidable: Boolean get() = true
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
    /** ‹ shows whatever fits (presenting, or hidden items behind it), so its room is always kept. */
    chevronAlways: Boolean,
    /** Room kept for ‹ inside [maxWidthPx] when it shows. */
    chevronReservePx: Int,
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
                            PointerEventType.Scroll -> e.changes.firstOrNull()?.takeIf { !it.isConsumed }?.let {
                                if (it.scrollDelta.y != 0f) { events.wheel(it.scrollDelta.y < 0); it.consume() }
                            }
                        }
                    }
                }
            }
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(50))
            .background(pillBg)
            .padding(horizontal = if (look.pill == Pill.NONE) 0.dp else STRIP_PILL_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val chevron: @Composable () -> Unit = {
            if (showChevron) Chevron(expanded, chevronOnLeft, look, events)
        }
        val items = if (chevronOnLeft) revealed + visible else visible + revealed
        if (chevronOnLeft) chevron()
        FitRow(ids = items.map { it.item.id }, maxWidthPx = maxWidthPx, chevronAlways = chevronAlways, chevronPx = chevronReservePx,
            keepEnd = chevronOnLeft, spacing = look.spacing, onDropped = onOverflow) {
            // key(): remembered state (hover, held width) belongs to the item, not to its position,
            // or an item popping in would inherit its neighbour's width.
            items.forEach { androidx.compose.runtime.key(it.item.id) { ItemView(it, look, events) } }
        }
        if (!chevronOnLeft) chevron()
    }
}

/**
 * Which of [widths] fit in [limit] with [gap] between them, keeping from the start (or the end with
 * [keepEnd]) and stopping at the first that doesn't fit; and the width they take.
 */
internal fun fitWidths(widths: List<Int>, gap: Int, limit: Int, keepEnd: Boolean): Pair<BooleanArray, Int> {
    val keep = BooleanArray(widths.size)
    var used = 0
    for (i in if (keepEnd) widths.indices.reversed() else widths.indices) {
        val w = widths[i] + if (used > 0) gap else 0
        if (used + w > limit) break
        keep[i] = true
        used += w
    }
    return keep to used
}

/** [fitWidths] for the strip: everything in [budget] if it fits there (‹ needn't show), else room for ‹. */
internal fun fitStrip(widths: List<Int>, gap: Int, budget: Int, chevronAlways: Boolean, chevronPx: Int, keepEnd: Boolean) =
    fitWidths(widths, gap, if (chevronAlways) budget - chevronPx else budget, keepEnd).takeIf { (k, _) -> k.all { it } }
        ?: fitWidths(widths, gap, budget - chevronPx, keepEnd)

/**
 * A row that drops children which don't fit, from the start (or the end when !keepEnd), and
 * reports the dropped [ids] so they can be offered elsewhere instead of silently vanishing.
 * [maxWidthPx] includes ‹: its [chevronPx] is kept when it shows anyway, or when something has to be
 * dropped (‹ then offers it). Decided from this measure pass alone, so the strip can't stay
 * overflowed just because it was overflowed before.
 */
@Composable
private fun FitRow(ids: List<String>, maxWidthPx: Int, chevronAlways: Boolean, chevronPx: Int, keepEnd: Boolean, spacing: Dp,
                   onDropped: (Set<String>) -> Unit, content: @Composable () -> Unit) {
    val last = remember { arrayOf<Set<String>?>(null) }
    Layout(content) { measurables, constraints ->
        val gap = spacing.roundToPx()
        val placeables = measurables.map { it.measure(Constraints(maxHeight = constraints.maxHeight)) }
        val widths = placeables.map { it.width }
        val budget = minOf(maxWidthPx, constraints.maxWidth).coerceAtLeast(0)
        val (keep, used) = fitStrip(widths, gap, budget, chevronAlways, chevronPx, keepEnd)
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
    // Where the item's left edge follows the pointer, in window coordinates; null when not dragging.
    // The draw offset is worked out from it and the item's current slot at draw time, so a slot that
    // moves (the preview reordering) can't leave the item a slot's width off for a frame.
    var dragLeft by remember { mutableStateOf<Float?>(null) }
    val settle = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val lifted = grab != null
    val liftScale by androidx.compose.animation.core.animateFloatAsState(if (lifted) 1.08f else 1f,
        androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMedium), label = "lift")
    // Live numbers (speeds, percentages, clocks) sit in a fixed slot sized for their widest
    // reading (Fmt.widthTemplate), right-aligned, so nothing next to them moves as they change.
    val density = androidx.compose.ui.platform.LocalDensity.current
    val measurer = rememberTextMeasurer()
    val textStyle = TextStyle(fontFamily = Fonts.bar, fontWeight = Fonts.barWeight, fontSize = look.textSp, fontFeatureSettings = "tnum", lineHeight = look.textSp)
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
    // A slider takes the text's place ("Icon and text" draws icon and slider, "Text" the slider alone);
    // shown as an icon only, the item has none.
    val slider = s.slider?.takeIf { display != Display.ICON }
    // While the pointer holds the slider: the level under it. Otherwise the item's own, which is the real one.
    var held by remember { mutableStateOf<Float?>(null) }
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == LayoutDirection.Rtl
    val context by rememberUpdatedState { events.context(entry.item, bounds) }
    val showIcon = display != Display.TEXT || s.text.isNullOrEmpty()
    val showText = slider == null && display != Display.ICON && !s.text.isNullOrEmpty()
    Row(
        Modifier.fillMaxHeight()
            // The slot is read during placement (before this frame draws), the bounds after layout.
            .onPlaced { slotX = it.positionInWindow().x }
            .onGloballyPositioned { bounds = it.boundsInWindow().toRect(); events.placed(entry.item.id, bounds) }
            .clicks({ events.click(entry.item, bounds) }, { events.context(entry.item, bounds) },
                if (io.github.kuscher.bentobar.items.Items.of(entry.item.type)?.usesWheel == true) { steps -> events.scroll(entry.item, steps) } else null,
                onDrag = { localX, downX, done ->
                    // Grabbed where the pointer is when the drag begins (past the touch slop), not where it
                    // was pressed: otherwise the item jumps the slop distance to catch up on the first frame.
                    if (grab == null) { grab = localX; startSlot = slotX; scope.launch { settle.snapTo(0f) } }
                    val left = slotX + localX - (grab ?: downX) // where the item's left edge follows the pointer
                    dragLeft = left
                    events.drag(entry.item, left - startSlot, done)
                    if (done) {
                        // The previewed order is now the real one; spring from where it was let go into its slot.
                        val from = left - slotX
                        grab = null
                        dragLeft = null
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
                translationX = (1f - appear.value) * 6.dp.toPx() + (dragLeft?.let { it - slotX } ?: 0f) + settle.value
                scaleX = liftScale; scaleY = liftScale
            }
            .clip(RoundedCornerShape(10.dp))
            .background(when {
                alert -> look.alertBg
                // Lifted, it's a solid tile in the bar's own colour, so text it passes over doesn't show through.
                lifted -> look.fg.copy(alpha = 0.18f).compositeOver(look.background)
                // The hover box stays while the slider is held, also when the pointer has left the item.
                hovered || held != null -> look.fg.copy(alpha = 0.14f)
                else -> Color.Transparent
            })
            .hoverable(source)
            .semantics(mergeDescendants = true) {
                contentDescription = s.desc.ifEmpty { s.text.orEmpty() }
                // The clicks come from raw pointer input, so tell assistive tech how to press it.
                onClick { events.click(entry.item, bounds); true }
                onLongClick(itemMenuLabel) { events.context(entry.item, bounds); true }
                // In the settings preview the track is only drawn: the item is a button there, like every other.
                if (slider == null || !events.slidable) role = Role.Button
                else {
                    // A slider for assistive tech too: it reads the description and can raise and lower the
                    // level, step by step where the slider has steps (0 to 15 for a volume of fifteen steps).
                    val top = if (slider.steps > 0) slider.steps.toFloat() else 1f
                    progressBarRangeInfo = ProgressBarRangeInfo(slider.level.coerceIn(0f, 1f) * top, 0f..top, (slider.steps - 1).coerceAtLeast(0))
                    setProgress { to -> events.slide(entry.item, SliderMath.snap(to / top, slider.steps), true); true }
                }
            }
            // The slider's zone runs to the item's very end (the last 6 dp mean "full"), so it brings that padding itself.
            .padding(start = 6.dp, end = if (slider != null) 0.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (showIcon) {
            val img = s.image
            if (img != null) {
                Image(img.asImageBitmap(), null, Modifier.size(look.iconSp.value.dp),
                    colorFilter = if (entry.item.optBool("mono", true) && entry.item.type == "app") ColorFilter.tint(color) else null)
            } else if (s.dayNumber != null) {
                DayBadge(s.dayNumber, look.iconSp, color, look.background)
            } else if (s.icon != null && s.icon.isNotEmpty()) {
                SymIcon(s.icon, size = look.iconSp, filled = s.filled, color = color)
            }
        }
        if (showIcon && (showText || slider != null) && (s.image != null || !s.icon.isNullOrEmpty())) Spacer(Modifier.width(5.dp))
        if (slider != null) {
            VolumeTrack(slider, held, handle = hovered || held != null, look = look, rtl = rtl,
                modifier = if (!events.slidable) Modifier
                else Modifier.slides(slider.steps, rtl, onHeld = { held = it }, onContext = { context() }) { level, done -> events.slide(entry.item, level, done) })
        }
        if (showText) {
            val template = if (s.widthKey != null) Fmt.widthTemplate(s.text!!) else null
            val slot = template?.let { tpl -> remember(tpl, textStyle) { measurer.measure(tpl, textStyle, maxLines = 1).size.width } }
            // With an icon, the number stays next to it and the spare room trails; text alone sits at the end.
            val iconShown = showIcon && (s.image != null || !s.icon.isNullOrEmpty())
            // An item that promises a length is held to it in width too: 8.5 dp a character at the usual text
            // size. The count decides what is said; this only catches scripts whose characters are wide.
            val cap = if (s.textLimit > 0) (8.5f * s.textLimit * look.textSp.value / 14f).dp else Dp.Unspecified
            Text(s.text!!, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = if (iconShown) TextAlign.Start else TextAlign.End,
                modifier = (if (slot != null) Modifier.widthIn(min = with(density) { slot.toDp() }) else Modifier).widthIn(max = cap),
                style = textStyle)
        }
    }
}

/**
 * The slider in the bar, where the item's text would be: a track [SLIDER_TRACK] wide and 4 dp high,
 * filled to the level, in a zone that runs on to the item's end and takes the pointer over the bar's
 * whole height. A quiet line at rest. With [handle] (the pointer is over the item, or holds the
 * slider) a small upright bar marks the level, with a gap on each side of it.
 *
 * While the pointer holds the slider ([held]) the level under the pointer is drawn; otherwise the
 * item's own, which is the real one and glides when it changes from elsewhere (the volume keys).
 */
@Composable
private fun VolumeTrack(slider: BarSlider, held: Float?, handle: Boolean, look: StripLook, rtl: Boolean, modifier: Modifier) {
    // Under the pointer nothing glides: the level set there is the item's own a moment later, and a glide that
    // were still on its way when the pointer lets go would draw the fill a step back first.
    val glided by androidx.compose.animation.core.animateFloatAsState(slider.level.coerceIn(0f, 1f),
        if (held != null) androidx.compose.animation.core.snap()
        else androidx.compose.animation.core.tween(120, easing = androidx.compose.animation.core.FastOutSlowInEasing), label = "level")
    val level = held ?: glided
    val track = look.sliderTrack
    // Muted, the fill is drawn solid in the color its strength gives on the bar: its round end lies over the
    // track's start, and a see-through fill would show that as a darker or lighter cap.
    val fill = if (slider.dimmed && held == null) look.sliderMuted.compositeOver(look.background) else look.fg
    val mark = look.fg
    Canvas(modifier.width(SLIDER_ZONE).fillMaxHeight()) {
        val w = SLIDER_TRACK.toPx()
        val h = SLIDER_HEIGHT.toPx()
        val top = (size.height - h) / 2
        val radius = CornerRadius(h / 2)
        // The track starts where the zone starts; right to left, that is the zone's right edge.
        fun x(along: Float) = if (rtl) size.width - along else along
        fun span(from: Float, to: Float, color: Color) {
            if (to - from <= 0f) return
            drawRoundRect(color, Offset(minOf(x(from), x(to)), top), Size(to - from, h), radius)
        }
        // Any level above nothing shows at least a dot; nothing shows nothing.
        val filled = SliderMath.fillWidth(level, w, min = h)
        if (!handle) {
            // The rest of the track is cut out of the whole track's shape, starting under the fill's round end:
            // the two meet without a notch, and the track's far end stays round.
            val from = (filled - h / 2).coerceAtLeast(0f)
            clipRect(left = minOf(x(from), x(w)), right = maxOf(x(from), x(w))) {
                drawRoundRect(track, Offset(minOf(x(0f), x(w)), top), Size(w, h), radius)
            }
            span(0f, filled, fill)
        } else {
            // The handle stands on the fill's end, and for 2 dp on each side of it neither fill nor track is drawn.
            val hw = SLIDER_HANDLE_WIDTH.toPx()
            val center = SliderMath.handleCenter(level, w, edge = hw / 2)
            val gap = hw / 2 + 2.dp.toPx()
            span(0f, center - gap, fill)
            span(center + gap, w, track)
            val hh = SLIDER_HANDLE_HEIGHT.toPx()
            drawRoundRect(mark, Offset(x(center) - hw / 2, (size.height - hh) / 2), Size(hw, hh), CornerRadius(hw / 2))
        }
    }
}

/**
 * The slider's pointer rules, on its zone (the track and the item's end; the icon and the gap after
 * it stay the item's own, see [clicks]).
 *
 * A mouse, a touchpad or a stylus: the level follows from the moment the button goes down, with no
 * slop, until it is released. A finger: a tap sets the level when it lifts, a finger that moves past
 * the touch slop makes the level follow, and one held for [LONG_PRESS_MS] without moving opens the
 * item's menu ([onContext]) and changes nothing. A click or a tap never sets nothing at all: its
 * lowest level is one step (a slip at the track's start must not silence a call); a drag can reach 0.
 *
 * The press is consumed, so the item around the slider takes it neither for a click nor for the
 * start of a drag to reorder. A secondary press is left alone: it is the item's menu, as anywhere on
 * the item. [onHeld] gets the level while the pointer holds the slider, and null when it lets go.
 * Whatever was reported while it held is followed by one last report with `done`, also when the
 * press is taken away.
 */
private fun Modifier.slides(steps: Int, rtl: Boolean, onHeld: (Float?) -> Unit, onContext: () -> Unit,
                            onLevel: (level: Float, done: Boolean) -> Unit): Modifier = composed {
    val level by rememberUpdatedState(onLevel)
    val holds by rememberUpdatedState(onHeld)
    val menu by rememberUpdatedState(onContext)
    pointerInput(steps, rtl) {
        val track = SLIDER_TRACK.toPx()
        // The rules are SliderGesture's (unit-tested); this turns pointer events into its four calls.
        val gesture = SliderGesture(steps)
        fun say(l: Float) { holds(l); level(l, false) }
        fun end(how: SliderGesture.End) {
            holds(null)
            when (how) {
                is SliderGesture.End.Level -> level(how.level, true)
                SliderGesture.End.Menu -> menu()
                SliderGesture.End.None -> {}
            }
        }
        try {
            awaitPointerEventScope {
                var pointer: androidx.compose.ui.input.pointer.PointerId? = null
                var downX = 0f
                var downAt = 0L
                while (true) {
                    val e = awaitPointerEvent()
                    // The pointer that holds the slider, while one does; a second finger is not it.
                    val c = (if (gesture.down) e.changes.firstOrNull { it.id == pointer } else e.changes.firstOrNull()) ?: continue
                    val at = SliderMath.level(c.position.x, if (rtl) size.width - track else 0f, track, steps, rtl)
                    when (e.type) {
                        PointerEventType.Press -> when {
                            // Another finger while the slider is held: the slider keeps its press, and the item around it gets none.
                            gesture.down -> e.changes.forEach { it.consume() }
                            // The primary button, a finger or a pen; a right or a middle click is the item's own.
                            !e.buttons.isSecondaryPressed && !e.buttons.isTertiaryPressed && !c.isConsumed -> {
                                pointer = c.id; downX = c.position.x; downAt = c.uptimeMillis
                                // A finger may still be about to tap or to hold: nothing is set until it says which.
                                gesture.press(at, follows = c.type != androidx.compose.ui.input.pointer.PointerType.Touch)?.let { say(it) }
                                c.consume()
                            }
                        }
                        // A pressed mouse that crosses the zone's edge arrives as Exit or Enter: it has moved all the same.
                        PointerEventType.Move, PointerEventType.Enter, PointerEventType.Exit -> if (gesture.down) {
                            if (c.pressed) gesture.move(at, kotlin.math.abs(c.position.x - downX) > viewConfiguration.touchSlop)?.let { say(it) }
                            c.consume()
                        }
                        PointerEventType.Release -> if (gesture.down && !c.pressed) {
                            // Taken away by the system, not lifted (such a change arrives consumed): what was set stays, nothing more is.
                            if (c.isConsumed) end(gesture.cancel())
                            else {
                                val inside = c.position.x >= 0 && c.position.y >= 0 && c.position.x <= size.width && c.position.y <= size.height
                                end(gesture.release(at, inside, longHold = c.uptimeMillis - downAt > LONG_PRESS_MS))
                                c.consume()
                            }
                        }
                    }
                }
            }
        } finally {
            // Cut short (the strip went away, the item left it): what was set is the last word, and nothing holds the slider.
            end(gesture.cancel())
        }
    }
}

/**
 * Primary click, secondary click (right button or touch long-press), mouse-wheel steps and, with
 * [onDrag], a sideways drag: a primary press that moves past the touch slop (mouse, touchpad press
 * or touch) becomes a drag instead of a click. A press that something inside the item has taken for
 * itself (the slider's track, see [slides]) is not the item's: no click and no drag come of it.
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
                    PointerEventType.Press -> if (e.changes.any { it.isConsumed }) {
                        // Taken by something inside the item (the slider): its release and its moves aren't ours either.
                        armed = false
                        dragging = false
                    } else {
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
                        // A move the slider inside has taken (it holds that pointer) is never the start of a reorder.
                        if (c != null && c.pressed && !c.isConsumed) {
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
        Text(label, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Today's day number as a small filled date badge, the size of a status bar icon: the number is cut
 * out in the bar's own color, so it reads at this size on any bar.
 */
@Composable
private fun DayBadge(day: Int, size: TextUnit, fg: Color, bg: Color) {
    Box(Modifier.size(size.value.dp + 1.dp).clip(RoundedCornerShape(4.dp)).background(fg), contentAlignment = Alignment.Center) {
        Text("$day", color = bg.copy(alpha = 1f), maxLines = 1,
            style = TextStyle(fontFamily = Fonts.bar, fontSize = (size.value * 0.68f).sp, lineHeight = (size.value * 0.68f).sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontFeatureSettings = "tnum"))
    }
}
