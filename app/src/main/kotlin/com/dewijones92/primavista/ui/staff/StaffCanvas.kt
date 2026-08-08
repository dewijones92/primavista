package com.dewijones92.primavista.ui.staff

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dewijones92.primavista.notation.GlyphMetrics
import com.dewijones92.primavista.notation.LaidOutNote
import com.dewijones92.primavista.notation.StaffSpaces
import com.dewijones92.primavista.notation.StaffSystem
import com.dewijones92.primavista.score.Ticks
import com.dewijones92.primavista.theme.LocalNotationColors

/**
 * Draws a laid-out [StaffSystem].
 *
 * This composable owns exactly one thing: turning staff-space geometry into pixels. It computes no
 * positions of its own — every coordinate already came from `:core:notation`, which is why the
 * engraving is unit-testable and why the playhead cannot drift from the notes it is judging.
 * A position calculated here would be a second layout engine.
 *
 * [pinnedAt] asks for the clef, key and time signature in force at that musical position to be held
 * still at the left while everything else scrolls; null scrolls the furniture away with the music.
 */
@Composable
public fun StaffCanvas(
    system: StaffSystem,
    metrics: GlyphMetrics,
    modifier: Modifier = Modifier,
    staffSpace: Dp = DEFAULT_STAFF_SPACE,
    scrollX: StaffSpaces = StaffSpaces.ZERO,
    playheadX: StaffSpaces? = null,
    pinnedAt: Ticks? = null,
    appearance: (LaidOutNote) -> NoteAppearance = { NoteAppearance.PLAIN },
) {
    val colors = LocalNotationColors.current
    val measurer = rememberTextMeasurer()
    val glyphCache = remember(metrics, staffSpace) { GlyphTextCache(metrics, measurer) }
    val furniture = remember(system) { PinnedFurniture.of(system) }

    Canvas(modifier = modifier) {
        val paint = StaffPaint(glyphCache, staffSpace.toPx(), staffSpace, colors)
        // Slack is shared above and below rather than all falling under the staff, so a short
        // exercise reads as one line centred on a sheet instead of stranded at the top of one.
        val centring = ((size.height - system.height.value.toFloat() * paint.space) / 2f)
            .coerceAtLeast(0f)
        val group = pinnedAt?.let { furniture.at(it) }
        translate(top = centring) {
            translate(left = -scrollX.value.toFloat() * paint.space) {
                drawLines(system.lines, paint)
                drawBeams(system.beams, paint.space, colors.ink)
                drawCurves(system.curves, paint.space, colors.ink)
                system.glyphs.forEach { glyphCache.draw(this, it, paint, colors.ink) }
                system.notes.forEach { drawNote(paint, it, appearance(it)) }
            }
        }
        group?.let {
            drawPinnedBackdrop(it, paint)
            translate(top = centring) { drawPinnedContent(it, system.lines, paint) }
        }
        playheadX?.let { drawPlayhead((it - scrollX).value.toFloat() * paint.space, paint) }
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
    pinnedAt: Ticks? = null,
    appearance: (LaidOutNote) -> NoteAppearance = { NoteAppearance.PLAIN },
) {
    BoxWithConstraints(modifier) {
        StaffCanvas(
            system = system,
            metrics = metrics,
            modifier = Modifier.fillMaxSize(),
            staffSpace = fittedStaffSpace(system, maxHeight),
            scrollX = scrollX,
            playheadX = playheadX,
            pinnedAt = pinnedAt,
            appearance = appearance,
        )
    }
}

private fun fittedStaffSpace(system: StaffSystem, available: Dp): Dp {
    val needed = system.height.value.toFloat().coerceAtLeast(1f)
    return (available.value / needed).coerceIn(MIN_STAFF_SPACE.value, MAX_STAFF_SPACE.value).dp
}

private val DEFAULT_STAFF_SPACE = 7.dp
private val MIN_STAFF_SPACE = 4.dp
private val MAX_STAFF_SPACE = 14.dp
