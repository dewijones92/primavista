package com.dewijones92.primavista.pitch

import com.dewijones92.primavista.common.Diag
import com.dewijones92.primavista.common.NoOpDiag
import java.util.Arrays
import java.util.Locale
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Rising edges in a log-energy envelope, against a threshold adapted from the recent median.
 *
 * Working in log energy is what makes one threshold serve both a fortissimo entry and a quiet
 * one; see `.claude/CODE-NOTES.md` for that, for why the first block only primes the envelope,
 * and for what the minimum inter-onset interval is protecting against.
 */
public class EnergyOnsetDetector(
    override val sampleRate: Int,
    public val blockFrames: Int = DEFAULT_BLOCK_FRAMES,
    private val historyBlocks: Int = DEFAULT_HISTORY_BLOCKS,
    private val thresholdMargin: Double = DEFAULT_THRESHOLD_MARGIN,
    private val thresholdMultiplier: Double = DEFAULT_THRESHOLD_MULTIPLIER,
    private val minInterOnsetFrames: Int = sampleRate / MIN_INTER_ONSET_DIVISOR,
    private val silenceRms: Double = DEFAULT_SILENCE_RMS,
    private val smoothing: Double = DEFAULT_SMOOTHING,
    private val diag: Diag = NoOpDiag,
) : OnsetDetector {

    init {
        require(sampleRate > 0) { "sampleRate must be positive: $sampleRate" }
        require(blockFrames >= MIN_BLOCK_FRAMES) { "blockFrames must be at least $MIN_BLOCK_FRAMES: $blockFrames" }
        require(historyBlocks >= 1) { "historyBlocks must be at least 1: $historyBlocks" }
        require(thresholdMargin > 0.0) { "thresholdMargin must be positive: $thresholdMargin" }
        require(thresholdMultiplier >= 0.0) { "thresholdMultiplier must not be negative: $thresholdMultiplier" }
        require(minInterOnsetFrames >= 0) { "minInterOnsetFrames must not be negative: $minInterOnsetFrames" }
        require(smoothing > 0.0 && smoothing <= 1.0) { "smoothing must be inside (0,1]: $smoothing" }
    }

    /** An onset is located to the block it was heard in, so the block is the precision. */
    override val hopFrames: Int get() = blockFrames

    private val block = FloatArray(blockFrames)
    private val fluxHistory = DoubleArray(historyBlocks)
    private val fluxScratch = DoubleArray(historyBlocks)

    private var blockFill = 0
    private var blocksDone = 0L
    private var historyFill = 0
    private var historyAt = 0
    private var smoothedLevel = 0.0
    private var primed = false
    private var aboveThreshold = false
    private var lastOnsetFrame = NO_FRAME

    override fun push(pcm: FloatArray, frames: Int): List<NoteOnset> {
        require(frames >= 0 && frames <= pcm.size) { "frames=$frames outside pcm of ${pcm.size}" }
        val onsets = mutableListOf<NoteOnset>()
        for (i in 0 until frames) {
            block[blockFill] = pcm[i]
            blockFill++
            if (blockFill == blockFrames) {
                blockFill = 0
                val startFrame = blocksDone * blockFrames
                blocksDone++
                evaluate(startFrame)?.let(onsets::add)
            }
        }
        return onsets
    }

    override fun reset() {
        blockFill = 0
        blocksDone = 0L
        historyFill = 0
        historyAt = 0
        smoothedLevel = 0.0
        primed = false
        aboveThreshold = false
        lastOnsetFrame = NO_FRAME
        diag.event(DIAG_TAG, "reset sampleRate=$sampleRate blockFrames=$blockFrames")
    }

    private fun evaluate(startFrame: Long): NoteOnset? {
        diag.counted(DIAG_TAG, "blocks")
        val rms = blockRootMeanSquare()
        val level = ln(rms + LEVEL_FLOOR)
        if (!primed) {
            primed = true
            smoothedLevel = level
            return null
        }
        val flux = max(0.0, level - smoothedLevel)
        smoothedLevel += smoothing * (level - smoothedLevel)
        val limit = thresholdMargin + thresholdMultiplier * medianFlux()
        recordFlux(flux)
        val loudEnough = rms >= silenceRms
        val above = flux > limit && loudEnough
        val rising = above && !aboveThreshold
        aboveThreshold = above
        if (flux > limit && !loudEnough) {
            diag.counted(DIAG_TAG, "noOnset.belowSilenceFloor")
        }
        if (!rising) return null
        if (lastOnsetFrame != NO_FRAME && startFrame - lastOnsetFrame < minInterOnsetFrames) {
            diag.counted(DIAG_TAG, "noOnset.withinMinInterOnsetInterval")
            return null
        }
        lastOnsetFrame = startFrame
        diag.event(
            DIAG_TAG,
            "onset atFrame=$startFrame flux=${format(flux)} limit=${format(limit)} rms=${format(rms)}",
        )
        return NoteOnset(atFrame = startFrame, strength = flux.toFloat())
    }

    private fun blockRootMeanSquare(): Double {
        var sum = 0.0
        for (n in 0 until blockFrames) {
            val sample = block[n].toDouble()
            sum += sample * sample
        }
        return sqrt(sum / blockFrames)
    }

    private fun medianFlux(): Double {
        if (historyFill == 0) return 0.0
        fluxHistory.copyInto(fluxScratch, 0, 0, historyFill)
        Arrays.sort(fluxScratch, 0, historyFill)
        return fluxScratch[historyFill / 2]
    }

    private fun recordFlux(flux: Double) {
        fluxHistory[historyAt] = flux
        historyAt = if (historyAt == historyBlocks - 1) 0 else historyAt + 1
        if (historyFill < historyBlocks) historyFill++
    }

    private fun format(value: Double): String = String.format(Locale.ROOT, "%.4f", value)

    public companion object {
        /** About 5.8 ms at 44.1 kHz — fine enough that block quantisation is not the dominant error. */
        public const val DEFAULT_BLOCK_FRAMES: Int = 256

        public const val DEFAULT_HISTORY_BLOCKS: Int = 20
        public const val DEFAULT_THRESHOLD_MARGIN: Double = 0.45
        public const val DEFAULT_THRESHOLD_MULTIPLIER: Double = 1.6
        public const val DEFAULT_SILENCE_RMS: Double = 1.0e-4
        public const val DEFAULT_SMOOTHING: Double = 0.5

        /** 50 ms: faster than any repeated note a human plays, slower than one attack's own ripple. */
        public const val MIN_INTER_ONSET_DIVISOR: Int = 20

        private const val DIAG_TAG = "pitch.onset"
        private const val MIN_BLOCK_FRAMES = 16
        private const val NO_FRAME = -1L
        private const val LEVEL_FLOOR = 1.0e-9
    }
}
