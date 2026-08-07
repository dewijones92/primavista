package com.dewijones92.primavista.practice

import com.dewijones92.primavista.score.Alter
import com.dewijones92.primavista.score.Clef
import com.dewijones92.primavista.score.Duration
import com.dewijones92.primavista.score.KeySignature
import com.dewijones92.primavista.score.Letter
import com.dewijones92.primavista.score.Measure
import com.dewijones92.primavista.score.Midi
import com.dewijones92.primavista.score.MusicalTime
import com.dewijones92.primavista.score.Note
import com.dewijones92.primavista.score.NoteSymbol
import com.dewijones92.primavista.score.Pitch
import com.dewijones92.primavista.score.PitchBand
import com.dewijones92.primavista.score.Polyphony
import com.dewijones92.primavista.score.Score
import com.dewijones92.primavista.score.ScoreId
import com.dewijones92.primavista.score.ScoreOrigin
import com.dewijones92.primavista.score.ScoreSummary
import com.dewijones92.primavista.score.SkillTag
import com.dewijones92.primavista.score.Staff
import com.dewijones92.primavista.score.Ticks
import com.dewijones92.primavista.score.TimeSignature
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

internal const val TEST_TEMPO_BPM = 60

internal fun beat(index: Int): Ticks = MusicalTime.quarters(index)

internal fun halfBeat(index: Int): Ticks = Ticks(MusicalTime.TICKS_PER_QUARTER * index / 2)

internal fun ms(millis: Double): Long = (millis * 1_000_000.0).toLong()

internal fun midiOf(letter: Letter, octave: Int): Midi = Pitch(letter, Alter.Natural, octave).midi

internal fun tone(
    onset: Ticks,
    letter: Letter,
    octave: Int,
    staff: Staff = Staff.Upper,
    symbol: NoteSymbol = NoteSymbol.Quarter,
    tiedFromPrevious: Boolean = false,
    tiedToNext: Boolean = false,
): Note = Note(
    onset = onset,
    duration = Duration(symbol),
    staff = staff,
    voice = 1,
    pitch = Pitch(letter, Alter.Natural, octave),
    tiedFromPrevious = tiedFromPrevious,
    tiedToNext = tiedToNext,
)

internal fun scoreOf(notes: List<Note>, bars: Int = 2, title: String = "fixture"): Score = Score(
    id = ScoreId("fixture"),
    title = title,
    composer = null,
    origin = ScoreOrigin.Parsed(sourceName = "fixture", licence = "public domain"),
    staves = listOf(Staff.Upper, Staff.Lower),
    measures = (0 until bars).map { index ->
        Measure(
            index = index,
            start = Ticks(TimeSignature.FourFour.measureTicks.value * index),
            time = TimeSignature.FourFour,
            key = KeySignature.C,
            clefs = mapOf(Staff.Upper to Clef.Treble, Staff.Lower to Clef.Bass),
        )
    },
    events = notes,
    defaultTempoBpm = TEST_TEMPO_BPM,
)

internal fun startedConductor(tempoBpm: Int = TEST_TEMPO_BPM): TempoConductor =
    TempoConductor(FakeClock(), tempoBpm).also { it.start() }

/** The judge may only ever see a snapshot, never the live transport (docs/spec.md I2). */
internal fun startedTiming(tempoBpm: Int = TEST_TEMPO_BPM): TickTiming =
    startedConductor(tempoBpm).timingSnapshot()

internal fun perfectPerformance(score: Score, timing: TickTiming): List<PlayedNote> =
    score.attackedNotes.map { PlayedNote(midi = it.pitch.midi, atNanos = timing.nanosFor(it.onset)) }

internal val polySource: AnswerSource = FakeSource("tap", Polyphony.Poly)

internal fun judged(outcome: JudgeOutcome): SessionResult =
    (outcome as? JudgeOutcome.Judged)?.result ?: error("expected a judgement, got $outcome")

internal fun ofNote(index: Int, verdict: Verdict): NoteJudgement = NoteJudgement.OfNote(index, verdict)

internal fun unexpected(verdict: Verdict.Extra): NoteJudgement = NoteJudgement.Unexpected(verdict)

internal class FakeSource(
    override val label: String,
    override val polyphony: Polyphony,
) : AnswerSource {
    override val latency: InputLatency = InputLatency.None

    override fun notes(): Flow<PlayedNote> = emptyFlow()
}

internal fun summaryOf(
    id: String,
    skills: Set<SkillTag>,
    polyphony: Polyphony = Polyphony.Mono,
    bars: Int = 8,
): ScoreSummary = ScoreSummary(
    id = ScoreId(id),
    title = id,
    composer = null,
    polyphony = polyphony,
    skills = skills,
    bars = bars,
    defaultTempoBpm = TEST_TEMPO_BPM,
)

internal val trebleMiddle: SkillTag = SkillTag.ClefRegion(Clef.Treble, PitchBand.MiddleStaff)

internal val quarterRhythm: SkillTag = SkillTag.RhythmFigure(NoteSymbol.Quarter, dots = 0, tupletNumerator = 1)

internal fun state(
    tag: SkillTag,
    strength: Double,
    dueAtEpochMillis: Long,
    attempts: Int = 1,
    lapses: Int = 0,
    repetition: Int = 0,
): SkillState = SkillState(
    tag = tag,
    strength = strength,
    dueAtEpochMillis = dueAtEpochMillis,
    attempts = attempts,
    lapses = lapses,
    repetition = repetition,
)
