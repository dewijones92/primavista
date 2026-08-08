package com.dewijones92.primavista.ui.results

import com.dewijones92.primavista.practice.NoteJudgement
import com.dewijones92.primavista.practice.SessionResult
import com.dewijones92.primavista.practice.Verdict
import com.dewijones92.primavista.score.Midi
import com.dewijones92.primavista.score.Ticks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The celebration gate. These are the assertions that stop the results sheet flattering a bad run,
 * which is the one thing the whole app must never do.
 */
class ResultToneTest {

    @Test
    fun `a poor run is never celebrated`() {
        val poor = resultOf(correct = 4, expected = 10)

        assertEquals(ResultTone.Rough, toneOf(poor))
        assertFalse(toneOf(poor).celebrates)
    }

    @Test
    fun `every note right plus a trill of extras is not a clean run`() {
        val noisy = resultOf(correct = 10, expected = 10, extras = 6)

        assertEquals(1.0, noisy.accuracy, 0.0)
        assertFalse("accuracy alone would have celebrated this", toneOf(noisy).celebrates)
    }

    @Test
    fun `a genuinely clean run is the only thing that celebrates`() {
        assertTrue(toneOf(resultOf(correct = 20, expected = 20)).celebrates)
        assertFalse(toneOf(resultOf(correct = 18, expected = 20)).celebrates)
        assertFalse(toneOf(resultOf(correct = 13, expected = 20)).celebrates)
    }

    @Test
    fun `a session with nothing to judge says so rather than scoring zero`() {
        val empty = resultOf(correct = 0, expected = 0)

        assertEquals(ResultTone.Nothing, toneOf(empty))
        assertFalse(toneOf(empty).celebrates)
    }

    @Test
    fun `the extras note appears only when there were extras, and states the cleanliness`() {
        assertEquals(null, extrasNote(resultOf(correct = 10, expected = 10)))

        val note = extrasNote(resultOf(correct = 10, expected = 10, extras = 10))

        assertTrue("expected the cleanliness figure in '$note'", note!!.contains("50%"))
    }

    @Test
    fun `no supporting line congratulates a run that did not earn it`() {
        val congratulatory = listOf("well done", "great", "nice", "brilliant", "perfect")
        listOf(ResultTone.Rough, ResultTone.Mixed).forEach { tone ->
            val line = supportOf(resultOf(correct = 3, expected = 10), tone).lowercase()
            congratulatory.forEach { word ->
                assertFalse("'$line' congratulates a $tone run", line.contains(word))
            }
        }
    }

    private fun resultOf(correct: Int, expected: Int, extras: Int = 0): SessionResult {
        val judged = List(correct) { NoteJudgement.OfNote(it, Verdict.Correct(0.0)) } +
            List(expected - correct) { NoteJudgement.OfNote(correct + it, Verdict.Missed) } +
            List(extras) { NoteJudgement.Unexpected(Verdict.Extra(Midi(60), Ticks(it.toLong()))) }
        return SessionResult(judgements = judged, skillOutcomes = emptyList(), notesExpected = expected)
    }
}
