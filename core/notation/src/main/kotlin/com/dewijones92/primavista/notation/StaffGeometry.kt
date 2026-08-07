package com.dewijones92.primavista.notation

import com.dewijones92.primavista.score.Alter
import com.dewijones92.primavista.score.Clef
import com.dewijones92.primavista.score.KeySignature
import com.dewijones92.primavista.score.Letter
import com.dewijones92.primavista.score.MusicalTime
import com.dewijones92.primavista.score.NoteSymbol
import com.dewijones92.primavista.score.Pitch
import com.dewijones92.primavista.score.TimeSignature
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

internal const val STAFF_LINE_COUNT: Int = 5
internal const val STAFF_HEIGHT: Double = 4.0
internal const val MIDDLE_LINE: Double = 2.0
internal const val HALF_SPACE: Double = 0.5
internal const val STEPS_ACROSS_STAFF: Int = 8

private const val QUARTERS_PER_WHOLE: Long = 4
private const val SHARP_STEP: Int = 4
private const val FLAT_STEP: Int = 3
private const val SHARP_CEILING_ABOVE_FIRST: Int = 1
private const val FLAT_CEILING_ABOVE_FIRST: Int = 3
private const val COMPOUND_GROUPING: Int = 3
private const val COMPOUND_MIN_BEAT_UNIT: Int = 8

private val SHARP_ORDER = listOf(Letter.F, Letter.C, Letter.G, Letter.D, Letter.A, Letter.E, Letter.B)
private val FLAT_ORDER = SHARP_ORDER.reversed()

internal val Double.spaces: StaffSpaces get() = StaffSpaces(this)

/** The one vertical mapping: half a staff space per diatonic step, y increasing downwards. */
internal fun yOfDiatonicIndex(clef: Clef, diatonicIndex: Int, staffTopY: Double): Double =
    staffTopY + STAFF_HEIGHT - (diatonicIndex - clef.referenceDiatonicIndex) * HALF_SPACE

internal fun noteY(clef: Clef, pitch: Pitch, staffTopY: Double): Double =
    yOfDiatonicIndex(clef, pitch.diatonicIndex, staffTopY)

/** Rows, relative to the staff's top line, needing a leger line for a notehead at [relativeY]. */
internal fun legerLineRows(relativeY: Double): List<Double> = when {
    relativeY < 0.0 -> (1..floor(-relativeY).toInt()).map { -it.toDouble() }
    relativeY > STAFF_HEIGHT -> (1..floor(relativeY - STAFF_HEIGHT).toInt()).map { STAFF_HEIGHT + it }
    else -> emptyList()
}

internal fun glyphFor(clef: Clef): SmuflGlyph =
    SmuflGlyph.entries.first { it.glyphName == clef.glyphName }

/**
 * The staff line a clef glyph's own origin sits on, as the diatonic index of its pitch.
 * Derived from the clef's letter rather than branched per clef — see CODE-NOTES.
 */
internal fun clefBaselineDiatonicIndex(clef: Clef): Int {
    val middle = clef.referenceDiatonicIndex + STEPS_ACROSS_STAFF / 2
    val letter = Letter.entries.firstOrNull { it.name == clef.glyphName.take(1).uppercase() } ?: return middle
    return (0..STEPS_ACROSS_STAFF)
        .map { clef.referenceDiatonicIndex + it }
        .filter { it % Pitch.LETTERS_PER_OCTAVE == letter.diatonicStep }
        .minByOrNull { abs(it - middle) } ?: middle
}

internal fun keySignatureAlters(key: KeySignature): Map<Letter, Alter> {
    val alter = if (key.isSharpKey) Alter.Sharp else Alter.Flat
    return signatureOrder(key).take(key.accidentalCount).associateWith { alter }
}

/** Rows for a key signature's accidentals, in the conventional order. See CODE-NOTES. */
internal fun keySignatureRows(key: KeySignature, clef: Clef): List<Double> {
    val step = if (key.isSharpKey) SHARP_STEP else FLAT_STEP
    val first = highestStaffOffsetOf(signatureOrder(key).first(), clef)
    val ceiling = first + if (key.isSharpKey) SHARP_CEILING_ABOVE_FIRST else FLAT_CEILING_ABOVE_FIRST
    return (0 until key.accidentalCount).map { index ->
        var offset = first + (step * index) % Pitch.LETTERS_PER_OCTAVE
        while (offset > ceiling) offset -= Pitch.LETTERS_PER_OCTAVE
        STAFF_HEIGHT - offset * HALF_SPACE
    }
}

private fun signatureOrder(key: KeySignature): List<Letter> =
    if (key.isSharpKey) SHARP_ORDER else FLAT_ORDER

private fun highestStaffOffsetOf(letter: Letter, clef: Clef): Int =
    (STEPS_ACROSS_STAFF downTo 0).first { offset ->
        (clef.referenceDiatonicIndex + offset) % Pitch.LETTERS_PER_OCTAVE == letter.diatonicStep
    }

/** The beat a beam may not cross. Compound signatures beat in threes, so 6/8 groups in threes. */
internal fun beatTicks(time: TimeSignature): Long {
    val unit = MusicalTime.TICKS_PER_QUARTER * QUARTERS_PER_WHOLE / time.beatUnit
    val compound = time.beatUnit >= COMPOUND_MIN_BEAT_UNIT && time.beats % COMPOUND_GROUPING == 0
    return if (compound) unit * COMPOUND_GROUPING else unit
}

internal fun noteheadFor(symbol: NoteSymbol): SmuflGlyph = when (symbol) {
    NoteSymbol.DoubleWhole -> SmuflGlyph.NoteheadDoubleWhole
    NoteSymbol.Whole -> SmuflGlyph.NoteheadWhole
    NoteSymbol.Half -> SmuflGlyph.NoteheadHalf
    NoteSymbol.Quarter, NoteSymbol.Eighth, NoteSymbol.Sixteenth, NoteSymbol.ThirtySecond ->
        SmuflGlyph.NoteheadBlack
}

internal fun accidentalFor(alter: Alter): SmuflGlyph = when (alter.semitones) {
    Alter.DoubleFlat.semitones -> SmuflGlyph.AccidentalDoubleFlat
    Alter.Flat.semitones -> SmuflGlyph.AccidentalFlat
    Alter.Sharp.semitones -> SmuflGlyph.AccidentalSharp
    Alter.DoubleSharp.semitones -> SmuflGlyph.AccidentalDoubleSharp
    else -> SmuflGlyph.AccidentalNatural
}

/** One scale for the whole system, widened until the closest onsets clear. See CODE-NOTES. */
internal fun spacesPerQuarter(onsets: List<Long>, style: LayoutStyle): Double {
    val gaps = onsets.distinct().sorted().zipWithNext { earlier, later -> later - earlier }
    val closest = gaps.filter { it > 0 }.minOrNull() ?: return style.quarterNoteWidth.value
    val needed = style.minimumNoteSpacing.value * MusicalTime.TICKS_PER_QUARTER / closest
    return max(style.quarterNoteWidth.value, needed)
}
