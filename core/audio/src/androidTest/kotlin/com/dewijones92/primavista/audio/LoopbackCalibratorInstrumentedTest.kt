package com.dewijones92.primavista.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.dewijones92.primavista.practice.AudioRoute
import com.dewijones92.primavista.practice.InputLatency
import com.dewijones92.primavista.practice.RouteKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val TAG = "dewidebug.loopback"
private const val PLAUSIBLE_CEILING_MILLIS = 500.0
private const val KNOWN_ONSET_NANOS = 1_000_000_000L
private const val NANOS_PER_MILLI = 1_000_000L
private const val TOLERANCE_NANOS = 1L

/**
 * Calibration against real hardware.
 *
 * Split deliberately into what a device can *prove* and what only Dewi's phone in a quiet room can
 * *measure*. An emulator's microphone does not hear its own speaker, so asserting that a click comes
 * back would fail there for a reason that has nothing to do with this code. What every device can
 * prove is that the path runs, identifies itself, and refuses honestly when it hears nothing — and
 * that the correction arithmetic moves an onset by exactly the figure it was given.
 *
 * The measured figure is logged under `dewidebug.loopback` rather than asserted, so a run on the
 * real phone reports the number this whole area is waiting for
 * (docs/todos/measure-audio-latency.md).
 */
class LoopbackCalibratorInstrumentedTest {

    private lateinit var diag: RecordingDiag
    private lateinit var capture: AudioRecordPcmCapture
    private lateinit var player: AudioTrackTonePlayer

    @Before
    fun setUp() {
        grantRecordAudio()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        diag = RecordingDiag()
        capture = AudioRecordPcmCapture(
            micPermission = {
                context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
            },
            diag = diag,
            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager,
        )
        player = AudioTrackTonePlayer(diag)
    }

    @After
    fun tearDown() {
        capture.release()
        player.release()
    }

    /** The mapping in `DeviceAudioRoutes` had never met a real `AudioDeviceInfo` until this ran. */
    @Test
    fun theRealCaptureNamesThePathItOpenedOn() {
        val started = capture.start() as CaptureStart.Started

        assertNotEquals("the platform named no input device", AudioRoute.Unidentified, started.route)
        assertEquals(started.route, capture.currentRoute)
        assertTrue("a route id must be storable: ${started.route.id}", started.route.id.isNotBlank())
        assertEquals("the id must survive a round trip through storage", started.route, AudioRoute.of(started.route.id))
        Log.i(TAG, "route=${started.route.id} kind=${started.route.kind} rate=${started.sampleRate}Hz")
    }

    /** A phone's own microphone is a built-in one; anything else here would be a mapping bug. */
    @Test
    fun aPhoneWithNoHeadsetAttachedReportsABuiltInPath() {
        val started = capture.start() as CaptureStart.Started

        assertTrue(
            "an unattached device should be BuiltIn, not ${started.route.kind}",
            started.route.kind in setOf(RouteKind.BuiltIn, RouteKind.Unknown),
        )
    }

    /**
     * The whole loopback against real hardware. Either it hears the click and the figure is
     * plausible, or it refuses and says why — never a silent failure, and never a figure outside
     * the band the calibrator itself enforces.
     */
    @Test
    fun theLoopbackEitherMeasuresAPlausibleFigureOrRefusesWithAReason() {
        val measurement = LoopbackCalibrator(capture, SystemMonotonicClock, diag).measure(player)

        assertNotEquals(AudioRoute.Unidentified, measurement.route)
        when (val result = measurement.result) {
            is InputLatencyResult.Measured -> {
                Log.i(TAG, "MEASURED lat=${result.millis}ms conf=${result.confidence} on ${measurement.route.id}")
                assertTrue("implausible ${result.millis}ms", result.millis > 0.0)
                assertTrue("implausible ${result.millis}ms", result.millis <= PLAUSIBLE_CEILING_MILLIS)
                assertTrue("confidence out of range ${result.confidence}", result.confidence in 0.0..1.0)
            }

            is InputLatencyResult.Unmeasurable -> {
                Log.i(TAG, "UNMEASURABLE on ${measurement.route.id}: ${result.reason}")
                assertTrue("a refusal with no reason is a silent failure", result.reason.isNotBlank())
            }
        }
        assertNotNull(
            "the attempt must be in the report either way: ${diag.events}",
            diag.events.firstOrNull { it.contains("loopback measured") || it.contains("latency not measured") },
        )
    }

    /**
     * The tolerance the todo asks for, on the half that is deterministic: given a figure, the
     * boundary moves a known onset back by exactly that much and by nothing else.
     */
    @Test
    fun theCorrectionMovesAKnownOnsetByExactlyTheMeasuredFigure() {
        val started = capture.start() as CaptureStart.Started
        val figure = InputLatency(EXAMPLE_MILLIS, InputLatency.Provenance.Measured)

        val corrected = MicTimestampCorrection.correct(
            onsetNanos = KNOWN_ONSET_NANOS,
            detectionDelayFrames = LONG_ANALYSIS_FRAMES,
            sampleRate = started.sampleRate,
            latency = figure,
        )

        val expected = KNOWN_ONSET_NANOS - (EXAMPLE_MILLIS * NANOS_PER_MILLI).toLong()
        assertEquals(expected.toDouble(), corrected.correctedNanos.toDouble(), TOLERANCE_NANOS.toDouble())
        assertTrue(
            "how long the analysis took must not move the note as well",
            corrected.detectionDelayNanos > 0L && corrected.correctedNanos == expected,
        )
    }

    private companion object {
        const val EXAMPLE_MILLIS = 41.5
        const val LONG_ANALYSIS_FRAMES = 2_048
    }
}
