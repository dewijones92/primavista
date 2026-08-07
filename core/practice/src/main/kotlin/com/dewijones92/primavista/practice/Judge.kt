package com.dewijones92.primavista.practice

import com.dewijones92.primavista.score.Midi
import com.dewijones92.primavista.score.Score
import com.dewijones92.primavista.score.SkillTag
import com.dewijones92.primavista.score.Ticks

/**
 * What happened to one notated note. Sealed, so adding an outcome forces every consumer to
 * account for it rather than silently falling into an else branch.
 *
 * Every timing figure is milliseconds, signed, positive meaning late. Named `dt` everywhere it
 * is logged, with its unit spelled — two different situations must never produce the same line.
 */
public sealed interface Verdict {
    public data class Correct(val dtMillis: Double) : Verdict

    public data class WrongPitch(val expected: Midi, val heard: Midi, val dtMillis: Double) : Verdict

    /** Right pitch, outside the tolerance window on the early side. */
    public data class Early(val dtMillis: Double) : Verdict

    public data class Late(val dtMillis: Double) : Verdict

    /** The window passed with nothing played. Detected on a clock tick, never by waiting. */
    public data object Missed : Verdict

    /** Something played that no notated note was expecting. */
    public data class Extra(val heard: Midi, val atTicks: Ticks) : Verdict

    public val isClean: Boolean get() = this is Correct
}

public data class NoteJudgement(
    /** Index into [Score.attackedNotes] — a tied continuation is not judged. */
    val noteIndex: Int,
    val verdict: Verdict,
)

/**
 * How much slack a note gets. Deliberately explicit rather than a single "difficulty" number,
 * because tolerating a wrong pitch and tolerating bad timing are different pedagogical choices.
 */
public data class Tolerances(
    /** Half-width of the on-time window. Outside it but within [windowMillis] is Early/Late. */
    val onTimeMillis: Double = 90.0,
    /** Beyond this, a note is Missed and a played note becomes Extra rather than very late. */
    val windowMillis: Double = 400.0,
) {
    init {
        require(onTimeMillis > 0 && windowMillis >= onTimeMillis) {
            "the on-time window must be positive and no wider than the matching window"
        }
    }
}

public data class SkillOutcome(
    val tag: SkillTag,
    val attempts: Int,
    val cleanAttempts: Int,
) {
    public val accuracy: Double get() = if (attempts == 0) 0.0 else cleanAttempts.toDouble() / attempts
}

public data class SessionResult(
    val judgements: List<NoteJudgement>,
    val skillOutcomes: List<SkillOutcome>,
    val notesExpected: Int,
) {
    public val correct: Int get() = judgements.count { it.verdict.isClean }

    public val accuracy: Double
        get() = if (notesExpected == 0) 0.0 else correct.toDouble() / notesExpected
}

/** Why a session could not be judged at all. Carries enough to tell Dewi where to look. */
public sealed interface RefusalReason {
    /**
     * The honest refusal (docs/spec.md I3). Names the bar so the message can say where, because
     * "this piece is polyphonic" is useless advice and "bar 3 needs both hands" is not.
     */
    public data class PolyphonicScoreOnMonoInput(
        val firstPolyphonicBar: Int,
        val inputLabel: String,
    ) : RefusalReason

    public data class EmptyScore(val scoreTitle: String) : RefusalReason
}

public sealed interface JudgeOutcome {
    public data class Judged(val result: SessionResult) : JudgeOutcome

    public data class Refused(val reason: RefusalReason) : JudgeOutcome
}

/**
 * Opaque fold state for incremental judging. Deliberately not inspectable: the live path and
 * the batch path must be the same computation, so nothing outside the judge may reach in and
 * make a decision the other path would not make.
 */
public interface JudgeState

/**
 * Every verdict in the app comes from here, and it is **pure** — no clock, no flow, no
 * hardware. Purity is the requirement that makes docs/spec.md I2 checkable: given the same
 * score, the same played notes and the same timing map, it must reach the same verdicts, which
 * is exactly what lets a report from last week be re-judged.
 *
 * Live and batch judging are the **same fold**, not two implementations. [advance] is the whole
 * algorithm; [judgeAll] is a fold over it, and the UI drives the identical fold from a flow. A
 * second code path here would be the classic duplicated-logic failure, and it would show up as
 * the live verdicts and the final summary disagreeing.
 */
public interface PerformanceJudge {
    public val tolerances: Tolerances

    /** Refuses before any work when the score and input cannot honestly be paired. */
    public fun accepts(score: Score, source: AnswerSource): RefusalReason?

    public fun begin(score: Score, timing: TickTiming): JudgeState

    /** Folds in one played note, returning any verdicts it settled. */
    public fun advance(state: JudgeState, note: PlayedNote): Pair<JudgeState, List<NoteJudgement>>

    /**
     * Folds in the passage of time, settling notes whose window has closed as [Verdict.Missed].
     *
     * Separate from [advance] because a missed note is the absence of an event, and an absence
     * never arrives. It must be found by **sampling a clock**, which is the same lesson Totum
     * learned when a watchdog collected a `StateFlow` and so never fired on a stall.
     */
    public fun advanceTime(state: JudgeState, position: Ticks): Pair<JudgeState, List<NoteJudgement>>

    public fun finish(state: JudgeState): SessionResult

    public fun judgeAll(score: Score, timing: TickTiming, played: List<PlayedNote>): SessionResult
}
