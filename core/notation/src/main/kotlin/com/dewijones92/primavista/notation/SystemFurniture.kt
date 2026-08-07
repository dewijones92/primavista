package com.dewijones92.primavista.notation

import com.dewijones92.primavista.score.Clef
import com.dewijones92.primavista.score.KeySignature
import com.dewijones92.primavista.score.Measure
import com.dewijones92.primavista.score.Staff
import com.dewijones92.primavista.score.TimeSignature
import kotlin.math.max
import kotlin.math.min

private const val BRACE_GAP = 0.4
private const val AFTER_BARLINE_GAP = 0.3
private const val CLEF_GAP = 0.6
private const val KEY_ACCIDENTAL_GAP = 0.12
private const val KEY_TO_TIME_GAP = 0.5
private const val TIME_TO_NOTES_GAP = 1.2
private const val NUMERATOR_ROW = 1.0
private const val DENOMINATOR_ROW = 3.0

/**
 * What one measure draws before its notes: clefs, a key signature, a time signature.
 *
 * The opening of the system and a mid-piece change are the same type on purpose — a clef change
 * that placed notes but drew nothing was a breach of docs/spec.md I2, and one code path is what
 * stops it coming back. See CODE-NOTES.
 */
internal class MeasureFurniture(
    private val metrics: GlyphMetrics,
    val clefs: Map<Staff, Clef>,
    val cancelling: KeySignature?,
    val key: KeySignature?,
    val time: TimeSignature?,
) {
    val cancelledCount: Int =
        if (cancelling != null && key != null) cancelledAccidentals(cancelling, key) else 0

    private val clefWidth: Double = clefs.values.maxOfOrNull { metrics.advance(glyphFor(it)) } ?: 0.0

    private val keyWidth: Double =
        cancelledCount * (metrics.advance(SmuflGlyph.AccidentalNatural) + KEY_ACCIDENTAL_GAP) +
            (key?.let { it.accidentalCount * (metrics.advance(keyAccidentalOf(it)) + KEY_ACCIDENTAL_GAP) } ?: 0.0)

    val timeWidth: Double =
        time?.let { max(digitsWidth(metrics, it.beats), digitsWidth(metrics, it.beatUnit)) } ?: 0.0

    val drawsKey: Boolean get() = keyWidth > 0.0

    val width: Double get() = columnsFrom(0.0).noteAreaX

    fun columnsFrom(x: Double): FurnitureColumns {
        var cursor = x + AFTER_BARLINE_GAP
        val clefX = cursor
        if (clefs.isNotEmpty()) cursor += clefWidth + CLEF_GAP
        val keyX = cursor
        if (drawsKey) cursor += keyWidth + KEY_TO_TIME_GAP
        val timeX = cursor
        if (time != null) cursor += timeWidth + TIME_TO_NOTES_GAP
        return FurnitureColumns(clefX, keyX, timeX, cursor)
    }

    fun cancelRows(clef: Clef): List<Double> =
        cancelling?.let { keySignatureRows(it, clef).takeLast(cancelledCount) } ?: emptyList()
}

internal class FurnitureColumns(
    val clefX: Double,
    val keyX: Double,
    val timeX: Double,
    val noteAreaX: Double,
)

/** Where the first measure begins: after the brace, when there is one. */
internal fun systemStartX(staves: List<Staff>, metrics: GlyphMetrics, style: LayoutStyle): Double {
    val brace = if (staves.size > 1) metrics.advance(SmuflGlyph.Brace) + BRACE_GAP else 0.0
    return style.leadingPadding.value + brace
}

internal fun planFurniture(
    measures: List<Measure>,
    staves: List<Staff>,
    metrics: GlyphMetrics,
): List<MeasureFurniture> = measures.indices.map { index ->
    val previous = measures.getOrNull(index - 1)
    val key = measures[index].key.takeIf { previous == null || previous.key != it }
    MeasureFurniture(
        metrics = metrics,
        clefs = clefsToDraw(measures, staves, index),
        cancelling = if (key == null) null else previous?.key,
        key = key,
        time = measures[index].time.takeIf { previous == null || previous.time != it },
    )
}

private fun clefsToDraw(measures: List<Measure>, staves: List<Staff>, index: Int): Map<Staff, Clef> {
    if (index == 0) return staves.associateWith { clefInForce(measures, it, 0) }
    return staves.mapNotNull { staff ->
        val named = measures[index].clefs[staff] ?: return@mapNotNull null
        if (named == clefInForce(measures, staff, index - 1)) null else staff to named
    }.toMap()
}

/** How many of the outgoing key's accidentals need a natural in front of the new signature. */
internal fun cancelledAccidentals(outgoing: KeySignature, incoming: KeySignature): Int {
    val sameDirection = outgoing.isSharpKey == incoming.isSharpKey
    val retained = if (sameDirection) min(outgoing.accidentalCount, incoming.accidentalCount) else 0
    return outgoing.accidentalCount - retained
}

internal fun keyAccidentalOf(key: KeySignature): SmuflGlyph =
    if (key.isSharpKey) SmuflGlyph.AccidentalSharp else SmuflGlyph.AccidentalFlat

private fun digitsOf(number: Int): List<SmuflGlyph> =
    number.toString().map { SmuflGlyph.timeSigDigit(it - '0') }

