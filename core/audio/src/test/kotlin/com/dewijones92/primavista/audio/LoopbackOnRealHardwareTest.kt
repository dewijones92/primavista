package com.dewijones92.primavista.audio

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DEVICE_SETTLES_AFTER_READS = 8
private const val A_REAL_CLICK_FRAMES = 480
private const val A_QUIET_CLICK = 0.011f
private const val ROOM_NOISE = 0.0006f

/**
 * The loopback against a capture that behaves like the real one.
 *
 * Dewi ran this on a Pixel 7 and it refused. Two separate defects, and the whole suite was green
 * over both of them, because `FakeCapture` was **more capable than the adapter it stood in for**:
 * it reported `DeviceReported` timestamps from the first call and planted a one-sample impulse
 * where the real click sounds for milliseconds. These tests make the fake behave like the device.
 */
class LoopbackOnRealHardwareTest {

    /**
     * The dead-on-arrival one. `CaptureStart.Started` is built inside `start()`, before a single
     * frame is read, so its `timestampProvenance` is `ExtrapolatedFromStart` on every device for
     * ever. Reading it there rather than asking the capture live meant this feature refused
     * unconditionally, for everyone, always.
     */
    @Test
    fun `a timebase that settles while priming is enough to measure`() = runBlocking {
        val capture = FakeCapture(
            clickAtFrame = CLICK_FRAME,
            clickAmplitude = A_QUIET_CLICK,
            clickFrames = A_REAL_CLICK_FRAMES,
            noiseAmplitude = ROOM_NOISE,
            settlesAfterReads = DEVICE_SETTLES_AFTER_READS,
        )

        val result = measured(capture)

        assertTrue(
            "the timebase settled before the click, so this must measure: $result",
            result is InputLatencyResult.Measured
        )
    }

    /** A capture whose device never reports a timestamp still has to refuse, and say which. */
    @Test
    fun `a timebase that never settles refuses and names the timebase`() = runBlocking {
        val capture = FakeCapture(
            clickAtFrame = CLICK_FRAME,
            clickAmplitude = A_QUIET_CLICK,
            clickFrames = A_REAL_CLICK_FRAMES,
            noiseAmplitude = ROOM_NOISE,
            settlesTo = TimestampProvenance.ExtrapolatedFromStart,
        )

        val result = measured(capture)

        assertTrue("$result", result is InputLatencyResult.Unmeasurable)
        assertTrue(
            (result as InputLatencyResult.Unmeasurable).reason,
            result.reason.contains("captureTimebase=ExtrapolatedFromStart"),
        )
    }

    /**
     * The one that actually bit him. The click sounds for 10ms and the analysis window is 21ms, so
     * a detector taking its noise floor from the median of the very buffer the click is in
     * measures the click and calls it the room. The room is measured before anything is played.
     */
    @Test
    fun `a click that fills much of the buffer is still heard`() = runBlocking {
        val capture = FakeCapture(
            clickAtFrame = CLICK_FRAME,
            clickAmplitude = A_QUIET_CLICK,
            clickFrames = A_REAL_CLICK_FRAMES,
            noiseAmplitude = ROOM_NOISE,
            settlesAfterReads = DEVICE_SETTLES_AFTER_READS,
        )

        val result = measured(capture)

        assertTrue(
            "a sounding click must not be mistaken for room noise: $result",
            result is InputLatencyResult.Measured
        )
    }

    /**
     * A room smears a click well past its nominal length, and the old 40ms click was already
     * longer than the 21ms window. Once an event fills more than about half a buffer, a noise
     * floor taken as that buffer's own median rises with the event and the detector rejects its
     * own stimulus as room noise. Measuring the room before playing anything is what fixes it.
     */
    @Test
    fun `a click smeared across more than a whole buffer is still heard`() = runBlocking {
        val reverberant = FakeCapture(
            clickAtFrame = CLICK_FRAME,
            clickAmplitude = A_QUIET_CLICK,
            clickFrames = SMEARED_CLICK_FRAMES,
            noiseAmplitude = ROOM_NOISE,
            settlesAfterReads = DEVICE_SETTLES_AFTER_READS,
        )

        val result = measured(reverberant)

        // Asserting the FIGURE, not merely that one came back. With the floor taken from the
        // click's own buffer, every buffer inside the click is rejected and the onset is finally
        // "found" in the quiet tail — a measurement roughly 40ms too large, which is the whole
        // quantity being measured. A latency that is wrong is worse than one that is refused.
        assertTrue("$result", result is InputLatencyResult.Measured)
        val measured = (result as InputLatencyResult.Measured).millis
        assertEquals(TRUE_LATENCY_MILLIS, measured, TOLERANCE_MILLIS)
    }

