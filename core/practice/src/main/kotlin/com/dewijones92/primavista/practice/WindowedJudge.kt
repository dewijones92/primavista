package com.dewijones92.primavista.practice

import com.dewijones92.primavista.score.Midi
import com.dewijones92.primavista.score.MusicalTime
import com.dewijones92.primavista.score.Note
import com.dewijones92.primavista.score.Polyphony
import com.dewijones92.primavista.score.Score
import com.dewijones92.primavista.score.SkillTag
import com.dewijones92.primavista.score.Ticks
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * The app's only judge, and a pure one.
 *
 * [skillsOfNote] is injected rather than depending on `:core:score`'s `ScoreSkills` so a verdict
 * can be reproduced without loading the whole skill derivation. See .claude/CODE-NOTES.md.
 */
public class WindowedJudge(
    override val tolerances: Tolerances = Tolerances(),
    private val skillsOfNote: (Note) -> Set<SkillTag> = { emptySet() },
) : PerformanceJudge {
    private val windowTicks = Ticks((tolerances.windowBeats * MusicalTime.TICKS_PER_QUARTER).roundToLong())
    private val minWindowNanos = Nanos.ofMillis(tolerances.minWindowMillis)
    private val maxWindowNanos = Nanos.ofMillis(tolerances.maxWindowMillis)

    override fun accepts(score: Score, source: AnswerSource): RefusalReason? {
        if (score.attackedNotes.isEmpty()) return RefusalReason.EmptyScore(score.title)
        if (source.polyphony == Polyphony.Mono) {
            score.firstPolyphonicMeasure()?.let {
                return RefusalReason.PolyphonicScoreOnMonoInput(it, source.label)
            }
        }
        return null
    }

    override fun begin(score: Score, timing: TickTiming): JudgeState =
        Fold(
            score = score,
            timing = timing,
            windows = Windows(windowTicks, minWindowNanos, maxWindowNanos),
            expected = score.attackedNotes.mapIndexed { index, note ->
                Expectation(index = index, onset = note.onset, midi = note.pitch.midi)
            },
            settled = emptySet(),
            judgements = emptyList(),
        )

    override fun retime(state: JudgeState, timing: TickTiming): JudgeState =
        state.asFold().copy(timing = timing)

    override fun advance(state: JudgeState, note: PlayedNote): Pair<JudgeState, List<NoteJudgement>> {
        val fold = state.asFold()
        val match = bestMatch(fold, note)
        val judgement = if (match == null) {
            NoteJudgement.Unexpected(
                verdict = Verdict.Extra(note.midi, fold.timing.ticksAt(note.atNanos)),
                confidence = note.confidence,
            )
        } else {
            NoteJudgement.OfNote(match.index, verdictFor(fold, match, note, tolerances), note.confidence)
        }
        val next = fold.copy(
            settled = if (match == null) fold.settled else fold.settled + match.index,
            judgements = fold.judgements + judgement,
        )
        return next to listOf(judgement)
    }

    override fun advanceTime(state: JudgeState, position: Ticks): Pair<JudgeState, List<NoteJudgement>> {
        val fold = state.asFold()
        val closed = fold.expected.filter { expectation ->
            expectation.index !in fold.settled &&
                fold.timing.musicBetween(expectation.onset, position) > fold.windowAt(expectation.onset)
        }
        if (closed.isEmpty()) return fold to emptyList()
        val judgements = closed.map { NoteJudgement.OfNote(it.index, Verdict.Missed) }
        val next = fold.copy(
            settled = fold.settled + closed.map { it.index },
            judgements = fold.judgements + judgements,
        )
        return next to judgements
    }

    override fun finish(state: JudgeState): SessionResult {
        val fold = state.asFold()
        return SessionResult(
            judgements = fold.judgements,
            skillOutcomes = skillOutcomes(fold, skillsOfNote),
            notesExpected = fold.expected.size,
        )
    }

    override fun judgeAll(
        score: Score,
        source: AnswerSource,
        timing: TickTiming,
        played: List<PlayedNote>,
    ): JudgeOutcome {
        accepts(score, source)?.let { return JudgeOutcome.Refused(it) }
        var state = begin(score, timing)
        for (note in played.sortedBy { it.atNanos }) {
            state = advanceTime(state, timing.ticksAt(note.atNanos)).first
            state = advance(state, note).first
        }
        val closing = closingPosition(score, timing, played, maxWindowNanos, windowTicks)
        return JudgeOutcome.Judged(finish(advanceTime(state, closing).first))
    }
}

