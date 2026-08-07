package com.dewijones92.primavista.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/**
 * One synthesised piano-ish note: a few harmonics under a decaying envelope.
 * See .claude/CODE-NOTES.md.
 */
public class ToneVoice(
    sampleRate: Int,
    public val frequencyHertz: Double,
    durationFrames: Int,
    private val amplitude: Double = DEFAULT_AMPLITUDE,
) {
    init {
        require(sampleRate > 0) { "sample rate must be positive, was $sampleRate" }
        require(frequencyHertz > 0.0) { "frequency must be positive, was $frequencyHertz" }
        require(durationFrames > 0) { "duration must be at least one frame, was $durationFrames" }
    }

    private val nyquist = sampleRate / 2.0
    private val partialCount = HARMONIC_AMPLITUDES.size
    private val phase = DoubleArray(partialCount)

    private val phaseIncrement = DoubleArray(partialCount) { index ->
        TWO_PI * frequencyHertz * (index + 1) / sampleRate
    }

    private val level = DoubleArray(partialCount) { index ->
        if (frequencyHertz * (index + 1) >= nyquist) 0.0 else HARMONIC_AMPLITUDES[index]
    }

    private val decayPerFrame = DoubleArray(partialCount) { index ->
        exp(-(index + 1).toDouble() / (FUNDAMENTAL_DECAY_SECONDS * sampleRate))
    }

    private val attackFrames = max(1, (ATTACK_SECONDS * sampleRate).toInt())
    private val releaseFrames = max(1, (RELEASE_SECONDS * sampleRate).toInt())

    private var endFrame = durationFrames
    private var frame = 0

    public val isFinished: Boolean get() = frame >= endFrame

    public val framesRemaining: Int get() = max(0, endFrame - frame)

    /** Brings the note down over [RELEASE_SECONDS]; a hard cut clicks louder than the note. */
    public fun beginRelease() {
        val rampedEnd = frame + releaseFrames
        if (rampedEnd < endFrame) endFrame = rampedEnd
    }

    /** Adds this voice into [out] for up to [count] frames, advancing its own position. */
    public fun mixInto(out: FloatArray, count: Int) {
        val limit = minOf(count, out.size)
        var index = 0
        while (index < limit && frame < endFrame) {
            out[index] += (partialSum() * envelopeAt(frame) * amplitude).toFloat()
            index++
            frame++
        }
    }

    private fun partialSum(): Double {
        var sum = 0.0
        for (partial in 0 until partialCount) {
            if (level[partial] == 0.0) continue
            sum += level[partial] * sin(phase[partial])
            phase[partial] += phaseIncrement[partial]
            level[partial] *= decayPerFrame[partial]
        }
        return sum
    }

    private fun envelopeAt(at: Int): Double {
        val attack = if (at < attackFrames) at.toDouble() / attackFrames else 1.0
        val remaining = endFrame - at
        val release = if (remaining < releaseFrames) remaining.toDouble() / releaseFrames else 1.0
        return attack * release
    }

    public companion object {
        public const val TWO_PI: Double = 2.0 * PI
        public const val DEFAULT_AMPLITUDE: Double = 0.32
        public const val ATTACK_SECONDS: Double = 0.004
        public const val RELEASE_SECONDS: Double = 0.020
        public const val FUNDAMENTAL_DECAY_SECONDS: Double = 1.1

        /** Fundamental plus four partials; upper partials also decay faster, as a string's do. */
        private val HARMONIC_AMPLITUDES = doubleArrayOf(1.0, 0.42, 0.24, 0.11, 0.05)
    }
}
