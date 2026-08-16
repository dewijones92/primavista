package com.dewijones92.primavista.ui.practice

import com.dewijones92.primavista.score.Alter
import com.dewijones92.primavista.score.Clef
import com.dewijones92.primavista.score.Duration
import com.dewijones92.primavista.score.KeySignature
import com.dewijones92.primavista.score.Letter
import com.dewijones92.primavista.score.Measure
import com.dewijones92.primavista.score.Midi
import com.dewijones92.primavista.score.MusicalTime
import com.dewijones92.primavista.score.Note
import com.dewijones92.primavista.score.NoteSymbol
import com.dewijones92.primavista.score.Pitch
import com.dewijones92.primavista.score.Score
import com.dewijones92.primavista.score.ScoreId
import com.dewijones92.primavista.score.ScoreOrigin
import com.dewijones92.primavista.score.Staff
import com.dewijones92.primavista.score.Ticks
import com.dewijones92.primavista.score.TimeSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SEMITONES_PER_OCTAVE = 12
private const val TEMPO_BPM = 60

/**
 * How wide the tapped keyboard is, and where the music sits on it.
 *
 * It had no tests at all, which is how a beginner's five-note drill came to be answered on
 * twenty-one white keys with the notes crammed into the right-hand third. Both halves matter: a key
 * too narrow to hit records a mis-tap as a wrong note against Dewi (spec I2), and a note the
 * keyboard cannot reach at all is judged `Missed` — the app inventing a fault.
 */
class KeyboardRangeTest {

    @Test
    fun `every note the music uses is reachable`() {
        val music = listOf(
            listOf(p(Letter.A, 0)),
            listOf(p(Letter.C, 2), p(Letter.G, 3)),
            listOf(p(Letter.B, 4), p(Letter.F, 5)),
            listOf(p(Letter.C, 7)),
        )

        for (pitches in music) {
            val range = keyboardRange(scoreOf(pitches))

            for (pitch in pitches) {
                assertTrue("${pitch.midi.number} is off the keyboard $range", pitch.midi.number in range)
            }
        }
    }

    /** The point of snapping outward: the black-key pattern is what a hand reads position from. */
    @Test
    fun `the keyboard always starts on a C and ends on a B`() {
        val singles = listOf(
            p(Letter.A, 0),
            p(Letter.C, 2),
            p(Letter.B, 4),
            p(Letter.C, 5),
            p(Letter.G, 6),
            p(Letter.C, 8),
        )

        for (pitch in singles) {
            val range = keyboardRange(scoreOf(listOf(pitch)))

            assertEquals("$range", 0, range.start % SEMITONES_PER_OCTAVE)
            assertEquals("$range", SEMITONES_PER_OCTAVE - 1, range.endInclusive % SEMITONES_PER_OCTAVE)
        }
    }

    /**
     * The beginner case, and the one that was wrong. Stage one is five notes in the middle of the
     * treble staff, and it was being answered across three octaves of near-identical keys.
     */
    @Test
    fun `a five-note treble drill gets two octaves and not three`() {
        val drill = listOf(p(Letter.B, 4), p(Letter.C, 5), p(Letter.D, 5), p(Letter.E, 5), p(Letter.F, 5))

        val range = keyboardRange(scoreOf(drill))

        assertEquals(SEMITONES_PER_OCTAVE * 2, range.endInclusive - range.start + 1)
    }

    /** The recorded complaint: the padding all went below, so the notes sat at the far right. */
    @Test
    fun `the music sits nearer the middle of the keyboard than its edge`() {
        val drill = listOf(p(Letter.B, 4), p(Letter.F, 5))
        val range = keyboardRange(scoreOf(drill))

        val span = (range.endInclusive - range.start).toDouble()
        val middle = drill.map { it.midi.number }.average()
        val where = (middle - range.start) / span
        assertTrue("the music sits at ${"%.2f".format(where)} of $range", where in 0.25..0.75)
    }

    @Test
    fun `a two-note exercise still gets a keyboard rather than a few enormous keys`() {
        val range = keyboardRange(scoreOf(listOf(p(Letter.C, 4), p(Letter.E, 4))))

        assertTrue("$range", range.endInclusive - range.start + 1 >= SEMITONES_PER_OCTAVE * 2)
    }

    /** A grand-staff piece is wider than the floor, so the floor must not shrink it. */
    @Test
    fun `a piece spanning five octaves keeps all of them`() {
        val wide = listOf(p(Letter.C, 2), p(Letter.C, 7))

        val range = keyboardRange(scoreOf(wide))

        assertTrue("$range", wide.all { it.midi.number in range })
        assertTrue("$range", range.endInclusive - range.start + 1 >= SEMITONES_PER_OCTAVE * 6)
    }

    /** Padding must never push the range off the end of MIDI and leave a part-octave behind. */
    @Test
    fun `music at the very bottom of MIDI still gets whole octaves`() {
        val range = keyboardRange(scoreOf(listOf(p(Letter.C, -1), p(Letter.D, -1))))

        assertTrue("$range", range.start >= Midi.MIN)
        assertEquals("$range", 0, range.start % SEMITONES_PER_OCTAVE)
        assertTrue("$range", 0 in range)
    }

    @Test
    fun `music at the very top of MIDI still gets whole octaves`() {
        val top = p(Letter.G, 9)

        val range = keyboardRange(scoreOf(listOf(top)))

        assertTrue("$range", range.endInclusive <= Midi.MAX)
        assertTrue("$range", top.midi.number in range)
        assertEquals("$range", 0, range.start % SEMITONES_PER_OCTAVE)
    }

    /** Nothing chosen yet is still a playable keyboard, not an empty one. */
    @Test
    fun `no score yet gives a keyboard around middle C`() {
        val range = keyboardRange(null)

        assertTrue("$range", p(Letter.C, 4).midi.number in range)
        assertTrue("$range", range.endInclusive - range.start + 1 >= SEMITONES_PER_OCTAVE * 2)
    }

    private fun p(letter: Letter, octave: Int) = Pitch(letter, Alter.Natural, octave)

    private fun scoreOf(pitches: List<Pitch>): Score = Score(
        id = ScoreId("keyboard-range"),
        title = "range",
        composer = null,
        origin = ScoreOrigin.Parsed(sourceName = "fixture", licence = "public domain"),
        staves = listOf(Staff.Upper),
        measures = listOf(
            Measure(
                index = 0,
                start = Ticks.ZERO,
                time = TimeSignature.FourFour,
                key = KeySignature.C,
                clefs = mapOf(Staff.Upper to Clef.Treble),
            ),
        ),
        events = pitches.mapIndexed { index, pitch ->
            Note(
                onset = Ticks(MusicalTime.TICKS_PER_QUARTER * index),
                duration = Duration(NoteSymbol.Quarter),
                staff = Staff.Upper,
                voice = 1,
                pitch = pitch,
            )
        },
        defaultTempoBpm = TEMPO_BPM,
    )
}
