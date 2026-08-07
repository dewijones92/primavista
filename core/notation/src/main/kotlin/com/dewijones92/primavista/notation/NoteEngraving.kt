package com.dewijones92.primavista.notation

import com.dewijones92.primavista.score.Alter
import com.dewijones92.primavista.score.Letter
import com.dewijones92.primavista.score.Note
import com.dewijones92.primavista.score.NoteSymbol
import com.dewijones92.primavista.score.Staff
import com.dewijones92.primavista.score.Ticks
import com.dewijones92.primavista.score.TimeSignature
import kotlin.math.abs
import kotlin.math.round

private const val ACCIDENTAL_GAP = 0.2
private const val DOT_GAP = 0.25
private const val ON_LINE_TOLERANCE = 1.0e-9
private const val SECOND_TOLERANCE = HALF_SPACE + ON_LINE_TOLERANCE

private val FLAGS_BY_COUNT = mapOf(
    NoteSymbol.Eighth.flagCount to (SmuflGlyph.Flag8thUp to SmuflGlyph.Flag8thDown),
    NoteSymbol.Sixteenth.flagCount to (SmuflGlyph.Flag16thUp to SmuflGlyph.Flag16thDown),
    NoteSymbol.ThirtySecond.flagCount to (SmuflGlyph.Flag32ndUp to SmuflGlyph.Flag32ndDown),
)

/** What beaming groups by: one beam never spans two staves, voices, measures or beats. */
internal data class BeamKey(val staff: Staff, val voice: Int, val measureIndex: Int, val beat: Long)

/** A note's geometry before stems, which need the beam decision, and before ties, which need stems. */
internal data class Placement(
    val note: Note,
    val noteIndex: Int,
    val attackIndex: Int?,
    val head: LaidOutGlyph,
    val accidental: SmuflGlyph?,
    val key: BeamKey,
) {
    val x: Double get() = head.x.value
    val y: Double get() = head.y.value
    val symbol: NoteSymbol get() = note.duration.symbol

    fun movedTo(x: Double): Placement = copy(head = head.copy(x = x.spaces))
}

/** Notes sounding together on one staff and voice. One stem, one direction. See CODE-NOTES. */
internal class Chord(val placements: List<Placement>) {
    val staff: Staff get() = placements.first().note.staff
    val symbol: NoteSymbol get() = placements.first().symbol
    val onset: Ticks get() = placements.first().note.onset
    val key: BeamKey get() = placements.first().key
    val topY: Double get() = placements.minOf { it.y }
    val bottomY: Double get() = placements.maxOf { it.y }
}

internal data class EngravedNote(val placement: Placement, val laidOut: LaidOutNote, val stemUp: Boolean)

/**
 * Which attack each note belongs to, and which note a tie continuation continues.
 *
 * Both are null when the score names a tie whose attack is not present. See CODE-NOTES.
 */
internal class TieLinks(val attackIndex: List<Int?>, val continuesFrom: List<Int?>)

private class OpenTie(val noteIndex: Int, val attackIndex: Int?, val staff: Staff, val voice: Int)

internal fun tieLinksOf(notes: List<Note>): TieLinks {
    val attacks = arrayOfNulls<Int>(notes.size)
    var next = 0
    notes.forEachIndexed { index, note -> if (!note.tiedFromPrevious) attacks[index] = next++ }

    val continuesFrom = arrayOfNulls<Int>(notes.size)
    val open = mutableMapOf<Int, MutableList<OpenTie>>()
    notes.indices.sortedBy { notes[it].onset.value }.forEach { index ->
        val note = notes[index]
        if (note.tiedFromPrevious) {
            val match = takeOpenTie(open, note)
            attacks[index] = match?.attackIndex
            continuesFrom[index] = match?.noteIndex
        }
        if (note.tiedToNext) {
            open.getOrPut(note.pitch.midi.number) { mutableListOf() } +=
                OpenTie(index, attacks[index], note.staff, note.voice)
        }
    }
    return TieLinks(attacks.toList(), continuesFrom.toList())
}

/** Staff is matched last, because a tie crossing the staves is routine in piano writing. */
private fun takeOpenTie(open: MutableMap<Int, MutableList<OpenTie>>, note: Note): OpenTie? {
    val waiting = open[note.pitch.midi.number] ?: return null
    val match = waiting.lastOrNull { it.staff == note.staff && it.voice == note.voice }
        ?: waiting.lastOrNull { it.voice == note.voice }
        ?: waiting.lastOrNull()
        ?: return null
    waiting.remove(match)
    return match
}

