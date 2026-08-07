package com.dewijones92.primavista.notation

import com.dewijones92.primavista.score.NoteSymbol
import com.dewijones92.primavista.score.Rest

private const val WHOLE_REST_ROW = 1.0
private const val TIE_END_GAP = 0.15
private const val TIE_HEIGHT = 0.45
private const val TIE_BULGE = 0.4

private val REST_GLYPHS = mapOf(
    NoteSymbol.DoubleWhole to SmuflGlyph.RestDoubleWhole,
    NoteSymbol.Whole to SmuflGlyph.RestWhole,
    NoteSymbol.Half to SmuflGlyph.RestHalf,
    NoteSymbol.Quarter to SmuflGlyph.RestQuarter,
    NoteSymbol.Eighth to SmuflGlyph.Rest8th,
    NoteSymbol.Sixteenth to SmuflGlyph.Rest16th,
    NoteSymbol.ThirtySecond to SmuflGlyph.Rest32nd,
)

/** Rests where SMuFL designs them: the semibreve hangs from line four, the rest centre. */
internal fun emitRests(context: LayoutContext, sink: SystemSink) {
    context.score.events.filterIsInstance<Rest>()
        .filter { it.staff in context.staves }
        .forEach { rest ->
            val glyph = REST_GLYPHS.getValue(rest.duration.symbol)
            val laidOut =
                LaidOutGlyph(glyph, restX(context, rest, glyph).spaces, restY(context, rest).spaces)
            sink.glyphs += laidOut
            sink.glyphs += augmentationDots(context, rest.duration.dots, laidOut, rest.staff)
        }
}

private fun restY(context: LayoutContext, rest: Rest): Double {
    val topY = context.topY(rest.staff)
    return if (rest.duration.symbol == NoteSymbol.Whole) topY + WHOLE_REST_ROW else topY + MIDDLE_LINE
}

/** A rest is centred in the time it fills, which is what makes a bar's rest look deliberate. */
private fun restX(context: LayoutContext, rest: Rest, glyph: SmuflGlyph): Double {
    val startX = context.xOf(rest.onset)
    val endX = context.xOf(rest.endsAt)
    val slack = (endX - startX - context.metrics.advance(glyph)) / 2
    return startX + slack.coerceAtLeast(0.0)
}

/** One curve per tie the score names, drawn between the two notes the tie actually joins. */
internal fun tieCurves(
    context: LayoutContext,
    engraved: List<EngravedNote>,
    links: TieLinks,
): List<LaidOutCurve> {
    val byNote = engraved.associateBy { it.placement.noteIndex }
    return engraved.mapNotNull { to ->
        val fromIndex = links.continuesFrom[to.placement.noteIndex] ?: return@mapNotNull null
        byNote[fromIndex]?.let { tieCurve(context, it, to) }
    }
}

/** The tie arcs away from the stem, so a stem-up note is tied underneath. */
private fun tieCurve(context: LayoutContext, from: EngravedNote, to: EngravedNote): LaidOutCurve {
    val side = if (from.stemUp) 1.0 else -1.0
    val headWidth = context.metrics.advance(from.placement.head.glyph)
    val startX = from.placement.x + headWidth + TIE_END_GAP
    val endX = to.placement.x - TIE_END_GAP
    val startY = from.placement.y + side * TIE_HEIGHT
    val endY = to.placement.y + side * TIE_HEIGHT
    return LaidOutCurve(
        startX = startX.spaces,
        startY = startY.spaces,
        controlX = ((startX + endX) / 2).spaces,
        controlY = ((startY + endY) / 2 + side * TIE_BULGE).spaces,
        endX = endX.spaces,
        endY = endY.spaces,
        thickness = context.engraving.tieMidpointThickness,
    )
}
