package com.dewijones92.primavista.audio

import com.dewijones92.primavista.pitch.Hertz
import com.dewijones92.primavista.pitch.MonophonicNoteTracker
import com.dewijones92.primavista.pitch.TrackedNote
import com.dewijones92.primavista.practice.InputLatency
import com.dewijones92.primavista.score.Midi
import com.dewijones92.primavista.score.Polyphony
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MicPitchAnswerSourceNotesTest {

    @Test
    fun declaresMonoSoTheJudgeCanRefusePolyphonyHonestly() {
        val source = source(FakeCapture(), FakeTracker(emptyList()))

        assertEquals("mic", source.label)
        assertEquals(Polyphony.Mono, source.polyphony)
    }

    @Test
    fun startsWithAnAssumedLatencyNeverAMeasuredOne() {
        val source = source(FakeCapture(), FakeTracker(emptyList()))

        assertEquals(InputLatency.Provenance.Assumed, source.latency.provenance)
        assertEquals(MicPitchAnswerSource.UNTIL_A_ROUTE_IS_KNOWN.latency.millis, source.latency.millis, 0.0)
    }

    @Test
    fun correctsTheOnsetForInputLatencyAtThisOneBoundary() = runBlocking {
        val note = TrackedNote(
            hertz = Hertz(A4_HERTZ),
            atFrame = ONSET_FRAME,
            confidence = GOOD_CONFIDENCE,
            detectionDelayFrames = DETECTION_DELAY_FRAMES,
        )
        val capture = FakeCapture(maxReads = 1)
        val played = source(capture, FakeTracker(listOf(note))).notes().toList()

        assertEquals(1, played.size)
        val only = played.single()
        assertEquals(Midi.A4, only.midi.number)
        assertEquals(EXPECTED_AT_NANOS, only.atNanos)
        assertEquals(GOOD_CONFIDENCE, only.confidence, 0f)
        assertEquals(0.0, only.centsOff!!, CENTS_TOLERANCE)
        assertEquals(1, capture.starts)
        assertEquals(1, capture.stops)
    }

    /**
     * The blocker, held end to end rather than only on the correction: the tracker's onset frame
     * already is the onset, so how long the analysis took to confirm the pitch must not move the
     * note. Subtracting it too biased every mic note early by the analysis window.
     */
    @Test
    fun howLongThePitchTookToConfirmDoesNotMoveTheNote() = runBlocking {
        val instant = TrackedNote(Hertz(A4_HERTZ), ONSET_FRAME, GOOD_CONFIDENCE, 0)
        val slow = TrackedNote(Hertz(A4_HERTZ), ONSET_FRAME, GOOD_CONFIDENCE, SLOW_DETECTION_FRAMES)

        val played = source(FakeCapture(maxReads = 1), FakeTracker(listOf(instant, slow))).notes().toList()

        assertEquals(2, played.size)
        assertEquals(played[0].atNanos, played[1].atNanos)
        assertEquals(EXPECTED_AT_NANOS, played[0].atNanos)
    }

    /**
     * Declining the microphone is an ordinary thing a person does. It must surface as a refusal
     * with a reason, never as a crash (the contract's CaptureStart.Refused).
     */
    @Test
    fun refusesToListenWhenTheMicrophonePermissionWasDeclined() = runBlocking {
        val denied = FakeCapture(refusal = CaptureStart.Refused.PermissionDenied)
        val diag = RecordingDiag()

        val played = source(denied, FakeTracker(emptyList()), diag).notes().toList()

        assertTrue(played.isEmpty())
        assertNotNull(
            "the refusal must name its reason: ${diag.events}",
            diag.events.firstOrNull { it.contains("microphone permission") },
        )
        assertEquals("nothing was opened, so nothing should be stopped", 0, denied.stops)
    }

    @Test
    fun refusesToListenWhenNoConfigurationOpenedAndRepeatsTheReason() = runBlocking {
        val reason = "no source in [UNPROCESSED] opened at [48000]"
        val unusable = FakeCapture(refusal = CaptureStart.Refused.NoUsableConfiguration(reason))
        val diag = RecordingDiag()

        val played = source(unusable, FakeTracker(emptyList()), diag).notes().toList()

        assertTrue(played.isEmpty())
        assertNotNull(diag.events.firstOrNull { it.contains(reason) })
    }

    @Test
    fun releasingTheSourceReleasesTheCaptureItWasGiven() {
        val capture = FakeCapture()

        source(capture, FakeTracker(emptyList())).release()

        assertEquals(1, capture.releases)
    }

    @Test
    fun dropsDetectionsThatAreTooUncertainAndSaysSoInTheLog() = runBlocking {
        val note = TrackedNote(Hertz(A4_HERTZ), ONSET_FRAME, POOR_CONFIDENCE, DETECTION_DELAY_FRAMES)
        val diag = RecordingDiag()
        val played = source(FakeCapture(maxReads = 1), FakeTracker(listOf(note)), diag).notes().toList()

        assertTrue(played.isEmpty())
        assertEquals(1, diag.counts["mic.dropped.lowConfidence"])
    }

    @Test
    fun refusesFrequenciesOutsideMidiRangeRatherThanThrowing() = runBlocking {
        val note = TrackedNote(Hertz(SUBSONIC_HERTZ), ONSET_FRAME, GOOD_CONFIDENCE, 0)
        val diag = RecordingDiag()
        val played = source(FakeCapture(maxReads = 1), FakeTracker(listOf(note)), diag).notes().toList()

        assertTrue(played.isEmpty())
        assertEquals(1, diag.counts["mic.dropped.outsideMidiRange"])
    }

    @Test
    fun givesUpWithAReasonWhenCaptureStopsProducingRatherThanSpinningSilently() = runBlocking {
        val diag = RecordingDiag()
        source(FakeCapture(maxReads = 0), FakeTracker(emptyList()), diag).notes().toList()

        assertNotNull(diag.events.firstOrNull { it.contains("consecutive empty reads") })
        assertTrue((diag.counts["mic.emptyReads"] ?: 0) > 0)
    }

    @Test
    fun saysSoWhenTheTrackerAndTheCaptureDisagreeAboutTheSampleRate() = runBlocking {
        val diag = RecordingDiag()
        val tracker = FakeTracker(emptyList(), sampleRate = FAKE_RATE / 2)

        source(FakeCapture(maxReads = 0), tracker, diag).notes().toList()

        assertNotNull(
            "a rate mismatch silently skews every timestamp: ${diag.events}",
            diag.events.firstOrNull { it.contains("but capture opened at") },
        )
    }

    @Test
    fun countsDetectionsRatherThanLoggingALinePerFrame() = runBlocking {
        val notes = List(MANY_NOTES) {
            TrackedNote(Hertz(A4_HERTZ), ONSET_FRAME + it, GOOD_CONFIDENCE, DETECTION_DELAY_FRAMES)
        }
        val diag = RecordingDiag()
        val played = source(FakeCapture(maxReads = 1), FakeTracker(notes), diag).notes().toList()

        assertEquals(MANY_NOTES, played.size)
        assertEquals(MANY_NOTES, diag.counts["mic.detected"])
        assertTrue(
            "per-note events would flood the ring buffer: ${diag.events}",
            diag.events.size < MANY_NOTES,
        )
    }

    @Test
    fun resetsTheTrackerSoAReusedOneCannotCarryStaleFrameCounters() = runBlocking {
        val tracker = FakeTracker(emptyList())
        val source = source(FakeCapture(maxReads = 1), tracker)

        source.notes().toList()
        source.notes().toList()

        assertEquals(2, tracker.resets)
    }

    @Test
    fun refusesASecondListenerBecauseThereIsOneMicrophone() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val diag = RecordingDiag()
        val source = source(FakeCapture(), GatedTracker(entered, release), diag)
        val first = launch(Dispatchers.Default) { source.notes().toList() }

        assertTrue("first listener never reached the tracker", entered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        assertTrue("a second listener must be refused", source.notes().toList().isEmpty())
        assertNotNull(diag.events.firstOrNull { it.contains("already listening") })

        release.countDown()
        first.cancelAndJoin()
    }

    private fun source(
        capture: FakeCapture,
        tracker: MonophonicNoteTracker,
        diag: RecordingDiag = RecordingDiag(),
    ) = MicPitchAnswerSource(
        capture = capture,
        trackerFor = { tracker },
        diag = diag,
        bufferFrames = FAKE_FRAMES_PER_READ,
    )

    private companion object {
        const val A4_HERTZ = 440.0
        const val SUBSONIC_HERTZ = 1.0
        const val ONSET_FRAME = 48_000L
        const val DETECTION_DELAY_FRAMES = 1_024
        const val SLOW_DETECTION_FRAMES = 4_096
        const val GOOD_CONFIDENCE = 0.9f
        const val POOR_CONFIDENCE = 0.1f
        const val CENTS_TOLERANCE = 0.05
        const val MANY_NOTES = 40
        const val TIMEOUT_SECONDS = 5L

        /** 1s onset, minus the 60ms assumed input latency. See .claude/CODE-NOTES.md. */
        const val EXPECTED_AT_NANOS = 940_000_000L
    }
}