internal fun placements(context: LayoutContext, links: TieLinks): List<Placement> {
    val notes = context.score.notes
    val ordered = notes.indices
        .filter { notes[it].staff in context.staves }
        .sortedWith(
            compareBy(
                { notes[it].onset.value },
                { notes[it].staff.ordinal },
                { notes[it].voice },
                { -notes[it].pitch.diatonicIndex },
            ),
        )
    val soundingAlters = mutableMapOf<Triple<Staff, Letter, Int>, Alter>()
    var currentMeasure = -1
    return ordered.map { index ->
        val measureIndex = context.measureIndexAt(notes[index].onset)
        if (measureIndex != currentMeasure) {
            soundingAlters.clear()
            currentMeasure = measureIndex
        }
        placement(context, index, measureIndex, links.attackIndex[index], soundingAlters)
    }
}

private fun placement(
    context: LayoutContext,
    noteIndex: Int,
    measureIndex: Int,
    attackIndex: Int?,
    soundingAlters: MutableMap<Triple<Staff, Letter, Int>, Alter>,
): Placement {
    val note = context.score.notes[noteIndex]
    val measure = context.measures[measureIndex]
    val clef = context.clefFor(note.staff, measureIndex)
    val alterKey = Triple(note.staff, note.pitch.letter, note.pitch.octave)
    val implied = keySignatureAlters(measure.key)[note.pitch.letter] ?: Alter.Natural
    val sounding = soundingAlters[alterKey] ?: implied
    val needsAccidental = note.pitch.alter != sounding && !note.tiedFromPrevious
    soundingAlters[alterKey] = note.pitch.alter
    val head = LaidOutGlyph(
        glyph = noteheadFor(note.duration.symbol),
        x = context.xOf(note.onset).spaces,
        y = noteY(clef, note.pitch, context.topY(note.staff)).spaces,
    )
    return Placement(
        note = note,
        noteIndex = noteIndex,
        attackIndex = attackIndex,
        head = head,
        accidental = if (needsAccidental) accidentalFor(note.pitch.alter) else null,
        key = BeamKey(
            staff = note.staff,
            voice = note.voice,
            measureIndex = measureIndex,
            beat = beatOf(note.onset.value - measure.start.value, measure.time),
        ),
    )
}

private fun beatOf(ticksIntoMeasure: Long, time: TimeSignature): Long = ticksIntoMeasure / beatTicks(time)

/** Notes sharing a staff, voice and onset are one chord, so they share one stem. */
internal fun chordsOf(placements: List<Placement>): List<Chord> =
    placements.groupBy { Triple(it.note.staff, it.note.voice, it.note.onset.value) }
        .values
        .map { Chord(it) }

internal fun stemUpFor(context: LayoutContext, chord: Chord): Boolean {
    val middle = context.middleY(chord.staff)
    val furthest = chord.placements.maxByOrNull { abs(it.y - middle) } ?: return false
    return furthest.y > middle
}

internal fun engrave(context: LayoutContext, chord: Chord, beam: BeamGroup?): List<EngravedNote> {
    val stemUp = beam?.stemUp ?: stemUpFor(context, chord)
    val heads = spreadSeconds(context, chord, stemUp)
    val originIndex = if (stemUp) heads.lastIndex else 0
    val stem = stemLine(context, heads, originIndex, stemUp, beam)
    val leftX = heads.minOf { it.x }
    return heads.mapIndexed { index, placement ->
        val own = if (index == originIndex) stem else null
        EngravedNote(
            placement = placement,
            stemUp = stemUp,
            laidOut = LaidOutNote(
                attackIndex = placement.attackIndex,
                onset = placement.note.onset,
                staff = placement.note.staff,
                notehead = placement.head,
                accidental = accidentalGlyph(context, placement, leftX),
                dots = dotGlyphs(context, placement),
                stem = own,
                flag = if (beam == null && own != null) flagGlyph(context, placement, own, stemUp) else null,
                legerLines = legerLines(context, placement),
            ),
        )
    }
}

/** Two noteheads a second apart cannot share a column, so one crosses the stem. See CODE-NOTES. */
private fun spreadSeconds(context: LayoutContext, chord: Chord, stemUp: Boolean): List<Placement> {
    val placements = chord.placements
    if (placements.size < 2) return placements
    val moved = placements.toMutableList()
    var previousY: Double? = null
    var previousDisplaced = false
    val order = if (stemUp) placements.indices.reversed() else placements.indices
    for (index in order) {
        val placement = placements[index]
        val adjacent = previousY?.let { abs(placement.y - it) <= SECOND_TOLERANCE } ?: false
        val displaced = adjacent && !previousDisplaced
        if (displaced) moved[index] = placement.movedTo(placement.x + crossStemOffset(context, placement, stemUp))
        previousY = placement.y
        previousDisplaced = displaced
    }
    return moved
}

