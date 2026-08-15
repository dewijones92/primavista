package com.dewijones92.primavista.ui.practice

import com.dewijones92.primavista.practice.NoteJudgement
import com.dewijones92.primavista.practice.RefusalReason
import com.dewijones92.primavista.practice.SessionResult
import com.dewijones92.primavista.practice.TransportState
import com.dewijones92.primavista.practice.Verdict
import com.dewijones92.primavista.score.Midi
import com.dewijones92.primavista.score.Ticks
import com.dewijones92.primavista.ui.mascot.MascotMood
import com.dewijones92.primavista.ui.mascot.isPleased
import com.dewijones92.primavista.ui.results.GOOD_AT
import com.dewijones92.primavista.ui.results.moodFor
import com.dewijones92.primavista.ui.results.toneOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one hard rule the mascot has: she is never pleased about a bad run. It is asserted here
 * rather than eyeballed, because a cheerful bird over a poor session undoes the whole app.
 */
class SessionMascotTest {

    @Test
    fun `a rough run gets sympathy, never a celebration`() {
        val mood = sessionMood(finished(run(correct = 2, expected = 10)))

        assertEquals(MascotMood.Wincing, mood)
        assertFalse(mood.isPleased)
    }

    @Test
    fun `nothing under a good run ever reaches a pleased face`() {
        for (correct in 0..12) {
            val result = run(correct = correct, expected = 12)
            val mood = sessionMood(finished(result))

            if (mood.isPleased) {
                assertTrue(
                    "Trill is $mood over a ${toneOf(result)} run",
                    result.cleanliness >= GOOD_AT,
                )
            }
        }
    }

    @Test
    fun `notes played that were never written cost her the celebration`() {
        val everyNoteRight = run(correct = 10, expected = 10)
        val sameRunWithSlips = run(correct = 10, expected = 10, extras = 6)

        assertTrue(sessionMood(finished(everyNoteRight)).isPleased)
        assertFalse(
            "extras were not counted against the run",
            sessionMood(finished(sameRunWithSlips)).isPleased,
        )
    }

    /** One mapping from a run to a face, shared with the results sheet beside it. */
    @Test
    fun `her face is the results sheet's face for the same run`() {
        for (correct in 0..12) {
            val result = run(correct = correct, expected = 12)

            assertEquals(moodFor(toneOf(result)), sessionMood(finished(result)))
        }
    }

    @Test
    fun `she goes still the moment the notation starts moving`() {
        val reading = PracticeUiState(transport = TransportState.Running, lastRun = run(2, 10))

        assertEquals(MascotMood.Listening, sessionMood(reading))
        assertEquals(
            MascotMood.Listening,
            sessionMood(reading.copy(transport = TransportState.CountingIn)),
        )
    }

    @Test
    fun `the finished run is what she reacts to, and it survives the sheet being dismissed`() {
        val dismissed = finished(run(correct = 12, expected = 12)).copy(result = null)

        assertTrue(sessionMood(dismissed).isPleased)
    }

    @Test
    fun `a listen that reached the end is not a run she has an opinion about`() {
        val listened = PracticeUiState(transport = TransportState.Finished, lastRun = null)

        assertEquals(MascotMood.Curious, sessionMood(listened))
    }

    @Test
    fun `a refusal is apologised for rather than celebrated`() {
        val refused = finished(run(correct = 12, expected = 12)).copy(
            refusal = RefusalReason.PolyphonicScoreOnMonoInput(3, "mic"),
        )

        assertEquals(MascotMood.Wincing, sessionMood(refused))
    }

    private fun finished(result: SessionResult) = PracticeUiState(
        transport = TransportState.Finished,
        result = result,
        lastRun = result,
    )

    private fun run(correct: Int, expected: Int, extras: Int = 0): SessionResult {
        val judgements = List(correct) { NoteJudgement.OfNote(it, Verdict.Correct(0.0)) } +
            List(expected - correct) { NoteJudgement.OfNote(correct + it, Verdict.Missed) } +
            List(extras) { NoteJudgement.Unexpected(Verdict.Extra(Midi(60), Ticks.ZERO)) }
        return SessionResult(judgements, skillOutcomes = emptyList(), notesExpected = expected)
    }
}
