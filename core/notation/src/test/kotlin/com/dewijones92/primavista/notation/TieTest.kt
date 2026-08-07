package com.dewijones92.primavista.notation

import com.dewijones92.primavista.score.Clef
import com.dewijones92.primavista.score.Staff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * docs/spec.md I2 again, from the other end: a notehead's [LaidOutNote.attackIndex] is what a
 * verdict is painted through, so an invented one paints the wrong note.
 */
class TieTest {
    @Test
    fun `a tie continuation carries the index of the attack it continues`() {
        val system = layoutOf(
            scoreOf(
                listOf(
                    noteOf(ticksOf(0.0), "C4"),
                    noteOf(ticksOf(1.0), "E4", tiedToNext = true),
                    noteOf(ticksOf(2.0), "E4", tiedFromPrevious = true, tiedToNext = true),
                    noteOf(ticksOf(3.0), "E4", tiedFromPrevious = true),
                ),
            ),
        )
        assertEquals(listOf(0, 1, 1, 1), system.notes.map { it.attackIndex })
        assertEquals("one curve per joined pair", 2, system.curves.size)
    }

    @Test
    fun `a tie across the staves resolves, and is drawn`() {
        val system = layoutOf(crossStaffTie())
        val upper = system.noteAt(ticksOf(1.0), Staff.Upper)
        val lower = system.noteAt(ticksOf(2.0), Staff.Lower)
        assertEquals(1, upper.attackIndex)
        assertEquals("not the fallback first note of the piece", 1, lower.attackIndex)
        val curve = system.curves.single()
        assertTrue("the curve leaves the upper note", curve.startX.value > upper.notehead.x.value)
        assertTrue("and lands on the lower one", curve.endX.value < lower.notehead.x.value)
        assertTrue("it crosses the staves", curve.endY.value > curve.startY.value)
    }

    @Test
    fun `a continuation with no attack in the score is given no attack, not attack zero`() {
        val system = layoutOf(
            scoreOf(
                listOf(
                    noteOf(ticksOf(0.0), "C4"),
                    noteOf(ticksOf(1.0), "G5", tiedFromPrevious = true),
                ),
            ),
        )
        val orphan = system.noteAt(ticksOf(1.0))
        assertNull("no attack exists, so no index is invented", orphan.attackIndex)
        assertEquals(0, system.noteAt(ticksOf(0.0)).attackIndex)
        assertNotNull("the notehead is still drawn", orphan.notehead)
        assertTrue("and nothing is tied to it", system.curves.isEmpty())
    }

    @Test
    fun `an unmatched tie start leaves the following attacks numbered as the score numbers them`() {
        val score = scoreOf(
            listOf(
                noteOf(ticksOf(0.0), "C4", tiedFromPrevious = true),
                noteOf(ticksOf(1.0), "D4"),
                noteOf(ticksOf(2.0), "E4"),
            ),
        )
        val system = layoutOf(score)
        assertEquals(listOf(null, 0, 1), system.notes.map { it.attackIndex })
        system.notes.filter { it.attackIndex != null }.forEach { note ->
            assertEquals(note.onset, score.attackedNotes[note.attackIndex!!].onset)
        }
    }

    @Test
    fun `the tie's thickness comes from the font, not from us`() {
        val system = layoutOf(crossStaffTie())
        assertEquals(RealBravura.metrics.engraving.tieMidpointThickness, system.curves.single().thickness)
        assertTrue(RealBravura.metrics.engraving.slurMidpointThickness.value > 0.0)
    }

    /** The attack is deliberately not the first note, so the old fallback of zero is visible. */
    private fun crossStaffTie() = scoreOf(
        events = listOf(
            noteOf(ticksOf(0.0), "A4"),
            noteOf(ticksOf(1.0), "C4", tiedToNext = true),
            noteOf(ticksOf(2.0), "C4", staff = Staff.Lower, tiedFromPrevious = true),
        ),
        measures = listOf(measureOf(0, clefs = mapOf(Staff.Upper to Clef.Treble, Staff.Lower to Clef.Bass))),
        staves = listOf(Staff.Upper, Staff.Lower),
    )
}
