package io.github.kuscher.bentobar.ui

import android.graphics.BlurMaskFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * A popup's glass as it stands at this frame (docs/design/1.3): written by its window's frame clock
 * (`MenuWindow`), read only in the draw and layer phases of its content, so a frame of the opening
 * redraws the glass and moves the blocks without composing or measuring anything again.
 */
class MenuGlassState {
    /** How tall the glass shows, in dp: it unfolds down from a lip and folds back to it. */
    var heightDp by mutableFloatStateOf(MenuMotion.LIP_DP)
    /** The glass itself (veil, rim, blur), 0 to 1. */
    var presence by mutableFloatStateOf(0f)
    /** Its shadow's strength, 0 to 1: it comes with the opening, so no dark line lies under the lip. */
    var shadow by mutableFloatStateOf(0f)
    /** The contents as a whole, 0 to 1: they fade together in place when the popup closes. */
    var contents by mutableFloatStateOf(1f)
    /** Everything at once, 0 to 1: a popup left for another dissolves in place. */
    var alpha by mutableFloatStateOf(1f)
    /** Time since the popup's first frame, for the blocks; infinite once they all rest. */
    var clockMs by mutableFloatStateOf(0f)
    /** The platform blurs behind windows now (not in battery saver): glass; otherwise a solid card. */
    var blur by mutableStateOf(true)
    /** The card's whole height, in px, once it has been measured (0 before); the shadow's window sizes itself by it. */
    var fullHeightPx by androidx.compose.runtime.mutableIntStateOf(0)
}

/** The popup whose contents are being laid out, for its blocks to drop into place on its clock (none: no entrance). */
val LocalMenuGlass = staticCompositionLocalOf<MenuGlassState?> { null }

/** Marks a child of a [DropColumn] that belongs with the block below it (a divider), so the two arrive together. */
const val JOINS_BELOW = "joinsBelow"

/** Room around a popup's card inside its window, for its shadow (visual.md, "shadow.room"; the top is the gap under the bar). */
object MenuRoom {
    val side = 16.dp
    val top = 4.dp
    val bottom = 24.dp
}

/**
 * A column whose children drop into place one after another as their popup opens (motion.md §2):
 * each child is a block, laid out once and moved only in its layer. Outside a popup, a plain column.
 */
@Composable
fun DropColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val glass = LocalMenuGlass.current
    Layout(content, modifier) { measurables, constraints ->
        val child = Constraints(maxWidth = constraints.maxWidth)
        val placeables = measurables.map { it.measure(child) }
        val width = maxOf(constraints.minWidth, placeables.maxOfOrNull { it.width } ?: 0).coerceAtMost(constraints.maxWidth)
        val height = placeables.sumOf { it.height }.coerceIn(constraints.minHeight, constraints.maxHeight)
        // Which block each child is: a divider joins the block below it.
        val blocks = IntArray(placeables.size)
        var next = 0
        for (i in placeables.indices) {
            blocks[i] = next
            if (measurables[i].layoutId != JOINS_BELOW) next++
        }
        val count = next
        val drop = MenuMotion.DROP_DP.dp.toPx() / MenuMotion.DROP_DP
        layout(width, height) {
            var y = 0
            placeables.forEachIndexed { i, p ->
                if (glass == null) p.place(0, y)
                else p.placeWithLayer(0, y) {
                    val b = MenuMotion.block(blocks[i], glass.clockMs, count)
                    translationY = b.dy * drop
                    alpha = b.alpha
                }
                y += p.height
            }
        }
    }
}

/** The glass's shape as far as it shows: the card's top [heightPx] px, its corners never more than half its height. */
class ShownGlass(private val heightPx: Float, private val cornerPx: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val h = min(heightPx, size.height)
        return Outline.Rounded(RoundRect(0f, 0f, size.width, h, CornerRadius(min(cornerPx, h / 2f))))
    }
}

