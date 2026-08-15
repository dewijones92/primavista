package com.dewijones92.primavista.ui.results

import com.dewijones92.primavista.ui.mascot.MascotMood
import com.dewijones92.primavista.ui.mascot.isPleased
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The mascot half of the celebration gate. `ResultToneTest` stops the *numbers* flattering a bad
 * run; these stop the *face* doing it, which is the easier one to get wrong because a cheerful bird
 * feels harmless.
 */
class ResultMoodTest {

    @Test
    fun `the pleased faces are exactly the two tones a good run earns`() {
        val pleased = ResultTone.entries.filter { moodFor(it).isPleased }

        assertEquals(listOf(ResultTone.Good, ResultTone.Excellent), pleased)
    }

    @Test
    fun `a poor run drawn through the real path is never met with a pleased bird`() {
        val poor = resultOf(correct = 4, expected = 10)

        assertFalse(moodFor(toneOf(poor)).isPleased)
        assertEquals(MascotMood.Wincing, moodFor(toneOf(poor)))
    }

    @Test
    fun `every note right plus a trill of extras does not earn a pleased bird either`() {
        val noisy = resultOf(correct = 10, expected = 10, extras = 6)

        assertEquals(1.0, noisy.accuracy, 0.0)
        assertFalse("accuracy alone would have cheered this", moodFor(toneOf(noisy)).isPleased)
    }

    @Test
    fun `a session with nothing to judge is a question, not a verdict`() {
        val empty = resultOf(correct = 0, expected = 0)

        assertEquals(MascotMood.Curious, moodFor(toneOf(empty)))
    }

    @Test
    fun `a clean run gets the rarer of the two pleased faces`() {
        assertEquals(MascotMood.Impressed, moodFor(toneOf(resultOf(correct = 20, expected = 20))))
        assertEquals(MascotMood.Delighted, moodFor(toneOf(resultOf(correct = 18, expected = 20))))
    }
}
