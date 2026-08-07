package com.dewijones92.primavista.notation

import kotlin.math.abs
import kotlin.math.min

private const val MIN_STEM_LENGTH = 2.0
private const val MAX_BEAM_RISE = 1.5
private const val MAX_BEAM_SLOPE = 0.25
private const val STUB_LENGTH = 1.0
private const val FIRST_SECONDARY_LEVEL = 2
private const val MIN_BEAMED_COLUMNS = 2

/** One beam and its chords, one [columns] entry per onset. [startY]/[endY] are the centre line. */
internal class BeamGroup(
    val columns: List<Chord>,
    val stemUp: Boolean,
    val startX: Double,
    val startY: Double,
    val endX: Double,
    val endY: Double,
) {
    fun yAt(x: Double): Double {
        val run = endX - startX
        return if (run == 0.0) startY else startY + (endY - startY) * (x - startX) / run
    }
}

internal fun beamGroups(context: LayoutContext, chords: List<Chord>): List<BeamGroup> =
    chords.groupBy { it.key }.values.flatMap { beamsWithinBeat(context, it) }

private fun beamsWithinBeat(context: LayoutContext, withinBeat: List<Chord>): List<BeamGroup> {
    val columns = withinBeat.sortedBy { it.onset.value }
    val groups = mutableListOf<BeamGroup>()
    var run = mutableListOf<Chord>()
    for (column in columns) {
        val beamable = column.symbol.isBeamable
        val contiguous = run.isEmpty() || run.last().placements.first().note.endsAt == column.onset
        if (!beamable || !contiguous || run.size >= context.style.beamNoteCountLimit) {
            closeRun(context, run)?.let { groups += it }
            run = mutableListOf()
        }
        if (beamable) run += column
    }
    closeRun(context, run)?.let { groups += it }
    return groups
}

private fun closeRun(context: LayoutContext, columns: List<Chord>): BeamGroup? {
    if (columns.size < MIN_BEAMED_COLUMNS) return null
    val staff = columns.first().staff
    val middle = context.middleY(staff)
    val furthest = columns.flatMap { it.placements }.maxByOrNull { abs(it.y - middle) }?.y ?: middle
    val stemUp = furthest > middle
    val xs = columns.map { stemXOf(context, it, stemUp) }
    val tips = columns.map { tipOf(context, it, stemUp) }

    val rise = min(MAX_BEAM_RISE, abs(xs.last() - xs.first()) * MAX_BEAM_SLOPE)
    val slopedBy = (tips.last() - tips.first()).coerceIn(-rise, rise)
    val middleOfTips = (tips.first() + tips.last()) / 2
    val start = middleOfTips - slopedBy / 2
    val end = middleOfTips + slopedBy / 2

    val unshifted = BeamGroup(columns, stemUp, xs.first(), start, xs.last(), end)
    val corrections = columns.indices.map { clearanceOf(columns[it], stemUp) - unshifted.yAt(xs[it]) }
    val shift = if (stemUp) {
        corrections.min().coerceAtMost(0.0)
    } else {
        corrections.max().coerceAtLeast(0.0)
    }
    return BeamGroup(columns, stemUp, xs.first(), start + shift, xs.last(), end + shift)
}

/** The beam y that leaves the shortest stem in a chord at [MIN_STEM_LENGTH]. */
private fun clearanceOf(column: Chord, stemUp: Boolean): Double =
    if (stemUp) column.topY - MIN_STEM_LENGTH else column.bottomY + MIN_STEM_LENGTH

/** Where a standard-length stem would end, measured from the notehead at the stem's far end. */
private fun tipOf(context: LayoutContext, column: Chord, stemUp: Boolean): Double {
    val length = context.style.standardStemLength.value
    return if (stemUp) column.topY - length else column.bottomY + length
}

/** The chord's one stem x, taken from the same notehead the engraver attaches the stem to. */
internal fun stemXOf(context: LayoutContext, column: Chord, stemUp: Boolean): Double =
    stemAttachment(context.metrics, stemOriginOf(column, stemUp), stemUp).first

internal fun stemOriginOf(column: Chord, stemUp: Boolean): Placement =
    if (stemUp) column.placements.last() else column.placements.first()

internal fun beamsOf(context: LayoutContext, group: BeamGroup): List<LaidOutBeam> {
    val thickness = context.engraving.beamThickness
    val towardsNoteheads = if (group.stemUp) 1.0 else -1.0
    val step = (thickness.value + context.engraving.beamSpacing.value) * towardsNoteheads
    val xs = group.columns.map { stemXOf(context, it, group.stemUp) }
    val beams = mutableListOf(
        LaidOutBeam(
            startX = group.startX.spaces,
            startY = group.startY.spaces,
            endX = group.endX.spaces,
            endY = group.endY.spaces,
            thickness = thickness,
        ),
    )
    val deepest = group.columns.maxOf { it.symbol.flagCount }
    for (level in FIRST_SECONDARY_LEVEL..deepest) {
        beams += secondaryBeams(group, xs, level, step * (level - 1), thickness)
    }
    return beams
}

/** One subdivision level; a level on a single note becomes a stub. See CODE-NOTES. */
private fun secondaryBeams(
    group: BeamGroup,
    xs: List<Double>,
    level: Int,
    offset: Double,
    thickness: StaffSpaces,
): List<LaidOutBeam> {
    val present = group.columns.map { it.symbol.flagCount >= level }
    val beams = mutableListOf<LaidOutBeam>()
    var index = 0
    while (index < present.size) {
        if (!present[index]) {
            index++
            continue
        }
        var last = index
        while (last + 1 < present.size && present[last + 1]) last++
        val span = when {
            last > index -> xs[index] to xs[last]
            index > 0 -> xs[index] - STUB_LENGTH to xs[index]
            else -> xs[index] to xs[index] + STUB_LENGTH
        }
        beams += LaidOutBeam(
            startX = span.first.spaces,
            startY = (group.yAt(span.first) + offset).spaces,
            endX = span.second.spaces,
            endY = (group.yAt(span.second) + offset).spaces,
            thickness = thickness,
        )
        index = last + 1
    }
    return beams
}
