package com.dewijones92.primavista.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin
import kotlin.random.Random

class LatencyCalibrationTest {

    @Test
    fun findsTheLeadingEdgeOfAClickRatherThanItsPeak() {
        val pcm = FloatArray(SIZE)
        for (offset in 0 until CLICK_FRAMES) {
            pcm[ONSET + offset] = CLICK_PEAK * (1f - offset.toFloat() / CLICK_FRAMES)
        }

        val found = LatencyCalibration.findClick(pcm, SIZE)

        assertEquals(ONSET, found.foundFrame())
    }

    @Test
    fun refusesSilenceSoAFailedLoopbackCannotBeMistakenForZeroLatency() {
        val refusal = LatencyCalibration.findClick(FloatArray(SIZE), SIZE)

        assertTrue("$refusal", refusal is ClickSearch.NotFound)
        assertTrue("$refusal", (refusal as ClickSearch.NotFound).reason.contains("audible floor"))
    }

    /**
     * The whole of major finding 2: room noise loud enough to clear the audible floor used to come
     * back as a located onset, so an ambient hiss was reported as a measured latency.
     */
    @Test
    fun refusesNoiseOnlyInputEvenWhenItIsLoudEnoughToBeAudible() {
        val noise = noiseBuffer(NOISY_ROOM)

        val refusal = LatencyCalibration.findClick(noise, SIZE)

        assertTrue("noise was accepted as a click: $refusal", refusal is ClickSearch.NotFound)
        val reason = (refusal as ClickSearch.NotFound).reason
        assertTrue(reason, reason.contains("noise floor"))
        assertTrue("the peak cleared the audible floor, so that is not why: $reason", noise.max() > 0.02f)
    }

    @Test
    fun refusesAClickThatBarelyStandsOutFromTheRoom() {
        val pcm = noiseBuffer(NOISY_ROOM)
        pcm[ONSET] = NOISY_ROOM * MARGINAL_CLICK_RATIO

        val refusal = LatencyCalibration.findClick(pcm, SIZE)

        assertTrue("$refusal", refusal is ClickSearch.NotFound)
        assertTrue("$refusal", (refusal as ClickSearch.NotFound).reason.contains("room noise"))
    }

    @Test
    fun findsARealClickSittingOnTopOfRoomNoise() {
        val pcm = noiseBuffer(QUIET_ROOM)
        for (offset in 0 until CLICK_FRAMES) {
            pcm[ONSET + offset] += CLICK_PEAK * sin(Math.PI * offset / CLICK_FRAMES).toFloat()
        }

        val found = LatencyCalibration.findClick(pcm, SIZE)

        assertTrue("a loud click over quiet noise must be found: $found", found is ClickSearch.Found)
        val click = found as ClickSearch.Found
        assertTrue("onset ${click.frame} should be inside the click", click.frame in ONSET..(ONSET + CLICK_FRAMES))
        assertTrue("snr=${click.peakToNoise}", click.peakToNoise > LatencyCalibration.DEFAULT_REQUIRED_PEAK_TO_NOISE)
    }

    /** The rise is what the calibration turns into its stated uncertainty, so it must be real. */
    @Test
    fun reportsHowFarTheOnsetIsFromThePeakSoTheMeasurementCanStateItsUncertainty() {
        val sharp = FloatArray(SIZE).also { it[ONSET] = CLICK_PEAK }
        val smeared = FloatArray(SIZE)
        for (offset in 0 until CLICK_FRAMES) {
            smeared[ONSET + offset] = CLICK_PEAK * (offset + 1).toFloat() / CLICK_FRAMES
        }

        val sharpRise = (LatencyCalibration.findClick(sharp, SIZE) as ClickSearch.Found).riseFrames
        val smearedRise = (LatencyCalibration.findClick(smeared, SIZE) as ClickSearch.Found).riseFrames

        assertEquals(0, sharpRise)
        assertTrue("smeared=$smearedRise", smearedRise > sharpRise)
    }

    @Test
    fun onlyLooksAtTheFramesActuallyRead() {
        val pcm = FloatArray(SIZE)
        pcm[LATE_ONSET] = CLICK_PEAK

        assertTrue(
            "a click past the read must not be found",
            LatencyCalibration.findClick(pcm, ONSET) is ClickSearch.NotFound,
        )
        assertEquals(LATE_ONSET, LatencyCalibration.findClick(pcm, SIZE).foundFrame())
    }

    @Test
    fun toleratesAnEmptyRead() {
        assertTrue(LatencyCalibration.findClick(FloatArray(SIZE), 0) is ClickSearch.NotFound)
        assertTrue(LatencyCalibration.findClick(FloatArray(0), SIZE) is ClickSearch.NotFound)
    }

    @Test
    fun aHigherThresholdMovesTheOnsetLaterIntoTheAttack() {
        val pcm = FloatArray(SIZE)
        for (offset in 0 until CLICK_FRAMES) {
            pcm[ONSET + offset] = CLICK_PEAK * (offset + 1).toFloat() / CLICK_FRAMES
        }

        val loose = LatencyCalibration.findClick(pcm, SIZE, thresholdRatio = 0.1).foundFrame()
        val strict = LatencyCalibration.findClick(pcm, SIZE, thresholdRatio = 0.9).foundFrame()

        assertTrue("loose=$loose strict=$strict", strict > loose)
    }

    @Test
    fun rejectsAnImpossibleThreshold() {
        assertThrows(IllegalArgumentException::class.java) {
            LatencyCalibration.findClick(FloatArray(SIZE), SIZE, thresholdRatio = 0.0)
        }
    }

    @Test
    fun rejectsANoiseMarginThatWouldLetNoiseThrough() {
        assertThrows(IllegalArgumentException::class.java) {
            LatencyCalibration.findClick(FloatArray(SIZE), SIZE, requiredPeakToNoise = 0.5)
        }
    }

    private fun ClickSearch.foundFrame(): Int = (this as ClickSearch.Found).frame

    private fun noiseBuffer(amplitude: Float): FloatArray {
        val random = Random(SEED)
        return FloatArray(SIZE) { random.nextFloat() * 2f * amplitude - amplitude }
    }

    private companion object {
        const val SIZE = 4_096
        const val ONSET = 1_000
        const val LATE_ONSET = 3_000
        const val CLICK_FRAMES = 200
        const val CLICK_PEAK = 0.6f
        const val NOISY_ROOM = 0.15f
        const val QUIET_ROOM = 0.01f
        const val MARGINAL_CLICK_RATIO = 3f
        const val SEED = 20_260_807L
    }
}
