package com.dewijones92.primavista.ui.practice

import com.dewijones92.primavista.practice.SpacedPracticeScheduler
import com.dewijones92.primavista.score.SeededExerciseGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val A_TEMPO = 72
private const val A_SEED = 7L

/**
 * What the header says before a piece exists.
 *
 * It said the piece was called *PrimaVista* and was at *0 bpm* — the app's own name in the title
 * slot and a tempo for nothing. Seen on the emulator, it reads as a broken piece rather than as one
 * being chosen, and nought beats a minute is not a tempo for the same reason an unmeasured latency
 * must not read as 0ms.
 */
class PracticeHeadingTest {

    private val score = SeededExerciseGenerator().generate(A_SEED, SpacedPracticeScheduler.DefaultBase)

    @Test
    fun `with no piece yet the heading never claims the app's own name as a title`() {
        val heading = headingFor(PracticeUiState())

        assertTrue(heading, !heading.contains("PrimaVista"))
        assertTrue(heading, heading.isNotBlank())
    }

    @Test
    fun `with no piece yet there is no tempo at all rather than nought`() {
        assertNull(tempoLabelFor(PracticeUiState()))
        assertNull(tempoLabelFor(PracticeUiState(tempoBpm = 0)))
    }

    @Test
    fun `once a piece is chosen the heading is its title`() {
        val state = PracticeUiState(score = score, tempoBpm = A_TEMPO)

        assertEquals(score.title, headingFor(state))
    }

    @Test
    fun `once a piece is chosen the tempo is shown with its unit`() {
        val state = PracticeUiState(score = score, tempoBpm = A_TEMPO)

        assertEquals("$A_TEMPO bpm", tempoLabelFor(state))
    }

    /** A piece with a nonsense tempo is still not a reason to print "0 bpm". */
    @Test
    fun `a piece whose tempo is nought shows no tempo`() {
        assertNull(tempoLabelFor(PracticeUiState(score = score, tempoBpm = 0)))
    }
}
