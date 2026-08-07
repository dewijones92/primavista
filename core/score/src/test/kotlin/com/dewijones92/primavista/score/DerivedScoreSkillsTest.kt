package com.dewijones92.primavista.score

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DerivedScoreSkillsTest {

    private val skills = DerivedScoreSkills()

    @Test
    fun `treble bands and signed leger lines at the boundaries`() {
        val cases = listOf(
            Triple(pitch(Letter.G, 3), PitchBand.FarBelowStaff, -2),
            Triple(pitch(Letter.A, 3), PitchBand.BelowStaff, -2),
            Triple(pitch(Letter.C, 4), PitchBand.BelowStaff, -1),
            Triple(pitch(Letter.D, 4), PitchBand.BelowStaff, 0),
            Triple(pitch(Letter.E, 4), PitchBand.LowerStaff, 0),
            Triple(pitch(Letter.B, 4), PitchBand.MiddleStaff, 0),
            Triple(pitch(Letter.D, 5), PitchBand.UpperStaff, 0),
            Triple(pitch(Letter.F, 5), PitchBand.UpperStaff, 0),
            Triple(pitch(Letter.G, 5), PitchBand.AboveStaff, 0),
            Triple(pitch(Letter.A, 5), PitchBand.AboveStaff, 1),
            Triple(pitch(Letter.C, 6), PitchBand.AboveStaff, 2),
            Triple(pitch(Letter.D, 6), PitchBand.FarAboveStaff, 2),
        )
        assertBands(Clef.Treble, cases)
    }

    @Test
    fun `bass bands and signed leger lines at the boundaries`() {
        val cases = listOf(
            Triple(pitch(Letter.B, 1), PitchBand.FarBelowStaff, -2),
            Triple(pitch(Letter.C, 2), PitchBand.BelowStaff, -2),
            Triple(pitch(Letter.E, 2), PitchBand.BelowStaff, -1),
            Triple(pitch(Letter.F, 2), PitchBand.BelowStaff, 0),
            Triple(pitch(Letter.G, 2), PitchBand.LowerStaff, 0),
            Triple(pitch(Letter.D, 3), PitchBand.MiddleStaff, 0),
            Triple(pitch(Letter.A, 3), PitchBand.UpperStaff, 0),
            Triple(pitch(Letter.B, 3), PitchBand.AboveStaff, 0),
            Triple(pitch(Letter.C, 4), PitchBand.AboveStaff, 1),
            Triple(pitch(Letter.E, 4), PitchBand.AboveStaff, 2),
        )
        assertBands(Clef.Bass, cases)
    }

    @Test
    fun `middle C is one leger line in both clefs, and only the sign says which side`() {
        val middleC = pitch(Letter.C, 4)
        assertEquals(-1, skills.legerLines(Clef.Treble, middleC))
        assertEquals(1, skills.legerLines(Clef.Bass, middleC))
        assertEquals(PitchBand.BelowStaff, skills.bandOf(Clef.Treble, middleC))
        assertEquals(PitchBand.AboveStaff, skills.bandOf(Clef.Bass, middleC))
    }

    @Test
    fun `a leger line tag carries the side of the staff it is on`() {
        val score = grandStaffScore()
        val middleC = skills.skillsOf(score, attackOf(score, Pitch(Letter.C, Alter.Natural, 4)))
        assertTrue(middleC.contains(SkillTag.LegerLines(Clef.Treble, count = 1, above = false)))

        val lowG = skills.skillsOf(score, attackOf(score, Pitch(Letter.G, Alter.Natural, 2)))
        assertTrue(lowG.none { it is SkillTag.LegerLines })

        val overTheBass = bassStaffScore(pitch(Letter.C, 4))
        assertTrue(
            skills.skillsOf(overTheBass, 0).contains(SkillTag.LegerLines(Clef.Bass, count = 1, above = true)),
        )
        val underTheBass = bassStaffScore(pitch(Letter.C, 2))
        assertTrue(
            skills.skillsOf(underTheBass, 0).contains(SkillTag.LegerLines(Clef.Bass, count = 2, above = false)),
        )
    }

    @Test
    fun `an accidental is only a skill when the key does not already imply it`() {
        val score = grandStaffScore()
        val fSharp = attackOf(score, Pitch(Letter.F, Alter.Sharp, 4))
        val fNatural = attackOf(score, Pitch(Letter.F, Alter.Natural, 4))
        assertTrue(skills.skillsOf(score, fSharp).none { it is SkillTag.Accidental })
        assertTrue(skills.skillsOf(score, fNatural).contains(SkillTag.Accidental(Alter.Natural)))
    }

    @Test
    fun `a note carries its clef region, leger lines, key and rhythm`() {
        val score = grandStaffScore()
        val tags = skills.skillsOf(score, attackOf(score, Pitch(Letter.C, Alter.Natural, 4)))
        assertTrue(tags.contains(SkillTag.ClefRegion(Clef.Treble, PitchBand.BelowStaff)))
        assertTrue(tags.contains(SkillTag.LegerLines(Clef.Treble, 1, above = false)))
        assertTrue(tags.contains(SkillTag.KeyReading(1)))
        assertTrue(tags.contains(SkillTag.RhythmFigure(NoteSymbol.Quarter, 0, 1)))
        assertTrue("the first note of a part has no leap", tags.none { it is SkillTag.Leap })
    }

    @Test
    fun `a leap is measured from the previous note of the same staff and voice`() {
        val score = grandStaffScore()
        val second = attackOf(score, Pitch(Letter.G, Alter.Natural, 4))
        assertTrue(skills.skillsOf(score, second).contains(SkillTag.Leap(7)))
    }

    @Test
    fun `the previous note is found by position, so a repeated pitch cannot be mistaken for it`() {
        val quarter = Duration(NoteSymbol.Quarter)
        val line = listOf(Letter.C to 4, Letter.G to 4, Letter.C to 4, Letter.E to 4)
            .mapIndexed { index, (letter, octave) ->
                Note(quarter.ticks * index, quarter, Staff.Upper, 1, Pitch(letter, Alter.Natural, octave))
            }
        val score = singleStaffScore(line)
        assertEquals(setOf(SkillTag.Leap(7)), skills.skillsOf(score, 1).filterIsInstance<SkillTag.Leap>().toSet())
        assertEquals(setOf(SkillTag.Leap(7)), skills.skillsOf(score, 2).filterIsInstance<SkillTag.Leap>().toSet())
        assertEquals(setOf(SkillTag.Leap(4)), skills.skillsOf(score, 3).filterIsInstance<SkillTag.Leap>().toSet())
    }

    @Test
    fun `an attack index outside the score is refused rather than answered`() {
        val score = grandStaffScore()
        assertThrows(IllegalArgumentException::class.java) { skills.skillsOf(score, score.attackedNotes.size) }
        assertThrows(IllegalArgumentException::class.java) { skills.skillsOf(score, -1) }
    }

    @Test
    fun `hand independence is tagged only where both staves sound in the same bar`() {
        val score = grandStaffScore()
        val inBarOne = attackOf(score, Pitch(Letter.C, Alter.Natural, 4))
        val inBarTwo = score.attackedNotes.indexOfFirst {
            it.onset >= score.measures[1].start && it.staff == Staff.Upper
        }
        assertTrue(skills.skillsOf(score, inBarOne).contains(SkillTag.HandIndependence))
        assertFalse(skills.skillsOf(score, inBarTwo).contains(SkillTag.HandIndependence))
    }

    @Test
    fun `a short opening bar does not borrow the next bar's other hand`() {
        val quarter = Duration(NoteSymbol.Quarter)
        val clefs = mapOf(Staff.Upper to Clef.Treble, Staff.Lower to Clef.Bass)
        val pickup = Note(Ticks.ZERO, quarter, Staff.Upper, 1, Pitch(Letter.D, Alter.Natural, 5))
        val barTwoUpper = Note(quarter.ticks, quarter, Staff.Upper, 1, Pitch(Letter.E, Alter.Natural, 5))
        val barTwoLower = Note(quarter.ticks, quarter, Staff.Lower, 2, Pitch(Letter.G, Alter.Natural, 2))
        val score = Score(
            id = ScoreId("anacrusis"),
            title = "Pickup",
            composer = null,
            origin = ScoreOrigin.Parsed("anacrusis", "n/a"),
            staves = listOf(Staff.Upper, Staff.Lower),
            measures = listOf(
                Measure(0, Ticks.ZERO, TimeSignature.FourFour, KeySignature.C, clefs),
                Measure(1, quarter.ticks, TimeSignature.FourFour, KeySignature.C, clefs),
            ),
            events = listOf(pickup, barTwoUpper, barTwoLower),
            defaultTempoBpm = 80,
        )
        assertFalse(
            "the pickup bar is one quarter long, not a whole bar",
            skills.skillsOf(score, 0).contains(SkillTag.HandIndependence),
        )
        assertTrue(skills.skillsOf(score, 1).contains(SkillTag.HandIndependence))
    }

    @Test
    fun `a single-staff score never asks for hand independence`() {
        val single = grandStaffScore().let { grand ->
            grand.copy(
                staves = listOf(Staff.Upper),
                events = grand.events.filter { it.staff == Staff.Upper },
            )
        }
        assertFalse(skills.skillsOf(single).contains(SkillTag.HandIndependence))
    }

    @Test
    fun `the score's skills are the union of its notes' skills`() {
        val score = grandStaffScore()
        val union = score.attackedNotes.indices.flatMap { skills.skillsOf(score, it) }.toSet()
        assertEquals(union, skills.skillsOf(score))
    }

    @Test
    fun `a tied continuation is not counted as a note to read`() {
        val score = grandStaffScore()
        assertEquals(score.notes.size - 1, score.attackedNotes.size)
    }

    private fun assertBands(clef: Clef, cases: List<Triple<Pitch, PitchBand, Int>>) {
        for ((pitch, band, legerLines) in cases) {
            val label = "$clef ${pitch.letter}${pitch.octave}"
            assertEquals("$label band", band, skills.bandOf(clef, pitch))
            assertEquals("$label leger lines", legerLines, skills.legerLines(clef, pitch))
        }
    }

    private fun attackOf(score: Score, pitch: Pitch): Int =
        score.attackedNotes.indexOfFirst { it.pitch == pitch }.also {
            assertTrue("no attacked $pitch in the score", it >= 0)
        }

    private fun pitch(letter: Letter, octave: Int) = Pitch(letter, Alter.Natural, octave)

    private fun bassStaffScore(pitch: Pitch): Score {
        val quarter = Duration(NoteSymbol.Quarter)
        return Score(
            id = ScoreId("test-bass"),
            title = "One bass note",
            composer = null,
            origin = ScoreOrigin.Parsed("test", "n/a"),
            staves = listOf(Staff.Lower),
            measures = listOf(
                Measure(0, Ticks.ZERO, TimeSignature.FourFour, KeySignature.C, mapOf(Staff.Lower to Clef.Bass)),
            ),
            events = listOf(Note(Ticks.ZERO, quarter, Staff.Lower, 1, pitch)),
            defaultTempoBpm = 80,
        )
    }

    private fun singleStaffScore(notes: List<Note>) = Score(
        id = ScoreId("test-single-staff"),
        title = "One line",
        composer = null,
        origin = ScoreOrigin.Parsed("test", "n/a"),
        staves = listOf(Staff.Upper),
        measures = listOf(
            Measure(0, Ticks.ZERO, TimeSignature.FourFour, KeySignature.C, mapOf(Staff.Upper to Clef.Treble)),
        ),
        events = notes,
        defaultTempoBpm = 80,
    )

    private fun grandStaffScore(): Score {
        val quarter = Duration(NoteSymbol.Quarter)
        val bar = TimeSignature.FourFour.measureTicks
        val clefs = mapOf(Staff.Upper to Clef.Treble, Staff.Lower to Clef.Bass)
        val upperBarOne = listOf(
            Pitch(Letter.C, Alter.Natural, 4),
            Pitch(Letter.G, Alter.Natural, 4),
            Pitch(Letter.F, Alter.Sharp, 4),
            Pitch(Letter.F, Alter.Natural, 4),
        ).mapIndexed { index, pitch -> Note(quarter.ticks * index, quarter, Staff.Upper, 1, pitch) }
        val lowerBarOne = Note(
            onset = Ticks.ZERO,
            duration = Duration(NoteSymbol.Whole),
            staff = Staff.Lower,
            voice = 2,
            pitch = Pitch(Letter.G, Alter.Natural, 2),
        )
        val heldOver = Pitch(Letter.D, Alter.Natural, 5)
        val upperBarTwo = listOf(
            Note(bar, quarter, Staff.Upper, 1, heldOver, tiedToNext = true),
            Note(bar + quarter.ticks, quarter, Staff.Upper, 1, heldOver, tiedFromPrevious = true),
        )
        val restOfBarTwo = Rest(bar + quarter.ticks * 2, Duration(NoteSymbol.Half), Staff.Upper, 1)
        return Score(
            id = ScoreId("test-grand-staff"),
            title = "Two bars",
            composer = null,
            origin = ScoreOrigin.Parsed("test", "n/a"),
            staves = listOf(Staff.Upper, Staff.Lower),
            measures = listOf(
                Measure(0, Ticks.ZERO, TimeSignature.FourFour, KeySignature(1), clefs),
                Measure(1, bar, TimeSignature.FourFour, KeySignature(1), clefs),
            ),
            events = upperBarOne + lowerBarOne + upperBarTwo + restOfBarTwo,
            defaultTempoBpm = 80,
        )
    }
}
