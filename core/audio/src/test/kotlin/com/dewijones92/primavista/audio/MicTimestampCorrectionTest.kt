package com.dewijones92.primavista.audio

import com.dewijones92.primavista.practice.InputLatency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MicTimestampCorrectionTest {

    @Test
    fun subtractsInputLatencyOnlyAndKeepsTheAnalysisDelayVisibleForAReport() {
        val correction = MicTimestampCorrection.correct(
            onsetNanos = ONE_SECOND,
            detectionDelayFrames = 1_024,
            sampleRate = RATE,
            latency = InputLatency(60.0, InputLatency.Provenance.Measured),
        )

        assertEquals(21_333_333L, correction.detectionDelayNanos)
        assertEquals(60_000_000L, correction.inputLatencyNanos)
        assertEquals(940_000_000L, correction.correctedNanos)
        assertEquals(InputLatency.Provenance.Measured, correction.provenance)
    }

    @Test
    fun theAnalysisDelayNeverMovesTheNoteBecauseTheOnsetFrameAlreadyIsTheOnset() {
        val onset = MicTimestampCorrection.correct(
            onsetNanos = ONE_SECOND,
            detectionDelayFrames = 0,
            sampleRate = RATE,
            latency = InputLatency(60.0, InputLatency.Provenance.Measured),
        )
        val slowToConfirm = MicTimestampCorrection.correct(
            onsetNanos = ONE_SECOND,
            detectionDelayFrames = 4_096,
            sampleRate = RATE,
            latency = InputLatency(60.0, InputLatency.Provenance.Measured),
        )

        assertEquals(onset.correctedNanos, slowToConfirm.correctedNanos)
    }

    @Test
    fun changesNothingWhenThereIsNothingToCorrect() {
        val correction = MicTimestampCorrection.correct(
            onsetNanos = ONE_SECOND,
            detectionDelayFrames = 0,
            sampleRate = RATE,
            latency = InputLatency.None,
        )

        assertEquals(ONE_SECOND, correction.correctedNanos)
        assertEquals(InputLatency.Provenance.NotApplicable, correction.provenance)
    }

    @Test
    fun neverMovesANoteLaterBecauseLatencyIsAlwaysADelay() {
        val cases = listOf(0 to 0.0, 512 to 12.5, 4_096 to 200.0)
        for ((delayFrames, latencyMillis) in cases) {
            val correction = MicTimestampCorrection.correct(
                onsetNanos = ONE_SECOND,
                detectionDelayFrames = delayFrames,
                sampleRate = RATE,
                latency = InputLatency(latencyMillis, InputLatency.Provenance.Assumed),
            )

            assertTrue(
                "delay=$delayFrames lat=$latencyMillis must not move a note later",
                correction.correctedNanos <= ONE_SECOND,
            )
        }
    }

    @Test
    fun namesTheUnitAndTheProvenanceInItsLogForm() {
        val rendered = MicTimestampCorrection.correct(
            onsetNanos = ONE_SECOND,
            detectionDelayFrames = 1_024,
            sampleRate = RATE,
            latency = InputLatency(60.0, InputLatency.Provenance.Assumed),
        ).toString()

        assertTrue(rendered, rendered.contains("ms"))
        assertTrue(rendered, rendered.contains("Assumed"))
    }

    private companion object {
        const val RATE = 48_000
        const val ONE_SECOND = 1_000_000_000L
    }
}