private fun digitsWidth(metrics: GlyphMetrics, number: Int): Double =
    digitsOf(number).sumOf { metrics.advance(it) }

/** Staff lines, brace, clefs, key and time signatures, barlines — everything that is not a note. */
internal fun emitFurniture(context: LayoutContext, plan: List<MeasureFurniture>, sink: SystemSink) {
    context.staves.forEach { emitStaffLines(context, it, sink) }
    context.spacing.anchors.forEachIndexed { index, anchor ->
        val pass = FurniturePass(context, plan[index], plan[index].columnsFrom(anchor.x.value), index)
        context.staves.forEach { pass.emit(it, sink) }
    }
    emitBrace(context, sink)
    emitBarlines(context, sink)
}

/** One measure's furniture on one staff. A class so each step needs only what it uses. */
private class FurniturePass(
    private val context: LayoutContext,
    private val furniture: MeasureFurniture,
    private val columns: FurnitureColumns,
    private val measureIndex: Int,
) {
    fun emit(staff: Staff, sink: SystemSink) {
        emitClef(staff, sink)
        emitKeySignature(staff, sink)
        emitTimeSignature(staff, sink)
    }

    private fun emitClef(staff: Staff, sink: SystemSink) {
        val clef = furniture.clefs[staff] ?: return
        val baseline = yOfDiatonicIndex(clef, clefBaselineDiatonicIndex(clef), context.topY(staff))
        sink.glyphs += LaidOutGlyph(glyphFor(clef), columns.clefX.spaces, baseline.spaces)
    }

    private fun emitKeySignature(staff: Staff, sink: SystemSink) {
        if (!furniture.drawsKey) return
        val clef = context.clefFor(staff, measureIndex)
        val topY = context.topY(staff)
        var x = columns.keyX
        val naturalStep = context.metrics.advance(SmuflGlyph.AccidentalNatural) + KEY_ACCIDENTAL_GAP
        furniture.cancelRows(clef).forEach { row ->
            sink.glyphs += LaidOutGlyph(SmuflGlyph.AccidentalNatural, x.spaces, (topY + row).spaces)
            x += naturalStep
        }
        val key = furniture.key ?: return
        val glyph = keyAccidentalOf(key)
        val step = context.metrics.advance(glyph) + KEY_ACCIDENTAL_GAP
        keySignatureRows(key, clef).forEach { row ->
            sink.glyphs += LaidOutGlyph(glyph, x.spaces, (topY + row).spaces)
            x += step
        }
    }

    private fun emitTimeSignature(staff: Staff, sink: SystemSink) {
        val time = furniture.time ?: return
        val topY = context.topY(staff)
        val rows = listOf(
            digitsOf(time.beats) to NUMERATOR_ROW,
            digitsOf(time.beatUnit) to DENOMINATOR_ROW,
        )
        rows.forEach { (digits, row) ->
            val width = digits.sumOf { context.metrics.advance(it) }
            var x = columns.timeX + (furniture.timeWidth - width) / 2
            digits.forEach { digit ->
                sink.glyphs += LaidOutGlyph(digit, x.spaces, (topY + row).spaces)
                x += context.metrics.advance(digit)
            }
        }
    }
}

private fun emitStaffLines(context: LayoutContext, staff: Staff, sink: SystemSink) {
    val topY = context.topY(staff)
    val leftX = context.spacing.startX.spaces
    val rightX = context.spacing.endX.spaces
    repeat(STAFF_LINE_COUNT) { line ->
        val y = (topY + line).spaces
        sink.lines += LaidOutLine(leftX, y, rightX, y, context.engraving.staffLineThickness)
    }
}

/** The brace is stretched to the system it spans, not drawn at the font's own four spaces. */
private fun emitBrace(context: LayoutContext, sink: SystemSink) {
    if (context.staves.size < 2) return
    val top = context.topY(context.staves.first())
    val bottom = context.bottomY(context.staves.last())
    val box = context.metrics.boundingBox(SmuflGlyph.Brace)
    val scaleY = (bottom - top) / box.height.value
    sink.glyphs += LaidOutGlyph(
        glyph = SmuflGlyph.Brace,
        x = (context.spacing.startX - BRACE_GAP - context.metrics.advance(SmuflGlyph.Brace)).spaces,
        y = (bottom + box.southWestY.value * scaleY).spaces,
        scaleY = scaleY,
    )
}

private fun emitBarlines(context: LayoutContext, sink: SystemSink) {
    val fromY = context.topY(context.staves.first()).spaces
    val toY = context.bottomY(context.staves.last()).spaces
    val thin = context.engraving.thinBarlineThickness
    context.spacing.anchors.forEachIndexed { index, anchor ->
        if (index > 0 || context.staves.size > 1) {
            sink.lines += LaidOutLine(anchor.x, fromY, anchor.x, toY, thin)
        }
    }
    val thick = context.engraving.thickBarlineThickness
    val thickX = context.spacing.endX
    val thinX = thickX - context.engraving.barlineSeparation.value - thick.value
    sink.lines += LaidOutLine(thinX.spaces, fromY, thinX.spaces, toY, thin)
    sink.lines += LaidOutLine(thickX.spaces, fromY, thickX.spaces, toY, thick)
}
