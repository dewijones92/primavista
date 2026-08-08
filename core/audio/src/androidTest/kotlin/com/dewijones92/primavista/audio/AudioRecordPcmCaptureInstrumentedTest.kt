package com.dewijones92.primavista.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Opens the real microphone; [grantRecordAudio] is what makes that possible unattended. */
class AudioRecordPcmCaptureInstrumentedTest {

    private lateinit var diag: RecordingDiag
    private lateinit var capture: AudioRecordPcmCapture

    @Before
    fun setUp() {
        grantRecordAudio()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        diag = RecordingDiag()
        capture = AudioRecordPcmCapture(
            micPermission = {
                context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
            },
            diag = diag,
            audioManager = audioManager,
        )
    }

    @After
    fun tearDown() {
        capture.release()
    }

    @Test
    fun opensAKnownSourceAtAKnownRateAndSaysWhichInTheLog() {
        val started = capture.start() as CaptureStart.Started
        val route = capture.activeRoute

        assertNotNull("no route was recorded", route)
        assertTrue(
            "unexpected rate ${route!!.sampleRate}",
            route.sampleRate in AudioRecordPcmCapture.DEFAULT_SAMPLE_RATES,
        )
        assertEquals(route.sampleRate, started.sampleRate)
        assertEquals(route.sourceName, started.audioSourceName)
        assertNotNull(
            "the selected source must be in the log: ${diag.events}",
            diag.events.firstOrNull { it.contains("capturing source=") },
        )
    }

    @Test
    fun readsRealFramesAndAdvancesTheAbsoluteFramePosition() {
        capture.start()
        val buffer = FloatArray(BUFFER_FRAMES)

        val first = capture.read(buffer)
        val second = capture.read(buffer)

        assertTrue("first read returned ${first.frames}", first.frames > 0)
        assertEquals(0L, first.firstFrame)
        assertEquals(first.frames.toLong(), second.firstFrame)
    }

    @Test
    fun startsOnAnExtrapolatedTimebaseAndNeverClaimsOtherwise() {
        val started = capture.start() as CaptureStart.Started

        assertEquals(TimestampProvenance.ExtrapolatedFromStart, started.timestampProvenance)
    }

    @Test
    fun upgradesItsTimebaseOnceTheDeviceReportsATimestamp() {
        capture.start()
        val buffer = FloatArray(BUFFER_FRAMES)
        repeat(READS_FOR_TIMESTAMP) { capture.read(buffer) }

        // A failure here is a finding. See .claude/CODE-NOTES.md.
        assertEquals(
            "timebase never came from the device; events=${diag.events} counts=${diag.counts}",
            TimestampProvenance.DeviceReported,
            capture.timestampProvenance,
        )
    }

    @Test
    fun frameTimestampsAdvanceWithTheFrameCountRatherThanWithWallTime() {
        capture.start()
        val buffer = FloatArray(BUFFER_FRAMES)
        repeat(READS_FOR_TIMESTAMP) { capture.read(buffer) }

        val rate = capture.sampleRate
        val start = capture.frameTimestampNanos(0L)
        val oneSecondLater = capture.frameTimestampNanos(rate.toLong())

        assertEquals(NANOS_PER_SECOND, oneSecondLater - start)
    }

    @Test
    fun aSecondStartIsIgnoredRatherThanLeakingAnAudioRecord() {
        capture.start()
        val route = capture.activeRoute

        val again = capture.start()

        assertTrue("a second start must still report the open capture", again is CaptureStart.Started)
        assertEquals(route, capture.activeRoute)
        assertNotNull(diag.events.firstOrNull { it.contains("already capturing") })
    }

    @Test
    fun refusesAfterReleaseRatherThanReopeningWhatWasThrownAway() {
        capture.start()
        capture.release()

        val refused = capture.start()

        assertTrue("$refused", refused is CaptureStart.Refused.NoUsableConfiguration)
    }

    private companion object {
        const val BUFFER_FRAMES = 1_024
        const val READS_FOR_TIMESTAMP = 24
        const val NANOS_PER_SECOND = 1_000_000_000L
    }
}
