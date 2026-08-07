package com.dewijones92.primavista.audio

import kotlin.math.abs

/** The outcome of looking for the calibration click in one captured buffer. */
public sealed interface ClickSearch {
    public data class Found(
        val frame: Int,
        val peakFrame: Int,
        val peak: Float,
        val noiseFloor: Float,
    ) : ClickSearch {
        /** Threshold-to-peak distance: how smeared the attack was, and so how loosely located. */
        public val riseFrames: Int get() = maxOf(0, peakFrame - frame)

        public val peakToNoise: Double
            get() = if (noiseFloor <= 0f) Double.POSITIVE_INFINITY else peak / noiseFloor.toDouble()

        override fun toString(): String =
            "frame=$frame rise=${riseFrames}frames peak=$peak floor=$noiseFloor snr=${peakToNoise}x"
    }

    /** Carries why, because a refusal a report cannot explain is indistinguishable from a crash. */
    public data class NotFound(val reason: String) : ClickSearch
}

/**
 * Finds the loopback click in a captured buffer, or refuses. See .claude/CODE-NOTES.md.
 */
public object LatencyCalibration {
    public fun findClick(
        pcm: FloatArray,
        frames: Int,
        thresholdRatio: Double = DEFAULT_THRESHOLD_RATIO,
        minimumPeak: Float = DEFAULT_MINIMUM_PEAK,
        requiredPeakToNoise: Double = DEFAULT_REQUIRED_PEAK_TO_NOISE,
    ): ClickSearch {
        require(thresholdRatio > 0.0 && thresholdRatio <= 1.0) { "threshold ratio must be in 0..1" }
        require(requiredPeakToNoise >= 1.0) { "a click has to clear the noise floor, not equal it" }
        val limit = minOf(frames, pcm.size)
        if (limit <= 0) return ClickSearch.NotFound("nothing to search: 0 frames of $frames read")

        var peak = 0f
        var peakFrame = 0
        for (index in 0 until limit) {
            val magnitude = abs(pcm[index])
            if (magnitude > peak) {
                peak = magnitude
                peakFrame = index
            }
        }
        val noiseFloor = noiseFloorOf(pcm, limit)
        refusalFor(peak, noiseFloor, minimumPeak, requiredPeakToNoise)?.let { return ClickSearch.NotFound(it) }

        val threshold = noiseFloor + (peak - noiseFloor) * thresholdRatio.toFloat()
        val onset = (0 until limit).firstOrNull { abs(pcm[it]) >= threshold } ?: peakFrame
        return ClickSearch.Found(onset, peakFrame, peak, noiseFloor)
    }

    /** Median magnitude: a short click barely moves it, so it survives having the click in frame. */
    private fun noiseFloorOf(pcm: FloatArray, limit: Int): Float {
        val magnitudes = FloatArray(limit) { abs(pcm[it]) }
        magnitudes.sort()
        return magnitudes[limit / 2]
    }

    private fun refusalFor(
        peak: Float,
        noiseFloor: Float,
        minimumPeak: Float,
        requiredPeakToNoise: Double,
    ): String? = when {
        peak < minimumPeak -> "peak $peak never reached the audible floor $minimumPeak"

        noiseFloor > 0f && peak < noiseFloor * requiredPeakToNoise ->
            "peak $peak is only ${peak / noiseFloor}x the $noiseFloor noise floor and a click has " +
                "to clear it by ${requiredPeakToNoise}x — this is room noise, not the click"

        else -> null
    }

    public const val DEFAULT_THRESHOLD_RATIO: Double = 0.25
    public const val DEFAULT_MINIMUM_PEAK: Float = 0.02f

    /** Roughly 18dB. Below it, ambient noise is indistinguishable from a quiet click. */
    public const val DEFAULT_REQUIRED_PEAK_TO_NOISE: Double = 8.0
}
