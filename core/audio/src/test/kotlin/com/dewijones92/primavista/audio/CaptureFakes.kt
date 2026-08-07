package com.dewijones92.primavista.audio

import com.dewijones92.primavista.pitch.MonophonicNoteTracker
import com.dewijones92.primavista.pitch.TrackedNote
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random

const val FAKE_RATE = 48_000
const val FAKE_FRAMES_PER_READ = 1_024

/** A [PcmCapture] that hands out frames on demand and can plant a click at a known frame. */
class FakeCapture(
    override val sampleRate: Int = FAKE_RATE,
    private val framesPerRead: Int = FAKE_FRAMES_PER_READ,
    private val maxReads: Int = Int.MAX_VALUE,
    private val timestampProvenance: TimestampProvenance = TimestampProvenance.DeviceReported,
    var clickAtFrame: Long? = null,
    private val clickAmplitude: Float = 0.8f,
    private val clickRiseFrames: Int = 0,
    private val noiseAmplitude: Float = 0f,
    private val refusal: CaptureStart.Refused? = null,
) : PcmCapture {

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
        return CaptureStart.Started(sampleRate, "FAKE", timestampProvenance)
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
        val first = position
        val frames = minOf(framesPerRead, into.size)
        for (index in into.indices) {
            into[index] = if (noiseAmplitude > 0f) noise.nextFloat() * 2f * noiseAmplitude - noiseAmplitude else 0f
        }
        clickAtFrame?.let { click ->
            val start = (click - first).toInt()
            for (step in 0..clickRiseFrames) {
                val offset = start + step
                if (offset in 0 until frames) {
                    into[offset] = clickAmplitude * (step + 1) / (clickRiseFrames + 1)
                }
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
class FakeTracker(
    notes: List<TrackedNote>,
    override val sampleRate: Int = FAKE_RATE,
) : MonophonicNoteTracker {
    private val queued = ArrayDeque(listOf(notes))

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
