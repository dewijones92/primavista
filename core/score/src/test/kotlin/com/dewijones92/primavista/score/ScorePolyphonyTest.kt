package com.dewijones92.primavista.score

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * docs/spec.md I3 rests entirely on this predicate: if it says Mono, a microphone that can follow
 * only one line is allowed to score the piece.
 */
class ScorePolyphonyTest {

    private val quarter = Duration(NoteSymbol.Quarter)
    private val half = Duration(NoteSymbol.Half)
    private val whole = Duration(NoteSymbol.Whole)
    private val barLength = TimeSignature.FourFour.measureTicks

    @Test
    fun `a held left hand under a moving right hand is polyphonic despite sharing no onset`() {
        val leftHand = Note(Ticks.ZERO, whole, Staff.Lower, 2, pitch(Letter.G, 2))
        val rightHand = (1..4).map { beat ->
            Note(quarter.ticks * beat, quarter, Staff.Upper, 1, pitch(Letter.C, 5))
        }
        val silentDownbeat = Rest(Ticks.ZERO, quarter, Staff.Upper, 1)
        val score = score(bars = 2, events = listOf(leftHand, silentDownbeat) + rightHand)

        assertEquals(
            "onsets never coincide, but the whole note is sounding under every one of them",
            Polyphony.Poly,
            score.polyphony,
        )
        assertEquals(1, score.firstPolyphonicMeasure())
    }

    @Test
    fun `a genuine single line is monophonic`() {
        val melody = (0 until 8).map { step ->
            Note(quarter.ticks * step, quarter, Staff.Upper, 1, pitch(Letter.entries[step % 7], 4))
        }
        val score = score(bars = 2, events = melody)

        assertEquals(Polyphony.Mono, score.polyphony)
        assertNull(score.firstPolyphonicMeasure())
    }

    @Test
    fun `notes that merely meet end to end do not overlap`() {
        val first = Note(Ticks.ZERO, half, Staff.Upper, 1, pitch(Letter.C, 4))
        val second = Note(half.ticks, half, Staff.Lower, 2, pitch(Letter.G, 2))
        val score = score(bars = 1, events = listOf(first, second))

        assertEquals(Polyphony.Mono, score.polyphony)
        assertNull(score.firstPolyphonicMeasure())
    }

    @Test
    fun `a chord is polyphonic and names the bar it is written in`() {
        val melody = (0 until 8).map { step ->
            Note(quarter.ticks * step, quarter, Staff.Upper, 1, pitch(Letter.C, 5))
        }
        val chordTone = Note(barLength + quarter.ticks, quarter, Staff.Upper, 1, pitch(Letter.E, 5))
        val score = score(bars = 2, events = melody + chordTone)

        assertEquals(Polyphony.Poly, score.polyphony)
        assertEquals("the chord is in the second bar", 2, score.firstPolyphonicMeasure())
    }

    @Test
    fun `a note tied over the barline is still sounding when the other hand enters`() {
        val held = pitch(Letter.G, 2)
        val leftHand = listOf(
            Note(Ticks.ZERO, whole, Staff.Lower, 2, held, tiedToNext = true),
            Note(barLength, whole, Staff.Lower, 2, held, tiedFromPrevious = true),
        )
        val rightHand = (0 until 4).map { beat ->
            Note(barLength + quarter.ticks * beat, quarter, Staff.Upper, 1, pitch(Letter.C, 5))
        }
        val score = score(bars = 2, events = leftHand + rightHand)

        assertEquals(
            "dropping the tied continuation would end the left hand at the barline",
            Polyphony.Poly,
            score.polyphony,
        )
        assertEquals(
            "the overlap is in bar 2, not the bar the held note started in",
            2,
            score.firstPolyphonicMeasure(),
        )
    }

    @Test
    fun `hands-separate writing with the other hand resting is monophonic`() {
        val rightHand = (0 until 4).map { beat ->
            Note(quarter.ticks * beat, quarter, Staff.Upper, 1, pitch(Letter.C, 5))
        }
        val leftHand = Rest(Ticks.ZERO, whole, Staff.Lower, 2)
        val score = score(bars = 1, events = rightHand + leftHand)

        assertEquals(Polyphony.Mono, score.polyphony)
        assertNull(score.firstPolyphonicMeasure())
    }

    @Test
    fun `a score with no notes at all is monophonic rather than unanswered`() {
        val score = score(bars = 1, events = listOf(Rest(Ticks.ZERO, whole, Staff.Upper, 1)))

        assertEquals(Polyphony.Mono, score.polyphony)
        assertNull(score.firstPolyphonicMeasure())
    }

    @Test
    fun `the bar it names is 1-based, so the last bar of three is 3`() {
        val score = score(
            bars = 3,
            events = listOf(
                Note(barLength * 2, whole, Staff.Lower, 2, pitch(Letter.G, 2)),
                Note(barLength * 2 + quarter.ticks, quarter, Staff.Upper, 1, pitch(Letter.C, 5)),
            ),
        )

        assertEquals(3, score.firstPolyphonicMeasure())
        assertEquals(score.measures.last().number, score.firstPolyphonicMeasure())
    }

    private fun pitch(letter: Letter, octave: Int) = Pitch(letter, Alter.Natural, octave)

    private fun score(bars: Int, events: List<ScoreEvent>) = Score(
        id = ScoreId("test-polyphony"),
        title = "Polyphony fixture",
        composer = null,
        origin = ScoreOrigin.Parsed("test", "n/a"),
        staves = listOf(Staff.Upper, Staff.Lower),
        measures = (0 until bars).map { index ->
            Measure(
                index = index,
                start = barLength * index,
                time = TimeSignature.FourFour,
                key = KeySignature.C,
                clefs = mapOf(Staff.Upper to Clef.Treble, Staff.Lower to Clef.Bass),
            )
        },
        events = events.sortedWith(scoreEventOrder),
        defaultTempoBpm = 80,
    )
}
