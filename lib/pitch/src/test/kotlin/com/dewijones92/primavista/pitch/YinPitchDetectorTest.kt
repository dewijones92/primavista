package com.dewijones92.primavista.pitch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

class YinPitchDetectorTest {

    private val sampleRate = Signals.SAMPLE_RATE

    @Test
    fun `pure A4 is detected within five cents`() {
        val signal = Signals.sine(A4, Signals.frames(0.3))
        val estimates = detect(signal)

        assertTrue("no estimate produced for a clean A4", estimates.isNotEmpty())
        estimates.forEach {
            val error = abs(centsBetween(Hertz(A4), it.hertz))
            assertTrue(
                "A4 read as ${it.hertz.value} Hz, $error cents off (bar: $A4_TOLERANCE_CENTS)",
                error <= A4_TOLERANCE_CENTS
            )
        }
    }

    /**
     * The tolerance is the claim, so it is asserted rather than described. Worst observed error
     * across this table is 0.05 cents; the bar is set an order of magnitude above that so a real
     * regression fails while floating-point noise does not.
     */
    @Test
    fun `every semitone from low E to G6 is detected within one cent`() {
        for (hertz in SWEEP_HERTZ) {
            val signal = Signals.sine(hertz, Signals.frames(0.2))
            val estimates = detect(signal)

            assertTrue("no estimate produced for $hertz Hz", estimates.isNotEmpty())
            estimates.forEach {
                val error = abs(centsBetween(Hertz(hertz), it.hertz))
                assertTrue(
                    "$hertz Hz read as ${it.hertz.value} Hz, $error cents off (bar: $SWEEP_TOLERANCE_CENTS)",
                    error <= SWEEP_TOLERANCE_CENTS,
                )
            }
        }
    }

    /**
     * Without parabolic interpolation the estimate quantises to an integer lag, which at G6 is
     * 1575 Hz for a 1568 Hz tone — nearly eight cents, from arithmetic alone.
     */
    @Test
    fun `parabolic interpolation beats integer-lag quantisation at high pitch`() {
        val hertz = G6
        val integerLag = (sampleRate / hertz).roundToInt()
        val quantisedError = abs(centsBetween(Hertz(hertz), Hertz(sampleRate.toDouble() / integerLag)))
        assertTrue("this table is only meaningful where the lag is fractional", quantisedError > 5.0)

        val estimates = detect(Signals.sine(hertz, Signals.frames(0.2)))
        val worst = estimates.maxOf { abs(centsBetween(Hertz(hertz), it.hertz)) }

        assertTrue(
            "interpolated error $worst cents is no better than integer-lag $quantisedError cents",
            worst < quantisedError / 5,
        )
    }

    @Test
    fun `a tone with strong partials is heard at its fundamental, not an octave up`() {
        for (fundamental in doubleArrayOf(98.0, 220.0, 440.0, 880.0)) {
            val signal = Signals.withHarmonics(fundamental, PARTIALS, Signals.frames(0.2), amplitude = 0.2)
            val estimates = detect(signal)

            assertTrue("no estimate produced for $fundamental Hz plus partials", estimates.isNotEmpty())
            estimates.forEach {
                val error = abs(centsBetween(Hertz(fundamental), it.hertz))
                assertTrue(
                    "$fundamental Hz plus partials read as ${it.hertz.value} Hz ($error cents off)",
                    error <= HARMONIC_TOLERANCE_CENTS,
                )
            }
        }
    }

    /** The classic YIN failure: a weak fundamental makes the second partial look like the note. */
    @Test
    fun `a fundamental weaker than its partials is still the reported pitch`() {
        val fundamental = 196.0
        val signal = Signals.withHarmonics(fundamental, WEAK_FUNDAMENTAL_PARTIALS, Signals.frames(0.2), amplitude = 0.1)

        val estimates = detect(signal)

        assertTrue(estimates.isNotEmpty())
        estimates.forEach {
            val error = abs(centsBetween(Hertz(fundamental), it.hertz))
            assertTrue(
                "read as ${it.hertz.value} Hz, $error cents above the fundamental",
                error <= HARMONIC_TOLERANCE_CENTS
            )
        }
    }

