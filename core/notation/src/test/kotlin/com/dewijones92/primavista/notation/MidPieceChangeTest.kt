package com.dewijones92.primavista.notation

import com.dewijones92.primavista.score.Clef
import com.dewijones92.primavista.score.KeySignature
import com.dewijones92.primavista.score.Staff
import com.dewijones92.primavista.score.TimeSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * docs/spec.md I2. A key or clef change that is honoured when placing the notes but never drawn
 * leaves the staff showing a different pitch from the one being judged, which is the app lying
 * about what to play rather than a cosmetic omission.
 */
class MidPieceChangeTest {
    @Test
    fun `a mid-piece key change is drawn, in the space reserved for it`() {
        val system = layoutOf(scoreOf(events = threeBarsOfC(), measures = keyChangeAtBarTwo()))
        val anchor = system.measureAnchors[1]
        val drawn = system.glyphs.filter {
            it.glyph == SmuflGlyph.AccidentalFlat &&
                it.x.value >= anchor.x.value && it.x.value < anchor.noteAreaX.value
        }
        assertEquals("two flats for B flat major", 2, drawn.size)
        assertTrue("the bar is wider than its notes need", anchor.noteAreaX.value > anchor.x.value)
        assertTrue("the notes start after the new key", noteXAt(system, 1) >= anchor.noteAreaX.value - EPSILON)
    }

    @Test
    fun `a key change back to C cancels the old accidentals with naturals`() {
        val measures = listOf(
            measureOf(0, key = KeySignature(2)),
            measureOf(1, key = KeySignature.C),
            measureOf(2, key = KeySignature.C),
        )
        val system = layoutOf(scoreOf(events = threeBarsOfC(), measures = measures))
        val anchor = system.measureAnchors[1]
        val naturals = system.glyphs.filter {
            it.glyph == SmuflGlyph.AccidentalNatural &&
                it.x.value >= anchor.x.value && it.x.value < anchor.noteAreaX.value
        }
        assertEquals("both sharps of D major are cancelled", 2, naturals.size)
        assertEquals(2, cancelledAccidentals(KeySignature(2), KeySignature.C))
        assertEquals(0, cancelledAccidentals(KeySignature(2), KeySignature(3)))
        assertEquals(1, cancelledAccidentals(KeySignature(2), KeySignature(1)))
        assertEquals(2, cancelledAccidentals(KeySignature(2), KeySignature(-1)))
    }

    @Test
    fun `an unchanged key is not redrawn every bar`() {
        val system = layoutOf(scoreOf(events = threeBarsOfC(), measures = twoSharpsThroughout()))
        val second = system.measureAnchors[1]
        val redrawn = system.glyphs.count {
            it.glyph == SmuflGlyph.AccidentalSharp && it.x.value >= second.x.value
        }
        assertEquals(0, redrawn)
    }

    @Test
    fun `a mid-piece clef change is drawn where the notes it governs start`() {
        val system = layoutOf(scoreOf(events = threeBarsOfC(), measures = clefChangeAtBarTwo()))
        val anchor = system.measureAnchors[1]
        val bassClefs = system.glyphs.filter { it.glyph == SmuflGlyph.FClef }
        assertEquals("only the change draws a bass clef", 1, bassClefs.size)
        val drawn = bassClefs.single()
        assertTrue("inside the reserved space", drawn.x.value in anchor.x.value..anchor.noteAreaX.value)
        assertTrue("the notes start after it", noteXAt(system, 1) >= anchor.noteAreaX.value - EPSILON)
    }

    @Test
    fun `the clef that places a note is the clef drawn above it`() {
        val events = threeBarsOfC()
        val unchanged = layoutOf(scoreOf(events = events, measures = listOf(measureOf(0), measureOf(1), measureOf(2))))
        val changed = layoutOf(scoreOf(events = events, measures = clefChangeAtBarTwo()))
        val bar = TimeSignature.FourFour.measureTicks

        assertEquals(
            "bar one is untouched",
            unchanged.relativeY(unchanged.noteAt(ticksOf(0.0)).notehead.y),
            changed.relativeY(changed.noteAt(ticksOf(0.0)).notehead.y),
            EPSILON,
        )
        assertNotEquals(
            "the same written pitch moves under the new clef",
            unchanged.relativeY(unchanged.noteAt(bar).notehead.y),
            changed.relativeY(changed.noteAt(bar).notehead.y),
            EPSILON,
        )
        val expected = noteY(Clef.Bass, pitchOf("C4"), changed.staffTopY.getValue(Staff.Upper).value)
        assertEquals(expected, changed.noteAt(bar).notehead.y.value, EPSILON)
    }

    @Test
    fun `a mid-piece time change is drawn and the bar after it keeps its own span`() {
        val measures = listOf(
            measureOf(0),
            measureOf(1).copy(time = TimeSignature(2, 4)),
        )
        val system = layoutOf(scoreOf(events = listOf(noteOf(ticksOf(0.0), "C4")), measures = measures))
        val anchor = system.measureAnchors[1]
        val digits = system.glyphs.filter {
            it.glyph.glyphName.startsWith("timeSig") &&
                it.x.value >= anchor.x.value && it.x.value < anchor.noteAreaX.value
        }
        assertEquals("a 2 and a 4", 2, digits.size)
        assertEquals(TimeSignature(2, 4).measureTicks, anchor.durationTicks)
    }

    @Test
    fun `every bar is contiguous with the next, furniture included`() {
        val system = layoutOf(scoreOf(events = threeBarsOfC(), measures = keyChangeAtBarTwo()))
        system.measureAnchors.zipWithNext { earlier, later ->
            assertEquals(earlier.x.value + earlier.width.value, later.x.value, EPSILON)
        }
        system.measureAnchors.forEach {
            assertTrue("notes start inside the bar", it.noteAreaX.value in it.x.value..it.x.value + it.width.value)
        }
    }

    private fun noteXAt(system: StaffSystem, bar: Int): Double =
        system.noteAt(TimeSignature.FourFour.measureTicks * bar).notehead.x.value

    private fun threeBarsOfC() = (0 until 3).map { noteOf(ticksOf(it * 4.0), "C4") }

    private fun keyChangeAtBarTwo() = listOf(
        measureOf(0),
        measureOf(1, key = KeySignature(-2)),
        measureOf(2, key = KeySignature(-2)),
    )

    private fun twoSharpsThroughout() = (0 until 3).map { measureOf(it, key = KeySignature(2)) }

    private fun clefChangeAtBarTwo() = listOf(
        measureOf(0),
        measureOf(1, clefs = mapOf(Staff.Upper to Clef.Bass)),
        measureOf(2),
    )
}
