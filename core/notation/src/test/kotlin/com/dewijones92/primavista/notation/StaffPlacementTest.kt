package com.dewijones92.primavista.notation

import com.dewijones92.primavista.score.Clef
import com.dewijones92.primavista.score.KeySignature
import com.dewijones92.primavista.score.Staff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The numbers a staff is. Every claim here is relative to a staff's top line, so it holds whatever
 * vertical offset the finished system ends up with.
 */
class StaffPlacementTest {
    @Test
    fun `treble runs E4 on the bottom line to F5 on the top`() {
        assertEquals(BOTTOM_LINE, noteY(Clef.Treble, pitchOf("E4"), 0.0), EPSILON)
        assertEquals(TOP_LINE, noteY(Clef.Treble, pitchOf("F5"), 0.0), EPSILON)
        assertEquals(2.0, noteY(Clef.Treble, pitchOf("B4"), 0.0), EPSILON)
    }

    @Test
    fun `bass runs G2 on the bottom line to A3 on the top`() {
        assertEquals(BOTTOM_LINE, noteY(Clef.Bass, pitchOf("G2"), 0.0), EPSILON)
        assertEquals(TOP_LINE, noteY(Clef.Bass, pitchOf("A3"), 0.0), EPSILON)
        assertEquals(2.0, noteY(Clef.Bass, pitchOf("D3"), 0.0), EPSILON)
    }

    @Test
    fun `middle C is one leger line below the treble staff and one above the bass`() {
        val trebleY = noteY(Clef.Treble, pitchOf("C4"), 0.0)
        assertEquals(BOTTOM_LINE + 1.0, trebleY, EPSILON)
        assertEquals(listOf(5.0), legerLineRows(trebleY))

        val bassY = noteY(Clef.Bass, pitchOf("C4"), 0.0)
        assertEquals(TOP_LINE - 1.0, bassY, EPSILON)
        assertEquals(listOf(-1.0), legerLineRows(bassY))
    }

    @Test
    fun `a higher pitch always has a strictly smaller y`() {
        val ladder = listOf("C2", "E2", "G2", "C3", "B3", "C4", "F4", "B4", "C5", "A5", "C6", "G6")
        listOf(Clef.Treble, Clef.Bass, Clef.Alto).forEach { clef ->
            val ys = ladder.map { noteY(clef, pitchOf(it), 0.0) }
            ys.zipWithNext { lower, higher ->
                assertTrue("$clef: $lower should be below $higher", lower > higher)
            }
        }
    }

    @Test
    fun `leger lines are counted from the staff outwards, both ways`() {
        assertEquals(emptyList<Double>(), legerLineRows(noteY(Clef.Treble, pitchOf("G5"), 0.0)))
        assertEquals(listOf(-1.0), legerLineRows(noteY(Clef.Treble, pitchOf("A5"), 0.0)))
        assertEquals(listOf(-1.0), legerLineRows(noteY(Clef.Treble, pitchOf("B5"), 0.0)))
        assertEquals(listOf(-1.0, -2.0), legerLineRows(noteY(Clef.Treble, pitchOf("C6"), 0.0)))
        assertEquals(listOf(5.0), legerLineRows(noteY(Clef.Bass, pitchOf("E2"), 0.0)))
        assertEquals(listOf(5.0, 6.0), legerLineRows(noteY(Clef.Bass, pitchOf("C2"), 0.0)))
    }

    @Test
    fun `a leger line extends past the notehead by the font's extension`() {
        val system = layoutOf(scoreOf(listOf(noteOf(ticksOf(0.0), "C4"))))
        val note = system.notes.single()
        val extension = RealBravura.metrics.engraving.legerLineExtension.value
        val head = note.notehead
        val leger = note.legerLines.single()
        assertEquals(head.x.value - extension, leger.x1.value, EPSILON)
        assertEquals(
            head.x.value + RealBravura.metrics.advanceWidth(head.glyph).value + extension,
            leger.x2.value,
            EPSILON,
        )
        assertEquals(head.y.value, leger.y1.value, EPSILON)
        assertEquals(leger.y1.value, leger.y2.value, EPSILON)
    }

    @Test
    fun `a clef glyph sits on the line it names`() {
        assertEquals(pitchOf("G4").diatonicIndex, clefBaselineDiatonicIndex(Clef.Treble))
        assertEquals(pitchOf("F3").diatonicIndex, clefBaselineDiatonicIndex(Clef.Bass))
        assertEquals(pitchOf("C4").diatonicIndex, clefBaselineDiatonicIndex(Clef.Alto))
    }

    @Test
    fun `sharps sit in the conventional treble and bass rows`() {
        val sevenSharps = KeySignature(KeySignature.MAX_FIFTHS)
        assertEquals(
            listOf(0.0, 1.5, -0.5, 1.0, 2.5, 0.5, 2.0),
            keySignatureRows(sevenSharps, Clef.Treble),
        )
        assertEquals(
            listOf(1.0, 2.5, 0.5, 2.0, 3.5, 1.5, 3.0),
            keySignatureRows(sevenSharps, Clef.Bass),
        )
    }

    @Test
    fun `flats sit in the conventional treble rows and start on the middle line`() {
        val sevenFlats = KeySignature(-KeySignature.MAX_FIFTHS)
        assertEquals(
            listOf(2.0, 0.5, 2.5, 1.0, 3.0, 1.5, 3.5),
            keySignatureRows(sevenFlats, Clef.Treble),
        )
        assertEquals(listOf(2.0, 0.5, 2.5), keySignatureRows(KeySignature(-3), Clef.Treble))
    }

    @Test
    fun `a key signature is drawn on every staff of the system`() {
        val system = grandStaffInThreeSharps()
        val sharps = system.glyphs.filter { it.glyph == SmuflGlyph.AccidentalSharp }
        assertEquals(6, sharps.size)
        val boundary = (
            system.staffTopY.getValue(Staff.Upper).value + BOTTOM_LINE +
                system.staffTopY.getValue(Staff.Lower).value
            ) / 2
        listOf(Staff.Upper to Clef.Treble, Staff.Lower to Clef.Bass).forEach { (staff, clef) ->
            val expected = keySignatureRows(KeySignature(3), clef).sorted()
            val drawn = sharps
                .filter { (it.y.value < boundary) == (staff == Staff.Upper) }
                .map { system.relativeY(it.y, staff) }
                .sorted()
            assertEquals("$staff count", expected.size, drawn.size)
            expected.zip(drawn) { row, actual -> assertEquals("$staff row", row, actual, EPSILON) }
        }
    }

    private fun grandStaffInThreeSharps(): StaffSystem = layoutOf(
        scoreOf(
            events = listOf(noteOf(ticksOf(0.0), "A4"), noteOf(ticksOf(0.0), "C3", staff = Staff.Lower)),
            measures = listOf(
                measureOf(
                    index = 0,
                    key = KeySignature(3),
                    clefs = mapOf(Staff.Upper to Clef.Treble, Staff.Lower to Clef.Bass),
                ),
            ),
            staves = listOf(Staff.Upper, Staff.Lower),
        ),
    )

    private companion object {
        const val TOP_LINE = 0.0
        const val BOTTOM_LINE = 4.0
    }
}
