package com.dewijones92.primavista.practice

import com.dewijones92.primavista.score.Alter
import com.dewijones92.primavista.score.Clef
import com.dewijones92.primavista.score.Corpus
import com.dewijones92.primavista.score.DifficultySpec
import com.dewijones92.primavista.score.DomMusicXmlParser
import com.dewijones92.primavista.score.KeySignature
import com.dewijones92.primavista.score.Midi
import com.dewijones92.primavista.score.MusicalTime
import com.dewijones92.primavista.score.NoteSymbol
import com.dewijones92.primavista.score.Polyphony
import com.dewijones92.primavista.score.Score
import com.dewijones92.primavista.score.ScoreId
import com.dewijones92.primavista.score.SeededExerciseGenerator
import com.dewijones92.primavista.score.Staff
import com.dewijones92.primavista.score.Ticks
import com.dewijones92.primavista.score.TimeSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SEED = 20260815L
private const val TEMPO_BPM = 72
private const val LATENCY_MS = 61.0
private const val ORIGIN_NANOS = 1_400_000_000_000L
private const val WRONG_NOTE_INDEX = 2
private const val A_SEMITONE = 1
private const val A_LONG_TIME_NANOS = 5_000_000_000L

/**
 * **docs/spec.md I7, made real.**
 *
 * The spec's bar is not "there are logs": a report Dewi shares a week later must let a fresh
 * session re-judge the performance and reach the same verdicts, or say why it cannot. So this
 * plays a session, writes the replay the report carries, reads it back out of a report's worth of
 * surrounding text, rebuilds the music from the seed alone, and re-judges — then asserts the
 * verdicts match what the report claimed.
 *
 * Everything here is pure and in fake time. That is the point: the invariant that says a report
 * can settle what happened is checkable without a phone.
 */
class SessionReplayTest {

    private val generator = SeededExerciseGenerator()
    private val parser = DomMusicXmlParser()
    private val judge = WindowedJudge(Tolerances()) { emptySet() }
    private val spec = FakeSpecText()

    @Test
    fun `a report can re-judge the session it came from and reach the same verdicts`() {
        val played = performance(exercise(), wrongAt = WRONG_NOTE_INDEX)
        val replay = replayOf(played)

        val reread = readBack(reportAround(SessionReplayCodec.encode(replay, spec)))

        assertEquals(replay.claimed, rejudge(reread).map(ClaimedVerdict::of))
    }

    @Test
    fun `the report's own claim is not what the replay reads, so a wrong claim is caught`() {
        val replay = replayOf(performance(exercise(), wrongAt = WRONG_NOTE_INDEX))
        val lying = replay.copy(claimed = replay.claimed.map { it.copy(kind = "Correct") })

        val reread = readBack(reportAround(SessionReplayCodec.encode(lying, spec)))

        assertNotEquals(reread.claimed, rejudge(reread).map(ClaimedVerdict::of))
    }

    @Test
    fun `a perfect performance replays as entirely clean`() {
        val replay = replayOf(performance(exercise(), wrongAt = null))

        val verdicts = rejudge(readBack(reportAround(SessionReplayCodec.encode(replay, spec))))

        assertTrue(verdicts.toString(), verdicts.all { it.verdict.isClean })
        assertEquals(exercise().attackedNotes.size, verdicts.size)
    }

    /** A pause moves musical zero in wall time; a replay that lost the legs would re-judge late. */
    @Test
    fun `the pauses that happened travel with the replay`() {
        val score = exercise()
        val paused = listOf(PauseLeg(0L, ORIGIN_NANOS), PauseLeg(2L * QUARTER, ORIGIN_NANOS + A_LONG_TIME_NANOS))
        val timing = SessionReplay(
            score = ScoreRef.Generated(SEED, difficulty()),
            tempoBpm = TEMPO_BPM,
            time = TimeSignature.FourFour,
            legs = paused,
            inputLabel = "TAP",
            polyphony = Polyphony.Poly,
            latency = InputLatency.None,
            played = emptyList(),
            claimed = emptyList(),
        )
        val played = score.attackedNotes.map { note ->
            PlayedNote(note.pitch.midi, timing.timing().nanosFor(note.onset))
        }
        val replay = timing.copy(
            played = played,
            claimed = judgeWith(score, timing.timing(), played).map(ClaimedVerdict::of)
        )

        val reread = readBack(reportAround(SessionReplayCodec.encode(replay, spec)))

        assertEquals(paused, reread.legs)
        assertEquals(replay.claimed, rejudge(reread).map(ClaimedVerdict::of))
        assertTrue("a pause must not make the notes after it read late", reread.claimed.all { it.kind == "Correct" })
    }

    @Test
    fun `a shipped piece replays from its id`() {
        val piece = Corpus.pieces.first()
        val rebuilt = ScoreRef.Shipped(piece.id).rebuild(generator, parser)

        assertTrue("$rebuilt", rebuilt is ReplayScore.Rebuilt)
        assertEquals(piece.id, (rebuilt as ReplayScore.Rebuilt).score.id)
    }

    @Test
    fun `a passage replays from its piece and its bars`() {
        val piece = Corpus.pieces.first { whole(it.id).measures.size >= PASSAGE_BARS }
        val rebuilt = ScoreRef.Passage(piece.id, fromBar = 1, bars = PASSAGE_BARS).rebuild(generator, parser)

        assertTrue("$rebuilt", rebuilt is ReplayScore.Rebuilt)
        assertEquals(PASSAGE_BARS, (rebuilt as ReplayScore.Rebuilt).score.measures.size)
    }

