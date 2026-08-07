package com.dewijones92.primavista.pitch

/**
 * Monophonic pitch and onset detection over a stream of PCM.
 *
 * Deliberately independent of `:core:score`: this library speaks **hertz and frames**, never
 * notation, so it is standalone, reusable, and testable against synthesised tones with no device
 * and no music model involved. Converting hertz to a notated pitch is one function in the
 * adapter (`:core:audio`), not a responsibility here. Same reasoning as Totum's `:lib:ytdlp`.
 */
@JvmInline
public value class Hertz(public val value: Double) {
    init {
        require(value > 0) { "frequency must be positive" }
    }
}

/**
 * A pitch estimate for one analysis window.
 *
 * [atFrame] is the sample-frame index the window is centred on, and it is the only honest way to
 * timestamp audio: frame counts come from the capture device, whereas the moment a read returned
 * depends on scheduling. The adapter converts frames to nanos once (docs/spec.md I2).
 *
 * [confidence] is 0..1. YIN's aperiodicity measure inverted — low confidence means the window did
 * not contain a periodic signal, which is usually silence, noise, or a chord.
 */
public data class DetectedPitch(
    val hertz: Hertz,
    val confidence: Float,
    val atFrame: Long,
)

/**
 * The moment a note started, which is a different question from what pitch it is — and the one
 * judging actually depends on. A pitch tracker alone cannot tell a repeated note from a held one:
 * both are a continuous run of the same frequency. Without onset detection, playing a note four
 * times reads as playing it once, so this is not an optimisation.
 */
public data class NoteOnset(
    val atFrame: Long,
    val strength: Float,
)

/**
 * An onset paired with the pitch that followed it — what an [AnswerSource] adapter actually wants.
 *
 * [detectionDelayFrames] is how far after [atFrame] the pitch could first be known. It is not
 * overhead to be hidden: a periodic estimate needs several cycles, so the delay is inherently
 * longer for low notes than high ones. Reporting it lets the adapter correct per note instead of
 * applying one average and leaving a pitch-dependent timing bias, which would be invisible in a
 * session average and therefore worse (docs/todos/measure-audio-latency.md).
 */
public data class TrackedNote(
    val hertz: Hertz,
    val atFrame: Long,
    val confidence: Float,
    val detectionDelayFrames: Int,
)

/**
 * Push-based so the caller owns the buffer and the thread. Implementations must be allocation-light
 * per call: this runs on every audio buffer.
 */
public interface PitchDetector {
    public val sampleRate: Int
    public val windowFrames: Int
    public val hopFrames: Int

    /** Feeds [frames] samples of mono float PCM in -1..1, returning estimates for completed windows. */
    public fun push(pcm: FloatArray, frames: Int): List<DetectedPitch>

    public fun reset()
}

public interface OnsetDetector {
    public fun push(pcm: FloatArray, frames: Int): List<NoteOnset>

    public fun reset()
}

/**
 * Combines the two into the notes a human would say they played. The only part of this library the
 * adapter needs, and the part worth testing hardest — the interesting failures are a repeated note
 * read as one, and a vibrato read as several.
 */
public interface MonophonicNoteTracker {
    public fun push(pcm: FloatArray, frames: Int): List<TrackedNote>

    public fun reset()
}

public object Tuning {
    public const val A4_HERTZ: Double = 440.0
    public const val A4_MIDI: Int = 69
    public const val SEMITONES_PER_OCTAVE: Int = 12
    public const val CENTS_PER_SEMITONE: Int = 100

    /** Fractional MIDI number for a frequency. Fractional on purpose — the remainder is the cents. */
    public fun midiOf(hertz: Hertz): Double =
        A4_MIDI + SEMITONES_PER_OCTAVE * kotlin.math.log2(hertz.value / A4_HERTZ)

    public fun hertzOf(midi: Int): Hertz =
        Hertz(A4_HERTZ * Math.pow(2.0, (midi - A4_MIDI).toDouble() / SEMITONES_PER_OCTAVE))
}
