package com.dewijones92.primavista.ui.staff

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dewijones92.primavista.notation.GlyphMetrics
import com.dewijones92.primavista.notation.LaidOutBeam
import com.dewijones92.primavista.notation.LaidOutCurve
import com.dewijones92.primavista.notation.LaidOutGlyph
import com.dewijones92.primavista.notation.LaidOutLine
import com.dewijones92.primavista.notation.SmuflGlyph
import com.dewijones92.primavista.notation.StaffSpaces
import com.dewijones92.primavista.notation.StaffSystem
import com.dewijones92.primavista.theme.BravuraFamily
import com.dewijones92.primavista.theme.LocalNotationColors

/**
 * Draws a laid-out [StaffSystem].
 *
 * This composable owns exactly one thing: turning staff-space geometry into pixels. It computes no
 * positions of its own — every coordinate already came from `:core:notation`, which is why the
 * engraving is unit-testable and why the playhead cannot drift from the notes it is judging.
 * A position calculated here would be a second layout engine.
 */
@Composable
public fun StaffCanvas(
    system: StaffSystem,
    metrics: GlyphMetrics,
    modifier: Modifier = Modifier,
    staffSpace: Dp = DEFAULT_STAFF_SPACE,
    scrollX: StaffSpaces = StaffSpaces.ZERO,
    playheadX: StaffSpaces? = null,
    noteTint: (attackIndex: Int?) -> Color? = { null },
) {
    val colors = LocalNotationColors.current
    val measurer = rememberTextMeasurer()
    val glyphCache = remember(metrics, staffSpace) { GlyphTextCache(metrics, measurer) }

    Canvas(modifier = modifier) {
        val space = staffSpace.toPx()
        translate(left = -scrollX.value.toFloat() * space) {
            drawLines(system.lines, space, colors.staffLine, colors.ink)
            drawBeams(system.beams, space, colors.ink)
            drawCurves(system.curves, space, colors.ink)
            system.glyphs.forEach { glyphCache.draw(this, it, space, colors.ink, staffSpace) }
            system.notes.forEach { note ->
                val tint = noteTint(note.attackIndex) ?: colors.ink
                note.legerLines.forEach { drawStaffLine(it, space, colors.ink) }
                note.stem?.let { drawStaffLine(it, space, tint) }
                note.accidental?.let { glyphCache.draw(this, it, space, tint, staffSpace) }
                glyphCache.draw(this, note.notehead, space, tint, staffSpace)
                note.flag?.let { glyphCache.draw(this, it, space, tint, staffSpace) }
                note.dots.forEach { glyphCache.draw(this, it, space, tint, staffSpace) }
            }
        }
        playheadX?.let { drawPlayhead(it, scrollX, space, size.height, colors.playhead) }
    }
}

/**
 * A staff whose vertical size is chosen from the available height rather than fixed, so the same
 * screen works on a phone in portrait and a tablet without a second layout.
 */
@Composable
public fun FittedStaffCanvas(
    system: StaffSystem,
    metrics: GlyphMetrics,
    modifier: Modifier = Modifier,
    scrollX: StaffSpaces = StaffSpaces.ZERO,
    playheadX: StaffSpaces? = null,
    noteTint: (attackIndex: Int?) -> Color? = { null },
) {
    BoxWithConstraints(modifier) {
        StaffCanvas(
            system = system,
            metrics = metrics,
            modifier = Modifier.fillMaxSize(),
            staffSpace = fittedStaffSpace(system, maxHeight),
            scrollX = scrollX,
            playheadX = playheadX,
            noteTint = noteTint,
        )
    }
}

private fun DrawScope.drawLines(
    lines: List<LaidOutLine>,
    space: Float,
    staffLineColor: Color,
    inkColor: Color,
) {
    lines.forEach { line ->
        // Staff lines are horizontal; stems and barlines are not. Distinguishing by geometry keeps
        // the layout engine from having to carry a semantic tag it would otherwise only need here.
        val horizontal = line.y1 == line.y2
        drawStaffLine(line, space, if (horizontal) staffLineColor else inkColor)
    }
}

