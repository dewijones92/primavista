package com.dewijones92.primavista.practice

import com.dewijones92.primavista.score.DifficultySpec
import com.dewijones92.primavista.score.Polyphony
import com.dewijones92.primavista.score.ScoreId
import com.dewijones92.primavista.score.ScoreSummary
import com.dewijones92.primavista.score.SkillTag

/**
 * What the app believes about one reading skill.
 *
 * [strength] is 0.0 (never read it correctly) to 1.0 (reliable). [dueAtEpochMillis] is when it
 * is worth revisiting — spacing matters as much as accuracy, because a skill you got right once
 * yesterday is not one you have learnt.
 *
 * [repetition] is the SM-2 rung: clean sessions **since the last lapse**, which is what sets the
 * interval. It cannot be derived from [attempts] and [lapses] — those are lifetime totals, and
 * using their difference meant one good session after a failure jumped a mature skill to roughly
 * ten days, i.e. the app hiding the thing Dewi had just got wrong (docs/spec.md I5).
 */
public data class SkillState(
    val tag: SkillTag,
    val strength: Double,
    val dueAtEpochMillis: Long,
    val attempts: Int,
    val lapses: Int,
    val repetition: Int = 0,
) {
    init {
        require(strength in 0.0..1.0) { "strength $strength outside 0..1" }
    }

    public fun isDue(nowEpochMillis: Long): Boolean = nowEpochMillis >= dueAtEpochMillis

    /**
     * Whether the reading is reliable. The **one** definition of solid in the app: [Curriculum]
     * passes a stage on it, and the Progress screen's "Solid" bucket is this plus not-due. See
     * .claude/CODE-NOTES.md.
     */
    public val isSolid: Boolean get() = strength >= SOLID_STRENGTH

    public companion object {
        /** Deliberately below 1.0: [strength] approaches certainty asymptotically and never reaches it. */
        public const val SOLID_STRENGTH: Double = 0.8
    }
}

/**
 * A narrowing of the field a choice may be drawn from — how a [Stage] reaches the scheduler.
 *
 * The stage decides *which skills are in play* and at *what level*; the scheduler still decides
 * which of those to drill and the generator still writes the notes. A stage that chose its own
 * material would be a second scheduler, which is the failure docs/journey.md names outright.
 *
 * [base] is the spec a generated drill starts from, so a stage-seven exercise is built on
 * stage-seven material rather than on the bottom rung with one dial turned. Null means the
 * scheduler's own base.
 */
public data class PracticeFocus(
    val skills: Set<SkillTag>,
    val base: DifficultySpec? = null,
) {
    public companion object {
        /** No narrowing: the whole skill store, as it was before stages existed. */
        public val None: PracticeFocus = PracticeFocus(emptySet())
    }
}

/** Persistence port. Implemented by Room in `:core:database`; faked in tests. */
public interface SkillStore {
    public suspend fun states(): List<SkillState>

    public suspend fun record(outcomes: List<SkillOutcome>, nowEpochMillis: Long)
}

/**
 * What to practise next. Sealed, and the two cases are the whole point of the generator: when
 * the corpus has nothing at the right level for a weak skill, the app synthesises material that
 * drills it rather than handing Dewi a piece he cannot read yet (CLAUDE.md, *The ladder problem*).
 */
public sealed interface PracticeChoice {
    public data class Piece(val id: ScoreId, val tempoBpm: Int, val targeting: Set<SkillTag>) : PracticeChoice

    public data class Generated(val seed: Long, val spec: DifficultySpec, val targeting: Set<SkillTag>) : PracticeChoice
}

/**
 * Chooses the next thing to read, weighted towards skills that are weak and due.
 *
 * Pure and deterministic given its inputs, including [nowEpochMillis] and [seed] — passed in
 * rather than read from the environment so a choice can be reproduced from a report, and so the
 * tests that prove docs/spec.md I5 ("getting better visibly changes what it gives you") are
 * ordinary assertions rather than statistical ones.
 */
public interface PracticeScheduler {
    /**
     * [input] is what the session's [AnswerSource] can actually hear, and it is required rather
     * than assumed: without it the scheduler recommended two-hand material to a mono mic, which
     * `PerformanceJudge.accepts` then refused — the app proposing work it will not mark. The
     * polyphony gate belongs on everything offered, generated material included.
     *
     * [focus] narrows the field to a stage's skills without taking the choice away — a skill named
     * there but never yet read is a candidate at full weakness, since a stage's new skills are
     * exactly the ones with no history.
     */
    public fun next(
        available: List<ScoreSummary>,
        states: List<SkillState>,
        input: Polyphony,
        nowEpochMillis: Long,
        seed: Long,
        focus: PracticeFocus = PracticeFocus.None,
    ): PracticeChoice

    /** The skills most worth drilling right now, worst first. Exposed so the UI can say why. */
    public fun weakest(states: List<SkillState>, nowEpochMillis: Long, limit: Int = 5): List<SkillState>

    /**
     * Folds a session's outcomes into new skill states. Separate from the store so the update
     * rule is pure and testable, and the store only persists what it is handed.
     */
    public fun update(
        states: List<SkillState>,
        outcomes: List<SkillOutcome>,
        nowEpochMillis: Long,
    ): List<SkillState>
}
