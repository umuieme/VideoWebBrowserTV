package com.umuieme.videowebbrowser.ui.pointer

import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/** Pointer look, in dp. A 1080p TV UI is ~960 × 540 dp, so 30 dp is ~5.5% of screen height. */
private object PointerStyle {
    val HEIGHT = 30.dp
    val OUTLINE = 2.dp
    val RING_START_RADIUS = 6.dp
    val RING_END_RADIUS = 28.dp
    val RING_STROKE = 3.dp
    val FILL = Color.White
    val DARK = Color(0xFF101010)
}

/**
 * Classic arrow cursor, tip at (0, 0), in design units 17.2 tall. Scaled to [PointerStyle.HEIGHT].
 * Left edge straight down, a tail on the lower right, like desktop cursors people recognize.
 */
private val ARROW_POINTS = listOf(
    0f to 0f, 0f to 15f, 3.6f to 11.6f, 6.1f to 17.2f, 8.3f to 16.2f, 5.8f to 10.7f, 10.6f to 10.7f,
)
private const val ARROW_DESIGN_HEIGHT = 17.2f

/**
 * Draws the pointer and its click ring above the WebView.
 *
 * Performance: [PointerState.position] and [PointerState.clickPulse] are read only inside the
 * draw lambda and [PointerState.alpha] only inside graphicsLayer. Compose tracks reads per
 * phase, so changes to them re-run just the draw (or layer) step, never recomposition. The
 * arrow path is built once per size/density in drawWithCache.
 *
 * Input: this is a plain Spacer with no focusable/clickable/pointerInput modifiers, so it can
 * never take D-pad focus or consume touches meant for the WebView underneath.
 */
@Composable
fun PointerOverlay(state: PointerState, modifier: Modifier = Modifier) {
    Spacer(
        modifier = modifier
            .graphicsLayer { alpha = state.alpha.value }
            .drawWithCache {
                val scale = PointerStyle.HEIGHT.toPx() / ARROW_DESIGN_HEIGHT
                val arrow = Path().apply {
                    ARROW_POINTS.forEachIndexed { i, (x, y) ->
                        if (i == 0) moveTo(x * scale, y * scale) else lineTo(x * scale, y * scale)
                    }
                    close()
                }
                val outline = Stroke(width = PointerStyle.OUTLINE.toPx(), join = StrokeJoin.Round)
                onDrawBehind {
                    val tip = Offset(state.position.x.dp.toPx(), state.position.y.dp.toPx())
                    drawClickRing(tip, state.clickPulse.value)
                    translate(tip.x, tip.y) {
                        drawPath(arrow, PointerStyle.FILL)
                        drawPath(arrow, PointerStyle.DARK, style = outline)
                    }
                }
            },
    )
}

/** Expanding, fading ring around the tip; dark under white so it shows on any background. */
private fun DrawScope.drawClickRing(center: Offset, progress: Float) {
    if (progress >= 1f) return
    val start = PointerStyle.RING_START_RADIUS.toPx()
    val radius = start + (PointerStyle.RING_END_RADIUS.toPx() - start) * progress
    val alpha = 1f - progress
    val stroke = PointerStyle.RING_STROKE.toPx()
    drawCircle(PointerStyle.DARK, radius, center, alpha * 0.6f, style = Stroke(stroke + 2.dp.toPx()))
    drawCircle(PointerStyle.FILL, radius, center, alpha, style = Stroke(stroke))
}