private fun DrawScope.drawStaffLine(line: LaidOutLine, space: Float, color: Color) {
    drawLine(
        color = color,
        start = Offset(line.x1.value.toFloat() * space, line.y1.value.toFloat() * space),
        end = Offset(line.x2.value.toFloat() * space, line.y2.value.toFloat() * space),
        strokeWidth = line.thickness.value.toFloat() * space,
        cap = StrokeCap.Butt,
    )
}

private fun DrawScope.drawBeams(beams: List<LaidOutBeam>, space: Float, color: Color) {
    beams.forEach { beam ->
        // A beam is a sloped quadrilateral with vertically-cut ends, not a stroked line: a stroke
        // would round or bevel the ends perpendicular to the slope, which reads as a sloppy join
        // exactly where the eye follows the rhythm.
        val half = beam.thickness.value.toFloat() * space / 2f
        val x1 = beam.startX.value.toFloat() * space
        val y1 = beam.startY.value.toFloat() * space
        val x2 = beam.endX.value.toFloat() * space
        val y2 = beam.endY.value.toFloat() * space
        val path = Path().apply {
            moveTo(x1, y1 - half)
            lineTo(x2, y2 - half)
            lineTo(x2, y2 + half)
            lineTo(x1, y1 + half)
            close()
        }
        drawPath(path, color)
    }
}

private fun DrawScope.drawCurves(curves: List<LaidOutCurve>, space: Float, color: Color) {
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

private fun DrawScope.drawPlayhead(
    playheadX: StaffSpaces,
    scrollX: StaffSpaces,
    space: Float,
    height: Float,
    color: Color,
) {
    val x = (playheadX - scrollX).value.toFloat() * space
    drawLine(
        color = color.copy(alpha = PLAYHEAD_ALPHA),
        start = Offset(x, 0f),
        end = Offset(x, height),
        strokeWidth = PLAYHEAD_WIDTH_SPACES * space,
        cap = StrokeCap.Round,
    )
}

/**
 * Glyphs are drawn as text, because that is what a SMuFL font is — and measuring the same forty
 * glyphs on every frame would be the obvious way to make a scrolling staff stutter, so results are
 * cached per glyph and per size.
 */
private class GlyphTextCache(
    private val metrics: GlyphMetrics,
    private val measurer: TextMeasurer,
) {
    private val cache = HashMap<Key, TextLayoutResult>()

    fun draw(scope: DrawScope, glyph: LaidOutGlyph, space: Float, color: Color, staffSpace: Dp) {
        val layout = cache.getOrPut(Key(glyph.glyph, space)) {
            measurer.measure(
                text = codepointString(glyph.glyph),
                style = TextStyle(
                    fontFamily = BravuraFamily,
                    // A SMuFL font's em is four staff spaces by specification, so this is the one
                    // conversion between the engraving unit and a text size.
                    fontSize = (staffSpace.value * SPACES_PER_EM).sp,
                ),
            )
        }
        with(scope) {
            // Bravura's glyph origin sits on the staff line the glyph is registered to, whereas
            // drawText places the text's top-left. Shifting by the layout's baseline-equivalent
            // (its height above the origin) is what puts a notehead on its line instead of below it.
            val originY = layout.firstBaseline
            val x = glyph.x.value.toFloat() * space
            val y = glyph.y.value.toFloat() * space
            // scaleY is 1.0 for everything but the grand-staff brace, whose height belongs to the
            // system rather than to the typeface. Scaling about the glyph's origin keeps it anchored
            // where the layout put it.
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

private fun fittedStaffSpace(system: StaffSystem, available: Dp): Dp {
    val needed = system.height.value.toFloat().coerceAtLeast(1f)
    return (available.value / needed).coerceIn(MIN_STAFF_SPACE.value, MAX_STAFF_SPACE.value).dp
}

private const val SPACES_PER_EM = 4f
private const val PLAYHEAD_ALPHA = 0.85f
private const val PLAYHEAD_WIDTH_SPACES = 0.22f

private val DEFAULT_STAFF_SPACE = 7.dp
private val MIN_STAFF_SPACE = 4.dp
private val MAX_STAFF_SPACE = 14.dp
