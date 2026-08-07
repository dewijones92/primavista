package com.dewijones92.primavista.score

import kotlin.math.abs
import kotlin.math.max

private const val LEAP_HEADROOM_SEMITONES = 2
private const val BAND_WIDENING_STEPS = 1
private const val FAR_BAND_STEPS = 8

/** The staff a targeted clef belongs on: the one already carrying it, else the nearest reader. */
internal fun DifficultySpec.staffFor(clef: Clef): Staff =
    clefs.entries.firstOrNull { it.value == clef }?.key
        ?: staves.minByOrNull { staff ->
            abs((clefs[staff] ?: staffClefDefault(staff)).referenceDiatonicIndex - clef.referenceDiatonicIndex)
        }
        ?: staves.first()

internal fun DifficultySpec.withClefRegion(clef: Clef, band: PitchBand): DifficultySpec {
    val steps = stepsOf(band)
    val widened = (steps.first - BAND_WIDENING_STEPS)..(steps.last + BAND_WIDENING_STEPS)
    return withStaffRange(clef, widened)
}

internal fun DifficultySpec.withLegerLines(clef: Clef, count: Int, above: Boolean): DifficultySpec {
    val wanted = count.coerceAtLeast(1) * StaffGeometry.STEPS_PER_LEGER_LINE
    val previous = wanted - StaffGeometry.STEPS_PER_LEGER_LINE
    val steps = if (above) {
        (StaffGeometry.TOP_STEP + previous)..(StaffGeometry.TOP_STEP + wanted + 1)
    } else {
        (-wanted - 1)..(-previous)
    }
    return withStaffRange(clef, steps)
}

internal fun DifficultySpec.withRhythmFigure(figure: SkillTag.RhythmFigure): DifficultySpec {
    val narrowed = copy(
        symbols = setOf(figure.symbol),
        maxDots = figure.dots.coerceIn(0, Duration.MAX_DOTS),
        allowTuplets = figure.tupletNumerator > 1,
    )
    return narrowed.widenUntilBarsFill(symbols)
}

internal fun DifficultySpec.withLeap(semitones: Int): DifficultySpec {
    val leap = max(maxLeapSemitones, semitones)
    val needed = leap + LEAP_HEADROOM_SEMITONES
    val widened = range.mapValues { (_, bounds) ->
        val shortfall = needed - (bounds.endInclusive.number - bounds.start.number)
        if (shortfall <= 0) {
            bounds
        } else {
            val low = (bounds.start.number - shortfall).coerceAtLeast(Midi.MIN)
            val high = (bounds.endInclusive.number + shortfall).coerceAtMost(Midi.MAX)
            Midi(low)..Midi(high)
        }
    }
    return copy(maxLeapSemitones = leap, range = widened)
}

internal fun DifficultySpec.withBothHands(): DifficultySpec {
    val missing = Staff.entries.firstOrNull { it !in staves }
        ?: return copy(bothHandsActive = true)
    val clef = staffClefDefault(missing)
    return copy(
        staves = staves + missing,
        clefs = clefs + (missing to clef),
        range = range + (missing to midiRangeOf(clef, key, 0..StaffGeometry.TOP_STEP)),
        bothHandsActive = true,
    )
}

internal fun midiRangeOf(clef: Clef, key: KeySignature, steps: IntRange): ClosedRange<Midi> {
    val sounding = steps.map { step ->
        StaffGeometry.soundingNumber(StaffGeometry.pitchAt(StaffGeometry.diatonicIndexAt(clef, step), key))
    }
    val low = sounding.min().coerceIn(Midi.MIN, Midi.MAX)
    val high = sounding.max().coerceIn(Midi.MIN, Midi.MAX)
    return Midi(minOf(low, high))..Midi(maxOf(low, high))
}

private fun DifficultySpec.withStaffRange(clef: Clef, steps: IntRange): DifficultySpec {
    val staff = staffFor(clef)
    return copy(
        clefs = clefs + (staff to clef),
        range = range + (staff to midiRangeOf(clef, key, steps)),
    )
}

/** Narrowing to one note value can leave a bar unfillable, and a short bar is a real bug. */
private fun DifficultySpec.widenUntilBarsFill(fallbacks: Set<NoteSymbol>): DifficultySpec {
    var widened = this
    if (BarFill(time.measureTicks.value, rhythmChoices(widened)).isPossible) return widened
    val candidates = (fallbacks + NoteSymbol.entries)
        .filter { it.undottedTicks <= time.measureTicks.value }
        .sortedByDescending { it.undottedTicks }
    for (symbol in candidates) {
        widened = widened.copy(symbols = widened.symbols + symbol)
        if (BarFill(time.measureTicks.value, rhythmChoices(widened)).isPossible) break
    }
    return widened
}

private fun stepsOf(band: PitchBand): IntRange {
    val near = StaffGeometry.NEAR_OUTSIDE_STEPS
    val top = StaffGeometry.TOP_STEP
    return when (band) {
        PitchBand.FarBelowStaff -> (-near - FAR_BAND_STEPS)..(-near - 1)
        PitchBand.BelowStaff -> -near..-1
        PitchBand.AboveStaff -> (top + 1)..(top + near)
        PitchBand.FarAboveStaff -> (top + near + 1)..(top + near + FAR_BAND_STEPS)
        PitchBand.LowerStaff, PitchBand.MiddleStaff, PitchBand.UpperStaff -> {
            val first = (band.ordinal - PitchBand.LowerStaff.ordinal) * StaffGeometry.BAND_STEPS
            first until first + StaffGeometry.BAND_STEPS
        }
    }
}
