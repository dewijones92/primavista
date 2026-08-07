package com.dewijones92.primavista.notation

import com.dewijones92.primavista.common.Diag
import com.dewijones92.primavista.common.NoOpDiag
import com.dewijones92.primavista.score.KeySignature
import com.dewijones92.primavista.score.Measure
import com.dewijones92.primavista.score.MusicalTime
import com.dewijones92.primavista.score.Score
import com.dewijones92.primavista.score.Staff
import com.dewijones92.primavista.score.Ticks
import com.dewijones92.primavista.score.TimeSignature
import java.util.IdentityHashMap
import kotlin.math.max

private const val VERTICAL_MARGIN = 1.0
private const val DIAG_TAG = "notation"

/**
 * Classical engraving of a [Score], one engine for every clef. Vertical placement is
 * [yOfDiatonicIndex]; horizontal is measure by measure. See CODE-NOTES.
 */
public class ClassicalStaffLayout(private val diag: Diag = NoOpDiag) : StaffLayout {
    override fun layout(score: Score, metrics: GlyphMetrics, style: LayoutStyle): StaffSystem {
        val staves = score.staves.distinct().sortedBy { it.ordinal }.ifEmpty { listOf(Staff.Upper) }
        val staffTopY = staves.withIndex().associate { (index, staff) ->
            staff to index * style.staffSeparation.value
        }
        val measures = measuresOf(score)
        val furniture = planFurniture(measures, staves, metrics)
        val spacing = MeasureSpacing(
            measureAnchorsOf(
                measures = measures,
                furniture = furniture,
                spacesPerQuarter = spacesPerQuarter(score.events.map { it.onset.value }, style),
                startX = systemStartX(staves, metrics, style),
                endTick = endTickOf(score, measures),
            ),
        )
        val context = LayoutContext(score, measures, metrics, style, spacing, staffTopY)

        val sink = SystemSink()
        val links = tieLinksOf(score.notes)
        val chords = chordsOf(placements(context, links))
        val groups = beamGroups(context, chords)
        val beamOf = IdentityHashMap<Chord, BeamGroup>()
        groups.forEach { group -> group.columns.forEach { beamOf[it] = group } }
        val engraved = chords.flatMap { engrave(context, it, beamOf[it]) }
        sink.notes += engraved.map { it.laidOut }
        groups.forEach { sink.beams += beamsOf(context, it) }
        sink.curves += tieCurves(context, engraved, links)
        emitRests(context, sink)
        emitFurniture(context, furniture, sink)

        val system = StaffSystem(
            notes = sink.notes,
            glyphs = sink.glyphs,
            lines = sink.lines,
            beams = sink.beams,
            curves = sink.curves,
            width = (spacing.endX + style.trailingPadding.value).spaces,
            height = StaffSpaces.ZERO,
            staffTopY = staffTopY.mapValues { (_, y) -> y.spaces },
            measureAnchors = spacing.anchors,
        ).fitVertically(metrics, VERTICAL_MARGIN)

        diag.event(DIAG_TAG, describe(score, system, chords, furniture))
        return system
    }

    override fun xOf(system: StaffSystem, position: Ticks, style: LayoutStyle): StaffSpaces {
        if (system.measureAnchors.isEmpty()) return style.leadingPadding
        return MeasureSpacing(system.measureAnchors).xOf(position.value).spaces
    }
}

private fun describe(
    score: Score,
    system: StaffSystem,
    chords: List<Chord>,
    furniture: List<MeasureFurniture>,
): String {
    val changes = furniture.drop(1)
    return "dewidebug layout id=${score.id.value} staves=${system.staffTopY.size} " +
        "bars=${system.measureAnchors.size} notes=${system.notes.size} chords=${chords.size} " +
        "beams=${system.beams.size} ties=${system.curves.size} " +
        "clefChanges=${changes.count { it.clefs.isNotEmpty() }} " +
        "keyChanges=${changes.count { it.drawsKey }} " +
        "timeChanges=${changes.count { it.time != null }} " +
        "unresolvedTies=${system.notes.count { it.attackIndex == null }} " +
        "width=${system.width.value} height=${system.height.value}"
}

/** A score with no measures still has to be drawn, so it gets one bar of the default furniture. */
private fun measuresOf(score: Score): List<Measure> =
    score.measures.sortedBy { it.start.value }.ifEmpty {
        listOf(Measure(0, Ticks.ZERO, TimeSignature.FourFour, KeySignature.C, emptyMap()))
    }

private fun endTickOf(score: Score, measures: List<Measure>): Long {
    val fromMeasures = measures.last().let { it.start.value + it.time.measureTicks.value }
    return max(fromMeasures, score.endsAt.value).coerceAtLeast(MusicalTime.TICKS_PER_QUARTER)
}
