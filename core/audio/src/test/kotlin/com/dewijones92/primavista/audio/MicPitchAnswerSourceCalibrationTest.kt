package com.dewijones92.primavista.audio

import com.dewijones92.primavista.practice.InputLatency
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The point of every case here: an assumed figure must never come back labelled Measured
 * (docs/todos/measure-audio-latency.md).
 */
class MicPitchAnswerSourceCalibrationTest {

    @Test
    fun measuresAgainstThePlaybackAnchorWhenTheCaptureTimebaseComesFromTheDevice() = runBlocking {
        val capture = FakeCapture(clickAtFrame = CLICK_FRAME)
        val player = AnchoredTonePlayer(onsetNanos() - EXPECTED_LATENCY_NANOS)
        val source = source(capture, player)

        val result = source.calibrateLatency()

        assertTrue("$result", result is InputLatencyResult.Measured)
        val measured = result as InputLatencyResult.Measured
        assertEquals(EXPECTED_LATENCY_MILLIS, measured.millis, MILLIS_TOLERANCE)
        assertEquals(InputLatency.Provenance.Measured, source.latency.provenance)
        assertEquals(EXPECTED_LATENCY_MILLIS, source.latency.millis, MILLIS_TOLERANCE)
        assertTrue("the calibration click must actually be played", player.played.isNotEmpty())
        assertEquals(1, capture.stops)
    }

    /**
     * Major finding 3. A player whose anchor is a whole render buffer out over-states latency by
     * up to that buffer, and the old code reported the number with nothing attached to say so.
     */
    @Test
    fun anUncertainPlaybackAnchorShowsUpAsLowerConfidenceRatherThanDisappearing() = runBlocking {
        val exact = measure(AnchoredTonePlayer(onsetNanos() - EXPECTED_LATENCY_NANOS))
        val vague = measure(
            AnchoredTonePlayer(
                onsetNanos() - EXPECTED_LATENCY_NANOS,
                uncertaintyMillis = RENDER_BUFFER_MILLIS,
            ),
        )

        assertEquals(1.0, exact.confidence, CONFIDENCE_TOLERANCE)
        assertTrue("vague=${vague.confidence}", vague.confidence < exact.confidence)
        assertEquals(
            1.0 - RENDER_BUFFER_MILLIS / EXPECTED_LATENCY_MILLIS,
            vague.confidence,
            CONFIDENCE_TOLERANCE,
        )
    }

    /** A smeared click is located less precisely, and that shows in the confidence too. */
    @Test
    fun aSlowlyRisingClickIsMeasuredWithLessConfidenceThanASharpOne() = runBlocking {
        val smeared = FakeCapture(clickAtFrame = CLICK_FRAME, clickRiseFrames = SMEARED_RISE_FRAMES)
        val player = AnchoredTonePlayer(onsetNanos() - EXPECTED_LATENCY_NANOS)

        val result = source(smeared, player).calibrateLatency()

        val measured = result as InputLatencyResult.Measured
        assertTrue("conf=${measured.confidence}", measured.confidence < 1.0)
        assertTrue("conf=${measured.confidence}", measured.confidence > SMEARED_CONFIDENCE_FLOOR)
    }

    /**
     * Major finding 2. Room noise loud enough to be audible used to be returned as a located
     * click, so an ambient hiss became a Measured latency.
     */
    @Test
    fun refusesToMeasureRoomNoiseAndSaysThatIsWhatItHeard() = runBlocking {
        val noisy = FakeCapture(clickAtFrame = null, noiseAmplitude = ROOM_NOISE)
        val source = source(noisy, AnchoredTonePlayer(0L))

        val result = source.calibrateLatency()

        assertTrue("room noise came back as a measurement: $result", result is InputLatencyResult.Unmeasurable)
        assertTrue((result as InputLatencyResult.Unmeasurable).reason.contains("noise floor"))
        assertEquals(InputLatency.Provenance.Assumed, source.latency.provenance)
    }

    @Test
    fun isUnmeasurableWithNoTonePlayerBecauseThereIsNoLoopback() = runBlocking {
        val source = MicPitchAnswerSource(
            capture = FakeCapture(clickAtFrame = CLICK_FRAME),
            trackerFor = { FakeTracker(emptyList()) },
            tonePlayer = null,
            diag = RecordingDiag(),
            bufferFrames = FAKE_FRAMES_PER_READ,
        )

        val result = source.calibrateLatency()

        assertTrue("$result", result is InputLatencyResult.Unmeasurable)
        assertEquals(InputLatency.Provenance.Assumed, source.latency.provenance)
        assertEquals(MicPitchAnswerSource.ASSUMED_LATENCY_MILLIS, source.latency.millis, 0.0)
    }

