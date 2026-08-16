package com.dewijones92.primavista.audio

import com.dewijones92.primavista.pitch.MonophonicNoteTracker
import com.dewijones92.primavista.pitch.TrackedNote
import com.dewijones92.primavista.practice.AudioRoute
import com.dewijones92.primavista.practice.RouteKind
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random

const val FAKE_RATE = 48_000
const val FAKE_FRAMES_PER_READ = 1_024

val FAKE_ROUTE = AudioRoute(RouteKind.BuiltIn, "fake built-in mic")

/** About 2kHz at 48kHz, which is roughly the calibration click's own pitch. */
const val TONE_RADIANS_PER_FRAME = 0.26

/**
 * Far enough in that the calibrator's priming reads have gone by. A click planted before them is
 * consumed unheard, which reads as "the mic never heard it" and is a confusing way to fail.
 */
const val CLICK_FRAME = 12L * FAKE_FRAMES_PER_READ + 3L * FAKE_FRAMES_PER_READ + 100L

/** A [PcmCapture] that hands out frames on demand and can plant a click at a known frame. */
class FakeCapture(
    override val sampleRate: Int = FAKE_RATE,
    private val framesPerRead: Int = FAKE_FRAMES_PER_READ,
    private val maxReads: Int = Int.MAX_VALUE,
    private val settlesTo: TimestampProvenance = TimestampProvenance.DeviceReported,
    private val settlesAfterReads: Int = 0,
    var clickAtFrame: Long? = null,
    private val clickAmplitude: Float = 0.8f,
    private val clickRiseFrames: Int = 0,
    /** How long the click sounds for. The default is an impulse; real clicks last milliseconds. */
    private val clickFrames: Int = 1,
    private val noiseAmplitude: Float = 0f,
    private val refusal: CaptureStart.Refused? = null,
    private val route: AudioRoute = FAKE_ROUTE,
    private val rerouteTo: AudioRoute? = null,
    private val rerouteAfterReads: Int = Int.MAX_VALUE,
) : PcmCapture {

    /** Android reroutes a live capture when a headset connects, so the fake can too. */
    override var currentRoute: AudioRoute = route
        private set

    /**
     * Modelled on the real adapter: a capture opens on an extrapolated timebase and only upgrades
     * once the device has reported a timestamp, several reads in. The fake used to claim
     * [TimestampProvenance.DeviceReported] from the very first call — a thing
     * `AudioRecordPcmCapture` cannot do — which is how nine passing tests covered a feature that
     * refused on every real device.
     */
    override val timestampProvenance: TimestampProvenance
        get() = if (reads >= settlesAfterReads) settlesTo else TimestampProvenance.ExtrapolatedFromStart

    var starts = 0
        private set
    var stops = 0
        private set
    var releases = 0
        private set

    private var position = 0L
    private var reads = 0
    private val noise = Random(NOISE_SEED)

    override fun start(): CaptureStart {
        refusal?.let { return it }
        starts++
        return CaptureStart.Started(sampleRate, "FAKE", timestampProvenance, route)
    }

    override fun stop() {
        stops++
    }

    override fun release() {
        releases++
    }

    override fun frameTimestampNanos(frame: Long): Long =
        FrameTimebase.framesToNanos(frame, sampleRate)

    override fun read(into: FloatArray): CaptureRead {
        if (reads >= maxReads) return CaptureRead(0, position)
        reads++
        if (rerouteTo != null && reads > rerouteAfterReads) currentRoute = rerouteTo
        val first = position
        val frames = minOf(framesPerRead, into.size)
        for (index in into.indices) {
            into[index] = if (noiseAmplitude > 0f) noise.nextFloat() * 2f * noiseAmplitude - noiseAmplitude else 0f
        }
        clickAtFrame?.let { click ->
            val start = (click - first).toInt()
            for (step in 0 until maxOf(clickFrames, clickRiseFrames + 1)) {
                val offset = start + step
                if (offset !in 0 until frames) continue
                val rising = if (step <= clickRiseFrames) (step + 1f) / (clickRiseFrames + 1) else 1f
                // A sounding click is a wave, not a plateau: its median magnitude is what a
                // detector using the in-buffer median mistakes for the room.
                val wave = kotlin.math.sin(step * TONE_RADIANS_PER_FRAME).toFloat()
                into[offset] = clickAmplitude * rising * (if (clickFrames > 1) wave else 1f)
            }
        }
        position = first + framesPerRead
        return CaptureRead(frames, first)
    }

    private companion object {
        const val NOISE_SEED = 20_260_807L
    }
}

/** Emits a queued batch of notes per push, then nothing. */
/** One argument per push, so a test can put a note either side of something changing mid-stream. */
class FakeTracker(
    vararg pushes: List<TrackedNote>,
    override val sampleRate: Int = FAKE_RATE,
) : MonophonicNoteTracker {
    private val queued = ArrayDeque(pushes.toList())

    var resets = 0
        private set

    override fun push(pcm: FloatArray, frames: Int): List<TrackedNote> =
        queued.removeFirstOrNull() ?: emptyList()

    override fun reset() {
        resets++
    }
}

/** Blocks its first push until released, so a second listener can be attempted meanwhile. */
class GatedTracker(private val entered: CountDownLatch, private val release: CountDownLatch) :
    MonophonicNoteTracker {

    override val sampleRate: Int = FAKE_RATE

    override fun push(pcm: FloatArray, frames: Int): List<TrackedNote> {
        entered.countDown()
        release.await(GATE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        return emptyList()
    }

    override fun reset() = Unit

    private companion object {
        const val GATE_TIMEOUT_SECONDS = 5L
    }
}
