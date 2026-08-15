package com.dewijones92.primavista.ui.onboarding

import com.dewijones92.primavista.score.Clef
import com.dewijones92.primavista.score.StaffGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The introduction teaches one idea, so the arithmetic behind it has to be right: a perch height on
 * Trill's staff must name the pitch a musician would name.
 */
class StaffPitchTest {

    @Test
    fun `the treble staff's five lines are E G B D F, bottom to top`() {
        val lines = listOf(-4, -2, 0, 2, 4).map(::perchName)

        assertEquals(listOf("E4", "G4", "B4", "D5", "F5"), lines)
    }

    @Test
    fun `the four spaces spell FACE`() {
        val spaces = listOf(-3, -1, 1, 3).map { perchName(it).first() }

        assertEquals(listOf('F', 'A', 'C', 'E'), spaces)
    }

    @Test
    fun `a perch is described as a line or a space, and the middle line is named as one`() {
        assertEquals("on the middle line", perchPlace(0))
        assertEquals("on the bottom line", perchPlace(-4))
        assertEquals("on the top line", perchPlace(4))
        assertTrue(perchPlace(-1).startsWith("in the"))
        assertTrue(perchPlace(3).endsWith("space"))
    }

    @Test
    fun `the perch offset is the one the staff geometry itself uses`() {
        PERCH_RANGE.forEach { perch ->
            val pitch = perchPitch(perch)
            assertEquals(perch + MIDDLE_LINE_STEP, StaffGeometry.stepOf(Clef.Treble, pitch))
        }
    }

    @Test
    fun `going up the staff always goes up in sounding pitch`() {
        val sounding = PERCH_RANGE.map { perchPitch(it).midi.number }

        assertEquals(sounding.sorted(), sounding)
        assertEquals(PERCH_RANGE.count(), sounding.distinct().size)
    }

    @Test
    fun `the bass clef reads off the same arithmetic rather than a second table`() {
        val lines = listOf(-4, -2, 0, 2, 4).map { perchName(it, Clef.Bass) }

        assertEquals(listOf("G2", "B2", "D3", "F3", "A3"), lines)
    }
}