    /** A build that no longer ships the piece must say so, not return an empty score. */
    @Test
    fun `a piece this build no longer ships is a stated loss`() {
        val rebuilt = ScoreRef.Shipped(ScoreId("gone-in-a-later-build")).rebuild(generator, parser)

        assertTrue("$rebuilt", rebuilt is ReplayScore.Lost)
        assertTrue((rebuilt as ReplayScore.Lost).reason, rebuilt.reason.contains("gone-in-a-later-build"))
    }

    @Test
    fun `a report with no replay block says so rather than returning nothing`() {
        val reading = SessionReplayCodec.read("just some ordinary log lines", spec)

        assertTrue("$reading", reading is ReplayReading.Unreadable)
        assertTrue((reading as ReplayReading.Unreadable).reason, reading.reason.contains("no replay block"))
    }

    @Test
    fun `a report truncated mid-block says so rather than guessing`() {
        val whole = SessionReplayCodec.encode(replayOf(performance(exercise(), wrongAt = null)), spec)

        val reading = SessionReplayCodec.read(whole.substringBefore(SessionReplayCodec.END), spec)

        assertTrue("$reading", reading is ReplayReading.Unreadable)
        assertTrue((reading as ReplayReading.Unreadable).reason, reading.reason.contains("truncated"))
    }

    @Test
    fun `everything the session was set up with survives the round trip`() {
        val replay = replayOf(performance(exercise(), wrongAt = WRONG_NOTE_INDEX))

        assertEquals(replay, readBack(reportAround(SessionReplayCodec.encode(replay, spec))))
    }

    private fun exercise(): Score = generator.generate(SEED, difficulty())

    private fun whole(id: ScoreId): Score =
        (ScoreRef.Shipped(id).rebuild(generator, parser) as ReplayScore.Rebuilt).score

    private fun difficulty(): DifficultySpec = DifficultySpec(
        staves = listOf(Staff.Upper),
        clefs = mapOf(Staff.Upper to Clef.Treble),
        key = KeySignature.C,
        time = TimeSignature.FourFour,
        bars = 4,
        range = mapOf(Staff.Upper to Midi(60)..Midi(79)),
        symbols = setOf(NoteSymbol.Half, NoteSymbol.Quarter),
        maxDots = 0,
        allowTuplets = false,
        allowedAlterations = setOf(Alter.Natural),
        maxLeapSemitones = 7,
        tempoBpm = TEMPO_BPM,
        bothHandsActive = false,
    )

    /** Dead on the beat, with one note deliberately a semitone out when [wrongAt] says so. */
    private fun performance(score: Score, wrongAt: Int?): List<PlayedNote> {
        val timing = timingOf(SessionReplay.Unbroken.map { PauseLeg(it.fromTicks, ORIGIN_NANOS) })
        return score.attackedNotes.mapIndexed { index, note ->
            val midi = if (index == wrongAt) Midi(note.pitch.midi.number + A_SEMITONE) else note.pitch.midi
            PlayedNote(midi, timing.nanosFor(note.onset))
        }
    }

    private fun replayOf(played: List<PlayedNote>): SessionReplay {
        val score = exercise()
        val legs = listOf(PauseLeg(0L, ORIGIN_NANOS))
        return SessionReplay(
            score = ScoreRef.Generated(SEED, difficulty()),
            tempoBpm = TEMPO_BPM,
            time = TimeSignature.FourFour,
            legs = legs,
            inputLabel = "TAP",
            polyphony = Polyphony.Poly,
            latency = InputLatency(LATENCY_MS, InputLatency.Provenance.Measured),
            played = played,
            claimed = judgeWith(score, timingOf(legs), played).map(ClaimedVerdict::of),
        )
    }

    private fun rejudge(replay: SessionReplay): List<NoteJudgement> {
        val rebuilt = replay.score.rebuild(generator, parser)
        assertTrue("$rebuilt", rebuilt is ReplayScore.Rebuilt)
        return judgeWith((rebuilt as ReplayScore.Rebuilt).score, replay.timing(), replay.played)
    }

    private fun judgeWith(score: Score, timing: TickTiming, played: List<PlayedNote>): List<NoteJudgement> {
        var state = judge.begin(score, timing)
        val settled = mutableListOf<NoteJudgement>()
        played.forEach { note ->
            val (next, judged) = judge.advance(state, note)
            state = next
            settled += judged
        }
        val (_, closing) = judge.advanceTime(state, score.endsAt + Ticks(QUARTER))
        return settled + closing
    }

    private fun timingOf(legs: List<PauseLeg>): TickTiming = SessionReplay(
        score = ScoreRef.Generated(SEED, difficulty()),
        tempoBpm = TEMPO_BPM,
        time = TimeSignature.FourFour,
        legs = legs,
        inputLabel = "TAP",
        polyphony = Polyphony.Poly,
        latency = InputLatency.None,
        played = emptyList(),
        claimed = emptyList(),
    ).timing()

    /** A replay never arrives alone: it is one block inside a report full of other lines. */
    private fun reportAround(block: String): String =
        "PrimaVista 0.1.9 (abc1234)\ndevice=Pixel 7\n\n$block\n\nladder: next -> generated\n"

    private fun readBack(report: String): SessionReplay {
        val reading = SessionReplayCodec.read(report, spec)
        assertTrue("$reading", reading is ReplayReading.Readable)
        return (reading as ReplayReading.Readable).replay
    }

    private companion object {
        const val PASSAGE_BARS = 2
        const val QUARTER = MusicalTime.TICKS_PER_QUARTER
    }
}
