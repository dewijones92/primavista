package com.dewijones92.primavista.audio

import com.dewijones92.primavista.pitch.Hertz
import com.dewijones92.primavista.pitch.Tuning
import com.dewijones92.primavista.score.Midi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.pow

class PitchMappingTest {

    @Test
    fun mapsEqualTemperamentReferencesToTheirMidiNumbers() {
        val cases = listOf(
            440.0 to Midi.A4,
            261.625_565_3 to Midi.MIDDLE_C,
            27.5 to LOWEST_PIANO_A,
            4186.009 to HIGHEST_PIANO_C,
        )
        for ((hertz, expected) in cases) {
            val estimate = PitchMapping.estimate(Hertz(hertz))
            assertNotNull("$hertz Hz should map to a note", estimate)
            assertEquals("$hertz Hz", expected, estimate!!.midi.number)
            assertEquals("$hertz Hz cents", 0.0, estimate.centsOff, CENTS_TOLERANCE)
        }
    }

    @Test
    fun reportsTheRemainderAsCentsRatherThanRoundingItAway() {
        val sharpBy = 25.0
        val hertz = Hertz(Tuning.A4_HERTZ * 2.0.pow(sharpBy / Tuning.CENTS_PER_SEMITONE / SEMITONES))
        val estimate = PitchMapping.estimate(hertz)

        assertEquals(Midi.A4, estimate!!.midi.number)
        assertEquals(sharpBy, estimate.centsOff, CENTS_TOLERANCE)
    }

    @Test
    fun aQuarterToneAboveRoundsUpAndReportsANegativeRemainder() {
        val hertz = Hertz(Tuning.A4_HERTZ * 2.0.pow(HALF_SEMITONE_CENTS / Tuning.CENTS_PER_SEMITONE / SEMITONES))
        val estimate = PitchMapping.estimate(hertz)

        assertEquals(Midi.A4 + 1, estimate!!.midi.number)
        assertEquals(-HALF_SEMITONE_CENTS, estimate.centsOff, CENTS_TOLERANCE)
    }

    @Test
    fun refusesFrequenciesOutsideMidiRangeInsteadOfLettingTheValueClassThrow() {
        val outside = listOf(1.0, 4.0, 30_000.0, 100_000.0)
        for (hertz in outside) {
            assertNull("$hertz Hz is outside MIDI 0..127", PitchMapping.estimate(Hertz(hertz)))
        }
    }

    @Test
    fun acceptsTheExtremesOfTheMidiRange() {
        assertEquals(Midi.MIN, PitchMapping.estimate(Tuning.hertzOf(Midi.MIN))!!.midi.number)
        assertEquals(Midi.MAX, PitchMapping.estimate(Tuning.hertzOf(Midi.MAX))!!.midi.number)
    }

    private companion object {
        const val CENTS_TOLERANCE = 0.05
        const val SEMITONES = 12.0
        const val HALF_SEMITONE_CENTS = 50.0
        const val LOWEST_PIANO_A = 21
        const val HIGHEST_PIANO_C = 108
    }
}
