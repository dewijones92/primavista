package com.dewijones92.primavista.practice

import com.dewijones92.primavista.score.Alter
import com.dewijones92.primavista.score.Clef
import com.dewijones92.primavista.score.DifficultySpec
import com.dewijones92.primavista.score.KeySignature
import com.dewijones92.primavista.score.NoteSymbol
import com.dewijones92.primavista.score.PitchBand
import com.dewijones92.primavista.score.Polyphony
import com.dewijones92.primavista.score.ScoreSummary
import com.dewijones92.primavista.score.SkillTag
import com.dewijones92.primavista.score.Staff
import com.dewijones92.primavista.score.TimeSignature

private const val STRENGTH_GROWTH = 0.4
private const val LAPSE_RETENTION = 0.35
private const val BASE_INTERVAL_MILLIS = 4L * 60L * 60L * 1000L
private const val LAPSE_INTERVAL_MILLIS = 10L * 60L * 1000L
private const val MAX_DOUBLINGS = 6
private const val TARGET_SKILL_COUNT = 5
private const val FAMILIAR_STRENGTH = 0.5
private const val POLY_READY_STRENGTH = 0.5
private const val NEW_SKILL_BUDGET = 2

private const val EXERCISE_BARS = 4
private const val EXERCISE_TEMPO_BPM = 60
private const val EXERCISE_MAX_LEAP_SEMITONES = 4

/**
 * SM-2 flavoured spacing over reading skills. The update rule is deliberately small enough to
 * predict by hand; it is written out in .claude/CODE-NOTES.md.
 *
 * [specTargeting] is required, not defaulted: turning a weak skill into a [DifficultySpec] is
 * `ExerciseGenerator.specTargeting`'s job and there must be exactly one of it. See
 * .claude/CODE-NOTES.md.
 */
