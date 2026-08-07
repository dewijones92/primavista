package com.dewijones92.primavista.pitch

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.random.Random

/**
 * Synthesised test material. The whole point of `:lib:pitch` being pure JVM is that its
 * correctness can be asserted against signals whose true pitch and true onset frames are known
 * exactly, with no device and no recording involved.
 */
internal object Signals {

    const val SAMPLE_RATE: Int = 44_100

    /** Notes get a short attack ramp so a test signal is a note rather than a click. */
    const val ATTACK_FRAMES: Int = 128
    const val RELEASE_FRAMES: Int = 256

    fun frames(seconds: Double, sampleRate: Int = SAMPLE_RATE): Int = (seconds * sampleRate).toInt()

    fun silence(frames: Int): FloatArray = FloatArray(frames)

    fun sine(
        hertz: Double,
        frames: Int,
        sampleRate: Int = SAMPLE_RATE,
        amplitude: Double = 0.3,
        phase: Double = 0.0,
    ): FloatArray {
        val out = FloatArray(frames)
        val step = 2 * PI * hertz / sampleRate
        for (n in 0 until frames) {
            out[n] = (amplitude * sin(phase + step * n)).toFloat()
        }
        return out
    }

    /** A fundamental plus partials at 2f, 3f, … in [partials], relative to a unit fundamental. */
    fun withHarmonics(
        fundamental: Double,
        partials: DoubleArray,
        frames: Int,
        sampleRate: Int = SAMPLE_RATE,
        amplitude: Double = 0.3,
    ): FloatArray {
        val out = sine(fundamental, frames, sampleRate, amplitude)
        partials.forEachIndexed { index, relative ->
            val partial = sine(fundamental * (index + 2), frames, sampleRate, amplitude * relative)
            for (n in 0 until frames) {
                out[n] += partial[n]
            }
        }
        return out
    }

    /**
     * Frequency-modulated tone. Integrating the instantaneous frequency rather than modulating a
     * phase argument keeps the vibrato's mean pitch exactly [centreHertz].
     */
    fun vibrato(
        centreHertz: Double,
        depthCents: Double,
        rateHertz: Double,
        frames: Int,
        sampleRate: Int = SAMPLE_RATE,
        amplitude: Double = 0.3,
    ): FloatArray {
        val out = FloatArray(frames)
        var phase = 0.0
        for (n in 0 until frames) {
            out[n] = (amplitude * sin(phase)).toFloat()
            val wobble = depthCents * sin(2 * PI * rateHertz * n / sampleRate)
            val instantaneous = Hertz(centreHertz).shiftedByCents(wobble).value
            phase += 2 * PI * instantaneous / sampleRate
        }
        return out
    }

    /** Applies an attack and release ramp in place, so the signal has a rising edge to find. */
    fun shaped(signal: FloatArray, attackFrames: Int = ATTACK_FRAMES): FloatArray {
        require(attackFrames > 0) { "attackFrames must be positive: $attackFrames" }
        val attack = minOf(attackFrames, signal.size)
        for (n in 0 until attack) {
            signal[n] = (signal[n] * n / attack)
        }
        val release = minOf(RELEASE_FRAMES, signal.size)
        for (n in 0 until release) {
            val index = signal.size - 1 - n
            signal[index] = (signal[index] * n / release)
        }
        return signal
    }

    fun note(
        hertz: Double,
        frames: Int,
        sampleRate: Int = SAMPLE_RATE,
        amplitude: Double = 0.3,
        attackFrames: Int = ATTACK_FRAMES,
    ): FloatArray = shaped(sine(hertz, frames, sampleRate, amplitude), attackFrames)

    /**
     * A struck note: attack ramp, then an exponential decay that never reaches zero. What a
     * microphone actually delivers, unlike the digital-silence gap the older fixtures use.
     */
    fun struck(
        hertz: Double,
        frames: Int,
        sampleRate: Int = SAMPLE_RATE,
        amplitude: Double = 0.3,
        attackFrames: Int = ATTACK_FRAMES,
        decayHalfLifeFrames: Int = frames(0.12, sampleRate),
    ): FloatArray {
        require(attackFrames > 0) { "attackFrames must be positive: $attackFrames" }
        require(decayHalfLifeFrames > 0) { "decayHalfLifeFrames must be positive: $decayHalfLifeFrames" }
        val out = sine(hertz, frames, sampleRate, amplitude)
        val attack = minOf(attackFrames, frames)
        val rate = ln(2.0) / decayHalfLifeFrames
        for (n in 0 until frames) {
            val rise = if (n < attack) n.toDouble() / attack else 1.0
            val fall = if (n < attack) 1.0 else exp(-rate * (n - attack))
            out[n] = (out[n] * rise * fall).toFloat()
        }
        return out
    }

    /** A microphone's own hiss. Nothing recorded is ever digital silence, and the detector must cope. */
    fun noiseFloor(frames: Int, seed: Long = 11L, amplitude: Double = NOISE_FLOOR_AMPLITUDE): FloatArray =
        whiteNoise(frames, seed, amplitude)

    /** About -54 dBFS: a quiet room through a phone microphone, well above the detector's floor. */
    const val NOISE_FLOOR_AMPLITUDE: Double = 0.002

    fun mixInto(target: FloatArray, source: FloatArray, at: Int) {
        val overlap = minOf(source.size, target.size - at)
        for (n in 0 until overlap) {
            target[at + n] += source[n]
        }
    }

    fun whiteNoise(frames: Int, seed: Long = 1L, amplitude: Double = 0.2): FloatArray {
        val random = Random(seed)
        val out = FloatArray(frames)
        for (n in 0 until frames) {
            out[n] = (amplitude * (2 * random.nextDouble() - 1)).toFloat()
        }
        return out
    }

    fun concat(vararg parts: FloatArray): FloatArray {
        val out = FloatArray(parts.sumOf { it.size })
        var at = 0
        for (part in parts) {
            part.copyInto(out, at)
            at += part.size
        }
        return out
    }

    /** Feeds [signal] in ragged chunk sizes, the way a real capture callback would not. */
    fun <T> pushInChunks(signal: FloatArray, chunkSizes: IntArray, push: (FloatArray, Int) -> List<T>): List<T> {
        val out = mutableListOf<T>()
        var at = 0
        var index = 0
        while (at < signal.size) {
            val size = minOf(chunkSizes[index % chunkSizes.size], signal.size - at)
            out += push(signal.copyOfRange(at, at + size), size)
            at += size
            index++
        }
        return out
    }
}
