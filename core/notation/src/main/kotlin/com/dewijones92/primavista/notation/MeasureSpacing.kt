package com.dewijones92.primavista.notation

import com.dewijones92.primavista.score.Measure
import com.dewijones92.primavista.score.MusicalTime
import com.dewijones92.primavista.score.Ticks
import kotlin.math.max

/**
 * The one musical-time-to-x mapping, piecewise per measure. See CODE-NOTES.
 */
internal class MeasureSpacing(val anchors: List<MeasureAnchor>) {
    val startX: Double = anchors.firstOrNull()?.x?.value ?: 0.0

    val endX: Double = anchors.lastOrNull()?.let { it.x.value + it.width.value } ?: 0.0

    fun xOf(tick: Long): Double {
        val first = anchors.firstOrNull() ?: return 0.0
        if (tick <= first.start.value) return first.noteAreaX.value
        val anchor = anchors.last { it.start.value <= tick }
        val duration = anchor.durationTicks.value
        if (duration <= 0L) return anchor.noteAreaX.value
        val noteWidth = anchor.x.value + anchor.width.value - anchor.noteAreaX.value
        return anchor.noteAreaX.value + noteWidth * (tick - anchor.start.value) / duration
    }
}

/**
 * Lays the measures out left to right, each taking its furniture's width plus its duration's.
 */
internal fun measureAnchorsOf(
    measures: List<Measure>,
    furniture: List<MeasureFurniture>,
    spacesPerQuarter: Double,
    startX: Double,
    endTick: Long,
): List<MeasureAnchor> {
    var cursor = startX
    return measures.mapIndexed { index, measure ->
        val duration = durationOf(measures, index, endTick)
        val furnitureWidth = furniture[index].width
        val noteWidth = duration * spacesPerQuarter / MusicalTime.TICKS_PER_QUARTER
        val anchor = MeasureAnchor(
            measureIndex = measure.index,
            start = measure.start,
            durationTicks = Ticks(duration),
            x = cursor.spaces,
            noteAreaX = (cursor + furnitureWidth).spaces,
            width = (furnitureWidth + noteWidth).spaces,
        )
        cursor += anchor.width.value
        anchor
    }
}

/** The last measure stretches to hold anything overrunning it, so nothing is drawn past the end. */
private fun durationOf(measures: List<Measure>, index: Int, endTick: Long): Long {
    val measure = measures[index]
    val next = measures.getOrNull(index + 1)
    if (next != null) return max(1L, next.start.value - measure.start.value)
    return max(measure.time.measureTicks.value, endTick - measure.start.value)
}
