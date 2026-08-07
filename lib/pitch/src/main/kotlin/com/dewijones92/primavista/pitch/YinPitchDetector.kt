package com.dewijones92.primavista.pitch

import com.dewijones92.primavista.common.Diag
import com.dewijones92.primavista.common.NoOpDiag
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * YIN, as published: difference function, cumulative mean normalised difference, absolute
 * threshold, parabolic interpolation of the chosen minimum.
 *
 * See `.claude/CODE-NOTES.md` for why each of those four steps is load-bearing here, why the
 * lowest qualifying lag is the octave guard, and why a window with no periodic content returns
 * nothing rather than a low-confidence guess.
 */
public class YinPitchDetector(
    override val sampleRate: Int,
    override val windowFrames: Int = DEFAULT_WINDOW_FRAMES,
    override val hopFrames: Int = DEFAULT_HOP_FRAMES,
    private val threshold: Double = DEFAULT_THRESHOLD,
    private val minHertz: Double = DEFAULT_MIN_HERTZ,
    private val maxHertz: Double = DEFAULT_MAX_HERTZ,
    private val silenceRms: Double = DEFAULT_SILENCE_RMS,
    private val diag: Diag = NoOpDiag,
) : PitchDetector {

    init {
        require(sampleRate > 0) { "sampleRate must be positive: $sampleRate" }
        require(windowFrames >= MIN_WINDOW_FRAMES && windowFrames % 2 == 0) {
            "windowFrames must be even and at least $MIN_WINDOW_FRAMES: $windowFrames"
        }
        require(hopFrames in 1..windowFrames) { "hopFrames must be in 1..$windowFrames: $hopFrames" }
        require(threshold > 0.0 && threshold < 1.0) { "threshold must be inside (0,1): $threshold" }
        require(minHertz > 0.0 && maxHertz > minHertz) {
            "need 0 < minHertz < maxHertz, got $minHertz..$maxHertz"
        }
        require(silenceRms >= 0.0) { "silenceRms must not be negative: $silenceRms" }
    }

    private val innerFrames = windowFrames / 2
    private val minTau = max(MIN_TAU, ceil(sampleRate / maxHertz).toInt())
    private val maxTau = min(innerFrames - 1, floor(sampleRate / minHertz).toInt())

    init {
        require(maxTau >= minTau + 1) {
            "windowFrames=$windowFrames cannot cover $minHertz..$maxHertz Hz at $sampleRate Hz " +
                "(lag range $minTau..$maxTau)"
        }
    }

    private val ring = FloatArray(windowFrames)
    private val window = FloatArray(windowFrames)
    private val difference = DoubleArray(maxTau + 1)
    private val normalised = DoubleArray(maxTau + 1)

    private var writeIndex = 0
    private var framesPushed = 0L
    private var nextWindowEnd = windowFrames.toLong()

    /** Frequencies this instance can report, given its window and sample rate. */
    public val searchRange: ClosedFloatingPointRange<Double> =
        (sampleRate.toDouble() / maxTau)..(sampleRate.toDouble() / minTau)

    override fun push(pcm: FloatArray, frames: Int): List<DetectedPitch> {
        require(frames >= 0 && frames <= pcm.size) { "frames=$frames outside pcm of ${pcm.size}" }
        val estimates = mutableListOf<DetectedPitch>()
        for (i in 0 until frames) {
            ring[writeIndex] = pcm[i]
            writeIndex = if (writeIndex == windowFrames - 1) 0 else writeIndex + 1
            framesPushed++
            if (framesPushed == nextWindowEnd) {
                val centreFrame = nextWindowEnd - innerFrames
                nextWindowEnd += hopFrames
                copyWindowInOrder()
                analyse(centreFrame)?.let(estimates::add)
            }
        }
        return estimates
    }

    override fun reset() {
        ring.fill(0f)
        writeIndex = 0
        framesPushed = 0L
        nextWindowEnd = windowFrames.toLong()
        diag.event(DIAG_TAG, "reset sampleRate=$sampleRate windowFrames=$windowFrames hopFrames=$hopFrames")
    }

    private fun analyse(centreFrame: Long): DetectedPitch? {
        diag.counted(DIAG_TAG, "windows")
        if (rootMeanSquare() < silenceRms) {
            diag.counted(DIAG_TAG, "noEstimate.belowSilenceFloor")
            return null
        }
        computeDifference()
        computeCumulativeMeanNormalised()
        val tau = lowestLagBelowThreshold()
        if (tau == NO_LAG) {
            diag.counted(DIAG_TAG, "noEstimate.aperiodic")
            return null
        }
        val confidence = (1.0 - normalised[tau]).coerceIn(0.0, 1.0)
        return DetectedPitch(
            hertz = Hertz(sampleRate / interpolatedLag(tau)),
            confidence = confidence.toFloat(),
            atFrame = centreFrame,
        )
    }

    private fun copyWindowInOrder() {
        val tail = windowFrames - writeIndex
        ring.copyInto(window, 0, writeIndex, windowFrames)
        if (writeIndex > 0) {
            ring.copyInto(window, tail, 0, writeIndex)
        }
    }

    private fun rootMeanSquare(): Double {
        var sum = 0.0
        for (j in 0 until windowFrames) {
            val sample = window[j].toDouble()
            sum += sample * sample
        }
        return sqrt(sum / windowFrames)
    }

    private fun computeDifference() {
        var headPower = 0.0
        for (j in 0 until innerFrames) {
            val sample = window[j].toDouble()
            headPower += sample * sample
        }
        difference[0] = 0.0
        var laggedPower = headPower
        for (tau in 1..maxTau) {
            val entering = window[tau + innerFrames - 1].toDouble()
            val leaving = window[tau - 1].toDouble()
            laggedPower += entering * entering - leaving * leaving
            var cross = 0.0
            for (j in 0 until innerFrames) {
                cross += window[j].toDouble() * window[j + tau]
            }
            difference[tau] = max(0.0, headPower + laggedPower - 2 * cross)
        }
    }

    private fun computeCumulativeMeanNormalised() {
        normalised[0] = 1.0
        var runningSum = 0.0
        for (tau in 1..maxTau) {
            runningSum += difference[tau]
            normalised[tau] = if (runningSum <= 0.0) 1.0 else difference[tau] * tau / runningSum
        }
    }

    private fun lowestLagBelowThreshold(): Int {
        for (tau in minTau..maxTau) {
            if (normalised[tau] < threshold) {
                var best = tau
                while (best < maxTau && normalised[best + 1] < normalised[best]) {
                    best++
                }
                return best
            }
        }
        return NO_LAG
    }

    private fun interpolatedLag(tau: Int): Double {
        if (tau <= 0 || tau >= maxTau) return tau.toDouble()
        val before = difference[tau - 1]
        val after = difference[tau + 1]
        val curvature = before + after - 2 * difference[tau]
        if (curvature <= 0.0) return tau.toDouble()
        val shift = HALF_LAG * (before - after) / curvature
        return tau + shift.coerceIn(-HALF_LAG, HALF_LAG)
    }

    public companion object {
        public const val DEFAULT_WINDOW_FRAMES: Int = 2048
        public const val DEFAULT_HOP_FRAMES: Int = 512
        public const val DEFAULT_THRESHOLD: Double = 0.15

        /** A1 to roughly C7 — below the lowest bass note and above the top of the treble staff. */
        public const val DEFAULT_MIN_HERTZ: Double = 55.0
        public const val DEFAULT_MAX_HERTZ: Double = 2100.0

        /** About -80 dBFS: quieter than any microphone's own noise floor. */
        public const val DEFAULT_SILENCE_RMS: Double = 1.0e-4

        private const val DIAG_TAG = "pitch.yin"
        private const val MIN_WINDOW_FRAMES = 64
        private const val MIN_TAU = 2
        private const val NO_LAG = -1
        private const val HALF_LAG = 0.5
    }
}
