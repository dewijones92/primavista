package com.dewijones92.primavista.practice

import com.dewijones92.primavista.score.Corpus
import com.dewijones92.primavista.score.DifficultySpec
import com.dewijones92.primavista.score.ExerciseGenerator
import com.dewijones92.primavista.score.MusicXmlParser
import com.dewijones92.primavista.score.MusicXmlResult
import com.dewijones92.primavista.score.Polyphony
import com.dewijones92.primavista.score.Score
import com.dewijones92.primavista.score.ScoreId
import com.dewijones92.primavista.score.Ticks
import com.dewijones92.primavista.score.TimeSignature
import com.dewijones92.primavista.score.excerpt

/**
 * Everything needed to re-judge a session that already happened.
 *
 * This is what makes docs/spec.md **I7** a property rather than an intention. The bar the spec sets
 * is not "there are logs": a report Dewi shares a week later must let a fresh session reach *the
 * same verdicts*, or say why it cannot. That is only possible if the report carries the inputs to
 * the judgement — the music, the clock, and every note heard — rather than a summary of its output.
 *
 * Deliberately a **record of inputs**. [claimed] is carried alongside so a replay can be compared
 * against what the app said at the time; it is never what the replay reads to reach its answer.
 */
public data class SessionReplay(
    val score: ScoreRef,
    val tempoBpm: Int,
    val time: TimeSignature,
    /** The pauses that actually happened, in the order they happened. Empty is an unbroken run. */
    val legs: List<PauseLeg>,
    val inputLabel: String,
    val polyphony: Polyphony,
    /**
     * Carried whole rather than as a number of milliseconds, because the spec asks for the latency
     * **and which of the two it was**: an assumed 60ms and a measured 60ms lead to the same
     * correction and to completely different confidence in a verdict built on it.
     */
    val latency: InputLatency,
    val played: List<PlayedNote>,
    /** What the app said at the time, so a replay can be checked against it rather than trusted. */
    val claimed: List<ClaimedVerdict>,
) {
    /**
     * The tempo map as it was, pauses included.
     *
     * Rebuilt rather than re-derived from a running transport, for the reason [PerformanceJudge.begin]
     * spells out: a session containing a pause re-judges differently if the mapping has moved on.
     */
    public fun timing(): TickTiming = replayTiming(tempoBpm, time, legs)

    public companion object {
        /** A run with no pause, which is what an ordinary session is. */
        public val Unbroken: List<PauseLeg> = listOf(PauseLeg(0L, 0L))
    }
}

/** One stretch of unbroken time in the tempo map: after a pause, musical zero sits later in wall time. */
public data class PauseLeg(val fromTicks: Long, val originNanos: Long)

/**
 * What the app said about one note, in the smallest form that can be compared.
 *
 * [kind] and [dtMillis] rather than the whole [Verdict], because those are what a replay has to
 * agree about: the same verdict reached at a materially different `dt` is a timing drift worth
 * catching, and everything else in a verdict is derivable from the note it answers to. Derived
 * in one place so the encoder and the comparison cannot disagree about what "the same" means.
 */
public data class ClaimedVerdict(val noteIndex: Int, val kind: String, val dtMillis: Double?) {
    public companion object {
        public fun of(judgement: NoteJudgement): ClaimedVerdict = ClaimedVerdict(
            noteIndex = (judgement as? NoteJudgement.OfNote)?.noteIndex ?: EXTRA_INDEX,
            kind = judgement.verdict.kind,
            dtMillis = dtOf(judgement.verdict),
        )

        /** A [Verdict.Extra] answers to no notated note; it is not note number minus one. */
        public const val EXTRA_INDEX: Int = -1

        private fun dtOf(verdict: Verdict): Double? = when (verdict) {
            is Verdict.Correct -> verdict.dtMillis
            is Verdict.WrongPitch -> verdict.dtMillis
            is Verdict.Early -> verdict.dtMillis
            is Verdict.Late -> verdict.dtMillis
            Verdict.Missed, is Verdict.Extra -> null
        }
    }
}

