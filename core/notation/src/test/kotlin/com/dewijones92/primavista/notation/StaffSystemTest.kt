package com.dewijones92.primavista.notation

import com.dewijones92.primavista.score.Clef
import com.dewijones92.primavista.score.KeySignature
import com.dewijones92.primavista.score.NoteSymbol
import com.dewijones92.primavista.score.Staff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StaffSystemTest {
    @Test
    fun `a grand staff puts the lower staff below the upper, braced and barred together`() {
        val system = grandStaff()
        val upper = system.staffTopY.getValue(Staff.Upper).value
        val lower = system.staffTopY.getValue(Staff.Lower).value
        assertTrue("lower staff below upper", lower > upper)
        assertEquals(LayoutStyle().staffSeparation.value, lower - upper, EPSILON)
        assertEquals(1, system.glyphs.count { it.glyph == SmuflGlyph.Brace })
        assertEquals(SmuflGlyph.GClef, clefOn(system, Staff.Upper))
        assertEquals(SmuflGlyph.FClef, clefOn(system, Staff.Lower))

        val throughBoth = system.lines.filter {
            it.x1 == it.x2 && it.y1.value <= upper + EPSILON && it.y2.value >= lower + STAFF_HEIGHT - EPSILON
        }
        assertTrue("barlines span both staves: ${throughBoth.size}", throughBoth.size >= 2)
    }

    @Test
    fun `each staff gets five lines running the width of the music`() {
        val system = grandStaff()
        val thickness = RealBravura.metrics.engraving.staffLineThickness
        val staffLines = system.lines.filter { it.thickness == thickness && it.y1 == it.y2 }
        assertEquals(2 * STAFF_LINE_COUNT, staffLines.size)
        listOf(Staff.Upper, Staff.Lower).forEach { staff ->
            val rows = staffLines
                .map { system.relativeY(it.y1, staff) }
                .filter { it in 0.0..STAFF_HEIGHT }
            assertEquals(listOf(0.0, 1.0, 2.0, 3.0, 4.0), rows.sorted())
        }
        assertTrue(staffLines.all { it.x2.value > it.x1.value })
    }

    @Test
    fun `rests hang, sit and centre where they should`() {
        val expected = mapOf(
            NoteSymbol.Whole to 1.0,
            NoteSymbol.Half to 2.0,
            NoteSymbol.Quarter to 2.0,
            NoteSymbol.Eighth to 2.0,
        )
        expected.forEach { (symbol, row) ->
            val system = layoutOf(scoreOf(listOf(restOf(ticksOf(0.0), symbol))))
            val rest = system.glyphs.single { it.glyph.glyphName.startsWith("rest") }
            assertEquals("$symbol", row, system.relativeY(rest.y), EPSILON)
        }
    }

    @Test
    fun `a dotted rest keeps its dot, off the line, to the right of the rest`() {
        val system = layoutOf(
            scoreOf(listOf(restOf(ticksOf(0.0), NoteSymbol.Quarter, dots = 1))),
        )
        val rest = system.glyphs.single { it.glyph.glyphName.startsWith("rest") }
        val dot = system.glyphs.single { it.glyph == SmuflGlyph.AugmentationDot }
        assertTrue("dot right of the rest", dot.x.value > rest.x.value)
        assertEquals(system.relativeY(rest.y) - 0.5, system.relativeY(dot.y), EPSILON)
    }

    @Test
    fun `an accidental is printed once a bar, to the left of its notehead`() {
        val system = layoutOf(
            scoreOf(
                listOf(
                    noteOf(ticksOf(0.0), "F#4"),
                    noteOf(ticksOf(1.0), "F#4"),
                    noteOf(ticksOf(2.0), "F4"),
                ),
            ),
        )
        val first = system.noteAt(ticksOf(0.0))
        assertEquals(SmuflGlyph.AccidentalSharp, first.accidental?.glyph)
        assertTrue("accidental is left of the head", first.accidental!!.x.value < first.notehead.x.value)
        assertEquals(first.notehead.y, first.accidental.y)
        assertNull("no repeat within the bar", system.noteAt(ticksOf(1.0)).accidental)
        assertEquals(SmuflGlyph.AccidentalNatural, system.noteAt(ticksOf(2.0)).accidental?.glyph)
    }

    @Test
    fun `a key signature accidental is not printed again on the note`() {
        val gMajor = listOf(measureOf(0, key = KeySignature(1)))
        val system = layoutOf(
            scoreOf(listOf(noteOf(ticksOf(0.0), "F#4"), noteOf(ticksOf(1.0), "F4")), measures = gMajor),
        )
        assertNull(system.noteAt(ticksOf(0.0)).accidental)
        assertEquals(SmuflGlyph.AccidentalNatural, system.noteAt(ticksOf(1.0)).accidental?.glyph)
    }

    @Test
    fun `dots go to the right of the notehead and off the line`() {
        val system = layoutOf(
            scoreOf(
                listOf(
                    noteOf(ticksOf(0.0), "B4", dots = 1),
                    noteOf(ticksOf(2.0), "C5", dots = 2),
                ),
            ),
        )
        val onLine = system.noteAt(ticksOf(0.0))
        assertEquals(1, onLine.dots.size)
        assertTrue(onLine.dots.single().x.value > onLine.notehead.x.value)
        assertEquals(system.relativeY(onLine.notehead.y) - 0.5, system.relativeY(onLine.dots.single().y), EPSILON)

        val inSpace = system.noteAt(ticksOf(2.0))
        assertEquals(2, inSpace.dots.size)
        assertEquals(inSpace.notehead.y, inSpace.dots.first().y)
        assertTrue(inSpace.dots[1].x.value > inSpace.dots[0].x.value)
    }

    @Test
    fun `a tie curves from one notehead to the next, away from the stems`() {
        val tied = listOf(
            noteOf(ticksOf(0.0), "A4", tiedToNext = true),
            noteOf(ticksOf(1.0), "A4", tiedFromPrevious = true),
        )
        val system = layoutOf(scoreOf(tied))
        val curve = system.curves.single()
        val from = system.noteAt(ticksOf(0.0))
        val to = system.noteAt(ticksOf(1.0))
        assertTrue(curve.startX.value > from.notehead.x.value)
        assertTrue(curve.endX.value < to.notehead.x.value)
        assertTrue("stem is up so the tie is under", curve.controlY.value > from.notehead.y.value)
        assertTrue(curve.thickness.value > 0.0)
    }

    @Test
    fun `nothing is drawn above the system, and the height contains it all`() {
        val system = layoutOf(
            scoreOf(
                events = listOf(noteOf(ticksOf(0.0), "C7"), noteOf(ticksOf(1.0), "C1", staff = Staff.Lower)),
                measures = listOf(
                    measureOf(0, clefs = mapOf(Staff.Upper to Clef.Treble, Staff.Lower to Clef.Bass)),
                ),
                staves = listOf(Staff.Upper, Staff.Lower),
            ),
        )
        val ys = system.glyphs.map { it.y.value } +
            system.lines.flatMap { listOf(it.y1.value, it.y2.value) } +
            system.notes.flatMap { note -> note.legerLines.map { it.y1.value } }
        assertTrue("nothing above the box: ${ys.min()}", ys.min() >= 0.0)
        assertTrue("nothing below the box: ${ys.max()} vs ${system.height.value}", ys.max() <= system.height.value)
        assertTrue(system.height.value > 2 * STAFF_HEIGHT)
    }

    @Test
    fun `every laid-out note points at the attack it was drawn from`() {
        val score = scoreOf(
            events = listOf(
                noteOf(ticksOf(0.0), "C4"),
                noteOf(ticksOf(1.0), "E4", tiedToNext = true),
                noteOf(ticksOf(2.0), "E4", tiedFromPrevious = true),
                noteOf(ticksOf(3.0), "G4"),
            ),
        )
        val system = layoutOf(score)
        assertEquals(4, system.notes.size)
        system.notes.forEach { laidOut ->
            val attack = score.attackedNotes[requireNotNull(laidOut.attackIndex)]
            val drawn = score.notes.first { it.onset == laidOut.onset }
            assertEquals(attack.pitch, drawn.pitch)
            if (!drawn.tiedFromPrevious) {
                assertEquals(attack.onset, laidOut.onset)
            } else {
                assertTrue("a tie continuation points back at its attack", attack.onset < laidOut.onset)
            }
        }
        assertEquals(listOf(0, 1, 1, 2), system.notes.map { it.attackIndex })
    }

    private fun clefOn(system: StaffSystem, staff: Staff): SmuflGlyph? {
        val topY = system.staffTopY.getValue(staff).value
        return system.glyphs
            .filter { it.glyph.glyphName.endsWith("Clef") }
            .minByOrNull { kotlin.math.abs(it.y.value - (topY + MIDDLE_LINE)) }
            ?.glyph
    }

    private fun grandStaff(): StaffSystem = layoutOf(
        scoreOf(
            events = listOf(
                noteOf(ticksOf(0.0), "E4"),
                noteOf(ticksOf(0.0), "C3", staff = Staff.Lower),
                noteOf(ticksOf(4.0), "F4"),
                noteOf(ticksOf(4.0), "A2", staff = Staff.Lower),
            ),
            measures = listOf(
                measureOf(0, clefs = mapOf(Staff.Upper to Clef.Treble, Staff.Lower to Clef.Bass)),
                measureOf(1, clefs = mapOf(Staff.Upper to Clef.Treble, Staff.Lower to Clef.Bass)),
            ),
            staves = listOf(Staff.Upper, Staff.Lower),
        ),
    )
}
