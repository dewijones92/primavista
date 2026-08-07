package com.dewijones92.primavista.practice

import com.dewijones92.primavista.score.Letter
import com.dewijones92.primavista.score.NoteSymbol
import com.dewijones92.primavista.score.Polyphony
import com.dewijones92.primavista.score.Score
import com.dewijones92.primavista.score.SkillTag
import com.dewijones92.primavista.score.Staff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val SCALE = listOf(
    Letter.C to 4,
    Letter.D to 4,
    Letter.E to 4,
    Letter.F to 4,
    Letter.G to 4,
    Letter.A to 4,
    Letter.B to 4,
    Letter.C to 5,
)

private fun scaleScore(): Score =
    scoreOf(SCALE.mapIndexed { index, (letter, octave) -> tone(beat(index), letter, octave) })

private fun taggedJudge(): WindowedJudge = WindowedJudge(
    skillsOfNote = { note ->
        setOf(
            trebleMiddle,
            SkillTag.RhythmFigure(note.duration.symbol, dots = 0, tupletNumerator = 1),
        )
    },
)

private fun judgeAll(
    judge: WindowedJudge,
    score: Score,
    timing: TickTiming,
    played: List<PlayedNote>,
): SessionResult = judged(judge.judgeAll(score, polySource, timing, played))

class WindowedJudgeTest {
    @Test
    fun `a perfect performance has no faults at all`() {
        val score = scaleScore()
        val timing = startedTiming()

        val result = judgeAll(WindowedJudge(), score, timing, perfectPerformance(score, timing))

        assertEquals(SCALE.size, result.notesExpected)
        assertEquals(SCALE.size, result.judgements.size)
        assertEquals(SCALE.size, result.correct)
        assertEquals(1.0, result.accuracy, 0.0)
        assertEquals(1.0, result.cleanliness, 0.0)
        assertEquals(0, result.extras)
        assertTrue(result.judgements.all { it.verdict == Verdict.Correct(0.0) })
        assertEquals(
            score.attackedNotes.indices.toList(),
            result.judgements.filterIsInstance<NoteJudgement.OfNote>().map { it.noteIndex },
        )
    }

    @Test
    fun `timing verdicts follow the tolerance bands`() {
        val cases = listOf(
            Triple(-50.0, "50ms early", Verdict.Correct(-50.0)),
            Triple(50.0, "50ms late", Verdict.Correct(50.0)),
            Triple(-90.0, "on the early edge", Verdict.Correct(-90.0)),
            Triple(-150.0, "150ms early", Verdict.Early(-150.0)),
            Triple(250.0, "250ms late", Verdict.Late(250.0)),
        )
        cases.forEach { (offsetMillis, name, expected) ->
            val score = scoreOf(listOf(tone(beat(1), Letter.C, 4)))
            val timing = startedTiming()
            val played = PlayedNote(midiOf(Letter.C, 4), timing.nanosFor(beat(1)) + ms(offsetMillis))

            val result = judgeAll(WindowedJudge(), score, timing, listOf(played))

            assertEquals(name, expected, result.judgements.single().verdict)
        }
    }

    @Test
    fun `a wrong pitch played on time is a wrong pitch and not an extra`() {
        val score = scoreOf(listOf(tone(beat(1), Letter.C, 4)))
        val timing = startedTiming()
        val played = PlayedNote(midiOf(Letter.D, 4), timing.nanosFor(beat(1)))

        val result = judgeAll(WindowedJudge(), score, timing, listOf(played))

        assertEquals(
            ofNote(0, Verdict.WrongPitch(midiOf(Letter.C, 4), midiOf(Letter.D, 4), 0.0)),
            result.judgements.single(),
        )
        assertEquals(0, result.correct)
    }