/** The glass's look (visual.md): a veil over the blur, a thin white rim and a hairline outside it. */
data class GlassLook(val veil: Color, val rim: Color, val hairline: Color, val dark: Boolean)

/**
 * The shadow (visual.md): drawn here rather than by Android's light, whose spot is the screen's centre and so
 * pushes a popup's shadow sideways toward the nearer edge (the lean of 1.2). Two layers, offset only
 * downwards, at the glass as far as it shows; never under the glass (it is see-through).
 */
fun Modifier.menuShadow(glass: MenuGlassState, dark: Boolean): Modifier = this.then(
    Modifier.drawBehind {
        val strength = glass.shadow * glass.alpha
        if (strength <= 0f) return@drawBehind
        val h = min(glass.heightDp.dp.toPx(), size.height)
        val r = min(MenuMotion.RADIUS_DP.dp.toPx(), h / 2f)
        val card = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, h, CornerRadius(r))) }
        clipPath(card, ClipOp.Difference) {
            drawIntoCanvas { c ->
                // Key: y 7, blur σ 9, spread −4; contact: y 1, σ 1. Black at 0.12 / 0.04 light, 0.20 / 0.07 dark.
                val key = Shadows.paint(this, sigmaDp = 9f, alpha = (if (dark) 0.20f else 0.12f) * strength)
                val inset = 4.dp.toPx()
                val y = 7.dp.toPx()
                c.nativeCanvas.drawRoundRect(inset, y + inset, size.width - inset, h + y - inset, r - inset, r - inset, key)
                val contact = Shadows.paint(this, sigmaDp = 1f, alpha = (if (dark) 0.07f else 0.04f) * strength)
                val y1 = 1.dp.toPx()
                c.nativeCanvas.drawRoundRect(0f, y1, size.width, h + y1, r, r, contact)
            }
        }
    },
)

/** The veil, rim and hairline, at the glass as far as it shows and as present as it is. */
fun Modifier.menuGlass(glass: MenuGlassState, look: GlassLook): Modifier = this.then(
    Modifier.drawBehind {
        val p = glass.presence * glass.alpha
        if (p <= 0f) return@drawBehind
        val h = min(glass.heightDp.dp.toPx(), size.height)
        if (android.util.Log.isLoggable("BentoBarMotion", android.util.Log.DEBUG)) android.util.Log.d("BentoBarMotion", "glass drawn=${h.toInt()}")
        val r = min(MenuMotion.RADIUS_DP.dp.toPx(), h / 2f)
        val px = 1f
        drawRoundRect(look.veil.copy(alpha = look.veil.alpha * p), size = Size(size.width, h), cornerRadius = CornerRadius(r))
        // The hairline in the outermost pixel, the rim just inside it.
        drawRoundRect(look.hairline.copy(alpha = look.hairline.alpha * p), topLeft = Offset(px / 2, px / 2),
            size = Size(size.width - px, h - px), cornerRadius = CornerRadius(r - px / 2), style = Stroke(px))
        val rim = 1.dp.toPx()
        drawRoundRect(look.rim.copy(alpha = look.rim.alpha * p), topLeft = Offset(px + rim / 2, px + rim / 2),
            size = Size(size.width - 2 * px - rim, h - 2 * px - rim), cornerRadius = CornerRadius(r - px - rim / 2), style = Stroke(rim))
    },
)

/** Blurred paints for the shadow's layers, made once per blur and kept: a mask filter is costly to build every frame. */
private object Shadows {
    private val cache = HashMap<Int, android.graphics.Paint>()

    fun paint(d: Density, sigmaDp: Float, alpha: Float): android.graphics.Paint {
        val sigma = with(d) { sigmaDp.dp.toPx() }
        // Skia turns a mask filter's radius into a Gaussian sigma as 0.57735 × radius + 0.5.
        val radius = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.5f)
        val p = cache.getOrPut(radius.toBits()) {
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
            }
        }
        p.color = Color.Black.copy(alpha = alpha.coerceIn(0f, 1f)).toArgb()
        return p
    }
}