public class SpacedPracticeScheduler(
    private val specTargeting: (SkillTag, DifficultySpec) -> DifficultySpec,
    private val base: DifficultySpec = DefaultBase,
) : PracticeScheduler {
    override fun next(
        available: List<ScoreSummary>,
        states: List<SkillState>,
        input: Polyphony,
        nowEpochMillis: Long,
        seed: Long,
        focus: PracticeFocus,
    ): PracticeChoice {
        val pool = pooled(states, focus.skills, nowEpochMillis)
        val due = pool.filter { it.isDue(nowEpochMillis) }
        val targets = weakest(due.ifEmpty { pool }, nowEpochMillis, TARGET_SKILL_COUNT)
            .map { it.tag }
            .filter { input == Polyphony.Poly || it != SkillTag.HandIndependence }
        val byTag = states.associateBy { it.tag }
        val piece = available
            .filter { input == Polyphony.Poly || it.polyphony == Polyphony.Mono }
            .filter { summary -> targets.any(summary.skills::contains) && suitable(summary, byTag, targets) }
            .minWithOrNull(
                compareBy(
                    { summary -> targets.indexOfFirst(summary.skills::contains) },
                    { summary -> -summary.skills.count(targets::contains) },
                    { summary -> newSkills(summary, byTag, targets) },
                    { it.bars },
                    { it.id.value },
                ),
            )
        if (piece != null) {
            val targeting = piece.skills.filter(targets::contains).toSet()
            return PracticeChoice.Piece(piece.id, piece.defaultTempoBpm, targeting)
        }
        val from = focus.base ?: base
        val target = targets.firstOrNull() ?: fundamentalSkillOf(from)
        return PracticeChoice.Generated(seed, specTargeting(target, from).playableBy(input), setOf(target))
    }

    override fun weakest(states: List<SkillState>, nowEpochMillis: Long, limit: Int): List<SkillState> =
        states
            .sortedWith(
                compareBy(
                    { if (it.isDue(nowEpochMillis)) 0 else 1 },
                    { it.strength },
                    { it.tag.toString() },
                ),
            )
            .take(limit.coerceAtLeast(0))

    override fun update(
        states: List<SkillState>,
        outcomes: List<SkillOutcome>,
        nowEpochMillis: Long,
    ): List<SkillState> {
        val evidence = outcomes.filter { it.attempts > 0 }.associateBy { it.tag }
        val revised = states.map { state ->
            evidence[state.tag]?.let { folded(state, it, nowEpochMillis) } ?: state
        }
        val known = states.map { it.tag }.toSet()
        val added = evidence.values
            .filterNot { it.tag in known }
            .map { folded(fresh(it.tag, nowEpochMillis), it, nowEpochMillis) }
        return revised + added
    }

    /** A focused skill nobody has read yet is a candidate, not an omission. See .claude/CODE-NOTES.md. */
    private fun pooled(
        states: List<SkillState>,
        focus: Set<SkillTag>,
        nowEpochMillis: Long,
    ): List<SkillState> {
        if (focus.isEmpty()) return states
        val known = states.filter { it.tag in focus }
        val unread = focus.filterNot { tag -> states.any { it.tag == tag } }
        return known + unread.sortedBy { it.toString() }.map { fresh(it, nowEpochMillis) }
    }

    private fun suitable(
        summary: ScoreSummary,
        byTag: Map<SkillTag, SkillState>,
        targets: List<SkillTag>,
    ): Boolean {
        val handsStrength = byTag[SkillTag.HandIndependence]?.strength ?: 0.0
        if (summary.polyphony == Polyphony.Poly && handsStrength < POLY_READY_STRENGTH) return false
        return newSkills(summary, byTag, targets) <= NEW_SKILL_BUDGET
    }

    private fun newSkills(
        summary: ScoreSummary,
        byTag: Map<SkillTag, SkillState>,
        targets: List<SkillTag>,
    ): Int = summary.skills.count { it !in targets && (byTag[it]?.strength ?: 0.0) < FAMILIAR_STRENGTH }

    public companion object {
        /** Where the ladder starts: one treble staff, C major, quarters and halves. */
        public val DefaultBase: DifficultySpec = DifficultySpec(
            staves = listOf(Staff.Upper),
            clefs = mapOf(Staff.Upper to Clef.Treble),
            keys = setOf(KeySignature.C),
            time = TimeSignature.FourFour,
            bars = EXERCISE_BARS,
            range = mapOf(Staff.Upper to staffMidiRange(Clef.Treble, KeySignature.C, WHOLE_STAFF_STEPS)),
            symbols = setOf(NoteSymbol.Quarter, NoteSymbol.Half),
            maxDots = 0,
            allowTuplets = false,
            allowedAlterations = setOf(Alter.Natural),
            maxLeapSemitones = EXERCISE_MAX_LEAP_SEMITONES,
            tempoBpm = EXERCISE_TEMPO_BPM,
            bothHandsActive = false,
        )
    }
}

/** Nothing generated for a mono input may need two hands at once — its judge would refuse it. */
internal fun DifficultySpec.playableBy(input: Polyphony): DifficultySpec =
    if (input == Polyphony.Mono) copy(bothHandsActive = false) else this

internal fun fresh(tag: SkillTag, nowEpochMillis: Long): SkillState =
    SkillState(tag = tag, strength = 0.0, dueAtEpochMillis = nowEpochMillis, attempts = 0, lapses = 0)

private fun folded(state: SkillState, outcome: SkillOutcome, nowEpochMillis: Long): SkillState {
    val clean = outcome.isClean
    val repetition = if (clean) state.repetition + 1 else 0
    val strength = if (clean) {
        state.strength + (1.0 - state.strength) * STRENGTH_GROWTH
    } else {
        state.strength * LAPSE_RETENTION
    }
    val interval = if (clean) {
        BASE_INTERVAL_MILLIS shl (repetition - 1).coerceIn(0, MAX_DOUBLINGS)
    } else {
        LAPSE_INTERVAL_MILLIS
    }
    return state.copy(
        strength = strength.coerceIn(0.0, 1.0),
        dueAtEpochMillis = nowEpochMillis + interval,
        attempts = state.attempts + 1,
        lapses = state.lapses + if (clean) 0 else 1,
        repetition = repetition,
    )
}

private fun fundamentalSkillOf(spec: DifficultySpec): SkillTag {
    val staff = spec.staves.first()
    return SkillTag.ClefRegion(spec.clefs[staff] ?: Clef.Treble, PitchBand.MiddleStaff)
}
