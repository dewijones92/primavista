package com.dewijones92.primavista.score

/**
 * A MIDI note number. Middle C (C4) is 60.
 *
 * Separate from [Pitch] on purpose: F#4 and Gb4 are the *same* [Midi] and *different*
 * [Pitch]es, and this app is about reading notation, so the distinction is load-bearing
 * rather than pedantic. A judge compares [Midi] (you played the right sound); a staff
 * draws [Pitch] (it sits on a different line with a different accidental).
 */
@JvmInline
public value class Midi(public val number: Int) : Comparable<Midi> {
    init {
        require(number in MIN..MAX) { "MIDI note $number outside $MIN..$MAX" }
    }

    override fun compareTo(other: Midi): Int = number.compareTo(other.number)

    public companion object {
        public const val MIN: Int = 0
        public const val MAX: Int = 127
        public const val MIDDLE_C: Int = 60
        public const val A4: Int = 69
    }
}

public enum class Letter {
    C, D, E, F, G, A, B;

    /** Semitones above C within the octave, natural. */
    public val semitonesFromC: Int
        get() = when (this) {
            C -> 0
            D -> 2
            E -> 4
            F -> 5
            G -> 7
            A -> 9
            B -> 11
        }

    /** Position in the diatonic sequence, which is what staff placement actually depends on. */
    public val diatonicStep: Int
        get() = ordinal
}

/** Semitone displacement from the natural: -2 double flat … +2 double sharp. */
@JvmInline
public value class Alter(public val semitones: Int) {
    init {
        require(semitones in -MAX_MAGNITUDE..MAX_MAGNITUDE) { "alteration $semitones beyond double" }
    }

    public companion object {
        public const val MAX_MAGNITUDE: Int = 2
        public val DoubleFlat: Alter = Alter(-2)
        public val Flat: Alter = Alter(-1)
        public val Natural: Alter = Alter(0)
        public val Sharp: Alter = Alter(1)
        public val DoubleSharp: Alter = Alter(2)
    }
}

/**
 * A *notated* pitch: a letter, an alteration, and an octave in scientific pitch notation
 * (C4 is middle C).
 */
public data class Pitch(
    val letter: Letter,
    val alter: Alter,
    val octave: Int,
) : Comparable<Pitch> {
    /** The sounding pitch. C4 = 60, so C0 = 12. */
    public val midi: Midi
        get() = Midi(SEMITONES_PER_OCTAVE * (octave + 1) + letter.semitonesFromC + alter.semitones)

    /**
     * Diatonic height, counting only letter and octave. Staff placement depends on this and
     * never on [midi] — which is exactly why Cb4 sits on the C line while sounding as B3.
     */
    public val diatonicIndex: Int
        get() = octave * LETTERS_PER_OCTAVE + letter.diatonicStep

    override fun compareTo(other: Pitch): Int = midi.compareTo(other.midi)

    public companion object {
        public const val SEMITONES_PER_OCTAVE: Int = 12
        public const val LETTERS_PER_OCTAVE: Int = 7
    }
}

/**
 * Where a pitch sits relative to a staff. The unit the scheduler reasons in, because
 * "notes above the treble staff" is a reading skill in a way that "F5" is not.
 */
public enum class PitchBand {
    FarBelowStaff,
    BelowStaff,
    LowerStaff,
    MiddleStaff,
    UpperStaff,
    AboveStaff,
    FarAboveStaff,
}
