package com.dewijones92.primavista.audio

import com.dewijones92.primavista.audio.ToneVoice.Companion.TWO_PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/** Renders the metronome's percussive tick. See .claude/CODE-NOTES.md. */
public object ClickSynth {
    public fun render(sampleRate: Int, accent: Boolean): FloatArray {
        require(sampleRate > 0) { "sample rate must be positive, was $sampleRate" }
        val frames = max(1, (CLICK_SECONDS * sampleRate).toInt())
        val tailFrames = max(1, (TAIL_SECONDS * sampleRate).toInt())
        val fundamental = if (accent) ACCENT_HERTZ else BEAT_HERTZ
        val amplitude = if (accent) ACCENT_AMPLITUDE else BEAT_AMPLITUDE
        val decay = exp(-1.0 / (DECAY_SECONDS * sampleRate))
        val out = FloatArray(frames)
        var level = 1.0
        for (frame in 0 until frames) {
            val time = frame.toDouble() / sampleRate
            val body = sin(TWO_PI * fundamental * time) +
                UPPER_PARTIAL_LEVEL * sin(TWO_PI * fundamental * UPPER_PARTIAL_RATIO * time)
            val remaining = frames - frame
            val tail = if (remaining < tailFrames) remaining.toDouble() / tailFrames else 1.0
            out[frame] = (body * level * tail * amplitude).toFloat()
            level *= decay
        }
        return out
    }

    private const val CLICK_SECONDS = 0.030
    private const val TAIL_SECONDS = 0.004
    private const val DECAY_SECONDS = 0.006
    private const val BEAT_HERTZ = 1_600.0
    private const val ACCENT_HERTZ = 2_400.0
    private const val BEAT_AMPLITUDE = 0.30
    private const val ACCENT_AMPLITUDE = 0.45
    private const val UPPER_PARTIAL_RATIO = 2.7
    private const val UPPER_PARTIAL_LEVEL = 0.35
}
