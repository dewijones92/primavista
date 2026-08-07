package com.dewijones92.primavista.notation

import com.dewijones92.primavista.score.Clef
import com.dewijones92.primavista.score.MusicalTime
import com.dewijones92.primavista.score.Staff
import com.dewijones92.primavista.score.Ticks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The proof, on real shipped material rather than a fixture: a layout is a list of numbers, so the
 * engraving claims that matter are asserted rather than looked at.
 */
class RenderProofTest {
    private val layout = ClassicalStaffLayout()
    private val minuet = corpusScore("corpus-minuet-in-g")

    @Test
    fun `the shipped grand-staff piece lays out as four braced bars of real music`() {
        val system = layoutOf(minuet)
        assertEquals(setOf(Staff.Upper, Staff.Lower), minuet.staves.toSet())
        assertEquals(4, system.measureAnchors.size)
        assertTrue("it has music on both staves", system.notes.count { it.staff == Staff.Lower } >= 4)
        assertEquals(2 * STAFF_LINE_COUNT, system.lines.count { it.y1 == it.y2 })
        system.measureAnchors.forEach {
            assertEquals("three-four", Ticks(3 * MusicalTime.TICKS_PER_QUARTER), it.durationTicks)
        }
    }

    @Test
    fun `the brace spans the whole system, not one staff`() {
        val system = layoutOf(minuet)
        val brace = system.glyphs.single { it.glyph == SmuflGlyph.Brace }
        val box = RealBravura.metrics.boundingBox(SmuflGlyph.Brace)
        val inkTop = brace.y.value - box.northEastY.value * brace.scaleY
        val inkBottom = brace.y.value - box.southWestY.value * brace.scaleY

        val top = system.staffTopY.getValue(Staff.Upper).value
        val bottom = system.staffTopY.getValue(Staff.Lower).value + STAFF_HEIGHT
        assertEquals("the brace starts at the top staff line", top, inkTop, EPSILON)
        assertEquals("and ends at the bottom one", bottom, inkBottom, EPSILON)
        assertEquals(
            "sixteen spaces, not the font's four",
            LayoutStyle().staffSeparation.value + STAFF_HEIGHT,
            inkBottom - inkTop,
            EPSILON,
        )
        assertTrue("so it had to be stretched: ${brace.scaleY}", brace.scaleY > 3.0)
        assertTrue("the brace sits left of the staff", brace.x.value < system.measureAnchors.first().x.value)
        assertTrue("and inside the drawn box", inkTop >= 0.0 && inkBottom <= system.height.value)
    }

    @Test
    fun `a clef change in the shipped piece reserves its width and draws its clef`() {
        val changed = minuet.withMeasure(2) { it.copy(clefs = it.clefs + (Staff.Upper to Clef.Bass)) }
        val plain = layoutOf(minuet)
        val system = layoutOf(changed)

        val anchor = system.measureAnchors[2]
        val reserved = anchor.noteAreaX.value - anchor.x.value
        assertTrue("width reserved for the clef: $reserved", reserved > plainFurnitureWidth(plain))
        assertEquals(
            "the bar is wider by exactly what the clef took",
            plain.measureAnchors[2].width.value + reserved - plainFurnitureWidth(plain),
            anchor.width.value,
            EPSILON,
        )

        val upperMiddle = system.staffTopY.getValue(Staff.Upper).value + MIDDLE_LINE
        val drawn = system.glyphs.filter {
            it.glyph == SmuflGlyph.FClef && kotlin.math.abs(it.y.value - upperMiddle) < STAFF_HEIGHT
        }
        assertEquals("a bass clef appears on the upper staff", 1, drawn.size)
        assertTrue("inside the space reserved", drawn.single().x.value in anchor.x.value..anchor.noteAreaX.value)

        val firstOfBar = system.notes.filter { it.onset >= anchor.start && it.staff == Staff.Upper }
            .minByOrNull { it.onset.value }!!
        assertTrue("the notes start after it", firstOfBar.notehead.x.value >= anchor.noteAreaX.value - EPSILON)
        val before = plain.notes.first { it.onset == firstOfBar.onset && it.staff == Staff.Upper }
        assertTrue("and the same written pitch has moved", firstOfBar.notehead.y.value != before.notehead.y.value)
    }

    @Test
    fun `a chord in the shipped piece gets one stem, not one per notehead`() {
        val lead = minuet.notes.first { it.staff == Staff.Upper }
        val chorded = minuet.plusNotes(
            listOf(lead.copy(pitch = pitchOf("B4")), lead.copy(pitch = pitchOf("G4"))),
        )
        val system = layoutOf(chorded)
        val chord = system.notes.filter { it.onset == lead.onset && it.staff == Staff.Upper }
        assertEquals(3, chord.size)
        assertEquals("one stem for three noteheads", 1, chord.count { it.stem != null })

        val stem = chord.single { it.stem != null }.stem!!
        val heads = chord.map { it.notehead.y.value }
        assertTrue("the stem reaches the top notehead", minOf(stem.y1.value, stem.y2.value) <= heads.min() + HALF_SPACE)
        assertTrue("and past the bottom one", maxOf(stem.y1.value, stem.y2.value) >= heads.max())
        assertEquals("one x for the whole chord", 1, chord.map { it.notehead.x.value }.distinct().size)
    }

    @Test
    fun `the playhead lands on every notehead of the shipped piece`() {
        val system = layoutOf(minuet)
        assertTrue(system.notes.size >= 20)
        system.notes.forEach { note ->
            assertEquals(
                "onset ${note.onset.value}",
                note.notehead.x.value,
                layout.xOf(system, note.onset).value,
                EPSILON,
            )
        }
        val steps = (0..48).map { layout.xOf(system, Ticks(it * MusicalTime.TICKS_PER_QUARTER / 4)).value }
        steps.zipWithNext { earlier, later -> assertTrue("$earlier then $later", later > earlier) }
    }

    /** The width a bar with no furniture reserves: the clearance after its barline. */
    private fun plainFurnitureWidth(system: StaffSystem): Double =
        system.measureAnchors[2].noteAreaX.value - system.measureAnchors[2].x.value
}