    @Test
    fun `a wrong note played on time stays a wrong note at any tempo`() {
        listOf(60, 200).forEach { bpm ->
            val score = scoreOf(listOf(tone(beat(1), Letter.C, 4), tone(beat(2), Letter.D, 4)))
            val timing = startedTiming(bpm)
            val played = PlayedNote(midiOf(Letter.D, 4), timing.nanosFor(beat(1)))

            val result = judgeAll(WindowedJudge(), score, timing, listOf(played))

            assertEquals(
                "at $bpm bpm",
                ofNote(0, Verdict.WrongPitch(midiOf(Letter.C, 4), midiOf(Letter.D, 4), 0.0)),
                result.judgements.first(),
            )
            assertEquals("at $bpm bpm", ofNote(1, Verdict.Missed), result.judgements.last())
        }
    }

    @Test
    fun `a window fixed at 400ms is what turned that wrong note into the next note played early`() {
        val fixed400ms = Tolerances(windowBeats = 10.0, minWindowMillis = 400.0, maxWindowMillis = 400.0)
        val score = scoreOf(listOf(tone(beat(1), Letter.C, 4), tone(beat(2), Letter.D, 4)))
        val timing = startedTiming(200)
        val played = PlayedNote(midiOf(Letter.D, 4), timing.nanosFor(beat(1)))

        val result = judgeAll(WindowedJudge(fixed400ms), score, timing, listOf(played))

        assertEquals(ofNote(1, Verdict.Early(-300.0)), result.judgements.first())
        assertEquals(ofNote(0, Verdict.Missed), result.judgements.last())
    }

    @Test
    fun `the matching window narrows with the tempo, between a floor and a ceiling`() {
        assertWindowEdges(bpm = 240, insideMillis = 115.0, outsideMillis = 140.0)
        assertWindowEdges(bpm = 500, insideMillis = 115.0, outsideMillis = 130.0)
        assertWindowEdges(bpm = 60, insideMillis = 395.0, outsideMillis = 410.0)
        assertWindowEdges(bpm = 30, insideMillis = 395.0, outsideMillis = 410.0)
    }

    @Test
    fun `an exact pitch is preferred to a nearer wrong pitch`() {
        val score = scoreOf(
            listOf(
                tone(beat(1), Letter.E, 4),
                tone(halfBeat(3), Letter.C, 4),
            ),
        )
        val timing = startedTiming()
        val played = PlayedNote(midiOf(Letter.E, 4), timing.nanosFor(beat(1)) + ms(400.0))

        val judge = WindowedJudge()
        val (_, settled) = judge.advance(judge.begin(score, timing), played)

        assertEquals(ofNote(0, Verdict.Late(400.0)), settled.single())
    }

    @Test
    fun `nothing played is missed, but only once the window has closed`() {
        val score = scoreOf(listOf(tone(beat(1), Letter.C, 4)))
        val timing = startedTiming()
        val judge = WindowedJudge()

        var state = judge.begin(score, timing)
        val atOnset = judge.advanceTime(state, beat(1))
        state = atOnset.first
        assertEquals(emptyList<NoteJudgement>(), atOnset.second)

        val onTheEdge = judge.advanceTime(state, timing.ticksAt(timing.nanosFor(beat(1)) + ms(400.0)))
        state = onTheEdge.first
        assertEquals("a note inside its window is not missed yet", emptyList<NoteJudgement>(), onTheEdge.second)

        val past = judge.advanceTime(state, beat(2))
        state = past.first
        assertEquals(listOf(ofNote(0, Verdict.Missed)), past.second)

        val result = judge.finish(state)
        assertEquals(0.0, result.accuracy, 0.0)
        assertEquals(1, result.notesExpected)
    }

    @Test
    fun `two notes inside one window are one correct and one extra`() {
        val score = scoreOf(listOf(tone(beat(1), Letter.C, 4)))
        val timing = startedTiming()
        val onset = timing.nanosFor(beat(1))
        val played = listOf(
            PlayedNote(midiOf(Letter.C, 4), onset),
            PlayedNote(midiOf(Letter.C, 4), onset + ms(80.0)),
        )

        val result = judgeAll(WindowedJudge(), score, timing, played)

        assertEquals(
            listOf(
                ofNote(0, Verdict.Correct(0.0)),
                unexpected(Verdict.Extra(midiOf(Letter.C, 4), timing.ticksAt(onset + ms(80.0)))),
            ),
            result.judgements,
        )
        assertEquals(1, result.correct)
        assertEquals(1, result.extras)
        assertEquals(0.5, result.cleanliness, 1e-9)
    }

