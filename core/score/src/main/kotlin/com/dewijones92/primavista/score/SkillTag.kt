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

    /**
     * Notes off the staff, where counting lines stops being automatic.
     *
     * [above] is carried rather than inferred: reading two leger lines above a bass staff and two
     * below it are different skills with different failure rates, and without the side, targeting
     * this skill is a coin flip about which end of the staff to generate.
     */
    public data class LegerLines(val clef: Clef, val count: Int, val above: Boolean) : SkillTag

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
    /**
     * [attackIndex] indexes [Score.attackedNotes] and is required, not optional: "the leap from the
     * previous note" needs to know *which* note this is, and finding it by scanning and matching on
     * equality picks the wrong one whenever a piece repeats a pitch — which music does constantly.
     */
    public fun skillsOf(score: Score, attackIndex: Int): Set<SkillTag>

    public fun skillsOf(score: Score): Set<SkillTag>

    public fun bandOf(clef: Clef, pitch: Pitch): PitchBand

    /** Signed: positive above the staff, negative below, zero within it. */
    public fun legerLines(clef: Clef, pitch: Pitch): Int
}
