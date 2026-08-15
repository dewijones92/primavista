package com.dewijones92.primavista.practice

import com.dewijones92.primavista.score.MusicalTime
import com.dewijones92.primavista.score.Ticks
import com.dewijones92.primavista.score.TimeSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private val QUARTER = Ticks(MusicalTime.TICKS_PER_QUARTER)

class ReadingLeadTest {

    @Test
    fun `off covers nothing, wherever the playhead is`() {
        assertFalse(ReadingLead.Off.isOn)
        assertEquals(Ticks(5 * QUARTER.value), ReadingLead.Off.coversUpTo(Ticks(5 * QUARTER.value), FOUR_FOUR))
    }

    @Test
    fun `a beat of lead hides a note one beat before it is played`() {
        val atBarThree = Ticks(8 * QUARTER.value)

        assertEquals(Ticks(9 * QUARTER.value), ReadingLead.OneBeat.coversUpTo(atBarThree, FOUR_FOUR))
    }

    @Test
    fun `a bar of lead in common time is four quarters`() {
        assertEquals(Ticks(4 * QUARTER.value), ReadingLead.OneBar.coversUpTo(Ticks.ZERO, FOUR_FOUR))
    }

    /** A beat is a beat: in 6/8 it is an eighth, so the same lead hides less wall-clock music. */
    @Test
    fun `a beat is the time signature's beat, not always a quarter`() {
        val eighth = QUARTER.value / 2

        assertEquals(Ticks(eighth), ReadingLead.OneBeat.coversUpTo(Ticks.ZERO, TimeSignature(6, 8)))
        assertEquals(Ticks(QUARTER.value * 2), ReadingLead.OneBeat.coversUpTo(Ticks.ZERO, TimeSignature(2, 2)))
    }

    /**
     * The count-in runs at a negative position, and the cover is deliberately not clamped: during it
     * the cover sits at or before bar one, so the opening is readable and slides under the card
     * exactly as the music starts. Clamping to zero would hide bar one before Dewi ever saw it.
     */
    @Test
    fun `during a count-in the cover has not reached the music yet`() {
        val fourBeatsBefore = Ticks(-4 * QUARTER.value)

        assertTrue(ReadingLead.OneBar.coversUpTo(fourBeatsBefore, FOUR_FOUR) <= Ticks.ZERO)
        assertTrue(ReadingLead.OneBeat.coversUpTo(fourBeatsBefore, FOUR_FOUR) < Ticks.ZERO)
    }

    @Test
    fun `the cover always moves with the playhead, never against it`() {
        val positions = (-4..16).map { Ticks(it.toLong() * QUARTER.value) }
        ReadingLead.choices.forEach { lead ->
            val covers = positions.map { lead.coversUpTo(it, FOUR_FOUR).value }
            assertEquals("$lead", covers.sorted(), covers)
        }
    }

    /** A bar of lead blanks a phone viewport, so it exists to be tested and not to be offered. */
    @Test
    fun `a bar of lead is not among the choices`() {
        assertFalse(ReadingLead.OneBar in ReadingLead.choices)
    }

    @Test
    fun `the choices offered start at off and only get harder`() {
        val beats = ReadingLead.choices.map { it.beats }

        assertEquals(0, beats.first())
        assertEquals(beats.sorted(), beats)
        assertEquals(beats.distinct(), beats)
    }

    @Test
    fun `a negative lead is refused, because it would cover music already played`() {
        val failure = runCatching { ReadingLead(-1) }.exceptionOrNull()

        assertTrue("$failure", failure is IllegalArgumentException)
    }

    private companion object {
        val FOUR_FOUR = TimeSignature.FourFour
    }
}
