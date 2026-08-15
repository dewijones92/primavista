package com.dewijones92.primavista.score

import kotlin.math.abs

/**
 * Whether a [DifficultySpec] covers a [Score] — "is this music at that level".
 *
 * The dual of [ExerciseGenerator.generate], and deliberately the same knowledge read backwards:
 * the generator *writes* music inside a spec's dials, this *checks* music against them. Having
 * both makes a property worth asserting — everything the generator produces for a spec must be
 * admitted by that spec — which is the kind of test that would have caught the `specTargeting`
 * defect recorded in CLAUDE.md.
 *
 * It exists because grading real repertoire against a stage's declared [SkillTag]s does not work.
 * A stage's skill set is a **teaching claim** ("this rung introduces the bass clef"), not an
 * enumeration: no stage lists `Leap(semitones=5)`, so a comparison against those sets rejects
 * every bar of real music ever written. Measured on the OpenScore Lieder corpus: 8,744 of 8,798
 * passages were unplaceable that way. The spec, by contrast, states the dials exactly.
 *
 * Three things are deliberately **not** checked, each for its own reason:
 *
 * - [DifficultySpec.bars] and [DifficultySpec.tempoBpm] — how long a passage is and how fast it is
 *   taken are decisions made when practising, not properties of the music.
 * - The time signature. A spec pins one because a generator has to write in something, but this app
 *   does not model metre as a reading skill ([SkillTag] has no tag for it), and refusing 3/4 at a
 *   rung whose spec happens to say 4/4 rejected 50,712 of the corpus's 8,798 passages on a
 *   difficulty this ladder never claimed to teach. If metre becomes a taught skill, it gets a tag
 *   first and a check second.
 * - The exact key. A spec names one key; a rung *reads* signatures up to a size, so the check is on
 *   how many accidentals the signature carries, not on which ones.
 */
public fun DifficultySpec.admits(score: Score): Admission {
    val reasons = buildList {
        addAll(furnitureReasons(score))
        addAll(noteReasons(score))
        addAll(leapReasons(score))
    }
    return if (reasons.isEmpty()) Admission.Admitted else Admission.Refused(reasons.distinct())
}

public sealed interface Admission {
    public data object Admitted : Admission

    /** Every dial the music exceeded, so "one step too hard" is distinguishable from "miles off". */
    public data class Refused(val reasons: List<String>) : Admission

    public val isAdmitted: Boolean get() = this is Admitted
}

private fun DifficultySpec.furnitureReasons(score: Score): List<String> = buildList {
    val extraStaves = score.staves.filterNot { it in staves }
    if (extraStaves.isNotEmpty()) add("uses ${extraStaves.joinToString()} beyond the ${staves.size} staves here")
    if (!bothHandsActive && score.staves.size > 1 && score.notes.any { it.staff != staves.first() }) {
        add("both hands play, and this level is hands-separate")
    }
    score.measures.forEach { measure ->
        if (measure.key.accidentalCount > key.accidentalCount) {
            add("a signature of ${measure.key.accidentalCount} accidentals at bar ${measure.number}")
        }
        measure.clefs.forEach { (staff, clef) ->
            if (staff in staves && clefs[staff] != clef) add("$clef on $staff at bar ${measure.number}")
        }
    }
}

private fun DifficultySpec.noteReasons(score: Score): List<String> = buildList {
    score.notes.forEach { note ->
        val bounds = range[note.staff]
        if (bounds != null && note.pitch.midi !in bounds) add("${note.pitch} is outside this level's range")
        if (note.duration.symbol !in symbols) add("${note.duration.symbol} notes")
        if (note.duration.dots > maxDots) add("${note.duration.dots} augmentation dots")
        if (!allowTuplets && note.duration.tupletNumerator != 1) add("tuplets")
        val printed = printedAccidental(score, note)
        if (printed != null && printed !in allowedAlterations) {
            add("an accidental of ${printed.semitones} semitones")
        }
    }
}

/**
 * The alteration a reader actually sees, or null when the key signature already implies it. An F♯
 * in G major is the key, not an accidental — the distinction [DerivedScoreSkills] draws, asked of
 * the same function here rather than re-derived, because two answers to it would be two levels.
 */
private fun printedAccidental(score: Score, note: Note): Alter? {
    val key = score.measures.lastOrNull { it.start <= note.onset }?.key ?: KeySignature.C
    return note.pitch.alter.takeIf { it != KeySignatureAlterations.impliedAlter(key, note.pitch.letter) }
}

/** Melodic, so measured within a hand and a voice: the gap between the hands is not a leap. */
private fun DifficultySpec.leapReasons(score: Score): List<String> =
    score.attackedNotes
        .groupBy { it.staff to it.voice }
        .values
        .flatMap { line ->
            line.sortedBy { it.onset.value }
                .zipWithNext()
                .map { (from, to) -> abs(to.pitch.midi.number - from.pitch.midi.number) }
                .filter { it > maxLeapSemitones }
                .map { "a leap of $it semitones, beyond this level's $maxLeapSemitones" }
        }