    @Test
    fun `silence produces no estimates at all`() {
        assertEquals(emptyList<DetectedPitch>(), detect(Signals.silence(Signals.frames(0.5))))
    }

    @Test
    fun `white noise produces no estimates rather than a low-confidence guess`() {
        assertEquals(emptyList<DetectedPitch>(), detect(Signals.whiteNoise(Signals.frames(0.5))))
    }

    /** A constant offset is periodic at every lag and at none; the difference function is all zero. */
    @Test
    fun `a DC offset produces no estimates`() {
        val dc = FloatArray(Signals.frames(0.3)) { 0.5f }
        assertEquals(emptyList<DetectedPitch>(), detect(dc))
    }

    /**
     * At 0 dB SNR the requirement is a refusal, so the refusal is what is asserted. Its pair — the
     * same tone without the noise — is what stops this passing on an empty result, which is how the
     * previous version managed to assert nothing at all. See `.claude/CODE-NOTES.md`.
     */
    @Test
    fun `a tone under equal-amplitude noise is refused rather than guessed`() {
        val clean = Signals.sine(A4, Signals.frames(0.3), amplitude = 0.2)
        val noisy = Signals.sine(A4, Signals.frames(0.3), amplitude = 0.2)
        val noise = Signals.whiteNoise(noisy.size, seed = 7L, amplitude = 0.2)
        for (n in noisy.indices) noisy[n] += noise[n]

        val estimates = detect(noisy)

        assertEquals(
            "0 dB SNR must yield no estimate, got ${estimates.map { it.hertz.value to it.confidence }}",
            emptyList<DetectedPitch>(),
            estimates,
        )
        assertTrue(
            "the same tone without the noise must still be detected, or this test proves nothing",
            detect(clean).isNotEmpty(),
        )
    }

    /** Confidence is 1 - aperiodicity at the chosen lag, and the lag had to clear the threshold. */
    @Test
    fun `every estimate returned clears the aperiodicity threshold`() {
        val threshold = 0.15
        val estimates =
            detect(Signals.sine(293.66, Signals.frames(0.3)), YinPitchDetector(sampleRate, threshold = threshold))

        assertTrue(estimates.isNotEmpty())
        estimates.forEach { assertTrue("confidence ${it.confidence}", it.confidence > 1.0 - threshold) }
    }

    /** A microphone opens at whatever rate the device offers, so nothing here may assume 44.1 kHz. */
    @Test
    fun `pitch is read as accurately at other sample rates`() {
        for (rate in intArrayOf(22_050, 44_100, 48_000)) {
            for (hertz in doubleArrayOf(SWEEP_HERTZ.first(), A4, SWEEP_HERTZ.last())) {
                val signal = Signals.sine(hertz, Signals.frames(0.3, rate), rate)
                val estimates = detect(signal, YinPitchDetector(rate))

                assertTrue("no estimate for $hertz Hz at $rate Hz", estimates.isNotEmpty())
                val worst = estimates.maxOf { abs(centsBetween(Hertz(hertz), it.hertz)) }
                assertTrue("$hertz Hz at $rate Hz was $worst cents off", worst <= SWEEP_TOLERANCE_CENTS)
            }
        }
    }

    @Test
    fun `atFrame is the window centre and estimates arrive one per hop`() {
        val detector = YinPitchDetector(sampleRate)
        val estimates = detector.push(Signals.sine(A4, Signals.frames(0.2)), Signals.frames(0.2))

        assertEquals((detector.windowFrames / 2).toLong(), estimates.first().atFrame)
        estimates.zipWithNext { earlier, later ->
            assertEquals(detector.hopFrames.toLong(), later.atFrame - earlier.atFrame)
        }
    }

