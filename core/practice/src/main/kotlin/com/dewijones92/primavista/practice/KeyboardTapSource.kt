package com.dewijones92.primavista.practice

import com.dewijones92.primavista.score.Midi
import com.dewijones92.primavista.score.Polyphony
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * The on-screen keyboard's half of tap input: pure, multi-touch, and stamped by the caller.
 *
 * A queue, not a broadcast: taps are held until the session collects them, and [notes] is drained
 * by exactly one collector. [notesDropped] is not decoration. See .claude/CODE-NOTES.md.
 */
public class KeyboardTapSource(
    bufferCapacity: Int = DEFAULT_BUFFER_CAPACITY,
) : TapAnswerSource {
    init {
        require(bufferCapacity > 0) { "a buffer of $bufferCapacity would drop taps by construction" }
    }

    override val label: String = "tap"

    override val polyphony: Polyphony = Polyphony.Poly

    override val latency: InputLatency = InputLatency.None

    private val taps = Channel<PlayedNote>(capacity = bufferCapacity)

    private val emitted = AtomicLong()
    private val dropped = AtomicLong()

    public val notesEmitted: Long get() = emitted.get()

    public val notesDropped: Long get() = dropped.get()

    override fun notes(): Flow<PlayedNote> = taps.receiveAsFlow()

    override fun onKeyPressed(midi: Midi, eventTimeNanos: Long) {
        val accepted = taps.trySend(PlayedNote(midi = midi, atNanos = eventTimeNanos)).isSuccess
        if (accepted) emitted.incrementAndGet() else dropped.incrementAndGet()
    }

    public companion object {
        /** Room for a two-hand trill several seconds long while the collector is descheduled. */
        public const val DEFAULT_BUFFER_CAPACITY: Int = 256
    }
}
