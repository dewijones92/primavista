package com.dewijones92.primavista.audio

import com.dewijones92.primavista.common.Diag
import com.dewijones92.primavista.common.NoOpDiag
import com.dewijones92.primavista.pitch.MonophonicNoteTracker
import com.dewijones92.primavista.pitch.TrackedNote
import com.dewijones92.primavista.practice.InputLatency
import com.dewijones92.primavista.practice.PlayedNote
import com.dewijones92.primavista.score.Polyphony
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * PCM to `:lib:pitch` to [PlayedNote]. Declares [Polyphony.Mono] so the judge can refuse
 * polyphonic material rather than mis-score it (docs/spec.md I3). See .claude/CODE-NOTES.md.
 */
public class MicPitchAnswerSource(
    private val capture: PcmCapture,
    private val trackerFor: (sampleRate: Int) -> MonophonicNoteTracker,
    private val tonePlayer: TonePlayer? = null,
    private val diag: Diag = NoOpDiag,
    private val clock: MonotonicClock = SystemMonotonicClock,
    private val bufferFrames: Int = DEFAULT_BUFFER_FRAMES,
    private val minConfidence: Float = DEFAULT_MIN_CONFIDENCE,
) : MicAnswerSource {

    override val label: String = "mic"

    override val polyphony: Polyphony = Polyphony.Mono

    @Volatile
    private var currentLatency: InputLatency = ASSUMED_LATENCY

    override val latency: InputLatency get() = currentLatency

    private val listening = AtomicBoolean(false)

    private val calibrator = LoopbackCalibrator(capture, clock, diag, bufferFrames)

    override fun notes(): Flow<PlayedNote> = flow {
        if (!listening.compareAndSet(false, true)) {
            diag.event(TAG, "refused: already listening; there is one AudioRecord per process")
            return@flow
        }
        var capturing = false
        try {
            when (val opened = capture.start()) {
                is CaptureStart.Refused -> diag.event(TAG, "refused: ${opened.reason}")

                is CaptureStart.Started -> {
                    capturing = true
                    listen(opened)
                }
            }
        } finally {
            if (capturing) capture.stop()
            listening.set(false)
            diag.event(TAG, "stopped listening")
        }
    }.flowOn(Dispatchers.IO)

    override fun release() {
        capture.release()
        diag.event(TAG, "released the capture; the TonePlayer belongs to whoever supplied it")
    }

    /**
     * A failed re-measure drops back to the assumed figure rather than keeping the previous one:
     * calibration is re-run when the route changes, and Bluetooth's latency is not the built-in
     * mic's. The one exception is a refusal that never reached the audio path at all.
     */
    override suspend fun calibrateLatency(): InputLatencyResult {
        val player = tonePlayer
            ?: return applied(refused("no TonePlayer is attached, so no loopback is possible"))
        if (!listening.compareAndSet(false, true)) {
            diag.event(TAG, "latency left at ${currentLatency.millis}ms (${currentLatency.provenance})")
            return refused("the mic is in use; calibrate before a session starts")
        }
        val result = try {
            withContext(Dispatchers.IO) { calibrator.measure(player) }
        } finally {
            listening.set(false)
        }
        return applied(result)
    }

    private suspend fun FlowCollector<PlayedNote>.listen(opened: CaptureStart.Started) {
        val tracker = trackerFor(opened.sampleRate).also { it.reset() }
        if (tracker.sampleRate != opened.sampleRate) {
            diag.event(
                TAG,
                "tracker rate=${tracker.sampleRate}Hz but capture opened at ${opened.sampleRate}Hz; " +
                    "every frame-to-nanos conversion below is wrong by that ratio",
            )
        }
        diag.event(
            TAG,
            "listening rate=${opened.sampleRate}Hz src=${opened.audioSourceName} " +
                "timebase=${opened.timestampProvenance} buffer=${bufferFrames}frames " +
                "minConf=$minConfidence lat=${currentLatency.millis}ms latSrc=${currentLatency.provenance}",
        )
        readInto(tracker, opened.sampleRate)
    }

    private suspend fun FlowCollector<PlayedNote>.readInto(tracker: MonophonicNoteTracker, rate: Int) {
        val buffer = FloatArray(bufferFrames)
        var emptyReads = 0
        while (currentCoroutineContext().isActive) {
            val read = capture.read(buffer)
            if (read.frames <= 0) {
                emptyReads++
                diag.counted(TAG, "emptyReads")
                if (emptyReads >= MAX_CONSECUTIVE_EMPTY_READS) {
                    diag.event(TAG, "giving up: $emptyReads consecutive empty reads, capture is not producing")
                    return
                }
                continue
            }
            emptyReads = 0
            tracker.push(buffer, read.frames).forEach { emitDetected(it, rate) }
        }
        diag.event(TAG, "reader cancelled by collector")
    }

    private suspend fun FlowCollector<PlayedNote>.emitDetected(note: TrackedNote, rate: Int) {
        if (note.confidence < minConfidence) {
            diag.counted(TAG, "dropped.lowConfidence")
            return
        }
        val estimate = PitchMapping.estimate(note.hertz)
        if (estimate == null) {
            diag.counted(TAG, "dropped.outsideMidiRange")
            return
        }
        val correction = MicTimestampCorrection.correct(
            onsetNanos = capture.frameTimestampNanos(note.atFrame),
            detectionDelayFrames = note.detectionDelayFrames,
            sampleRate = rate,
            latency = currentLatency,
        )
        diag.counted(TAG, "detected")
        diag.state(TAG) {
            "last midi=${estimate.midi.number} cents=${format(estimate.centsOff)} " +
                "conf=${note.confidence} frame=${note.atFrame} $correction"
        }
        emit(
            PlayedNote(
                midi = estimate.midi,
                atNanos = correction.correctedNanos,
                centsOff = estimate.centsOff,
                confidence = note.confidence,
            ),
        )
    }

    private fun applied(result: InputLatencyResult): InputLatencyResult {
        currentLatency = when (result) {
            is InputLatencyResult.Measured -> InputLatency(result.millis, InputLatency.Provenance.Measured)
            is InputLatencyResult.Unmeasurable -> ASSUMED_LATENCY
        }
        diag.event(
            TAG,
            "calibration -> $result; lat=${currentLatency.millis}ms src=${currentLatency.provenance}",
        )
        return result
    }

    private fun refused(reason: String): InputLatencyResult.Unmeasurable {
        diag.event(TAG, "calibration refused: $reason")
        return InputLatencyResult.Unmeasurable(reason)
    }

    private fun format(cents: Double): String = String.format(Locale.ROOT, "%+.1f", cents)

    public companion object {
        public const val DEFAULT_BUFFER_FRAMES: Int = 1_024
        public const val DEFAULT_MIN_CONFIDENCE: Float = 0.6f

        /** Stated, logged, and never presented as measured (docs/todos/measure-audio-latency.md). */
        public const val ASSUMED_LATENCY_MILLIS: Double = 60.0

        public val ASSUMED_LATENCY: InputLatency =
            InputLatency(ASSUMED_LATENCY_MILLIS, InputLatency.Provenance.Assumed)

        private const val TAG = "mic"
        private const val MAX_CONSECUTIVE_EMPTY_READS = 32
    }
}