    @Test
    fun isUnmeasurableWhenThePlayerCannotSayWhenTheClickSounded() = runBlocking {
        val diag = RecordingDiag()
        val source = source(FakeCapture(clickAtFrame = CLICK_FRAME), BlindTonePlayer(), diag)

        val result = source.calibrateLatency()

        assertTrue("$result", result is InputLatencyResult.Unmeasurable)
        assertTrue((result as InputLatencyResult.Unmeasurable).reason.contains("playbackAnchor=false"))
        assertEquals(InputLatency.Provenance.Assumed, source.latency.provenance)
    }

    @Test
    fun isUnmeasurableWhenTheCaptureTimebaseIsItselfAnExtrapolation() = runBlocking {
        val capture = FakeCapture(
            timestampProvenance = TimestampProvenance.ExtrapolatedFromStart,
            clickAtFrame = CLICK_FRAME,
        )
        val source = source(capture, AnchoredTonePlayer(onsetNanos() - EXPECTED_LATENCY_NANOS))

        val result = source.calibrateLatency()

        assertTrue("$result", result is InputLatencyResult.Unmeasurable)
        assertTrue(
            (result as InputLatencyResult.Unmeasurable).reason
                .contains("captureTimebase=ExtrapolatedFromStart"),
        )
    }

    @Test
    fun isUnmeasurableWhenNoClickIsHeard() = runBlocking {
        val source = source(FakeCapture(clickAtFrame = null), AnchoredTonePlayer(0L))

        val result = source.calibrateLatency()

        assertTrue("$result", result is InputLatencyResult.Unmeasurable)
        assertTrue((result as InputLatencyResult.Unmeasurable).reason.contains("no click in"))
    }

    @Test
    fun isUnmeasurableWhenTheMicrophoneItselfIsRefused() = runBlocking {
        val denied = FakeCapture(refusal = CaptureStart.Refused.PermissionDenied)

        val result = source(denied, AnchoredTonePlayer(0L)).calibrateLatency()

        assertTrue("$result", result is InputLatencyResult.Unmeasurable)
        assertTrue(
            (result as InputLatencyResult.Unmeasurable).reason.contains("microphone permission"),
        )
        assertEquals("a refused capture must not be stopped", 0, denied.stops)
    }

    @Test
    fun isUnmeasurableWhenTheOffsetIsImplausible() = runBlocking {
        val cases = listOf(
            onsetNanos() + IMPLAUSIBLE_MARGIN_NANOS,
            onsetNanos() - IMPLAUSIBLE_LATENCY_NANOS,
        )
        for (anchor in cases) {
            val source = source(FakeCapture(clickAtFrame = CLICK_FRAME), AnchoredTonePlayer(anchor))

            val result = source.calibrateLatency()

            assertTrue("anchor=$anchor should not be trusted: $result", result is InputLatencyResult.Unmeasurable)
            assertTrue((result as InputLatencyResult.Unmeasurable).reason.contains("outside 0"))
        }
    }

    private suspend fun measure(player: AnchoredTonePlayer): InputLatencyResult.Measured =
        source(FakeCapture(clickAtFrame = CLICK_FRAME), player).calibrateLatency()
            as InputLatencyResult.Measured

    private fun source(
        capture: FakeCapture,
        player: TonePlayer,
        diag: RecordingDiag = RecordingDiag(),
    ) = MicPitchAnswerSource(
        capture = capture,
        trackerFor = { FakeTracker(emptyList()) },
        tonePlayer = player,
        diag = diag,
        clock = FixedClock(0L),
        bufferFrames = FAKE_FRAMES_PER_READ,
    )

    private fun onsetNanos() = FrameTimebase.framesToNanos(CLICK_FRAME, FAKE_RATE)

    private companion object {
        /** Twelve primed reads, then the click three reads into the search. */
        const val CLICK_FRAME = 12L * FAKE_FRAMES_PER_READ + 3L * FAKE_FRAMES_PER_READ + 100L

        const val EXPECTED_LATENCY_MILLIS = 61.0
        const val EXPECTED_LATENCY_NANOS = 61_000_000L
        const val IMPLAUSIBLE_LATENCY_NANOS = 900_000_000L
        const val IMPLAUSIBLE_MARGIN_NANOS = 5_000_000L
        const val MILLIS_TOLERANCE = 0.01
        const val CONFIDENCE_TOLERANCE = 0.005
        const val ROOM_NOISE = 0.15f
        const val SMEARED_RISE_FRAMES = 48
        const val SMEARED_CONFIDENCE_FLOOR = 0.9

        /** 512 frames at 48kHz — the buffer the old anchor could be out by. */
        const val RENDER_BUFFER_MILLIS = 10.666
    }
}