    @Test
    fun `a trill of unexpected notes is all extras and does not disturb the missed note`() {
        val score = scoreOf(listOf(tone(beat(1), Letter.C, 4)))
        val timing = startedTiming()
        val trillStart = timing.nanosFor(beat(3))
        val played = (0 until 8).map { index ->
            PlayedNote(midiOf(if (index % 2 == 0) Letter.B else Letter.C, 5), trillStart + ms(index * 30.0))
        }

        val result = judgeAll(WindowedJudge(), score, timing, played)

        assertEquals(8, result.judgements.count { it.verdict is Verdict.Extra })
        assertEquals(8, result.extras)
        assertEquals(1, result.judgements.count { it.verdict == Verdict.Missed })
        assertEquals(ofNote(0, Verdict.Missed), result.judgements.first())
        assertEquals(0, result.correct)
    }

    @Test
    fun `a tied continuation is never judged`() {
        val score = scoreOf(
            listOf(
                tone(beat(0), Letter.C, 4, tiedToNext = true),
                tone(beat(1), Letter.C, 4, tiedFromPrevious = true),
                tone(beat(2), Letter.E, 4),
            ),
        )
        val timing = startedTiming()
        assertEquals(3, score.notes.size)
        assertEquals(2, score.attackedNotes.size)

        val reAttacked = PlayedNote(midiOf(Letter.C, 4), timing.nanosFor(beat(1)))
        val played = perfectPerformance(score, timing) + reAttacked

        val result = judgeAll(WindowedJudge(), score, timing, played)

        assertEquals(2, result.notesExpected)
        assertEquals(2, result.correct)
        assertEquals(
            listOf(
                ofNote(0, Verdict.Correct(0.0)),
                unexpected(Verdict.Extra(midiOf(Letter.C, 4), beat(1))),
                ofNote(1, Verdict.Correct(0.0)),
            ),
            result.judgements,
        )
    }

    @Test
    fun `the live fold and judgeAll reach identical verdicts`() {
        val score = scaleScore()
        val timing = startedTiming()
        val played = listOf(
            PlayedNote(midiOf(Letter.C, 4), timing.nanosFor(beat(0)) + ms(30.0)),
            PlayedNote(midiOf(Letter.D, 4), timing.nanosFor(beat(1)) + ms(200.0)),
            PlayedNote(midiOf(Letter.G, 4), timing.nanosFor(beat(3))),
            PlayedNote(midiOf(Letter.C, 4), timing.nanosFor(beat(3)) + ms(120.0)),
            PlayedNote(midiOf(Letter.A, 4), timing.nanosFor(beat(5)) - ms(150.0)),
            PlayedNote(midiOf(Letter.C, 5), timing.nanosFor(beat(7)) + ms(500.0)),
        )
        val judge = taggedJudge()

        val batch = judgeAll(judge, score, timing, played)
        val live = driveLive(judge, score, timing, played)

        assertEquals(batch.judgements, live.judgements)
        assertEquals(batch.skillOutcomes, live.skillOutcomes)
        assertEquals(batch.notesExpected, live.notesExpected)
        assertTrue(
            "the fixture must exercise every verdict shape",
            batch.judgements.map { it.verdict::class }.toSet().size >= 5,
        )
    }

