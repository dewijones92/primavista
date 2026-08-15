package com.dewijones92.primavista.ui.progress

import com.dewijones92.primavista.practice.SkillState
import com.dewijones92.primavista.score.Clef
import com.dewijones92.primavista.score.PitchBand
import com.dewijones92.primavista.score.SkillTag
import com.dewijones92.primavista.ui.mascot.MascotMood
import com.dewijones92.primavista.ui.mascot.isPleased
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Progress is where a mascot most easily starts lying, because the screen is full of numbers and a
 * face is not one of them. Every pleased greeting here has to name the evidence that earned it.
 */
class ProgressGreetingTest {

    @Test
    fun `an empty screen is a sleeping bird, not an encouraging one`() {
        val greeting = greetingFor(emptyList(), emptyList())

        assertEquals(MascotMood.Sleepy, greeting.mood)
        assertFalse(greeting.mood.isPleased)
        assertEquals("this line is the empty screen's headline", "Nothing read yet", greeting.line)
    }

    @Test
    fun `a stored session cannot wake her while no skill has been tracked`() {
        val greeting = greetingFor(emptyList(), sessionsOf(0.1, 0.2, 0.3, 0.99))

        assertEquals(MascotMood.Sleepy, greeting.mood)
    }

    @Test
    fun `a shaky skill store is met with an ordinary face and the plain count`() {
        val greeting = greetingFor(listOf(state(0.2), state(0.5)), sessionsOf(0.3, 0.2, 0.4, 0.1))

        assertEquals(MascotMood.Idle, greeting.mood)
        assertTrue(greeting.line, greeting.line.startsWith("2 reading skills tracked"))
    }

    @Test
    fun `a best-yet session is the impressed face, and the line states both figures`() {
        val greeting = greetingFor(listOf(state(0.4)), sessionsOf(0.30, 0.55, 0.40, 0.80))

        assertEquals(MascotMood.Impressed, greeting.mood)
        assertTrue(greeting.line, greeting.line.contains("80%"))
        assertTrue(greeting.line, greeting.line.contains("55%"))
    }

    @Test
    fun `three sessions are not enough for a best, however good the last one is`() {
        val greeting = greetingFor(listOf(state(0.4)), sessionsOf(0.1, 0.2, 0.99))

        assertFalse("a best out of three is arithmetic on nothing", greeting.mood.isPleased)
    }

    @Test
    fun `matching the previous best is not beating it`() {
        val greeting = greetingFor(listOf(state(0.4)), sessionsOf(0.2, 0.7, 0.3, 0.7))

        assertFalse(greeting.mood.isPleased)
    }

    @Test
    fun `a run of identical sessions never becomes a personal best`() {
        val greeting = greetingFor(listOf(state(0.4)), sessionsOf(0.5, 0.5, 0.5, 0.5, 0.5, 0.5))

        assertEquals(MascotMood.Idle, greeting.mood)
    }

    @Test
    fun `every skill reading solid is the one thing that earns a delighted bird`() {
        val solid = listOf(state(SkillState.SOLID_STRENGTH), state(0.95))

        assertEquals(MascotMood.Delighted, greetingFor(solid, emptyList()).mood)
    }

    @Test
    fun `one skill short of solid takes the delight away again`() {
        val nearly = listOf(state(SkillState.SOLID_STRENGTH), state(SkillState.SOLID_STRENGTH - 0.01))

        assertFalse(greetingFor(nearly, emptyList()).mood.isPleased)
    }

    private fun state(strength: Double, tag: SkillTag = SkillTag.ClefRegion(Clef.Bass, PitchBand.BelowStaff)) =
        SkillState(tag, strength, NOW, attempts = 3, lapses = 0)

    private fun sessionsOf(vararg accuracies: Double) =
        accuracies.mapIndexed { index, accuracy -> SessionPoint(NOW + index, accuracy, "piece") }

    private companion object {
        const val NOW = 1_754_000_000_000L
    }
}