private fun crossStemOffset(context: LayoutContext, placement: Placement, stemUp: Boolean): Double {
    val across = context.metrics.advance(placement.head.glyph) - context.engraving.stemThickness.value
    return if (stemUp) across else -across
}

private fun accidentalGlyph(context: LayoutContext, placement: Placement, leftX: Double): LaidOutGlyph? {
    val glyph = placement.accidental ?: return null
    val x = leftX - ACCIDENTAL_GAP - context.metrics.advance(glyph)
    return LaidOutGlyph(glyph, x.spaces, placement.head.y)
}

/** One stem for the whole chord: from the notehead it attaches to, past the far one. */
private fun stemLine(
    context: LayoutContext,
    heads: List<Placement>,
    originIndex: Int,
    stemUp: Boolean,
    beam: BeamGroup?,
): LaidOutLine? {
    val origin = heads[originIndex]
    if (!origin.symbol.hasStem) return null
    val (attachX, attachY) = stemAttachment(context.metrics, origin, stemUp)
    val far = if (stemUp) heads.first() else heads.last()
    val farY = stemAttachment(context.metrics, far, stemUp).second
    val middle = context.middleY(origin.note.staff)
    val standard = context.style.standardStemLength.value
    val endY = when {
        beam != null -> beam.yAt(attachX)
        stemUp -> (farY - standard).coerceAtMost(middle)
        else -> (farY + standard).coerceAtLeast(middle)
    }
    return LaidOutLine(
        x1 = attachX.spaces,
        y1 = attachY.spaces,
        x2 = attachX.spaces,
        y2 = endY.spaces,
        thickness = context.engraving.stemThickness,
    )
}

/** Where the stem meets the notehead, from the font's own anchor rather than by eye. */
internal fun stemAttachment(metrics: GlyphMetrics, placement: Placement, stemUp: Boolean): Pair<Double, Double> {
    val anchorName = if (stemUp) "stemUpSE" else "stemDownNW"
    val fallback = if (stemUp) metrics.advance(placement.head.glyph) to 0.0 else 0.0 to 0.0
    val (dx, dy) = metrics.anchorDown(placement.head.glyph, anchorName) ?: fallback
    return placement.x + dx to placement.y + dy
}

private fun dotGlyphs(context: LayoutContext, placement: Placement): List<LaidOutGlyph> =
    augmentationDots(
        context = context,
        dots = placement.note.duration.dots,
        after = placement.head,
        staff = placement.note.staff,
    )

/** Dots for a note or a rest. */
internal fun augmentationDots(
    context: LayoutContext,
    dots: Int,
    after: LaidOutGlyph,
    staff: Staff,
): List<LaidOutGlyph> {
    if (dots == 0) return emptyList()
    val y = after.y.value
    val relativeY = y - context.topY(staff)
    val onLine = abs(relativeY - round(relativeY)) < ON_LINE_TOLERANCE
    val dotY = if (onLine) y - HALF_SPACE else y
    val advance = context.metrics.advance(SmuflGlyph.AugmentationDot)
    val firstX = after.x.value + context.metrics.advance(after.glyph) + DOT_GAP
    return (0 until dots).map {
        LaidOutGlyph(SmuflGlyph.AugmentationDot, (firstX + it * advance).spaces, dotY.spaces)
    }
}

private fun flagGlyph(
    context: LayoutContext,
    placement: Placement,
    stem: LaidOutLine,
    stemUp: Boolean,
): LaidOutGlyph? {
    val pair = FLAGS_BY_COUNT[placement.symbol.flagCount] ?: return null
    val glyph = if (stemUp) pair.first else pair.second
    val anchorName = if (stemUp) "stemUpNW" else "stemDownSW"
    val (dx, dy) = context.metrics.anchorDown(glyph, anchorName) ?: (0.0 to 0.0)
    return LaidOutGlyph(glyph, (stem.x2.value - dx).spaces, (stem.y2.value - dy).spaces)
}

private fun legerLines(context: LayoutContext, placement: Placement): List<LaidOutLine> {
    val topY = context.topY(placement.note.staff)
    val extension = context.engraving.legerLineExtension.value
    val from = (placement.x - extension).spaces
    val to = (placement.x + context.metrics.advance(placement.head.glyph) + extension).spaces
    return legerLineRows(placement.y - topY).map { row ->
        val y = (topY + row).spaces
        LaidOutLine(from, y, to, y, context.engraving.legerLineThickness)
    }
}
