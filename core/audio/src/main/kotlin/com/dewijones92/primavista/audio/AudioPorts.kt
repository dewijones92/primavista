package com.dewijones92.primavista.audio

import com.dewijones92.primavista.practice.AnswerSource
import com.dewijones92.primavista.practice.NanoClock
import com.dewijones92.primavista.score.Midi
import com.dewijones92.primavista.score.TimeSignature

/**
 * Audible beat. Driven from the [com.dewijones92.primavista.practice.Conductor], never from its own
 * timer — a metronome keeping independent time is a second clock, and two clocks in this app is
 * the bug class docs/spec.md I1 forbids.
 */
public interface Metronome {
    public fun configure(tempoBpm: Int, time: TimeSignature)

    public fun start()

    public fun stop()

    public var enabled: Boolean
}

/**
 * Plays notes so Dewi can hear what he is looking at — the eye-to-ear link, and the fastest way to
 * tell whether a wrong verdict was his mistake or the app's.
 *
 * Synthesised rather than sampled: a usable piano soundfont is tens of megabytes for something used
 * to check a pitch, and the APK budget is better spent elsewhere. Revisit if it sounds bad enough to
 * be off-putting.
 */
public interface TonePlayer {
    public fun play(midi: Midi, durationMillis: Long)

    public fun playChord(midis: List<Midi>, durationMillis: Long)

    public fun stopAll()
}

/**
 * Mono float PCM from the microphone.
 *
 * [frameTimestampNanos] is the contract that matters: it converts a capture frame index into the
 * system's monotonic timebase, using `AudioRecord`'s own timestamp rather than the moment a read
 * returned. Everything about mic timing accuracy rests on this one function being right
 * (docs/spec.md I2).
 */
public interface PcmCapture {
    public val sampleRate: Int

    public fun start()

    public fun stop()

    public fun frameTimestampNanos(frame: Long): Long

    /** Reads into [into], returning frames read and the index of the first of them. */
    public fun read(into: FloatArray): CaptureRead
}

public data class CaptureRead(val frames: Int, val firstFrame: Long)

/**
 * Microphone input as an [AnswerSource]. Declares [AnswerSource.polyphony] as
 * `Polyphony.Mono`, which is what lets the judge refuse polyphonic material honestly instead of
 * scoring whichever notes it happened to detect (docs/spec.md I3).
 */
public interface MicAnswerSource : AnswerSource {
    /** Re-measures input latency for the current audio route. Bluetooth is not the built-in mic. */
    public suspend fun calibrateLatency(): Unit
}

/**
 * The system's monotonic clock, and the only implementation of [NanoClock] in production. Monotonic
 * matters: wall time can step backwards, which would send the playhead back mid-bar.
 */
public interface MonotonicClock : NanoClock
