package io.github.kuscher.discobar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kuscher.discobar.util.SymIcon

/** The frame every drop-down menu shares: a header with icon and title, then content. */
@Composable
fun MenuCard(
    icon: String,
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    /** A drawable instead of the [icon] symbol (DiscoBar's own mark). */
    iconRes: Int? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (iconRes != null) androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(iconRes), null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                else SymIcon(icon, size = 17.sp, filled = true, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (trailing != null) trailing()
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

/** "Label ........ value" with tabular figures. */
@Composable
fun InfoRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Medium, color = valueColor)
    }
}

@Composable
fun MenuDivider() = HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

@Composable
fun SectionLabel(text: String) = Text(text.uppercase(), style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))

/** A small line chart of recent values, filled underneath. Two series share one scale. */
@Composable
fun Sparkline(
    values: List<Double>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    second: List<Double>? = null,
    secondColor: Color = MaterialTheme.colorScheme.tertiary,
    max: Double? = null,
) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(10.dp)).background(track)) {
        val top = max ?: maxOf(values.maxOrNull() ?: 0.0, second?.maxOrNull() ?: 0.0, 1e-9)
        fun series(vs: List<Double>, c: Color, fill: Boolean) {
            if (vs.size < 2) return
            val stepX = size.width / (60 - 1).coerceAtLeast(vs.size - 1)
            val startX = size.width - stepX * (vs.size - 1)
            val path = Path()
            vs.forEachIndexed { i, v ->
                val x = startX + i * stepX
                val y = size.height - 4f - ((v / top).coerceIn(0.0, 1.0) * (size.height - 10f)).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            if (fill) {
                val area = Path().apply { addPath(path); lineTo(size.width, size.height); lineTo(startX, size.height); close() }
                drawPath(area, Brush.verticalGradient(listOf(c.copy(alpha = 0.35f), c.copy(alpha = 0.02f))))
            }
            drawPath(path, c, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        series(values, color, true)
        if (second != null) series(second, secondColor, false)
        drawLine(color.copy(alpha = 0.15f), Offset(0f, size.height - 1), Offset(size.width, size.height - 1))
    }
}

/** A full-width row button, like a desktop menu entry. */
@Composable
fun MenuEntry(icon: String, label: String, detail: String? = null, enabled: Boolean = true, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (hovered && enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else Color.Transparent)
            .hoverable(source)
            .clickable(enabled = enabled, interactionSource = source, indication = null, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val c = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        SymIcon(icon, size = 18.sp, color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else c)
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = c, modifier = Modifier.weight(1f),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A square-ish tile with an icon over a label, for grids of actions. */
@Composable
fun ActionTile(icon: String, label: String, selected: Boolean = false, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val bg = when {
        selected -> MaterialTheme.colorScheme.primaryContainer
        hovered -> MaterialTheme.colorScheme.surfaceContainerHighest
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    Column(
        Modifier.width(84.dp).clip(RoundedCornerShape(14.dp)).background(bg)
            .hoverable(source)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(vertical = 9.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SymIcon(icon, size = 20.sp, filled = selected,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TileGrid(content: @Composable () -> Unit) =
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }

/** Pill buttons in a row (presets such as 5, 10, 25 min). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipRow(labels: List<String>, onClick: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { i, l ->
            val source = remember { MutableInteractionSource() }
            val hovered by source.collectIsHoveredAsState()
            Text(l, style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.clip(RoundedCornerShape(50))
                    .background(if (hovered) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.secondaryContainer)
                    .hoverable(source)
                    .clickable(interactionSource = source, indication = null) { onClick(i) }
                    .pointerHoverIcon(PointerIcon.Hand)
                    .padding(horizontal = 12.dp, vertical = 6.dp))
        }
    }
}

/** A thin progress track. */
@Composable
fun Meter(fraction: Float, color: Color = MaterialTheme.colorScheme.primary) {
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(50)).background(color))
    }
}

/** Recomposes the caller once a second while the ticker runs. */
@Composable
fun rememberTick(): Long {
    val t by io.github.kuscher.discobar.items.Ticker.tick.collectAsState()
    return t
}

/** One small bar per CPU core (0..1 each). */
@Composable
fun CoreBars(values: DoubleArray, color: Color = MaterialTheme.colorScheme.primary) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(Modifier.fillMaxWidth().height(36.dp)) {
        if (values.isEmpty()) return@Canvas
        val gap = 3.dp.toPx()
        val w = (size.width - gap * (values.size - 1)) / values.size
        values.forEachIndexed { i, v ->
            val x = i * (w + gap)
            drawRoundRect(track, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Size(w, size.height),
                androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
            val h = (v.coerceIn(0.0, 1.0) * size.height).toFloat()
            if (h > 0.5f) drawRoundRect(color, androidx.compose.ui.geometry.Offset(x, size.height - h), androidx.compose.ui.geometry.Size(w, h),
                androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
        }
    }
}