private data class Expectation(val index: Int, val onset: Ticks, val midi: Midi)

/** Tempo-relative matching half-width, asked per onset. See .claude/CODE-NOTES.md. */
private class Windows(
    private val windowTicks: Ticks,
    private val minNanos: Long,
    private val maxNanos: Long,
) {
    fun at(timing: TickTiming, onset: Ticks): Long =
        timing.musicBetween(onset, onset + windowTicks).coerceIn(minNanos, maxNanos)
}

private data class Fold(
    val score: Score,
    val timing: TickTiming,
    val windows: Windows,
    val expected: List<Expectation>,
    val settled: Set<Int>,
    val judgements: List<NoteJudgement>,
) : JudgeState

private fun JudgeState.asFold(): Fold =
    this as? Fold ?: error("judge state came from a different PerformanceJudge implementation")

private fun TickTiming.musicBetween(from: Ticks, to: Ticks): Long =
    elapsedNanosAt(to) - elapsedNanosAt(from)

private fun Fold.windowAt(onset: Ticks): Long = windows.at(timing, onset)

private fun deltaNanos(fold: Fold, expectation: Expectation, note: PlayedNote): Long =
    fold.timing.musicBetween(expectation.onset, fold.timing.ticksAt(note.atNanos))

private fun bestMatch(fold: Fold, note: PlayedNote): Expectation? {
    val candidates = fold.expected.filter {
        it.index !in fold.settled && abs(deltaNanos(fold, it, note)) <= fold.windowAt(it.onset)
    }
    if (candidates.isEmpty()) return null
    val samePitch = candidates.filter { it.midi == note.midi }
    val pool = samePitch.ifEmpty { candidates }
    return pool.minWithOrNull(
        compareBy({ abs(deltaNanos(fold, it, note)) }, { it.index }),
    )
}

private fun verdictFor(
    fold: Fold,
    expectation: Expectation,
    note: PlayedNote,
    tolerances: Tolerances,
): Verdict {
    val dtMillis = Nanos.toMillis(deltaNanos(fold, expectation, note))
    return when {
        expectation.midi != note.midi -> Verdict.WrongPitch(expectation.midi, note.midi, dtMillis)
        abs(dtMillis) <= tolerances.onTimeMillis -> Verdict.Correct(dtMillis)
        dtMillis < 0 -> Verdict.Early(dtMillis)
        else -> Verdict.Late(dtMillis)
    }
}

private fun skillOutcomes(fold: Fold, skillsOfNote: (Note) -> Set<SkillTag>): List<SkillOutcome> {
    val notes = fold.score.attackedNotes
    return fold.judgements
        .filterIsInstance<NoteJudgement.OfNote>()
        .flatMap { judgement ->
            skillsOfNote(notes[judgement.noteIndex]).map { tag -> tag to judgement.verdict.isClean }
        }
        .groupBy({ it.first }, { it.second })
        .map { (tag, clean) -> SkillOutcome(tag, attempts = clean.size, cleanAttempts = clean.count { it }) }
}

private fun closingPosition(
    score: Score,
    timing: TickTiming,
    played: List<PlayedNote>,
    windowNanos: Long,
    windowTicks: Ticks,
): Ticks {
    val lastExpected = score.attackedNotes.maxOfOrNull { it.onset.value } ?: 0L
    val lastPlayed = played.maxOfOrNull { timing.ticksAt(it.atNanos).value } ?: 0L
    val from = Ticks(maxOf(lastExpected, lastPlayed))
    val step = if (windowTicks.value > 0) windowTicks else Ticks(1)
    val perStep = timing.musicBetween(from, from + step).coerceAtLeast(1L)
    return from + step * ((windowNanos / perStep).toInt() + 1)
}