    @Test
    fun `skill outcomes tally per tag and ignore extras`() {
        val score = scoreOf(
            listOf(
                tone(beat(0), Letter.C, 4),
                tone(beat(1), Letter.D, 4, symbol = NoteSymbol.Half),
            ),
            bars = 2,
        )
        val timing = startedTiming()
        val played = listOf(
            PlayedNote(midiOf(Letter.C, 4), timing.nanosFor(beat(0))),
            PlayedNote(midiOf(Letter.E, 4), timing.nanosFor(beat(1))),
            PlayedNote(midiOf(Letter.C, 4), timing.nanosFor(beat(3))),
        )

        val result = judgeAll(taggedJudge(), score, timing, played)
        val byTag = result.skillOutcomes.associateBy { it.tag }

        assertEquals(SkillOutcome(trebleMiddle, attempts = 2, cleanAttempts = 1), byTag[trebleMiddle])
        assertEquals(SkillOutcome(quarterRhythm, attempts = 1, cleanAttempts = 1), byTag[quarterRhythm])
        val halfRhythm = SkillTag.RhythmFigure(NoteSymbol.Half, dots = 0, tupletNumerator = 1)
        assertEquals(SkillOutcome(halfRhythm, attempts = 1, cleanAttempts = 0), byTag[halfRhythm])
        assertEquals(0.5, byTag.getValue(trebleMiddle).accuracy, 0.0)
    }

    @Test
    fun `confidence rides along with the verdict it belongs to`() {
        val score = scoreOf(listOf(tone(beat(1), Letter.C, 4)))
        val timing = startedTiming()
        val played = listOf(
            PlayedNote(midiOf(Letter.C, 4), timing.nanosFor(beat(1)), confidence = 0.42f),
            PlayedNote(midiOf(Letter.B, 4), timing.nanosFor(beat(3)), confidence = 0.11f),
        )

        val result = judgeAll(WindowedJudge(), score, timing, played)

        assertEquals(0.42f, result.judgements.first().confidence)
        assertEquals(0.11f, result.judgements.last().confidence)
    }

    @Test
    fun `a mono source on a polyphonic score is refused, naming the first polyphonic bar`() {
        val score = scoreOf(
            listOf(
                tone(beat(0), Letter.C, 4),
                tone(beat(4), Letter.E, 4),
                tone(beat(4), Letter.G, 2, staff = Staff.Lower),
            ),
            bars = 2,
        )

        val reason = WindowedJudge().accepts(score, FakeSource("mic", Polyphony.Mono))

        assertEquals(RefusalReason.PolyphonicScoreOnMonoInput(firstPolyphonicBar = 2, inputLabel = "mic"), reason)
    }

    @Test
    fun `a held left hand under a moving right hand is polyphonic, though no onset is shared`() {
        val score = scoreOf(
            listOf(
                tone(beat(0), Letter.C, 4),
                tone(beat(1), Letter.D, 4),
                tone(beat(2), Letter.E, 4),
                tone(beat(3), Letter.F, 4),
                tone(beat(4), Letter.G, 2, staff = Staff.Lower, symbol = NoteSymbol.Whole),
                tone(beat(5), Letter.G, 4),
                tone(beat(6), Letter.A, 4),
                tone(beat(7), Letter.B, 4),
            ),
            bars = 2,
        )

        val reason = WindowedJudge().accepts(score, FakeSource("mic", Polyphony.Mono))

        assertEquals(RefusalReason.PolyphonicScoreOnMonoInput(firstPolyphonicBar = 2, inputLabel = "mic"), reason)
        assertNull(WindowedJudge().accepts(score, FakeSource("tap", Polyphony.Poly)))
    }

    @Test
    fun `a polyphonic score is accepted by a polyphonic source`() {
        val score = scoreOf(
            listOf(
                tone(beat(0), Letter.E, 4),
                tone(beat(0), Letter.G, 4),
            ),
        )

        assertNull(WindowedJudge().accepts(score, KeyboardTapSource()))
        assertEquals(
            RefusalReason.PolyphonicScoreOnMonoInput(1, "mic"),
            WindowedJudge().accepts(score, FakeSource("mic", Polyphony.Mono)),
        )
    }

    @Test
    fun `a score with nothing to attack is refused`() {
        val tiedOnly = scoreOf(listOf(tone(beat(0), Letter.C, 4, tiedFromPrevious = true)), title = "empty one")

        assertEquals(
            RefusalReason.EmptyScore("empty one"),
            WindowedJudge().accepts(tiedOnly, FakeSource("tap", Polyphony.Poly)),
        )
        assertEquals(
            RefusalReason.EmptyScore("fixture"),
            WindowedJudge().accepts(scoreOf(emptyList()), FakeSource("mic", Polyphony.Mono)),
        )
    }

