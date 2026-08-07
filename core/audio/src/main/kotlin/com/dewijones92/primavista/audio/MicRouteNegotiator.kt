package com.dewijones92.primavista.audio

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import com.dewijones92.primavista.common.Diag
import kotlin.math.max

/**
 * Picks the least-processed microphone route that will actually open, and logs every attempt.
 * See .claude/CODE-NOTES.md.
 */
internal class MicRouteNegotiator(
    private val diag: Diag,
    private val audioManager: AudioManager?,
) {
    sealed interface Outcome {
        data class Opened(
            val record: AudioRecord,
            val sourceName: String,
            val sampleRate: Int,
            val bufferFrames: Int,
        ) : Outcome

        data object PermissionDenied : Outcome

        data class NoneUsable(val reason: String) : Outcome
    }

    private var sawPermissionDenial = false

    fun open(sampleRates: List<Int>): Outcome {
        sawPermissionDenial = false
        val sources = candidateSources()
        for (source in sources) {
            for (rate in sampleRates) {
                val record = openOne(source, rate) ?: continue
                return Outcome.Opened(record, source.name, rate, record.bufferSizeInFrames)
            }
        }
        if (sawPermissionDenial) {
            diag.event(TAG, "capture denied: the platform rejected every source as unpermitted")
            return Outcome.PermissionDenied
        }
        val reason = "no source in ${sources.map { it.name }} opened at $sampleRates"
        diag.event(TAG, "capture unavailable: $reason")
        return Outcome.NoneUsable(reason)
    }

    /**
     * UNPROCESSED first: the default MIC path applies AGC, noise suppression and often a
     * high-pass, all of which mangle a musical signal.
     */
    private fun candidateSources(): List<AudioSource> {
        val support = audioManager?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)
        return when {
            audioManager == null -> {
                diag.event(TAG, "UNPROCESSED support unverified: no AudioManager supplied; attempting it")
                AudioSource.entries
            }

            support == SUPPORTED -> AudioSource.entries

            else -> {
                diag.event(
                    TAG,
                    "UNPROCESSED unsupported (PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED=$support); " +
                        "falling back to VOICE_RECOGNITION then MIC, so expect device processing",
                )
                AudioSource.entries.filterNot { it == AudioSource.UNPROCESSED }
            }
        }
    }

    private fun openOne(source: AudioSource, rate: Int): AudioRecord? {
        val minBytes = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, ENCODING)
        if (minBytes <= 0) {
            diag.event(TAG, "${source.name}@${rate}Hz rejected: getMinBufferSize=$minBytes")
            return null
        }
        val candidate = buildRecord(source, rate, max(minBytes * BUFFER_MULTIPLE, MIN_BUFFER_BYTES))
            ?: return null
        if (candidate.state != AudioRecord.STATE_INITIALIZED) {
            diag.event(TAG, "${source.name}@${rate}Hz uninitialised state=${candidate.state}")
            candidate.release()
            return null
        }
        return startOrRelease(candidate, source, rate)
    }

    private fun buildRecord(source: AudioSource, rate: Int, bufferBytes: Int): AudioRecord? {
        val attempt = "${source.name}@${rate}Hz"
        return try {
            AudioRecord.Builder()
                .setAudioSource(source.androidSource)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(ENCODING)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(bufferBytes)
                .build()
        } catch (failure: UnsupportedOperationException) {
            diag.event(TAG, "$attempt rejected: ${failure.message}")
            null
        } catch (failure: IllegalArgumentException) {
            diag.event(TAG, "$attempt rejected: ${failure.message}")
            null
        } catch (failure: SecurityException) {
            sawPermissionDenial = true
            diag.event(TAG, "$attempt denied: ${failure.message} (RECORD_AUDIO not granted?)")
            null
        }
    }

    private fun startOrRelease(candidate: AudioRecord, source: AudioSource, rate: Int): AudioRecord? {
        try {
            candidate.startRecording()
        } catch (failure: IllegalStateException) {
            diag.event(TAG, "${source.name}@${rate}Hz would not start: ${failure.message}")
            candidate.release()
            return null
        } catch (failure: SecurityException) {
            sawPermissionDenial = true
            diag.event(TAG, "${source.name}@${rate}Hz denied on start: ${failure.message}")
            candidate.release()
            return null
        }
        if (candidate.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            diag.event(TAG, "${source.name}@${rate}Hz not recording state=${candidate.recordingState}")
            candidate.release()
            return null
        }
        return candidate
    }

    /** Ordered best-first for a musical signal. */
    enum class AudioSource(val androidSource: Int) {
        UNPROCESSED(MediaRecorder.AudioSource.UNPROCESSED),
        VOICE_RECOGNITION(MediaRecorder.AudioSource.VOICE_RECOGNITION),
        MIC(MediaRecorder.AudioSource.MIC),
    }

    private companion object {
        const val TAG = "audio.capture"
        const val ENCODING = AudioFormat.ENCODING_PCM_FLOAT
        const val BUFFER_MULTIPLE = 4
        const val MIN_BUFFER_BYTES = 8_192
        const val SUPPORTED = "true"
    }
}