    /**
     * His Pixel 7 at 9/25 media volume returned a peak of 0.011 and the old floor refused it as
     * inaudible. A phone's own speaker reaches its own microphone quietly; that is normal.
     */
    @Test
    fun `a click as quiet as a real phone at moderate volume is loud enough`() = runBlocking {
        val capture = FakeCapture(
            clickAtFrame = CLICK_FRAME,
            clickAmplitude = A_QUIET_CLICK,
            clickFrames = A_REAL_CLICK_FRAMES,
            noiseAmplitude = ROOM_NOISE,
            settlesAfterReads = DEVICE_SETTLES_AFTER_READS,
        )

        assertTrue(
            "0.011 is a real measured peak, not silence",
            A_QUIET_CLICK > LatencyCalibration.DEFAULT_MINIMUM_PEAK
        )
        assertTrue("$capture", measured(capture) is InputLatencyResult.Measured)
    }

    /** Digital silence must still be refused, or the floor has been lowered into uselessness. */
    @Test
    fun `the emulator's silence is still refused`() = runBlocking {
        val silent = FakeCapture(clickAtFrame = null, noiseAmplitude = 0.00006f)

        val result = measured(silent)

        assertTrue("$result", result is InputLatencyResult.Unmeasurable)
        val refusal = (result as InputLatencyResult.Unmeasurable).reason
        assertTrue(refusal, refusal.contains("no click in"))
    }

    /**
     * The one cause the person holding the phone can fix in a second. The click goes out as media,
     * so the slider scales it; a refusal that does not mention that sends them looking at the app.
     */
    @Test
    fun `a refusal names the media volume and says to turn it up when it is low`() = runBlocking {
        val silent = FakeCapture(clickAtFrame = null, noiseAmplitude = 0.00006f)

        val result = measured(silent, volume = 0.36) as InputLatencyResult.Unmeasurable

        assertTrue(result.reason, result.reason.contains("media volume 36%"))
        assertTrue(result.reason, result.reason.contains("turn the media volume up"))
    }

    @Test
    fun `a refusal at full volume does not tell him to turn it up`() = runBlocking {
        val silent = FakeCapture(clickAtFrame = null, noiseAmplitude = 0.00006f)

        val result = measured(silent, volume = 1.0) as InputLatencyResult.Unmeasurable

        assertTrue(result.reason, result.reason.contains("media volume 100%"))
        assertTrue(result.reason, !result.reason.contains("turn the media volume up"))
    }

    @Test
    fun `a volume that cannot be read is said to be unknown rather than guessed`() = runBlocking {
        val silent = FakeCapture(clickAtFrame = null, noiseAmplitude = 0.00006f)

        val result = measured(silent, volume = null) as InputLatencyResult.Unmeasurable

        assertTrue(result.reason, result.reason.contains("media volume unknown"))
    }

    /** A refusal quoting the last buffer describes silence after the click, which helps nobody. */
    @Test
    fun `a refusal quotes the loudest buffer it saw and the room it measured`() = runBlocking {
        val tooQuiet = FakeCapture(
            clickAtFrame = CLICK_FRAME,
            clickAmplitude = 0.0009f,
            clickFrames = A_REAL_CLICK_FRAMES,
            noiseAmplitude = 0.0006f,
            settlesAfterReads = DEVICE_SETTLES_AFTER_READS,
        )

        val result = measured(tooQuiet) as InputLatencyResult.Unmeasurable

        assertTrue(result.reason, result.reason.contains("loudest buffer"))
        assertTrue(result.reason, result.reason.contains("room="))
    }

    private suspend fun measured(capture: FakeCapture, volume: Double? = 1.0): InputLatencyResult {
        val source = MicPitchAnswerSource(
            capture = capture,
            trackerFor = { FakeTracker(emptyList()) },
            tonePlayer = AnchoredTonePlayer(
                FrameTimebase.framesToNanos(CLICK_FRAME, FAKE_RATE) - EXPECTED_LATENCY_NANOS,
            ),
            mediaVolume = { volume },
            diag = RecordingDiag(),
            bufferFrames = FAKE_FRAMES_PER_READ,
        )
        return source.calibrateLatency()
    }

    private companion object {
        const val TRUE_LATENCY_MILLIS = 45.0
        const val TOLERANCE_MILLIS = 5.0
        val EXPECTED_LATENCY_NANOS = FrameTimebase.millisToNanos(TRUE_LATENCY_MILLIS)

        /** Longer than FAKE_FRAMES_PER_READ, so every buffer it touches is wholly inside it. */
        const val SMEARED_CLICK_FRAMES = 2_000
    }
}