    @Test
    fun `ragged chunk sizes give exactly the same estimates as one push`() {
        val signal = Signals.sine(329.63, Signals.frames(0.3))
        val whole = detect(signal)

        val chunked = Signals.pushInChunks(signal, RAGGED_CHUNKS, YinPitchDetector(sampleRate)::push)

        assertEquals(whole, chunked)
    }

    @Test
    fun `reset restarts the frame count and the overlap buffer`() {
        val detector = YinPitchDetector(sampleRate)
        val signal = Signals.sine(A4, Signals.frames(0.2))
        val first = detector.push(signal, signal.size)

        detector.reset()
        val second = detector.push(signal, signal.size)

        assertEquals(first, second)
        assertTrue(first.isNotEmpty())
    }

    @Test
    fun `an incoherent configuration is refused at construction`() {
        assertThrows(IllegalArgumentException::class.java) { YinPitchDetector(0) }
        assertThrows(IllegalArgumentException::class.java) { YinPitchDetector(sampleRate, windowFrames = 2047) }
        assertThrows(IllegalArgumentException::class.java) { YinPitchDetector(sampleRate, hopFrames = 4096) }
        assertThrows(IllegalArgumentException::class.java) { YinPitchDetector(sampleRate, threshold = 1.5) }
        assertThrows(IllegalArgumentException::class.java) { YinPitchDetector(sampleRate, minHertz = 3000.0) }
        assertThrows(IllegalArgumentException::class.java) { YinPitchDetector(sampleRate, silenceRms = -1.0) }
        // 256 frames at 44.1 kHz cannot reach down to 55 Hz.
        assertThrows(IllegalArgumentException::class.java) { YinPitchDetector(sampleRate, windowFrames = 256) }
    }

    @Test
    fun `pushing more frames than the buffer holds is refused`() {
        assertThrows(IllegalArgumentException::class.java) { YinPitchDetector(sampleRate).push(FloatArray(8), 9) }
    }

    @Test
    fun `the search range covers the whole sweep this module claims to hear`() {
        val range = YinPitchDetector(sampleRate).searchRange
        assertTrue("$range excludes the bottom of the sweep", SWEEP_HERTZ.first() in range)
        assertTrue("$range excludes the top of the sweep", SWEEP_HERTZ.last() in range)
    }

    private fun detect(signal: FloatArray, detector: PitchDetector = YinPitchDetector(sampleRate)) =
        detector.push(signal, signal.size)

    private companion object {
        const val A4 = 440.0
        const val G6 = 1567.98
        const val A4_TOLERANCE_CENTS = 5.0
        const val SWEEP_TOLERANCE_CENTS = 1.0
        const val HARMONIC_TOLERANCE_CENTS = 2.0

        val PARTIALS = doubleArrayOf(0.7, 0.5, 0.3)
        val WEAK_FUNDAMENTAL_PARTIALS = doubleArrayOf(2.5, 2.0, 1.5)
        val RAGGED_CHUNKS = intArrayOf(1, 7, 333, 2049, 64, 511)

        /** Low E on a guitar up to G6, every semitone the app can be expected to hear. */
        val SWEEP_HERTZ = doubleArrayOf(
            82.41, 87.31, 92.50, 98.00, 103.83, 110.00, 116.54, 123.47,
            130.81, 138.59, 146.83, 155.56, 164.81, 174.61, 185.00, 196.00,
            207.65, 220.00, 233.08, 246.94, 261.63, 277.18, 293.66, 311.13,
            329.63, 349.23, 369.99, 392.00, 415.30, 440.00, 466.16, 493.88,
            523.25, 554.37, 587.33, 622.25, 659.26, 698.46, 739.99, 783.99,
            830.61, 880.00, 932.33, 987.77, 1046.50, 1108.73, 1174.66, 1244.51,
            1318.51, 1396.91, 1479.98, 1567.98,
        )
    }
}
