package com.dewijones92.primavista.notation

import com.dewijones92.primavista.score.Clef
import com.dewijones92.primavista.score.Measure
import com.dewijones92.primavista.score.Score
import com.dewijones92.primavista.score.Staff
import com.dewijones92.primavista.score.Ticks

private val DEFAULT_CLEFS = mapOf(Staff.Upper to Clef.Treble, Staff.Lower to Clef.Bass)

internal fun GlyphMetrics.advance(glyph: SmuflGlyph): Double = advanceWidth(glyph).value

/** An anchor with its y flipped into the engine's downward axis. */
internal fun GlyphMetrics.anchorDown(glyph: SmuflGlyph, name: String): Pair<Double, Double>? =
    anchor(glyph, name)?.let { (x, y) -> x.value to -y.value }

/** The clef in force at a measure, carried forward from the last measure that named one. */
internal fun clefInForce(measures: List<Measure>, staff: Staff, measureIndex: Int): Clef {
    for (index in measureIndex.coerceAtMost(measures.lastIndex) downTo 0) {
        measures[index].clefs[staff]?.let { return it }
    }
    return DEFAULT_CLEFS[staff] ?: Clef.Treble
}

/** Everything the engraving functions need to place one glyph, gathered once. */
internal class LayoutContext(
    val score: Score,
    val measures: List<Measure>,
    val metrics: GlyphMetrics,
    val style: LayoutStyle,
    val spacing: MeasureSpacing,
    private val staffTopY: Map<Staff, Double>,
) {
    val staves: List<Staff> = staffTopY.keys.toList()

    val engraving: EngravingDefaults get() = metrics.engraving

    fun xOf(tick: Ticks): Double = spacing.xOf(tick.value)

    fun topY(staff: Staff): Double = staffTopY[staff] ?: 0.0

    fun middleY(staff: Staff): Double = topY(staff) + MIDDLE_LINE

    fun bottomY(staff: Staff): Double = topY(staff) + STAFF_HEIGHT

    fun measureIndexAt(onset: Ticks): Int =
        measures.indexOfLast { it.start <= onset }.coerceAtLeast(0)

    fun clefFor(staff: Staff, measureIndex: Int): Clef = clefInForce(measures, staff, measureIndex)
}

/** Collects geometry as the passes run, so each pass appends rather than merging lists. */
internal class SystemSink {
    val notes: MutableList<LaidOutNote> = mutableListOf()
    val glyphs: MutableList<LaidOutGlyph> = mutableListOf()
    val lines: MutableList<LaidOutLine> = mutableListOf()
    val beams: MutableList<LaidOutBeam> = mutableListOf()
    val curves: MutableList<LaidOutCurve> = mutableListOf()
}
