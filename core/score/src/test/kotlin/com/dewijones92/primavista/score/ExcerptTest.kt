package com.dewijones92.primavista.score

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val BARS = 6
private const val QUARTER = 10080L

class ExcerptTest {

    private val whole = scoreOf(BARS)

    @Test
    fun `an excerpt starts at tick zero however far into the piece it began`() {
        val passage = whole.excerpt(fromIndex = 2, bars = 2)
        assertEquals(Ticks.ZERO, passage.measures.first().start)
        assertEquals(Ticks.ZERO, passage.events.first().onset)
        assertEquals(listOf(0, 1), passage.measures.map { it.index })
    }

    @Test
    fun `it keeps only what starts inside the window`() {
        val passage = whole.excerpt(fromIndex = 2, bars = 2)
        assertEquals(2 * NOTES_PER_BAR, passage.notes.size)
        val expected = whole.notes.subList(2 * NOTES_PER_BAR, 4 * NOTES_PER_BAR)
        assertEquals(expected.map { it.pitch }, passage.notes.map { it.pitch })
    }

    @Test
    fun `the title says which bars, and the id is distinct from its parent`() {
        val passage = whole.excerpt(fromIndex = 2, bars = 2)
        assertEquals("Six bars (bars 3–4)", passage.title)
        assertEquals(ScoreId("test#3-4"), passage.id)
        assertEquals(whole.composer, passage.composer)
        assertEquals(whole.origin, passage.origin)
    }

    @Test
    fun `a window that runs off the end stops at the end rather than failing`() {
        val passage = whole.excerpt(fromIndex = BARS - 1, bars = 4)
        assertEquals(1, passage.measures.size)
    }

    /**
     * A note whose tie began before the cut has nothing left to be tied to, so it must be attacked
     * — otherwise [Score.attackedNotes] hides it and the judge never expects it at all.
     */
    @Test
    fun `a tie cut by the window becomes an attack`() {
        val tied = whole.copy(
            events = listOf(
                Note(Ticks.ZERO, Duration(NoteSymbol.Whole), Staff.Upper, 1, middleC, tiedToNext = true),
                Note(Ticks(4 * QUARTER), Duration(NoteSymbol.Whole), Staff.Upper, 1, middleC, tiedFromPrevious = true),
            ),
        )
        val passage = tied.excerpt(fromIndex = 1, bars = 1)
        assertEquals(1, passage.notes.size)
        assertFalse(passage.notes.single().tiedFromPrevious)
        assertEquals(1, passage.attackedNotes.size)
    }

    @Test
    fun `a tie wholly inside the window survives`() {
        val tied = whole.copy(
            events = listOf(
                Note(Ticks.ZERO, Duration(NoteSymbol.Whole), Staff.Upper, 1, middleC, tiedToNext = true),
                Note(Ticks(4 * QUARTER), Duration(NoteSymbol.Whole), Staff.Upper, 1, middleC, tiedFromPrevious = true),
            ),
        )
        val passage = tied.excerpt(fromIndex = 0, bars = 2)
        assertEquals(2, passage.notes.size)
        assertTrue(passage.notes.last().tiedFromPrevious)
        assertEquals(1, passage.attackedNotes.size)
    }

    @Test
    fun `passages tile the piece without overlapping or losing a note`() {
        val tiled = whole.passages(bars = 2)
        assertEquals(3, tiled.size)
        assertEquals(whole.notes.size, tiled.sumOf { it.notes.size })
        assertEquals(listOf("test#1-2", "test#3-4", "test#5-6"), tiled.map { it.id.value })
    }

    @Test
    fun `an overlapping step is allowed, so a hard bar can be read as an opening and a continuation`() {
        val overlapping = whole.passages(bars = 3, step = 1)
        assertEquals(
            listOf("test#1-3", "test#2-4", "test#3-5", "test#4-6"),
            overlapping.map { it.id.value },
        )
    }

    @Test
    fun `a window longer than the piece yields no passages rather than a short one`() {
        assertEquals(emptyList<Score>(), whole.passages(bars = BARS + 1))
    }

    @Test
    fun `an excerpt of a generated exercise is still the same type, dials and all`() {
        val generated = SeededExerciseGenerator().generate(seed = 7L, spec = spec(bars = 4))
        val passage = generated.excerpt(fromIndex = 1, bars = 2)
        assertEquals(generated.origin, passage.origin)
        assertEquals(generated.defaultTempoBpm, passage.defaultTempoBpm)
        assertTrue(passage.notes.isNotEmpty())
    }

    @Test
    fun `asking for a bar the piece does not have says so`() {
        val failure = runCatching { whole.excerpt(fromIndex = BARS, bars = 1) }.exceptionOrNull()
        assertTrue("$failure", failure is IllegalArgumentException)
        assertTrue("$failure", failure?.message.orEmpty().contains("outside the $BARS bars"))
    }

    private companion object {
        const val NOTES_PER_BAR = 4
        val middleC = Pitch(Letter.C, Alter.Natural, 4)

        /** A rising natural scale from middle C, so every note in the fixture is distinct. */
        fun natural(step: Int): Pitch = Pitch(
            letter = Letter.entries[step % Pitch.LETTERS_PER_OCTAVE],
            alter = Alter.Natural,
            octave = 4 + step / Pitch.LETTERS_PER_OCTAVE,
        )

        fun scoreOf(bars: Int): Score {
            val quarter = Duration(NoteSymbol.Quarter)
            return Score(
                id = ScoreId("test"),
                title = "Six bars",
                composer = "A. Nonymous",
                origin = ScoreOrigin.Parsed("test", "n/a"),
                staves = listOf(Staff.Upper),
                measures = (0 until bars).map { index ->
                    Measure(
                        index = index,
                        start = Ticks(index * NOTES_PER_BAR * QUARTER),
                        time = TimeSignature.FourFour,
                        key = KeySignature.C,
                        clefs = mapOf(Staff.Upper to Clef.Treble),
                    )
                },
                events = (0 until bars * NOTES_PER_BAR).map { step ->
                    Note(
                        onset = Ticks(step * QUARTER),
                        duration = quarter,
                        staff = Staff.Upper,
                        voice = 1,
                        pitch = natural(step),
                    )
                },
                defaultTempoBpm = 80,
            )
        }
    }
}
