package com.dewijones92.primavista.practice

import com.dewijones92.primavista.score.Midi
import com.dewijones92.primavista.score.Polyphony
import kotlinx.coroutines.flow.Flow

/**
 * A note Dewi actually played.
 *
 * [atNanos] is stamped **at the source** — `MotionEvent.eventTime` for a tap, the
 * `AudioRecord` frame position for the mic — and converted into the Conductor's timebase once,
 * at the adapter boundary. Never restamped on arrival: "when the app noticed" is not "when he
 * played it", and the difference is the whole judgement (docs/spec.md I2).
 *
 * [centsOff] is how far from equal temperament the detected pitch was. Null for inputs where
 * the question is meaningless (a tapped key is exactly in tune by construction). It is kept
 * because on a real instrument it is the difference between a wrong note and a note that needs
 * tuning, and reporting the first when it was the second would be the app lying.
 */
public data class PlayedNote(
    val midi: Midi,
    val atNanos: Long,
    val centsOff: Double? = null,
    val confidence: Float = 1f,
)

/**
 * The one input seam. Tap, mic and (later) MIDI are adapters behind it, and everything
 * downstream is input-agnostic — which is what makes MIDI a new adapter rather than a rewrite
 * (docs/todos/midi-input.md). If adding an input ever requires a change outside its adapter,
 * this seam was drawn wrongly and that is the finding.
 */
public interface AnswerSource {
    /**
     * Short, stable, and present in every log line this input produces (`src=mic`). Named
     * rather than derived from the class so a report stays readable after a refactor.
     */
    public val label: String

    /**
     * What this input can actually hear at once. Checked against the score before a session
     * starts, so a mono input on polyphonic music is refused with a reason rather than
     * silently mis-scored (docs/spec.md I3).
     */
    public val polyphony: Polyphony

    /** Latency to correct for, with its provenance. [InputLatency.None] for taps. */
    public val latency: InputLatency

    public fun notes(): Flow<PlayedNote>
}

/**
 * The pure half of tap input. Lives here rather than in `:app` because deciding *what note a
 * touch means* is logic worth testing, while drawing the keyboard is not. The UI hands over the
 * key and the `MotionEvent.eventTime`; nothing about Android crosses this line.
 */
public interface TapAnswerSource : AnswerSource {
    public fun onKeyPressed(midi: Midi, eventTimeNanos: Long)
}
