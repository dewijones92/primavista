package com.dewijones92.primavista.pitch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CentsTest {

    @Test
    fun `an octave is twelve hundred cents and the sign follows the direction`() {
        assertEquals(1200.0, centsBetween(Hertz(220.0), Hertz(440.0)), TOLERANCE)
        assertEquals(-1200.0, centsBetween(Hertz(440.0), Hertz(220.0)), TOLERANCE)
        assertEquals(0.0, centsBetween(Hertz(440.0), Hertz(440.0)), TOLERANCE)
    }

    @Test
    fun `an equal-tempered semitone is a hundred cents`() {
        val semitoneUp = Tuning.hertzOf(Tuning.A4_MIDI + 1)
        assertEquals(100.0, centsBetween(Hertz(Tuning.A4_HERTZ), semitoneUp), TOLERANCE)
    }

    @Test
    fun `shifting by cents round-trips`() {
        val shifted = Hertz(Tuning.A4_HERTZ).shiftedByCents(37.5)
        assertEquals(37.5, centsBetween(Hertz(Tuning.A4_HERTZ), shifted), TOLERANCE)
        assertEquals(Tuning.A4_HERTZ, shifted.shiftedByCents(-37.5).value, TOLERANCE)
    }

    @Test
    fun `midiOf keeps the cents in the remainder`() {
        val sharpOfA4 = Hertz(Tuning.A4_HERTZ).shiftedByCents(50.0)
        assertTrue(abs(Tuning.midiOf(sharpOfA4) - (Tuning.A4_MIDI + 0.5)) < TOLERANCE)
    }

    @Test
    fun `a non-positive frequency is not a frequency`() {
        assertThrows(IllegalArgumentException::class.java) { Hertz(0.0) }
        assertThrows(IllegalArgumentException::class.java) { Hertz(-1.0) }
    }

    private companion object {
        const val TOLERANCE = 1.0e-9
    }
}
