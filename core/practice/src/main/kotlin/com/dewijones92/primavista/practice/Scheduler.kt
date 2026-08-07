package com.dewijones92.primavista.practice

import com.dewijones92.primavista.score.DifficultySpec
import com.dewijones92.primavista.score.ScoreId
import com.dewijones92.primavista.score.ScoreSummary
import com.dewijones92.primavista.score.SkillTag

/**
 * What the app believes about one reading skill.
 *
 * [strength] is 0.0 (never read it correctly) to 1.0 (reliable). [dueAtEpochMillis] is when it
 * is worth revisiting — spacing matters as much as accuracy, because a skill you got right once
 * yesterday is not one you have learnt.
 */
public data class SkillState(
    val tag: SkillTag,
    val strength: Double,
    val dueAtEpochMillis: Long,
    val attempts: Int,
    val lapses: Int,
) {
    init {
        require(strength in 0.0..1.0) { "strength $strength outside 0..1" }
    }

    public fun isDue(nowEpochMillis: Long): Boolean = nowEpochMillis >= dueAtEpochMillis
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
    public fun next(
        available: List<ScoreSummary>,
        states: List<SkillState>,
        nowEpochMillis: Long,
        seed: Long,
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
