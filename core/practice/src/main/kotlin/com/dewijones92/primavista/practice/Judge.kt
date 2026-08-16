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
    /**
     * This verdict's name, as a **literal** — never `this::class.simpleName`.
     *
     * The release build minifies (`isMinifyEnabled = true`), and R8 renames anything reflective, so
     * a report from the APK Dewi actually installs would carry `claimed=2:a:` instead of
     * `WrongPitch` and docs/spec.md I7 would hold in debug and nowhere else. Declared abstract so
     * a new verdict cannot be added without naming itself: that makes the guard a compile error
     * rather than a test nobody thought to write (CLAUDE.md, *Shift left*, rung 2).
     */
    public val kind: String

    public data class Correct(val dtMillis: Double) : Verdict {
        override val kind: String get() = "Correct"
    }

    public data class WrongPitch(val expected: Midi, val heard: Midi, val dtMillis: Double) : Verdict {
        override val kind: String get() = "WrongPitch"
    }

    /** Right pitch, outside the tolerance window on the early side. */
    public data class Early(val dtMillis: Double) : Verdict {
        override val kind: String get() = "Early"
    }

    public data class Late(val dtMillis: Double) : Verdict {
        override val kind: String get() = "Late"
    }

    /** The window passed with nothing played. Detected on a clock tick, never by waiting. */
    public data object Missed : Verdict {
        override val kind: String get() = "Missed"
    }

    /** Something played that no notated note was expecting. */
    public data class Extra(val heard: Midi, val atTicks: Ticks) : Verdict {
        override val kind: String get() = "Extra"
    }

    public val isClean: Boolean get() = this is Correct
}

/**
 * What happened, tied to the note it happened to — or explicitly not tied to one.
 *
 * Sealed rather than a nullable index, because a [Verdict.Extra] answers to no notated note and
 * the first implementation had to invent a `-1` sentinel to say so. A sentinel is an illegal state
 * made representable, which is the thing CLAUDE.md's compile-time-safety rule exists to stop; here
 * it would have meant `attackedNotes[-1]` was one careless call away.
 *
 * [confidence] travels with the judgement because the diagnostics report has to answer "was that
 * really a wrong note, or did the mic barely hear it?" — a verdict without it cannot be re-judged.
 */
public sealed interface NoteJudgement {
    public val verdict: Verdict
    public val confidence: Float

    /** [noteIndex] indexes [Score.attackedNotes]; a tied continuation is never judged. */
    public data class OfNote(
        val noteIndex: Int,
        override val verdict: Verdict,
        override val confidence: Float = 1f,
    ) : NoteJudgement

    public data class Unexpected(
        override val verdict: Verdict.Extra,
        override val confidence: Float = 1f,
    ) : NoteJudgement
}

/**
 * How much slack a note gets. Deliberately explicit rather than a single "difficulty" number,
 * because tolerating a wrong pitch and tolerating bad timing are different pedagogical choices.
 *
 * The **matching** window is a fraction of a beat rather than a fixed number of milliseconds.
 * A fixed 400ms was wider than a whole beat above 150bpm, so a wrong note played dead on time was
 * matched to the *next* written note and reported as that note played 288ms early — a verdict that
 * names the wrong pitch, the wrong note and the wrong fault, which is docs/spec.md I2 failing
 * outright. The floor and ceiling keep it sane at extreme tempi, where a fraction of a beat would
 * otherwise become either unhittable or wider than the music.
 *
 * The **on-time** window stays absolute: how precisely a human can place a note is a property of
 * the human, not of the tempo.
 */
public data class Tolerances(
    /** Half-width of the on-time window. Outside it but inside the matching window is Early/Late. */
    val onTimeMillis: Double = 90.0,
    /** Matching half-width, as a fraction of a quarter-note beat. */
    val windowBeats: Double = 0.5,
    val minWindowMillis: Double = 120.0,
    /** Beyond this, a note is Missed and a played note becomes Extra rather than very late. */
    val maxWindowMillis: Double = 400.0,
) {
    init {
        require(onTimeMillis > 0 && windowBeats > 0) { "tolerances must be positive" }
        require(minWindowMillis <= maxWindowMillis) {
            "a matching window floor of ${minWindowMillis}ms is above its ceiling of ${maxWindowMillis}ms"
        }
        require(onTimeMillis <= minWindowMillis) {
            "an on-time band of ${onTimeMillis}ms does not fit inside a ${minWindowMillis}ms matching window"
        }
    }
}

public data class SkillOutcome(
    val tag: SkillTag,
    val attempts: Int,
    val cleanAttempts: Int,
) {
    public val accuracy: Double get() = if (attempts == 0) 0.0 else cleanAttempts.toDouble() / attempts

    /**
     * Whether this counts as having read the skill, rather than having been shown it.
     *
     * The one definition of "that went well". The scheduler's update rule and the placement read's
     * decision to climb both ask it, because two thresholds for the same judgement would let the
     * app pass a stage it would not credit a session for. Zero attempts is never clean: an untested
     * skill is absence of evidence, not evidence of failure.
     */
    public val isClean: Boolean get() = attempts > 0 && accuracy >= CLEAN_ACCURACY

    public companion object {
        /** Fraction of a skill's attempts that must be clean for a session to count for it. */
        public const val CLEAN_ACCURACY: Double = 0.8
    }
}

public data class SessionResult(
    val judgements: List<NoteJudgement>,
    val skillOutcomes: List<SkillOutcome>,
    val notesExpected: Int,
) {
    public val correct: Int get() = judgements.count { it.verdict.isClean }

    /** Notes played that answered to nothing written — a trill of wrong notes, a slipped finger. */
    public val extras: Int get() = judgements.count { it is NoteJudgement.Unexpected }

    /**
     * How much of the written music was played correctly. Deliberately measured against
     * [notesExpected] rather than against what was judged, so stopping a third of the way through
     * scores a third and not full marks.
     */
    public val accuracy: Double
        get() = if (notesExpected == 0) 0.0 else correct.toDouble() / notesExpected

    /**
     * Accuracy with unexpected notes counted against you.
     *
     * Separate from [accuracy] because they answer different questions and conflating them hides a
     * real fault: a performance that plays every written note correctly *and* twenty notes that
     * were not written is not a clean performance, but by [accuracy] alone it scores 100%.
     */
    public val cleanliness: Double
        get() {
            val denominator = notesExpected + extras
            return if (denominator == 0) 0.0 else correct.toDouble() / denominator
        }
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

    /**
     * [timing] must be an immutable snapshot of the tempo map, not a live transport.
     *
     * Handing the judge a running `Conductor` makes it un-pure by the back door: a session
     * containing a pause then re-judges differently from a report of itself, because the mapping it
     * consulted has moved on. That breaks docs/spec.md I2's whole basis, so take
     * [Conductor.timingSnapshot] here rather than the Conductor. A later snapshot reaches an
     * in-flight fold through [retime], never by the fold reading a live transport.
     */
    public fun begin(score: Score, timing: TickTiming): JudgeState

    /** Swaps in a newer [timing] map on resume. See .claude/CODE-NOTES.md. */
    public fun retime(state: JudgeState, timing: TickTiming): JudgeState

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

    /**
     * The whole fold in one call, over a finished performance. Returns [JudgeOutcome] rather than a
     * bare result so a caller cannot skip [accepts] and receive a confident score for a pairing the
     * judge would have refused — which is the only way spec I3 could be bypassed.
     */
    public fun judgeAll(
        score: Score,
        source: AnswerSource,
        timing: TickTiming,
        played: List<PlayedNote>,
    ): JudgeOutcome
}