    @Test
    fun `a mono source on a monophonic score is accepted`() {
        assertNull(WindowedJudge().accepts(scaleScore(), FakeSource("mic", Polyphony.Mono)))
    }

    @Test
    fun `judgeAll refuses rather than scoring what the input cannot hear`() {
        val score = scoreOf(listOf(tone(beat(0), Letter.E, 4), tone(beat(0), Letter.G, 4)))
        val timing = startedTiming()
        val judge = WindowedJudge()
        val played = perfectPerformance(score, timing)

        val refused = judge.judgeAll(score, FakeSource("mic", Polyphony.Mono), timing, played)
        val accepted = judge.judgeAll(score, KeyboardTapSource(), timing, played)

        assertEquals(JudgeOutcome.Refused(RefusalReason.PolyphonicScoreOnMonoInput(1, "mic")), refused)
        assertEquals(2, judged(accepted).correct)
    }

    @Test
    fun `a window narrower than the on-time band is not a tolerance`() {
        val tooTight = runCatching { Tolerances(onTimeMillis = 200.0, minWindowMillis = 100.0) }.exceptionOrNull()
        val inverted = runCatching {
            Tolerances(minWindowMillis = 500.0, maxWindowMillis = 100.0)
        }.exceptionOrNull()
        val nonsense = runCatching { Tolerances(onTimeMillis = 0.0) }.exceptionOrNull()
        val noBeat = runCatching { Tolerances(windowBeats = 0.0) }.exceptionOrNull()

        assertTrue(tooTight is IllegalArgumentException)
        assertTrue(inverted is IllegalArgumentException)
        assertTrue(nonsense is IllegalArgumentException)
        assertTrue(noBeat is IllegalArgumentException)
    }
}

/** A note this late is still the written note; a note that late answers to nothing. */
private fun assertWindowEdges(bpm: Int, insideMillis: Double, outsideMillis: Double) {
    val score = scoreOf(listOf(tone(beat(1), Letter.C, 4)))
    val timing = startedTiming(bpm)
    val judge = WindowedJudge()
    fun verdictAt(offsetMillis: Double): NoteJudgement {
        val played = PlayedNote(midiOf(Letter.C, 4), timing.nanosFor(beat(1)) + ms(offsetMillis))
        return judge.advance(judge.begin(score, timing), played).second.single()
    }

    assertTrue("$bpm bpm, ${insideMillis}ms", verdictAt(insideMillis) is NoteJudgement.OfNote)
    assertTrue("$bpm bpm, ${outsideMillis}ms", verdictAt(outsideMillis) is NoteJudgement.Unexpected)
}

/**
 * The live path exactly as the UI drives it: sample the clock every frame, and sample it again at
 * an input's own timestamp before folding that input in.
 */
private fun driveLive(
    judge: PerformanceJudge,
    score: Score,
    timing: TickTiming,
    played: List<PlayedNote>,
    frameNanos: Long = 10_000_000L,
): SessionResult {
    var state = judge.begin(score, timing)
    val queue = played.sortedBy { it.atNanos }.toMutableList()
    val lastExpected = score.attackedNotes.maxOfOrNull { timing.nanosFor(it.onset) } ?: 0L
    val lastPlayed = played.maxOfOrNull { it.atNanos } ?: 0L
    val endNanos = maxOf(lastExpected, lastPlayed) + ms(600.0)
    var now = 0L
    while (now <= endNanos) {
        while (queue.isNotEmpty() && queue.first().atNanos <= now) {
            val note = queue.removeFirst()
            state = judge.advanceTime(state, timing.ticksAt(note.atNanos)).first
            state = judge.advance(state, note).first
        }
        state = judge.advanceTime(state, timing.ticksAt(now)).first
        now += frameNanos
    }
    return judge.finish(judge.advanceTime(state, timing.ticksAt(endNanos)).first)
}
