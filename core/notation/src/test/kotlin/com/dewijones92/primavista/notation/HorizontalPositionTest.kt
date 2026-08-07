package com.dewijones92.primavista.notation

import com.dewijones92.primavista.score.MusicalTime
import com.dewijones92.primavista.score.NoteSymbol
import com.dewijones92.primavista.score.Score
import com.dewijones92.primavista.score.Ticks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * docs/spec.md I1: the playhead's x and the drawn note's x are the same number, or a verdict appears
 * on the wrong note.
 */
class HorizontalPositionTest {
    private val layout = ClassicalStaffLayout()

    @Test
    fun `xOf agrees with where every note was drawn`() {
        val score = mixedScore()
        val system = layout.layout(score, RealBravura.metrics)
        assertTrue("the fixture must be interesting", system.notes.size >= 8)
        system.notes.forEach { note ->
            assertEquals(
                "onset ${note.onset.value}",
                note.notehead.x.value,
                layout.xOf(system, note.onset).value,
                EPSILON,
            )
        }
    }

    @Test
    fun `xOf agrees on a mid-bar tuplet and on the very last note`() {
        val score = mixedScore()
        val system = layout.layout(score, RealBravura.metrics)
        val tuplet = score.notes.filter { it.duration.isTuplet }
        assertTrue("fixture has a tuplet", tuplet.isNotEmpty())
        tuplet.forEach { note ->
            assertEquals(system.noteAt(note.onset).notehead.x.value, layout.xOf(system, note.onset).value, EPSILON)
        }
        val last = system.notes.maxByOrNull { it.onset.value }!!
        assertEquals(last.notehead.x.value, layout.xOf(system, last.onset).value, EPSILON)
    }

    @Test
    fun `x advances monotonically with musical time`() {
        val system = layout.layout(mixedScore(), RealBravura.metrics)
        val xs = (0..32).map { layout.xOf(system, Ticks(it * MusicalTime.TICKS_PER_QUARTER / 4)).value }
        xs.zipWithNext { earlier, later -> assertTrue("$earlier then $later", later > earlier) }
    }

    @Test
    fun `every measure anchor carries its own span, and they tile the system`() {
        val system = layout.layout(mixedScore(), RealBravura.metrics)
        assertEquals(listOf(0, 1, 2), system.measureAnchors.map { it.measureIndex })
        val bar = Ticks(MusicalTime.TICKS_PER_QUARTER * 4)
        system.measureAnchors.forEachIndexed { index, anchor ->
            assertEquals(bar * index, anchor.start)
            assertEquals(bar, anchor.durationTicks)
            assertTrue("notes start at or after the barline", anchor.noteAreaX.value >= anchor.x.value)
            assertTrue("the bar has room for its notes", anchor.width.value > anchor.noteAreaX.value - anchor.x.value)
        }
        system.measureAnchors.zipWithNext { earlier, later ->
            assertEquals(earlier.x.value + earlier.width.value, later.x.value, EPSILON)
        }
        val last = system.measureAnchors.last()
        assertTrue(
            "the music ends before the trailing padding",
            last.x.value + last.width.value < system.width.value,
        )
    }

    @Test
    fun `dense passages are spread to at least the minimum note spacing`() {
        val sixteenths = (0 until 4).map { noteOf(ticksOf(it * 0.25), "A4", NoteSymbol.Sixteenth) }
        val style = LayoutStyle()
        val system = layout.layout(scoreOf(sixteenths), RealBravura.metrics, style)
        val xs = system.notes.sortedBy { it.onset.value }.map { it.notehead.x.value }
        xs.zipWithNext { earlier, later ->
            assertTrue("spacing ${later - earlier}", later - earlier >= style.minimumNoteSpacing.value - EPSILON)
        }
    }

    @Test
    fun `the music starts after the clef, key and time signature`() {
        val system = layout.layout(mixedScore(), RealBravura.metrics)
        val firstNoteX = system.notes.minOf { it.notehead.x.value }
        val header = system.glyphs.filter {
            it.glyph.glyphName.endsWith("Clef") || it.glyph.glyphName.startsWith("timeSig")
        }
        assertTrue(header.isNotEmpty())
        assertTrue(header.all { it.x.value < firstNoteX })
    }

    /** Three bars of 4/4 that fill exactly, including a mid-bar triplet on exact tick boundaries. */
    private fun mixedScore(): Score {
        val tripletEighth = MusicalTime.TICKS_PER_QUARTER / 3
        return scoreOf(
            events = listOf(
                noteOf(ticksOf(0.0), "C4"),
                noteOf(ticksOf(1.0), "E4", NoteSymbol.Eighth),
                noteOf(ticksOf(1.5), "G4", NoteSymbol.Eighth),
                noteOf(ticksOf(2.0), "C5", NoteSymbol.Half),
                noteOf(ticksOf(4.0), "D5", NoteSymbol.Eighth, tuplet = 3 to 2),
                noteOf(Ticks(ticksOf(4.0).value + tripletEighth), "E5", NoteSymbol.Eighth, tuplet = 3 to 2),
                noteOf(Ticks(ticksOf(4.0).value + 2 * tripletEighth), "F5", NoteSymbol.Eighth, tuplet = 3 to 2),
                noteOf(ticksOf(5.0), "G5"),
                noteOf(ticksOf(6.0), "A5", NoteSymbol.Half),
                noteOf(ticksOf(8.0), "B5"),
                noteOf(ticksOf(9.0), "C6", NoteSymbol.Half),
                noteOf(ticksOf(11.0), "D6"),
            ),
            measures = listOf(measureOf(0), measureOf(1), measureOf(2)),
        )
    }
}
