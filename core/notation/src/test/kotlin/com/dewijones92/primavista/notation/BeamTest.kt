package com.dewijones92.primavista.notation

import com.dewijones92.primavista.score.MusicalTime
import com.dewijones92.primavista.score.Note
import com.dewijones92.primavista.score.NoteSymbol
import com.dewijones92.primavista.score.TimeSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BeamTest {
    @Test
    fun `eighths beam in pairs within the beat, and lose their flags`() {
        val system = layoutOf(scoreOf(notesOf(NoteSymbol.Eighth, count = 4)))
        assertEquals(2, system.beams.size)
        assertTrue(system.notes.all { it.flag == null })
        assertTrue(system.notes.all { it.stem != null })
    }

    @Test
    fun `a beam never crosses a beat boundary`() {
        val straddling = listOf(
            noteOf(ticksOf(0.5), "A4", NoteSymbol.Eighth),
            noteOf(ticksOf(1.0), "A4", NoteSymbol.Eighth),
        )
        val system = layoutOf(scoreOf(straddling))
        assertEquals(0, system.beams.size)
        assertEquals(2, system.notes.count { it.flag != null })
    }

    @Test
    fun `a lone beamable note keeps its flag`() {
        val system = layoutOf(scoreOf(listOf(noteOf(ticksOf(0.0), "A4", NoteSymbol.Eighth))))
        assertEquals(0, system.beams.size)
        assertEquals(SmuflGlyph.Flag8thUp, system.notes.single().flag?.glyph)
    }

    @Test
    fun `sixteenths get a secondary beam, spaced by the font`() {
        val system = layoutOf(scoreOf(notesOf(NoteSymbol.Sixteenth, count = 4)))
        assertEquals(2, system.beams.size)
        val engraving = RealBravura.metrics.engraving
        val gap = abs(system.beams[1].startY.value - system.beams[0].startY.value)
        assertEquals(engraving.beamThickness.value + engraving.beamSpacing.value, gap, EPSILON)
        assertTrue("secondary spans the group", system.beams[1].endX.value > system.beams[1].startX.value)
    }

    @Test
    fun `a beam group stops at the note-count limit`() {
        val inTwoTwo = scoreOf(
            events = notesOf(NoteSymbol.Sixteenth, count = 8),
            measures = listOf(measureOf(0, TimeSignature(2, 2))),
        )
        val system = layoutOf(inTwoTwo)
        assertEquals(4, system.beams.size)
        assertTrue(system.notes.all { it.flag == null })
    }

    @Test
    fun `a lone sixteenth in a group gets a stub, not a full secondary beam`() {
        val dottedPair = listOf(
            noteOf(ticksOf(0.0), "A4", NoteSymbol.Eighth, dots = 1),
            noteOf(ticksOf(0.75), "A4", NoteSymbol.Sixteenth),
        )
        val system = layoutOf(scoreOf(dottedPair))
        assertEquals(2, system.beams.size)
        val primary = system.beams[0]
        val stub = system.beams[1]
        val stubLength = stub.endX.value - stub.startX.value
        assertTrue("stub is short", stubLength < primary.endX.value - primary.startX.value)
        assertEquals(primary.endX.value, stub.endX.value, EPSILON)
    }

    @Test
    fun `thirty-seconds get three beam levels, each a font-spacing further from the primary`() {
        val system = layoutOf(scoreOf(notesOf(NoteSymbol.ThirtySecond, count = 4)))
        assertEquals(3, system.beams.size)
        val engraving = RealBravura.metrics.engraving
        val step = engraving.beamThickness.value + engraving.beamSpacing.value
        system.beams.forEachIndexed { level, beam ->
            assertEquals("level $level", system.beams[0].startY.value + level * step, beam.startY.value, EPSILON)
            assertEquals("level $level spans the group", system.beams[0].endX.value, beam.endX.value, EPSILON)
        }
    }

    @Test
    fun `a mixed group beams every note once and subdivides only the short ones`() {
        val mixed = listOf(
            noteOf(ticksOf(0.0), "A4", NoteSymbol.Sixteenth),
            noteOf(ticksOf(0.25), "A4", NoteSymbol.Eighth),
            noteOf(ticksOf(0.75), "A4", NoteSymbol.Sixteenth),
        )
        val system = layoutOf(scoreOf(mixed))
        assertEquals("one primary and two stubs", 3, system.beams.size)
        assertTrue(system.notes.all { it.flag == null })
        val primary = system.beams[0]
        val stubs = system.beams.drop(1)
        val primaryRun = primary.endX.value - primary.startX.value
        assertTrue("stubs are short", stubs.all { it.endX.value - it.startX.value < primaryRun })
        assertEquals("the first stub points into the group", primary.startX.value, stubs[0].startX.value, EPSILON)
        assertEquals("the last stub points back at the beat", primary.endX.value, stubs[1].endX.value, EPSILON)
    }

    @Test
    fun `secondary beams sit between the primary and the noteheads`() {
        val below = layoutOf(scoreOf(notesOf(NoteSymbol.Sixteenth, count = 4, pitch = "A4")))
        assertTrue("stem up, so the secondary is lower", below.beams[1].startY.value > below.beams[0].startY.value)
        val above = layoutOf(scoreOf(notesOf(NoteSymbol.Sixteenth, count = 4, pitch = "C6")))
        assertTrue("stem down, so the secondary is higher", above.beams[1].startY.value < above.beams[0].startY.value)
    }

    @Test
    fun `every beamed stem ends on the beam it belongs to`() {
        val system = layoutOf(scoreOf(notesOf(NoteSymbol.Eighth, count = 2)))
        val beam = system.beams.single()
        val slope = (beam.endY.value - beam.startY.value) / (beam.endX.value - beam.startX.value)
        system.notes.forEach { note ->
            val stem = note.stem!!
            val expected = beam.startY.value + slope * (stem.x1.value - beam.startX.value)
            assertEquals(expected, stem.y2.value, EPSILON)
        }
    }

    @Test
    fun `a beam slopes with its notes but only modestly`() {
        val rising = listOf(
            noteOf(ticksOf(0.0), "C4", NoteSymbol.Eighth),
            noteOf(ticksOf(0.5), "C6", NoteSymbol.Eighth),
        )
        val beam = layoutOf(scoreOf(rising)).beams.single()
        val rise = abs(beam.endY.value - beam.startY.value)
        assertTrue("beam is not flat", rise > 0.0)
        assertTrue("beam slope stays modest: $rise", rise <= MAX_MODEST_RISE)
    }

    @Test
    fun `no beamed stem is shorter than a stem may be`() {
        val system = layoutOf(scoreOf(notesOf(NoteSymbol.Eighth, count = 4)))
        system.notes.forEach { note ->
            val stem = note.stem!!
            assertTrue("stem is long enough", abs(stem.y2.value - stem.y1.value) >= MIN_STEM)
        }
    }

    private fun notesOf(symbol: NoteSymbol, count: Int, pitch: String = "A4"): List<Note> {
        val step = symbol.undottedTicks.toDouble() / MusicalTime.TICKS_PER_QUARTER
        return (0 until count).map { noteOf(ticksOf(it * step), pitch, symbol) }
    }

    private companion object {
        const val MAX_MODEST_RISE = 1.5
        const val MIN_STEM = 2.0
    }
}
