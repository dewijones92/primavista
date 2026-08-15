package com.dewijones92.primavista.practice

import com.dewijones92.primavista.score.Clef
import com.dewijones92.primavista.score.KeySignature
import com.dewijones92.primavista.score.Midi
import com.dewijones92.primavista.score.PitchBand
import com.dewijones92.primavista.score.StaffGeometry

/** Bottom line to top line. See .claude/CODE-NOTES.md. */
internal val WHOLE_STAFF_STEPS: IntRange = 0..StaffGeometry.TOP_STEP

/** One leger line either side of the staff, and no more. See .claude/CODE-NOTES.md. */
internal val ONE_LEGER_LINE_STEPS: IntRange = -3..(StaffGeometry.TOP_STEP + 3)

/** The three in-staff bands, in the arithmetic `ScoreSkills.bandOf` reads them back with. */
internal fun bandSteps(band: PitchBand): IntRange {
    require(band in PitchBand.LowerStaff..PitchBand.UpperStaff) { "$band is not a band of the staff itself" }
    val first = (band.ordinal - PitchBand.LowerStaff.ordinal) * StaffGeometry.BAND_STEPS
    return first until first + StaffGeometry.BAND_STEPS
}

internal fun staffMidiRange(clef: Clef, key: KeySignature, steps: IntRange): ClosedRange<Midi> {
    val sounding = steps.map { step ->
        StaffGeometry.soundingNumber(StaffGeometry.pitchAt(StaffGeometry.diatonicIndexAt(clef, step), key))
    }
    val low = sounding.min().coerceIn(Midi.MIN, Midi.MAX)
    val high = sounding.max().coerceIn(Midi.MIN, Midi.MAX)
    return Midi(minOf(low, high))..Midi(maxOf(low, high))
}
