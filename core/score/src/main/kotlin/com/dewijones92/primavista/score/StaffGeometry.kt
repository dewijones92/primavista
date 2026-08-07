package com.dewijones92.primavista.score

/**
 * Where a pitch sits on a staff, in diatonic steps from the bottom line.
 *
 * Everything here is arithmetic on [Clef.referenceDiatonicIndex] rather than a branch per
 * clef, which is what keeps treble, bass and alto one engine (CLAUDE.md, the twin laws).
 */
public object StaffGeometry {
    /** Bottom line is step 0; five lines and four spaces put the top line at step 8. */
    public const val TOP_STEP: Int = 8

    public const val STEPS_PER_LEGER_LINE: Int = 2

    /** The nine in-staff positions split three ways, giving lower/middle/upper. */
    public const val BAND_STEPS: Int = 3

    /** Beyond this far outside the staff, reading stops being "a few leger lines". */
    public const val NEAR_OUTSIDE_STEPS: Int = 4

    public fun stepOf(clef: Clef, pitch: Pitch): Int = pitch.diatonicIndex - clef.referenceDiatonicIndex

    public fun diatonicIndexAt(clef: Clef, step: Int): Int = clef.referenceDiatonicIndex + step

    public fun pitchAt(diatonicIndex: Int, key: KeySignature): Pitch {
        val letter = Letter.entries[diatonicIndex.mod(Pitch.LETTERS_PER_OCTAVE)]
        val octave = diatonicIndex.floorDiv(Pitch.LETTERS_PER_OCTAVE)
        return Pitch(letter, KeySignatureAlterations.impliedAlter(key, letter), octave)
    }

    /** Sounding pitch without building a [Midi], so out-of-instrument candidates can be filtered. */
    public fun soundingNumber(pitch: Pitch): Int =
        Pitch.SEMITONES_PER_OCTAVE * (pitch.octave + 1) + pitch.letter.semitonesFromC + pitch.alter.semitones
}
