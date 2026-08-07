package com.dewijones92.primavista.score

/**
 * A reading skill a note exercises.
 *
 * Tagged on **notes**, never on pieces, and derived rather than stored (see [ScoreSkills]).
 * That choice is what makes docs/spec.md I5 achievable: a newly imported piece grades itself
 * from its own content, and a failed note debits precisely the skills it actually used
 * rather than everything the piece happens to contain.
 */
public sealed interface SkillTag {
    /** Reading a pitch in a region of a given clef — the core skill, and the commonest failure. */
    public data class ClefRegion(val clef: Clef, val band: PitchBand) : SkillTag

    /** Notes off the staff, where counting lines stops being automatic. */
    public data class LegerLines(val clef: Clef, val count: Int) : SkillTag

    /** An accidental in front of the note. */
    public data class Accidental(val alter: Alter) : SkillTag

    /** Reading in a key, where accidentals are implied by the signature rather than printed. */
    public data class KeyReading(val fifths: Int) : SkillTag

    /** A rhythmic figure: symbol, dots, and whether it is part of a tuplet. */
    public data class RhythmFigure(
        val symbol: NoteSymbol,
        val dots: Int,
        val tupletNumerator: Int,
    ) : SkillTag

    /** A melodic leap of this many semitones — reading distance, not reading a pitch. */
    public data class Leap(val semitones: Int) : SkillTag

    /** Two staves demanding attention at once. Only ever tagged on grand-staff material. */
    public data object HandIndependence : SkillTag
}

/**
 * Derives the skills a score exercises. One derivation, so a piece's difficulty and a note's
 * verdict can never disagree about what was being tested.
 */
public interface ScoreSkills {
    public fun skillsOf(score: Score, note: Note): Set<SkillTag>

    public fun skillsOf(score: Score): Set<SkillTag>

    public fun bandOf(clef: Clef, pitch: Pitch): PitchBand

    public fun legerLineCount(clef: Clef, pitch: Pitch): Int
}