/**
 * How to rebuild the music that was read.
 *
 * Sealed because the three ways a [Score] can exist need different keys, and a report that merely
 * named a title could not rebuild any of them. A generated exercise is reproducible **exactly**
 * from its seed and spec, which is why the generator is seeded at all and why this is the single
 * highest-value line in a report (docs/todos/diagnostics-report.md).
 */
public sealed interface ScoreRef {
    public data class Generated(val seed: Long, val spec: DifficultySpec) : ScoreRef

    public data class Shipped(val piece: ScoreId) : ScoreRef

    /**
     * A window of a shipped piece, by **index** rather than by the bar number printed on it.
     * Sixteen of the shipped songs open on a pickup written `<measure number="0">`, so the two
     * differ for every bar of those pieces and only the index can rebuild the window. See
     * [com.dewijones92.primavista.score.PassageId].
     */
    public data class Passage(val piece: ScoreId, val fromIndex: Int, val bars: Int) : ScoreRef
}

/** Why a replay could not rebuild its music. A stated reason, never a silent null. */
public sealed interface ReplayScore {
    public data class Rebuilt(val score: Score) : ReplayScore

    public data class Lost(val reason: String) : ReplayScore
}

/**
 * Rebuilds the music a [SessionReplay] was read from.
 *
 * A shipped piece is found by id and parsed again; a generated one is regenerated from its seed.
 * Both are this build's answer, which is the point: a replay that disagrees with the recorded
 * verdicts has found either a defect or a change since, and either is worth knowing.
 */
public fun ScoreRef.rebuild(generator: ExerciseGenerator, parser: MusicXmlParser): ReplayScore = when (this) {
    is ScoreRef.Generated -> ReplayScore.Rebuilt(generator.generate(seed, spec))
    is ScoreRef.Shipped -> shippedScore(piece, parser)
    is ScoreRef.Passage -> when (val whole = shippedScore(piece, parser)) {
        is ReplayScore.Lost -> whole
        is ReplayScore.Rebuilt -> excerptOf(whole.score, fromIndex, bars)
    }
}

private fun shippedScore(id: ScoreId, parser: MusicXmlParser): ReplayScore {
    val piece = Corpus.pieces.firstOrNull { it.id == id }
        ?: return ReplayScore.Lost("this build ships no piece with id '${id.value}'")
    return when (val parsed = Corpus.parse(piece, parser)) {
        is MusicXmlResult.Parsed -> ReplayScore.Rebuilt(parsed.score)
        is MusicXmlResult.Failed -> ReplayScore.Lost("'${piece.title}' no longer parses: ${parsed.reason}")
    }
}

private fun excerptOf(whole: Score, fromIndex: Int, bars: Int): ReplayScore {
    if (fromIndex !in whole.measures.indices) {
        return ReplayScore.Lost("bar $fromIndex is outside the ${whole.measures.size} bars of '${whole.title}'")
    }
    return ReplayScore.Rebuilt(whole.excerpt(fromIndex, bars))
}

/** The pauses a replay records, as the immutable mapping the judge needs. */
private fun replayTiming(tempoBpm: Int, time: TimeSignature, legs: List<PauseLeg>): TickTiming {
    val map = TempoTickMap(tempoBpm, time.beatUnit)
    val rebuilt = legs.ifEmpty { SessionReplay.Unbroken }.map { TempoLeg(it.fromTicks, it.originNanos) }
    return TempoTimeline(map, rebuilt)
}

/** The pauses a live [Conductor] has been through, in the form a report can carry. */
public fun List<Pair<Long, Long>>.asPauseLegs(): List<PauseLeg> = map { PauseLeg(it.first, it.second) }

/** Convenience for the commonest case: a session that ran from [originNanos] with no pause. */
public fun unbrokenLegs(originNanos: Long): List<PauseLeg> = listOf(PauseLeg(0L, originNanos))

/** The musical position a replay's clock puts a wall-clock instant at. Used by tests and reports. */
public fun SessionReplay.positionAt(nanos: Long): Ticks = timing().ticksAt(nanos)
