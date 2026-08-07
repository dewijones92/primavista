package com.dewijones92.primavista.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTimestamp
import android.media.AudioTrack
import com.dewijones92.primavista.common.Diag
import com.dewijones92.primavista.common.NoOpDiag
import com.dewijones92.primavista.pitch.Tuning
import com.dewijones92.primavista.score.Midi
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Streaming [TonePlayer] over one [AudioTrack]. Mixing and the frame count live in [ToneMixer]
 * so both are JVM-testable. See .claude/CODE-NOTES.md.
 */
public class AudioTrackTonePlayer(
    private val diag: Diag = NoOpDiag,
    public val sampleRate: Int = DEFAULT_SAMPLE_RATE,
) : TonePlayer, PlaybackAnchor {

    private val lock = ReentrantLock()
    private val mixer = ToneMixer(sampleRate)

    @Volatile
    private var track: AudioTrack? = null

    @Volatile
    private var renderer: Thread? = null

    @Volatile
    private var running = false

    override fun play(midi: Midi, durationMillis: Long) {
        playChord(listOf(midi), durationMillis)
    }

    override fun playChord(midis: List<Midi>, durationMillis: Long) {
        if (midis.isEmpty()) {
            diag.event(TAG, "playChord ignored: empty chord")
            return
        }
        val output = ensureStarted() ?: return
        val durationFrames = max(1, (durationMillis * sampleRate / MILLIS_PER_SECOND).toInt())
        val equalPowerAmplitude = ToneVoice.DEFAULT_AMPLITUDE / sqrt(midis.size.toDouble())
        val added = midis.map { midi ->
            ToneVoice(sampleRate, Tuning.hertzOf(midi.number).value, durationFrames, equalPowerAmplitude)
        }
        val startsAtFrame = mixer.add(added)
        diag.counted(TAG, "notes", midis.size)
        diag.event(
            TAG,
            "play midis=${midis.map { it.number }} dur=${durationMillis}ms rate=${sampleRate}Hz " +
                "startsAtFrame=$startsAtFrame sounding=${mixer.soundingCount} state=${output.playState}",
        )
    }

    override fun stopAll() {
        val released = mixer.beginReleaseAll()
        diag.event(TAG, "stopAll released=$released voices (ramped, not cut)")
    }

    /** Frees the [AudioTrack]; safe to call more than once. */
    override fun release() {
        running = false
        val thread = renderer
        renderer = null
        thread?.interrupt()
        thread?.join(JOIN_TIMEOUT_MILLIS)
        val output = track
        track = null
        if (output != null) {
            runCatching { output.stop() }.onFailure { diag.event(TAG, "stop failed: ${it.message}") }
            output.release()
        }
        mixer.clear()
        diag.event(TAG, "released framesWritten=${mixer.framesRendered}")
    }

    /**
     * The anchor frame is exact; what remains is the device timestamp's own extrapolation error,
     * which is what [PlaybackMoment.uncertaintyMillis] carries.
     */
    override fun lastPlayback(): PlaybackMoment? {
        val output = track ?: return null
        val anchorFrame = mixer.anchorFrame ?: return null
        val timestamp = AudioTimestamp()
        if (!output.getTimestamp(timestamp)) {
            diag.counted(TAG, "outputTimestampMiss")
            return null
        }
        val extrapolationFrames = abs(anchorFrame - timestamp.framePosition)
        val uncertaintyMillis = FrameTimebase.framesToMillis(1L, sampleRate) +
            FrameTimebase.framesToMillis(extrapolationFrames, sampleRate) * ASSUMED_CLOCK_TOLERANCE
        val nanos = timestamp.nanoTime +
            FrameTimebase.framesToNanos(anchorFrame - timestamp.framePosition, sampleRate)
        diag.state(TAG) {
            "anchorFrame=$anchorFrame deviceFrame=${timestamp.framePosition} " +
                "extrapolated=${extrapolationFrames}frames uncertainty=${uncertaintyMillis}ms " +
                "(excludes AudioTimestamp's own accuracy, which the device does not report)"
        }
        return PlaybackMoment(nanos, uncertaintyMillis)
    }

    private fun ensureStarted(): AudioTrack? {
        track?.let { return it }
        return lock.withLock {
            track ?: buildTrack()?.also { built ->
                track = built
                running = true
                renderer = Thread({ renderLoop(built) }, RENDER_THREAD_NAME).apply {
                    priority = Thread.MAX_PRIORITY
                    start()
                }
                diag.event(
                    TAG,
                    "opened rate=${sampleRate}Hz encoding=float mono " +
                        "buffer=${built.bufferSizeInFrames}frames render=${RENDER_FRAMES}frames",
                )
            }
        }
    }

    private fun buildTrack(): AudioTrack? {
        val minBytes = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, ENCODING)
        if (minBytes <= 0) {
            diag.event(TAG, "no AudioTrack: getMinBufferSize=$minBytes for ${sampleRate}Hz float mono")
            return null
        }
        val bufferBytes = max(minBytes, RENDER_FRAMES * BYTES_PER_FLOAT * BUFFER_RENDER_MULTIPLE)
        val built = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(ENCODING)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
        } catch (failure: UnsupportedOperationException) {
            diag.event(TAG, "no AudioTrack at ${sampleRate}Hz: ${failure.message}")
            return null
        } catch (failure: IllegalArgumentException) {
            diag.event(TAG, "no AudioTrack at ${sampleRate}Hz: ${failure.message}")
            return null
        }
        if (built.state != AudioTrack.STATE_INITIALIZED) {
            diag.event(TAG, "AudioTrack uninitialised state=${built.state}")
            built.release()
            return null
        }
        built.play()
        return built
    }

    private fun renderLoop(output: AudioTrack) {
        val buffer = FloatArray(RENDER_FRAMES)
        while (running) {
            val frames = mixer.render(buffer)
            val written = output.write(buffer, 0, frames, AudioTrack.WRITE_BLOCKING)
            if (written < 0) {
                diag.event(TAG, "write failed code=$written; stopping renderer")
                running = false
            } else if (written != frames) {
                diag.counted(TAG, "shortWrites")
                diag.event(TAG, "short write $written of $frames frames; the frame anchor now lags")
            }
        }
        diag.event(TAG, "renderer exited framesWritten=${mixer.framesRendered}")
    }

    public companion object {
        public const val DEFAULT_SAMPLE_RATE: Int = 48_000
        public const val RENDER_FRAMES: Int = 512
        public const val BYTES_PER_FLOAT: Int = 4

        /** Stated, not measured: a typical audio crystal's drift, used only to widen uncertainty. */
        public const val ASSUMED_CLOCK_TOLERANCE: Double = 1e-4

        private const val TAG = "audio.tone"
        private const val ENCODING = AudioFormat.ENCODING_PCM_FLOAT
        private const val MILLIS_PER_SECOND = 1_000L
        private const val BUFFER_RENDER_MULTIPLE = 4
        private const val JOIN_TIMEOUT_MILLIS = 250L
        private const val RENDER_THREAD_NAME = "primavista-tone"
    }
}
