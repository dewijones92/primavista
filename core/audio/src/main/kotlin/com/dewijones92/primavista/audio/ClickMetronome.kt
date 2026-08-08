package com.dewijones92.primavista.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.dewijones92.primavista.common.Diag
import com.dewijones92.primavista.common.NoOpDiag
import com.dewijones92.primavista.score.Ticks
import com.dewijones92.primavista.score.TimeSignature
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Audible beat, driven by the Conductor through [onPosition] and by nothing else.
 * See .claude/CODE-NOTES.md.
 */
public class ClickMetronome(
    private val diag: Diag = NoOpDiag,
    public val sampleRate: Int = DEFAULT_SAMPLE_RATE,
) : Metronome {

    override var enabled: Boolean = true
        set(value) {
            val changed = field != value
            field = value
            if (changed) diag.event(TAG, "enabled=$value")
        }

    private val clicker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, CLICK_THREAD_NAME).apply { priority = Thread.MAX_PRIORITY }
    }

    @Volatile
    private var crossing: BeatCrossing? = null

    @Volatile
    private var running = false

    @Volatile
    private var released = false

    private val framesRendered = AtomicLong()

    /** Frames of click the audio device has actually played. See .claude/CODE-NOTES.md. */
    public val framesHeard: Long get() = framesRendered.get()

    private var tempoBpm: Int = DEFAULT_TEMPO_BPM
    private var time: TimeSignature = TimeSignature.FourFour
    private var beatTrack: AudioTrack? = null
    private var accentTrack: AudioTrack? = null

    override fun configure(tempoBpm: Int, time: TimeSignature, barStart: Ticks) {
        require(tempoBpm > 0) { "tempo must be positive, was $tempoBpm" }
        if (released) {
            diag.event(TAG, "configure refused: this metronome was released; build a new one")
            return
        }
        this.tempoBpm = tempoBpm
        this.time = time
        crossing = BeatCrossing(time, barStart)
        ensureTracks()
        running = true
        diag.event(
            TAG,
            "configured tempo=${tempoBpm}bpm time=${time.beats}/${time.beatUnit} " +
                "barStart=${barStart.value}ticks beatTicks=${crossing?.beatTicks} " +
                "(clicks are Conductor-driven, no internal timer)",
        )
    }

    override fun stop() {
        running = false
        crossing?.reset()
        diag.event(TAG, "stopped")
    }

    /**
     * Ticked with the Conductor's sampled position. Never blocks: the crossing decision is
     * arithmetic and the click is handed to [clicker].
     */
    override fun onPosition(position: Ticks) {
        if (!running) {
            diag.counted(TAG, "ticksWhileStopped")
            return
        }
        val beat = crossing?.crossed(position)
        if (beat == null) {
            diag.counted(TAG, "ticksBetweenBeats")
            return
        }
        if (!enabled) {
            diag.counted(TAG, "beatsMutedByToggle")
            return
        }
        val track = if (beat.isAccent) accentTrack else beatTrack
        if (track == null) {
            diag.counted(TAG, "beatsWithNoTrack")
            return
        }
        diag.counted(TAG, if (beat.isAccent) "accents" else "clicks")
        diag.state(TAG) {
            "beat=${beat.indexInBar + 1}/${time.beats} sinceBarStart=${beat.indexFromBarStart} " +
                "barStart=${crossing?.barStartTicks}ticks position=${position.value}ticks " +
                "tempo=${tempoBpm}bpm heard=${framesRendered.get()}frames"
        }
        clicker.execute { retrigger(track) }
    }

    /** Frees both click tracks and the click thread. */
    override fun release() {
        running = false
        released = true
        clicker.shutdownNow()
        runCatching { clicker.awaitTermination(SHUTDOWN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS) }
            .onFailure { diag.event(TAG, "click thread shutdown interrupted: ${it.message}") }
        listOfNotNull(beatTrack, accentTrack).forEach { track ->
            framesRendered.addAndGet(track.playbackHeadPosition.toLong())
            runCatching { track.stop() }.onFailure { diag.event(TAG, "track stop failed: ${it.message}") }
            track.release()
        }
        beatTrack = null
        accentTrack = null
        diag.event(TAG, "released heard=${framesRendered.get()}frames")
    }

    private fun retrigger(track: AudioTrack) {
        try {
            framesRendered.addAndGet(track.playbackHeadPosition.toLong())
            track.stop()
            val reloaded = track.reloadStaticData()
            if (reloaded != AudioTrack.SUCCESS) {
                diag.counted(TAG, "reloadFailures")
                diag.event(TAG, "click rewind failed rc=$reloaded state=${stateName(track.state)}")
            }
            track.play()
            diag.counted(TAG, "beatsPlayed")
        } catch (failure: IllegalStateException) {
            diag.counted(TAG, "clickFailures")
            diag.event(
                TAG,
                "click failed: ${failure.message} state=${stateName(track.state)} play=${track.playState}",
            )
        }
    }

    private fun ensureTracks() {
        if (beatTrack != null && accentTrack != null) return
        beatTrack = beatTrack ?: staticTrack(ClickSynth.render(sampleRate, accent = false), "beat")
        accentTrack = accentTrack ?: staticTrack(ClickSynth.render(sampleRate, accent = true), "accent")
        diag.event(
            TAG,
            "click tracks beat=${beatTrack != null} accent=${accentTrack != null} rate=${sampleRate}Hz",
        )
    }

    private fun staticTrack(samples: FloatArray, name: String): AudioTrack? {
        val bytes = samples.size * BYTES_PER_FLOAT
        val built = openTrack(bytes, name) ?: return null
        val refusal = loadStaticData(built, samples)
        if (refusal != null) {
            diag.event(TAG, "no $name click track: $refusal")
            built.release()
            return null
        }
        diag.event(
            TAG,
            "$name click track ready state=${stateName(built.state)} frames=${samples.size} " +
                "bytes=$bytes rate=${sampleRate}Hz",
        )
        return built
    }

    private fun openTrack(bufferBytes: Int, name: String): AudioTrack? = try {
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(bufferBytes)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
    } catch (failure: UnsupportedOperationException) {
        diag.event(TAG, "no $name click track at ${sampleRate}Hz: ${failure.message}")
        null
    } catch (failure: IllegalArgumentException) {
        diag.event(TAG, "no $name click track at ${sampleRate}Hz: ${failure.message}")
        null
    }

    /** Null when the track is ready to play; otherwise why it is not. See .claude/CODE-NOTES.md. */
    private fun loadStaticData(track: AudioTrack, samples: FloatArray): String? {
        if (track.state == AudioTrack.STATE_UNINITIALIZED) {
            return "state=${stateName(track.state)} at ${sampleRate}Hz"
        }
        val written = track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
        if (written != samples.size) return "wrote $written of ${samples.size} frames"
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            return "state=${stateName(track.state)} after ${samples.size} frames"
        }
        return null
    }

    public companion object {
        public const val DEFAULT_SAMPLE_RATE: Int = 48_000
        public const val DEFAULT_TEMPO_BPM: Int = 90
        public const val BYTES_PER_FLOAT: Int = 4

        private const val TAG = "audio.metronome"
        private const val CLICK_THREAD_NAME = "primavista-click"
        private const val SHUTDOWN_TIMEOUT_MILLIS = 200L
    }
}

/** See .claude/CODE-NOTES.md: `state=2` cost a session. */
private fun stateName(state: Int): String = when (state) {
    AudioTrack.STATE_INITIALIZED -> "INITIALIZED"
    AudioTrack.STATE_NO_STATIC_DATA -> "NO_STATIC_DATA"
    AudioTrack.STATE_UNINITIALIZED -> "UNINITIALIZED"
    else -> "UNKNOWN($state)"
}
