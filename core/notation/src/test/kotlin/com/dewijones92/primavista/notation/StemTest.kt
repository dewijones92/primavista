package com.dewijones92.primavista.notation

import com.dewijones92.primavista.score.NoteSymbol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StemTest {
    private val style = LayoutStyle()

    @Test
    fun `stem direction flips about the middle line`() {
        assertTrue("above the middle line stems down", stemOf("C5").let { it.y2.value > it.y1.value })
        assertTrue("on the middle line stems down", stemOf("B4").let { it.y2.value > it.y1.value })
        assertTrue("below the middle line stems up", stemOf("A4").let { it.y2.value < it.y1.value })
        assertTrue("far below stems up", stemOf("C4").let { it.y2.value < it.y1.value })
    }

    @Test
    fun `a stem starts exactly at the notehead's own anchor`() {
        val up = noteWith("A4")
        val upAnchor = RealBravura.metrics.anchor(up.notehead.glyph, "stemUpSE")!!
        assertEquals(up.notehead.x.value + upAnchor.first.value, up.stem!!.x1.value, EPSILON)
        assertEquals(up.notehead.y.value - upAnchor.second.value, up.stem.y1.value, EPSILON)

        val down = noteWith("C5")
        val downAnchor = RealBravura.metrics.anchor(down.notehead.glyph, "stemDownNW")!!
        assertEquals(down.notehead.x.value + downAnchor.first.value, down.stem!!.x1.value, EPSILON)
        assertEquals(down.notehead.y.value - downAnchor.second.value, down.stem.y1.value, EPSILON)
    }

    @Test
    fun `a stem is standard length near the staff and reaches the middle line from far out`() {
        val near = stemOf("A4")
        assertEquals(style.standardStemLength.value, near.y1.value - near.y2.value, EPSILON)

        val system = layoutOf(scoreOf(listOf(noteOf(ticksOf(0.0), "C6"))))
        val far = system.notes.single().stem!!
        assertEquals(MIDDLE_LINE, system.relativeY(far.y2), EPSILON)
        assertTrue(far.y2.value - far.y1.value > style.standardStemLength.value)
    }

    @Test
    fun `a stem uses the font's thickness and whole notes have none`() {
        assertEquals(RealBravura.metrics.engraving.stemThickness, stemOf("A4").thickness)
        assertNull(noteWith("A4", NoteSymbol.Whole).stem)
        assertNull(noteWith("A4", NoteSymbol.DoubleWhole).stem)
        assertEquals(RealBravura.metrics.engraving.stemThickness, stemOf("A4", NoteSymbol.Half).thickness)
    }

    @Test
    fun `an unbeamed eighth gets the flag its stem direction needs`() {
        assertEquals(SmuflGlyph.Flag8thUp, noteWith("A4", NoteSymbol.Eighth).flag?.glyph)
        assertEquals(SmuflGlyph.Flag8thDown, noteWith("C5", NoteSymbol.Eighth).flag?.glyph)
        assertEquals(SmuflGlyph.Flag16thUp, noteWith("A4", NoteSymbol.Sixteenth).flag?.glyph)
        assertEquals(SmuflGlyph.Flag32ndDown, noteWith("C5", NoteSymbol.ThirtySecond).flag?.glyph)
        assertNull(noteWith("A4", NoteSymbol.Quarter).flag)
    }

    @Test
    fun `a flag hangs off the end of the stem, on its own anchor`() {
        val note = noteWith("A4", NoteSymbol.Eighth)
        val flag = note.flag!!
        val anchor = RealBravura.metrics.anchor(flag.glyph, "stemUpNW")!!
        assertEquals(note.stem!!.x2.value - anchor.first.value, flag.x.value, EPSILON)
        assertEquals(note.stem.y2.value + anchor.second.value, flag.y.value, EPSILON)
    }

    private fun noteWith(pitch: String, symbol: NoteSymbol = NoteSymbol.Quarter): LaidOutNote =
        layoutOf(scoreOf(listOf(noteOf(ticksOf(0.0), pitch, symbol)))).notes.single()

    private fun stemOf(pitch: String, symbol: NoteSymbol = NoteSymbol.Quarter): LaidOutLine =
        noteWith(pitch, symbol).stem!!
}
