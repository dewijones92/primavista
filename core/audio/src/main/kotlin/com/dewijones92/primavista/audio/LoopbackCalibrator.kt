package com.dewijones92.primavista.audio

import com.dewijones92.primavista.common.Diag
import com.dewijones92.primavista.common.NoOpDiag
import com.dewijones92.primavista.practice.AudioRoute
import com.dewijones92.primavista.score.Midi
import kotlin.math.abs

/** What was measured, and which path it was measured on — a figure without its route is unusable. */
public data class LoopbackMeasurement(val route: AudioRoute, val result: InputLatencyResult)

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
    private val mediaVolume: MediaVolume = MediaVolume { null },
) {
    public fun measure(player: TonePlayer): LoopbackMeasurement =
        when (val opened = capture.start()) {
            is CaptureStart.Refused -> LoopbackMeasurement(
                AudioRoute.Unidentified,
                unmeasurable("the microphone would not open: ${opened.reason}"),
            )

            is CaptureStart.Started -> try {
                LoopbackMeasurement(opened.route, run(player, opened))
            } finally {
                capture.stop()
            }
        }

    /**
     * The priming reads are not only there to settle the stream: they are the only audio this app
     * can be certain contains no click, so they are what the room is measured from.
     */
    private fun run(player: TonePlayer, opened: CaptureStart.Started): InputLatencyResult {
        val buffer = FloatArray(bufferFrames)
        var room = LatencyCalibration.UNKNOWN_ROOM_NOISE
        repeat(PRIME_READS) {
            val read = capture.read(buffer)
            if (read.frames > 0) room = maxOf(room, LatencyCalibration.roomNoiseOf(buffer, read.frames))
        }
        diag.event(
            TAG,
            "primed ${PRIME_READS}reads room=$room timebase=${capture.timestampProvenance} " +
                "click=midi$CLICK_MIDI/${CLICK_MILLIS}ms window=${bufferFrames}frames",
        )
        val requestNanos = clock.nowNanos()
        player.play(Midi(CLICK_MIDI), CLICK_MILLIS)
        return when (val outcome = searchForClick(buffer, room)) {
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
        // Read LIVE, not from `opened`: that snapshot is taken inside start(), before a single
        // frame has been read, so it is ExtrapolatedFromStart on every device for ever. Reading it
        // there made this whole feature refuse unconditionally. See .claude/CODE-NOTES.md.
        val timebase = capture.timestampProvenance
        if (anchor == null || timebase != TimestampProvenance.DeviceReported) {
            return unmeasurable(
                "a click was heard at ${roundTripMillis}ms round trip, but a round trip is not an " +
                    "input latency: playbackAnchor=${anchor != null} captureTimebase=$timebase",
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

    /**
     * Reports the **loudest** buffer it saw rather than the last one. The last buffer is usually
     * silence after the click has passed, so a refusal quoting it says nothing about the attempt.
     */
    private fun searchForClick(buffer: FloatArray, room: Float): Loopback {
        var bestReason = "capture produced no frames at all"
        var bestPeak = -1f
        repeat(CAPTURE_READS) {
            val read = capture.read(buffer)
            if (read.frames <= 0) return@repeat
            when (val search = LatencyCalibration.findClick(buffer, read.frames, room)) {
                is ClickSearch.Found -> return Loopback.Heard(read.firstFrame + search.frame, search)
                is ClickSearch.NotFound -> {
                    val peak = peakOf(buffer, read.frames)
                    if (peak > bestPeak) {
                        bestPeak = peak
                        bestReason = search.reason
                    }
                }
            }
        }
        return Loopback.Silent(
            "no click in ${CAPTURE_READS * bufferFrames} frames after playing midi=$CLICK_MIDI " +
                "(room=$room, ${volumeText()}); loudest buffer: $bestReason",
        )
    }

    /**
     * Named in every refusal, because the commonest reason a phone cannot hear itself is that its
     * own volume is down — and that is the one cause the person holding it can fix in a second.
     */
    private fun volumeText(): String {
        val fraction = mediaVolume.fraction() ?: return "media volume unknown"
        val percent = (fraction * PERCENT).toInt()
        val advice = if (fraction < ENOUGH_VOLUME) " — turn the media volume up and try again" else ""
        return "media volume $percent%$advice"
    }

    private fun peakOf(pcm: FloatArray, frames: Int): Float {
        var peak = 0f
        for (index in 0 until minOf(frames, pcm.size)) peak = maxOf(peak, abs(pcm[index]))
        return peak
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

        /**
         * Short enough to sit inside one analysis buffer. At 48kHz a 1024-frame window is 21ms, and
         * the old 40ms click filled every window it appeared in — which drove the in-buffer median
         * up with the click and had the detector reject its own stimulus as room noise.
         */
        private const val CLICK_MILLIS = 10L
        private const val MAX_PLAUSIBLE_LATENCY_MILLIS = 500.0
        private const val PERCENT = 100
        private const val ENOUGH_VOLUME = 0.7
    }
}
