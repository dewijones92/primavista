package com.dewijones92.primavista.notation

import com.dewijones92.primavista.score.Clef
import com.dewijones92.primavista.score.NoteSymbol
import com.dewijones92.primavista.score.Staff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * A chord is one thing to play, so it is one stem. Drawing a stem per notehead put them in
 * opposing directions on opposite sides of the same chord.
 */
class ChordTest {
    @Test
    fun `an unbeamed chord gets exactly one stem, in one direction`() {
        val system = layoutOf(scoreOf(chordOf("C4", "E4", "G4")))
        val heads = system.notes
        assertEquals(3, heads.size)
        val stems = heads.mapNotNull { it.stem }
        assertEquals("one stem for the chord", 1, stems.size)
        val stem = stems.single()
        assertTrue("a C major triad below the middle line stems up", stem.y2.value < stem.y1.value)
        val anchor = RealBravura.metrics.anchor(SmuflGlyph.NoteheadBlack, "stemUpSE")!!
        assertEquals(
            "the stem starts on the bottom notehead's own anchor",
            heads.maxOf { it.notehead.y.value } - anchor.second.value,
            stem.y1.value,
            EPSILON,
        )
        assertTrue("the stem clears the top notehead", stem.y2.value < heads.minOf { it.notehead.y.value })
    }

    @Test
    fun `the whole chord's direction is decided by the note furthest from the middle line`() {
        val high = layoutOf(scoreOf(chordOf("E5", "G5", "C6"))).notes.mapNotNull { it.stem }.single()
        assertTrue("a high chord stems down", high.y2.value > high.y1.value)
        val low = layoutOf(scoreOf(chordOf("C4", "E4", "G4"))).notes.mapNotNull { it.stem }.single()
        assertTrue("a low chord stems up", low.y2.value < low.y1.value)
    }

    @Test
    fun `a second in a chord is offset across the stem, not drawn on top of its neighbour`() {
        val system = layoutOf(scoreOf(chordOf("C4", "D4")))
        val xs = system.notes.map { it.notehead.x.value }.sorted()
        val headWidth = RealBravura.metrics.advanceWidth(SmuflGlyph.NoteheadBlack).value
        val stemThickness = RealBravura.metrics.engraving.stemThickness.value
        assertEquals("the second crosses the stem", headWidth - stemThickness, xs[1] - xs[0], EPSILON)
        assertEquals("only one stem all the same", 1, system.notes.count { it.stem != null })
    }

    @Test
    fun `three notes with two seconds alternate sides rather than all displacing`() {
        val system = layoutOf(scoreOf(chordOf("C4", "D4", "E4")))
        val columns = system.notes.map { it.notehead.x.value }.distinct().sorted()
        assertEquals("two columns, not three", 2, columns.size)
        val displaced = system.notes.count { abs(it.notehead.x.value - columns[1]) < EPSILON }
        assertEquals("only the middle note crosses", 1, displaced)
    }

    @Test
    fun `a beamed chord still has one stem, ending on the beam`() {
        val events = chordOf("C4", "E4", symbol = NoteSymbol.Eighth) +
            chordOf("D4", "F4", symbol = NoteSymbol.Eighth, onsetQuarters = 0.5)
        val system = layoutOf(scoreOf(events))
        assertEquals(4, system.notes.size)
        assertEquals("one stem per chord", 2, system.notes.count { it.stem != null })
        assertTrue("no flags under a beam", system.notes.all { it.flag == null })
        val beam = system.beams.single()
        val slope = (beam.endY.value - beam.startY.value) / (beam.endX.value - beam.startX.value)
        system.notes.mapNotNull { it.stem }.forEach { stem ->
            val expected = beam.startY.value + slope * (stem.x1.value - beam.startX.value)
            assertEquals(expected, stem.y2.value, EPSILON)
        }
    }

    @Test
    fun `two staves at one onset are two chords, not one`() {
        val system = layoutOf(
            scoreOf(
                events = listOf(noteOf(ticksOf(0.0), "E4"), noteOf(ticksOf(0.0), "C3", staff = Staff.Lower)),
                measures = listOf(measureOf(0, clefs = mapOf(Staff.Upper to Clef.Treble))),
                staves = listOf(Staff.Upper, Staff.Lower),
            ),
        )
        assertEquals(2, system.notes.count { it.stem != null })
    }

    @Test
    fun `a whole-note chord has no stem at all`() {
        val system = layoutOf(scoreOf(chordOf("C4", "E4", symbol = NoteSymbol.Whole)))
        assertEquals(2, system.notes.size)
        system.notes.forEach { assertNull(it.stem) }
    }

    @Test
    fun `a chord's accidentals sit left of its leftmost notehead`() {
        val system = layoutOf(scoreOf(chordOf("C#4", "D#4")))
        val leftmost = system.notes.minOf { it.notehead.x.value }
        system.notes.forEach { note ->
            val accidental = requireNotNull(note.accidental)
            assertTrue("accidental clear of every head", accidental.x.value < leftmost)
        }
    }

    private fun chordOf(
        vararg pitches: String,
        symbol: NoteSymbol = NoteSymbol.Quarter,
        onsetQuarters: Double = 0.0,
    ) = pitches.map { noteOf(ticksOf(onsetQuarters), it, symbol) }
}
