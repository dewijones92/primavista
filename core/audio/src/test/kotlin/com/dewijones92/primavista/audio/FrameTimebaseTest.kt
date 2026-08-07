package com.dewijones92.primavista.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FrameTimebaseTest {

    @Test
    fun startsAssumedSoAnUnanchoredMappingCannotClaimToBeMeasured() {
        assertEquals(TimestampProvenance.ExtrapolatedFromStart, FrameTimebase(RATE_48).provenance)
    }

    @Test
    fun aDeviceTimestampUpgradesProvenanceAndMovesTheAnchor() {
        val timebase = FrameTimebase(RATE_48)
        timebase.anchorFromDevice(frame = 96_000L, nanos = 5_000_000_000L)

        assertEquals(TimestampProvenance.DeviceReported, timebase.provenance)
        assertEquals(96_000L, timebase.anchorFrame)
        assertEquals(5_000_000_000L, timebase.nanosFor(96_000L))
    }

    @Test
    fun extrapolatesOneSecondPerSampleRateWorthOfFramesEitherSide() {
        val cases = listOf(RATE_48, RATE_44)
        for (rate in cases) {
            val timebase = FrameTimebase(rate)
            timebase.anchorFromDevice(frame = 1_000L, nanos = 7_000_000_000L)

            assertEquals(
                "one second later at ${rate}Hz",
                7_000_000_000L + ONE_SECOND_NANOS,
                timebase.nanosFor(1_000L + rate),
            )
            assertEquals(
                "one second earlier at ${rate}Hz",
                7_000_000_000L - ONE_SECOND_NANOS,
                timebase.nanosFor(1_000L - rate),
            )
        }
    }

    @Test
    fun roundsByFloorSoTheDirectionDoesNotFlipAcrossTheAnchor() {
        val timebase = FrameTimebase(RATE_48)
        timebase.anchorFromDevice(frame = 0L, nanos = 0L)

        assertEquals(20_833L, timebase.nanosFor(1L))
        assertEquals(-20_834L, timebase.nanosFor(-1L))
    }

    @Test
    fun conversionHelpersAreExactAtWholeSeconds() {
        assertEquals(ONE_SECOND_NANOS, FrameTimebase.framesToNanos(RATE_44.toLong(), RATE_44))
        assertEquals(RATE_44.toLong(), FrameTimebase.nanosToFrames(ONE_SECOND_NANOS, RATE_44))
        assertEquals(61_000_000L, FrameTimebase.millisToNanos(61.0))
        assertEquals(61.0, FrameTimebase.nanosToMillis(61_000_000L), 1e-9)
        assertEquals(1_000.0, FrameTimebase.framesToMillis(RATE_48.toLong(), RATE_48), 1e-9)
        assertEquals(10.666, FrameTimebase.framesToMillis(512L, RATE_48), 1e-3)
    }

    @Test
    fun rejectsANonPositiveSampleRateRatherThanDividingByZeroLater() {
        assertThrows(IllegalArgumentException::class.java) { FrameTimebase(0) }
    }

    private companion object {
        const val RATE_48 = 48_000
        const val RATE_44 = 44_100
        const val ONE_SECOND_NANOS = 1_000_000_000L
    }
}
