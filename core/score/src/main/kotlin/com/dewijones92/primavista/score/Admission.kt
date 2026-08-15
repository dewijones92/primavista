package com.dewijones92.primavista.score

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
 * - The exact key. A spec names one key because a generator must write in something; what a rung can
 *   *read* is [DifficultySpec.maxKeyAccidentals], a separate dial, and the check is on how many
 *   accidentals a signature carries rather than on which ones.
 * - [DifficultySpec.maxLeapSemitones]. This one is the least obvious and cost the most to settle.
 *   It is a *writing* dial — "keep the generated line stepwise so it reads easily" — and reading it
 *   as a ceiling on what a reader can cope with double-counts a difficulty the range check already
 *   bounds: every pitch in an admitted passage is one this level reads, so the jump between two of
 *   them asks nothing new of the eye. Read as a ceiling it also rejected 24,639 corpus passages and
 *   placed *Ode to Joy* at the tenth rung, on a left-hand jump of a sixth. [SkillTag.Leap] is still
 *   derived, debited and drilled per note; it is simply not a gate on whether a passage is readable.
 */
public fun DifficultySpec.admits(score: Score): Admission {
    val reasons = refusals(score).toList()
    return if (reasons.isEmpty()) Admission.Admitted else Admission.Refused(reasons.distinct())
}

/**
 * The same question with no answer built: true when nothing in [score] exceeds this spec.
 *
 * It exists because placement asks it of every window against every rung, and building the full
 * list of reasons only to discard it dominated that pass — a lazy sequence stops at the first note
 * that refuses. Defined on the same [refusals] as [admits], so the two cannot drift.
 */
public fun DifficultySpec.covers(score: Score): Boolean = refusals(score).none()

private fun DifficultySpec.refusals(score: Score): Sequence<String> = sequence {
    yieldAll(furnitureReasons(score))
    yieldAll(noteReasons(score))
}

public sealed interface Admission {
    public data object Admitted : Admission

    /** Every dial the music exceeded, so "one step too hard" is distinguishable from "miles off". */
    public data class Refused(val reasons: List<String>) : Admission

    public val isAdmitted: Boolean get() = this is Admitted
}

private fun DifficultySpec.furnitureReasons(score: Score): Sequence<String> = sequence {
    val extraStaves = score.staves.filterNot { it in staves }
    if (extraStaves.isNotEmpty()) yield("uses ${extraStaves.joinToString()} beyond the ${staves.size} staves here")
    if (!bothHandsActive && score.staves.size > 1 && score.notes.any { it.staff != staves.first() }) {
        yield("both hands play, and this level is hands-separate")
    }
    score.measures.forEach { measure ->
        if (measure.key.accidentalCount > readableKeyAccidentals) {
            yield("a signature of ${measure.key.accidentalCount} accidentals at bar ${measure.number}")
        }
        measure.clefs.forEach { (staff, clef) ->
            if (staff in staves && clefs[staff] != clef) yield("$clef on $staff at bar ${measure.number}")
        }
    }
}

private fun DifficultySpec.noteReasons(score: Score): Sequence<String> = sequence {
    score.notes.forEach { note ->
        val bounds = range[note.staff]
        if (bounds != null && note.pitch.midi !in bounds) yield("${note.pitch} is outside this level's range")
        if (note.duration.symbol !in symbols) yield("${note.duration.symbol} notes")
        if (note.duration.dots > maxDots) yield("${note.duration.dots} augmentation dots")
        if (!allowTuplets && note.duration.tupletNumerator != 1) yield("tuplets")
        val printed = printedAccidental(score, note)
        if (printed != null && printed !in allowedAlterations) {
            yield("an accidental of ${printed.semitones} semitones")
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
