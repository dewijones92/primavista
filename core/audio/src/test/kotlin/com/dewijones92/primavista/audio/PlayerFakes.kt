package com.dewijones92.primavista.audio

import com.dewijones92.primavista.common.Diag
import com.dewijones92.primavista.score.Midi

/** A player that can report when its click was presented, and how sure it is of that. */
class AnchoredTonePlayer(
    private val anchorNanos: Long?,
    private val uncertaintyMillis: Double = 0.0,
) : TonePlayer, PlaybackAnchor {
    val played: MutableList<Int> = mutableListOf()

    override fun play(midi: Midi, durationMillis: Long) {
        played += midi.number
    }

    override fun playChord(midis: List<Midi>, durationMillis: Long) {
        played += midis.map { it.number }
    }

    override fun stopAll() = Unit

    override fun release() = Unit

    override fun lastPlayback(): PlaybackMoment? =
        anchorNanos?.let { PlaybackMoment(it, uncertaintyMillis) }
}

/** A player with no [PlaybackAnchor], which is what forces an assumed latency. */
class BlindTonePlayer : TonePlayer {
    val played: MutableList<Int> = mutableListOf()

    override fun play(midi: Midi, durationMillis: Long) {
        played += midi.number
    }

    override fun playChord(midis: List<Midi>, durationMillis: Long) {
        played += midis.map { it.number }
    }

    override fun stopAll() = Unit

    override fun release() = Unit
}

class FixedClock(private val nanos: Long) : MonotonicClock {
    override fun nowNanos(): Long = nanos
}

/** Keeps what was logged so a test can assert the diagnostics rule was honoured. */
class RecordingDiag : Diag {
    val events: MutableList<String> = mutableListOf()
    val counts: MutableMap<String, Int> = mutableMapOf()
    val states: MutableMap<String, String> = mutableMapOf()

    override fun event(tag: String, message: String) {
        events += "$tag $message"
    }

    override fun counted(tag: String, key: String, increment: Int) {
        counts["$tag.$key"] = (counts["$tag.$key"] ?: 0) + increment
    }

    override fun state(tag: String, snapshot: () -> String) {
        states[tag] = snapshot()
    }

    override fun report(header: Map<String, String>): String =
        (header.entries.map { "${it.key}=${it.value}" } + events + counts.map { "${it.key}=${it.value}" })
            .joinToString("\n")
}
