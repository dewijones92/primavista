package com.dewijones92.primavista.ui.staff

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import com.dewijones92.primavista.notation.LaidOutLine

/**
 * The clef, key and time signature, held still on an opaque strip while the music slides under it.
 *
 * Without this the furniture leaves the screen inside the first bar and Dewi is reading in a key he
 * can no longer see — a correctness point, not decoration. See CODE-NOTES.
 */
internal fun DrawScope.drawPinnedBackdrop(group: FurnitureGroup, paint: StaffPaint) {
    val width = group.width.value.toFloat() * paint.space
    drawRect(color = paint.colors.paper, size = Size(width, size.height))
    val edge = PINNED_EDGE_SPACES * paint.space
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(paint.colors.pinnedEdge, Color.Transparent),
            startX = width,
            endX = width + edge,
        ),
        topLeft = Offset(width, 0f),
        size = Size(edge, size.height),
    )
}

internal fun DrawScope.drawPinnedContent(
    group: FurnitureGroup,
    staffLines: List<LaidOutLine>,
    paint: StaffPaint,
) {
    clipRect(right = group.width.value.toFloat() * paint.space) {
        drawLines(staffLines, paint)
        group.glyphs.forEach { paint.cache.draw(this, it, paint, paint.colors.ink) }
    }
}

/**
 * The eye's anchor: a crisp brass line inside a soft radial bloom.
 *
 * The bloom is radial rather than a horizontal band because a band has two hard vertical edges, and
 * at this width they read as a highlighted block sitting on the staff rather than as light.
 */
internal fun DrawScope.drawPlayhead(x: Float, paint: StaffPaint) {
    val bloom = PLAYHEAD_BLOOM_SPACES * paint.space
    val middle = Offset(x, size.height / 2f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(paint.colors.playheadGlow.copy(alpha = PLAYHEAD_GLOW_ALPHA), Color.Transparent),
            center = middle,
            radius = bloom,
        ),
        radius = bloom,
        center = middle,
    )
    drawLine(
        color = paint.colors.playhead.copy(alpha = PLAYHEAD_ALPHA),
        start = Offset(x, 0f),
        end = Offset(x, size.height),
        strokeWidth = PLAYHEAD_WIDTH_SPACES * paint.space,
        cap = StrokeCap.Round,
    )
    val cap = PLAYHEAD_CAP_SPACES * paint.space
    drawCircle(paint.colors.playhead, radius = cap, center = Offset(x, cap))
    drawCircle(paint.colors.playhead, radius = cap, center = Offset(x, size.height - cap))
}

private const val PINNED_EDGE_SPACES = 1.1f
private const val PLAYHEAD_BLOOM_SPACES = 5.5f
private const val PLAYHEAD_GLOW_ALPHA = 0.30f
private const val PLAYHEAD_ALPHA = 0.92f
private const val PLAYHEAD_WIDTH_SPACES = 0.2f
private const val PLAYHEAD_CAP_SPACES = 0.34f
