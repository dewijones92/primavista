package com.dewijones92.primavista.audio

import com.dewijones92.primavista.common.Diag
import com.dewijones92.primavista.common.NoOpDiag
import com.dewijones92.primavista.score.Midi

/**
 * Plays a click, listens for it, and reports the input latency between the two — or refuses and
 * says why. Separate from [MicPitchAnswerSource] because measuring the path and reading notes off
 * it are different jobs. See .claude/CODE-NOTES.md.
 */
public class LoopbackCalibrator(
    private val capture: PcmCapture,
    private val clock: MonotonicClock = SystemMonotonicClock,
    private val diag: Diag = NoOpDiag,
    private val bufferFrames: Int = MicPitchAnswerSource.DEFAULT_BUFFER_FRAMES,
) {
    public fun measure(player: TonePlayer): InputLatencyResult =
        when (val opened = capture.start()) {
            is CaptureStart.Refused -> unmeasurable("the microphone would not open: ${opened.reason}")

            is CaptureStart.Started -> try {
                run(player, opened)
            } finally {
                capture.stop()
            }
        }

    private fun run(player: TonePlayer, opened: CaptureStart.Started): InputLatencyResult {
        val buffer = FloatArray(bufferFrames)
        repeat(PRIME_READS) { capture.read(buffer) }
        val requestNanos = clock.nowNanos()
        player.play(Midi(CLICK_MIDI), CLICK_MILLIS)
        return when (val outcome = searchForClick(buffer)) {
            is Loopback.Silent -> unmeasurable(outcome.reason)
            is Loopback.Heard -> offsetFrom(player, opened, outcome, requestNanos)
        }
    }

    private fun offsetFrom(
        player: TonePlayer,
        opened: CaptureStart.Started,
        heard: Loopback.Heard,
        requestNanos: Long,
    ): InputLatencyResult {
        val onsetNanos = capture.frameTimestampNanos(heard.absoluteFrame)
        val anchor = (player as? PlaybackAnchor)?.lastPlayback()
        val roundTripMillis = FrameTimebase.nanosToMillis(onsetNanos - requestNanos)
        if (anchor == null || opened.timestampProvenance != TimestampProvenance.DeviceReported) {
            return unmeasurable(
                "a click was heard at ${roundTripMillis}ms round trip, but a round trip is not an " +
                    "input latency: playbackAnchor=${anchor != null} " +
                    "captureTimebase=${opened.timestampProvenance}",
            )
        }
        val millis = FrameTimebase.nanosToMillis(onsetNanos - anchor.nanos)
        if (millis <= 0.0 || millis > MAX_PLAUSIBLE_LATENCY_MILLIS) {
            return unmeasurable("loopback offset ${millis}ms is outside 0..$MAX_PLAUSIBLE_LATENCY_MILLIS")
        }
        val riseMillis = FrameTimebase.framesToMillis(heard.click.riseFrames.toLong(), opened.sampleRate)
        val uncertaintyMillis = anchor.uncertaintyMillis + riseMillis
        val confidence = (1.0 - uncertaintyMillis / millis).coerceIn(0.0, 1.0)
        diag.event(
            TAG,
            "loopback measured lat=${millis}ms conf=$confidence rate=${opened.sampleRate}Hz " +
                "click[${heard.click}] anchorUncertainty=${anchor.uncertaintyMillis}ms " +
                "rise=${riseMillis}ms requestToOnset=${roundTripMillis}ms",
        )
        return InputLatencyResult.Measured(millis, confidence)
    }

    private fun searchForClick(buffer: FloatArray): Loopback {
        var lastReason = "capture produced no frames at all"
        repeat(CAPTURE_READS) {
            val read = capture.read(buffer)
            if (read.frames <= 0) {
                lastReason = "capture returned an empty read"
                return@repeat
            }
            when (val search = LatencyCalibration.findClick(buffer, read.frames)) {
                is ClickSearch.Found -> return Loopback.Heard(read.firstFrame + search.frame, search)
                is ClickSearch.NotFound -> lastReason = search.reason
            }
        }
        return Loopback.Silent(
            "no click in ${CAPTURE_READS * bufferFrames} frames after playing midi=$CLICK_MIDI; " +
                "last buffer: $lastReason",
        )
    }

    private fun unmeasurable(reason: String): InputLatencyResult.Unmeasurable {
        diag.event(TAG, "latency not measured: $reason")
        return InputLatencyResult.Unmeasurable(reason)
    }

    private sealed interface Loopback {
        data class Heard(val absoluteFrame: Long, val click: ClickSearch.Found) : Loopback

        data class Silent(val reason: String) : Loopback
    }

    public companion object {
        private const val TAG = "mic.calibration"
        private const val PRIME_READS = 12
        private const val CAPTURE_READS = 48
        private const val CLICK_MIDI = 96
        private const val CLICK_MILLIS = 40L
        private const val MAX_PLAUSIBLE_LATENCY_MILLIS = 500.0
    }
}
