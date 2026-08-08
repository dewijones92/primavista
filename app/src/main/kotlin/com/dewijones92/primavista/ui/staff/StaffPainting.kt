package com.dewijones92.primavista.ui.staff

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.dewijones92.primavista.notation.GlyphMetrics
import com.dewijones92.primavista.notation.LaidOutBeam
import com.dewijones92.primavista.notation.LaidOutCurve
import com.dewijones92.primavista.notation.LaidOutGlyph
import com.dewijones92.primavista.notation.LaidOutLine
import com.dewijones92.primavista.notation.LaidOutNote
import com.dewijones92.primavista.notation.SmuflGlyph
import com.dewijones92.primavista.theme.BravuraFamily
import com.dewijones92.primavista.theme.NotationColors

/** Everything a draw call needs that is the same for every mark on the staff. */
internal class StaffPaint(
    val cache: GlyphTextCache,
    val space: Float,
    val staffSpace: Dp,
    val colors: NotationColors,
)

internal fun DrawScope.drawLines(lines: List<LaidOutLine>, paint: StaffPaint) {
    lines.forEach { line ->
        // Staff lines are horizontal; stems and barlines are not. Distinguishing by geometry keeps
        // the layout engine from having to carry a semantic tag it would otherwise only need here.
        val horizontal = line.y1 == line.y2
        drawStaffLine(line, paint.space, if (horizontal) paint.colors.staffLine else paint.colors.ink)
    }
}

internal fun DrawScope.drawStaffLine(line: LaidOutLine, space: Float, color: Color) {
    drawLine(
        color = color,
        start = Offset(line.x1.value.toFloat() * space, line.y1.value.toFloat() * space),
        end = Offset(line.x2.value.toFloat() * space, line.y2.value.toFloat() * space),
        strokeWidth = line.thickness.value.toFloat() * space,
        cap = StrokeCap.Butt,
    )
}

internal fun DrawScope.drawBeams(beams: List<LaidOutBeam>, space: Float, color: Color) {
    beams.forEach { beam ->
        // A beam is a sloped quadrilateral with vertically-cut ends, not a stroked line: a stroke
        // would round or bevel the ends perpendicular to the slope, which reads as a sloppy join
        // exactly where the eye follows the rhythm.
        val half = beam.thickness.value.toFloat() * space / 2f
        val x1 = beam.startX.value.toFloat() * space
        val y1 = beam.startY.value.toFloat() * space
        val x2 = beam.endX.value.toFloat() * space
        val y2 = beam.endY.value.toFloat() * space
        drawPath(
            Path().apply {
                moveTo(x1, y1 - half)
                lineTo(x2, y2 - half)
                lineTo(x2, y2 + half)
                lineTo(x1, y1 + half)
                close()
            },
            color,
        )
    }
}

internal fun DrawScope.drawCurves(curves: List<LaidOutCurve>, space: Float, color: Color) {
    curves.forEach { curve ->
        val path = Path().apply {
            moveTo(curve.startX.value.toFloat() * space, curve.startY.value.toFloat() * space)
            quadraticTo(
                curve.controlX.value.toFloat() * space,
                curve.controlY.value.toFloat() * space,
                curve.endX.value.toFloat() * space,
                curve.endY.value.toFloat() * space,
            )
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = curve.thickness.value.toFloat() * space, cap = StrokeCap.Round),
        )
    }
}

internal fun DrawScope.drawNote(paint: StaffPaint, note: LaidOutNote, appearance: NoteAppearance) {
    val tint = appearance.color ?: paint.colors.ink
    val pivot = Offset(
        note.notehead.x.value.toFloat() * paint.space,
        note.notehead.y.value.toFloat() * paint.space,
    )
    if (appearance.halo > 0f) drawHalo(pivot, tint, appearance.halo, paint.space)
    // Leger lines belong to the staff rather than to the note, so a verdict's pop leaves them alone.
    note.legerLines.forEach { drawStaffLine(it, paint.space, tint) }
    if (appearance.scale == 1f) {
        drawNoteBody(paint, note, tint)
    } else {
        scale(appearance.scale, pivot) { drawNoteBody(paint, note, tint) }
    }
}

private fun DrawScope.drawNoteBody(paint: StaffPaint, note: LaidOutNote, tint: Color) {
    note.stem?.let { drawStaffLine(it, paint.space, tint) }
    note.accidental?.let { paint.cache.draw(this, it, paint, tint) }
    paint.cache.draw(this, note.notehead, paint, tint)
    note.flag?.let { paint.cache.draw(this, it, paint, tint) }
    note.dots.forEach { paint.cache.draw(this, it, paint, tint) }
}

private fun DrawScope.drawHalo(center: Offset, color: Color, strength: Float, space: Float) {
    val radius = HALO_RADIUS_SPACES * space
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = HALO_ALPHA * strength), Color.Transparent),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

/**
 * Glyphs are drawn as text, because that is what a SMuFL font is — and measuring the same forty
 * glyphs on every frame would be the obvious way to make a scrolling staff stutter, so results are
 * cached per glyph and per size.
 */
internal class GlyphTextCache(
    private val metrics: GlyphMetrics,
    private val measurer: TextMeasurer,
) {
    private val cache = HashMap<Key, TextLayoutResult>()

    fun draw(scope: DrawScope, glyph: LaidOutGlyph, paint: StaffPaint, color: Color) {
        val layout = cache.getOrPut(Key(glyph.glyph, paint.space)) {
            measurer.measure(
                text = codepointString(glyph.glyph),
                style = TextStyle(
                    fontFamily = BravuraFamily,
                    // A SMuFL font's em is four staff spaces by specification, so this is the one
                    // conversion between the engraving unit and a text size.
                    fontSize = (paint.staffSpace.value * SPACES_PER_EM).sp,
                ),
            )
        }
        with(scope) {
            // Bravura's glyph origin sits on the staff line the glyph is registered to, whereas
            // drawText places the text's top-left. Shifting by the layout's baseline is what puts a
            // notehead on its line instead of below it.
            val originY = layout.firstBaseline
            val x = glyph.x.value.toFloat() * paint.space
            val y = glyph.y.value.toFloat() * paint.space
            // scaleY is 1.0 for everything but the grand-staff brace, whose height belongs to the
            // system rather than to the typeface.
            if (glyph.scaleY == 1.0) {
                drawText(layout, color = color, topLeft = Offset(x, y - originY))
            } else {
                scale(scaleX = 1f, scaleY = glyph.scaleY.toFloat(), pivot = Offset(x, y)) {
                    drawText(layout, color = color, topLeft = Offset(x, y - originY))
                }
            }
        }
    }

    private fun codepointString(glyph: SmuflGlyph): String =
        String(Character.toChars(metrics.codepoint(glyph)))

    private data class Key(val glyph: SmuflGlyph, val space: Float)
}

private const val SPACES_PER_EM = 4f
private const val HALO_RADIUS_SPACES = 2.6f
private const val HALO_ALPHA = 0.55f
