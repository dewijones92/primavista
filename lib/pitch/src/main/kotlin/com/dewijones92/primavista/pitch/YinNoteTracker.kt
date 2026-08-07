package com.dewijones92.primavista.pitch

import com.dewijones92.primavista.common.Diag
import com.dewijones92.primavista.common.NoOpDiag
import java.util.Locale
import kotlin.math.abs

/**
 * Onsets say *when*, pitch estimates say *what*, and a note needs both. A note is emitted at the
 * frame it began, carrying how much later its pitch could be known — the honesty property in
 * docs/spec.md I2. See `.claude/CODE-NOTES.md` for the stabilisation rule, the no-onset
 * pitch-change rule that makes legato audible, why estimates that predate an onset are dropped,
 * and why the two emission paths each subtract their own quantisation before reporting.
 */
public class YinNoteTracker(
    override val sampleRate: Int,
    private val pitchDetector: PitchDetector = YinPitchDetector(sampleRate),
    private val onsetDetector: OnsetDetector = EnergyOnsetDetector(sampleRate),
    private val stableEstimates: Int = DEFAULT_STABLE_ESTIMATES,
    private val stabilityCents: Double = DEFAULT_STABILITY_CENTS,
    private val pitchChangeCents: Double = DEFAULT_PITCH_CHANGE_CENTS,
    private val confidenceFloor: Float = DEFAULT_CONFIDENCE_FLOOR,
    private val onsetGraceFrames: Int = DEFAULT_ONSET_GRACE_FRAMES,
    private val confirmWithinFrames: Int = sampleRate / CONFIRM_WITHIN_DIVISOR,
    private val diag: Diag = NoOpDiag,
) : MonophonicNoteTracker {

    init {
        require(sampleRate > 0) { "sampleRate must be positive: $sampleRate" }
        require(stableEstimates >= 1) { "stableEstimates must be at least 1: $stableEstimates" }
        require(stabilityCents > 0.0) { "stabilityCents must be positive: $stabilityCents" }
        require(pitchChangeCents > stabilityCents) {
            "pitchChangeCents ($pitchChangeCents) must exceed stabilityCents ($stabilityCents)"
        }
        require(confidenceFloor >= 0f && confidenceFloor <= 1f) {
            "confidenceFloor must be inside 0..1: $confidenceFloor"
        }
        require(onsetGraceFrames >= 0) { "onsetGraceFrames must not be negative: $onsetGraceFrames" }
        require(confirmWithinFrames > 0) { "confirmWithinFrames must be positive: $confirmWithinFrames" }
    }

    /** Per-path de-bias; see `.claude/CODE-NOTES.md`. */
    private val onsetCentringFrames = onsetDetector.hopFrames / 2
    private val pitchLagFrames = pitchDetector.windowFrames / 2

    private var pendingOnset: Long? = null
    private var runReference: Hertz? = null
    private var runFirstFrame = 0L
    private var runCount = 0
    private var runHertzSum = 0.0
    private var runConfidenceSum = 0.0
    private var soundingHertz: Hertz? = null

    override fun push(pcm: FloatArray, frames: Int): List<TrackedNote> {
        val onsets = onsetDetector.push(pcm, frames)
        val estimates = pitchDetector.push(pcm, frames)
        val notes = mutableListOf<TrackedNote>()
        var onsetAt = 0
        var estimateAt = 0
        while (onsetAt < onsets.size || estimateAt < estimates.size) {
            val onsetIsNext = estimateAt == estimates.size ||
                (onsetAt < onsets.size && onsets[onsetAt].atFrame <= estimates[estimateAt].atFrame)
            if (onsetIsNext) {
                acceptOnset(onsets[onsetAt])
                onsetAt++
            } else {
                acceptEstimate(estimates[estimateAt])?.let(notes::add)
                estimateAt++
            }
        }
        return notes
    }

    override fun reset() {
        pitchDetector.reset()
        onsetDetector.reset()
        pendingOnset = null
        soundingHertz = null
        clearRun()
        diag.event(DIAG_TAG, "reset sampleRate=$sampleRate")
    }

    private fun acceptOnset(onset: NoteOnset) {
        pendingOnset?.let { superseded ->
            diag.event(DIAG_TAG, "onsetSuperseded atFrame=$superseded byFrame=${onset.atFrame}")
        }
        pendingOnset = onset.atFrame
        clearRun()
    }

    private fun acceptEstimate(estimate: DetectedPitch): TrackedNote? {
        if (estimate.confidence < confidenceFloor) {
            diag.counted(DIAG_TAG, "estimate.belowConfidenceFloor")
            clearRun()
            return null
        }
        abandonStaleOnset(estimate.atFrame)
        val onset = pendingOnset
        if (onset != null && estimate.atFrame < onset - onsetGraceFrames) {
            diag.counted(DIAG_TAG, "estimate.precedesOnset")
            return null
        }
        val reference = runReference
        if (reference == null || abs(centsBetween(reference, estimate.hertz)) > stabilityCents) {
            startRun(estimate)
        } else {
            extendRun(estimate)
        }
        if (runCount < stableEstimates) return null
        return emitIfNew(estimate)
    }

    private fun emitIfNew(confirmedBy: DetectedPitch): TrackedNote? {
        val hertz = Hertz(runHertzSum / runCount)
        val onset = pendingOnset
        val sounding = soundingHertz
        val changed = sounding == null || abs(centsBetween(sounding, hertz)) > pitchChangeCents
        if (onset == null && !changed) {
            diag.counted(DIAG_TAG, "estimate.continuesSoundingNote")
            return null
        }
        val rawFrame = onset ?: runFirstFrame
        val atFrame = if (onset != null) {
            rawFrame + onsetCentringFrames
        } else {
            (rawFrame - pitchLagFrames).coerceAtLeast(0L)
        }
        val delay = (confirmedBy.atFrame - atFrame).coerceAtLeast(0L).toInt()
        val note = TrackedNote(
            hertz = hertz,
            atFrame = atFrame,
            confidence = (runConfidenceSum / runCount).toFloat(),
            detectionDelayFrames = delay,
        )
        pendingOnset = null
        soundingHertz = hertz
        startRun(confirmedBy)
        diag.event(
            DIAG_TAG,
            "note hz=${format(hertz.value)} atFrame=$atFrame rawFrame=$rawFrame delayFrames=$delay " +
                "conf=${format(note.confidence.toDouble())} src=${if (onset != null) "onset" else "pitchChange"}",
        )
        return note
    }

    private fun abandonStaleOnset(atFrame: Long) {
        val onset = pendingOnset ?: return
        if (atFrame - onset > confirmWithinFrames) {
            diag.event(
                DIAG_TAG,
                "onsetAbandoned atFrame=$onset reason=noStablePitch withinFrames=$confirmWithinFrames",
            )
            pendingOnset = null
        }
    }

    private fun startRun(estimate: DetectedPitch) {
        runReference = estimate.hertz
        runFirstFrame = estimate.atFrame
        runCount = 1
        runHertzSum = estimate.hertz.value
        runConfidenceSum = estimate.confidence.toDouble()
    }

    private fun extendRun(estimate: DetectedPitch) {
        runCount++
        runHertzSum += estimate.hertz.value
        runConfidenceSum += estimate.confidence.toDouble()
    }

    private fun clearRun() {
        runReference = null
        runCount = 0
        runHertzSum = 0.0
        runConfidenceSum = 0.0
        runFirstFrame = 0L
    }

    private fun format(value: Double): String = String.format(Locale.ROOT, "%.2f", value)

    public companion object {
        public const val DEFAULT_STABLE_ESTIMATES: Int = 3
        public const val DEFAULT_STABILITY_CENTS: Double = 50.0

        /** Nearly a semitone: wide enough that vibrato is one note, narrow enough to catch a slur. */
        public const val DEFAULT_PITCH_CHANGE_CENTS: Double = 90.0

        public const val DEFAULT_CONFIDENCE_FLOOR: Float = 0.5f

        /** An onset frame is block-quantised, so an estimate just before it may still be the note's. */
        public const val DEFAULT_ONSET_GRACE_FRAMES: Int = EnergyOnsetDetector.DEFAULT_BLOCK_FRAMES

        /** 333 ms to confirm a pitch, after which the onset was a thud rather than a note. */
        public const val CONFIRM_WITHIN_DIVISOR: Int = 3

        private const val DIAG_TAG = "pitch.track"
    }
}
