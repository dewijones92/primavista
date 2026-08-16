package com.dewijones92.primavista.audio

import com.dewijones92.primavista.practice.AnswerSource
import com.dewijones92.primavista.practice.AudioRoute
import com.dewijones92.primavista.practice.NanoClock
import com.dewijones92.primavista.score.Midi
import com.dewijones92.primavista.score.Ticks
import com.dewijones92.primavista.score.TimeSignature

/** Anything holding a native audio resource. Forgetting to release one leaks it for the process. */
public interface AudioResource {
    public fun release()
}

/**
 * Audible beat. Driven from the [com.dewijones92.primavista.practice.Conductor] via [onPosition],
 * never from its own timer — a metronome keeping independent time is a second clock, and two clocks
 * in this app is the bug class docs/spec.md I1 forbids.
 */
public interface Metronome : AudioResource {
    /**
     * [barStart] is the tick the current bar begins at, so the downbeat accent lands on the real
     * bar line. Without it the accent is derived from tick zero and a single time signature, which
     * silently accents the wrong beat in any piece with a pickup bar or a metre change.
     */
    public fun configure(tempoBpm: Int, time: TimeSignature, barStart: Ticks = Ticks.ZERO)

    /** Called with the Conductor's sampled position; it decides whether a beat has just been crossed. */
    public fun onPosition(position: Ticks)

    public fun stop()

    public var enabled: Boolean
}

/**
 * Plays notes so Dewi can hear what he is looking at — the eye-to-ear link, and the fastest way to
 * tell whether a wrong verdict was his mistake or the app's.
 *
 * Synthesised rather than sampled: a usable piano soundfont is tens of megabytes for something used
 * to check a pitch. Revisit if it sounds bad enough to be off-putting.
 */
public interface TonePlayer : AudioResource {
    public fun play(midi: Midi, durationMillis: Long)

    public fun playChord(midis: List<Midi>, durationMillis: Long)

    public fun stopAll()
}

/**
 * Mono float PCM from the microphone.
 *
 * [frameTimestampNanos] is the contract that matters: it converts a capture frame index into the
 * system's monotonic timebase using `AudioRecord`'s own timestamp rather than the moment a read
 * returned. Everything about mic timing accuracy rests on this one function being right
 * (docs/spec.md I2).
 */
public interface PcmCapture : AudioResource {
    /**
     * Only meaningful once [start] has returned [CaptureStart.Started]; before that the device has
     * not negotiated a rate and any value is a guess presented as a fact.
     */
    public val sampleRate: Int

    /**
     * Whether frame timestamps are the device's own **right now**.
     *
     * Live, not the value in [CaptureStart.Started]: that is snapshotted before the first read, so
     * it always says [TimestampProvenance.ExtrapolatedFromStart]. Anything deciding whether a
     * timestamp can be trusted must ask here. See .claude/CODE-NOTES.md.
     */
    public val timestampProvenance: TimestampProvenance

    /**
     * The path sound is arriving by **right now**, which is not always the one [start] opened on:
     * Android reroutes a live capture when a headset connects. [AudioRoute.Unidentified] before a
     * capture opens, and it must be re-read rather than remembered.
     */
    public val currentRoute: AudioRoute

    /**
     * A result rather than an exception, because the commonest failure is Dewi declining the
     * microphone permission — an ordinary thing a person does, which must surface as a refusal with
     * a reason, not as a crash. This mirrors the judge's refusal-with-reason (spec I3).
     */
    public fun start(): CaptureStart

    public fun stop()

    public fun frameTimestampNanos(frame: Long): Long

    /** Reads into [into], returning frames read and the index of the first of them. */
    public fun read(into: FloatArray): CaptureRead
}

public sealed interface CaptureStart {
    public data class Started(
        val sampleRate: Int,
        val audioSourceName: String,
        val timestampProvenance: TimestampProvenance,
        val route: AudioRoute,
    ) : CaptureStart

    public sealed interface Refused : CaptureStart {
        public val reason: String

        public data object PermissionDenied : Refused {
            override val reason: String get() = "the microphone permission has not been granted"
        }

        public data class NoUsableConfiguration(override val reason: String) : Refused
    }
}

/**
 * Whether frame timestamps come from the audio device or from our own extrapolation. Spec I2's
 * timing claims rest on this, so it is reported rather than assumed — an extrapolated timestamp
 * presented as a device one is the same class of lie as an assumed latency called measured.
 */
public enum class TimestampProvenance { DeviceReported, ExtrapolatedFromStart }

public data class CaptureRead(val frames: Int, val firstFrame: Long)

/**
 * Microphone input as an [AnswerSource]. Declares [AnswerSource.polyphony] as `Polyphony.Mono`,
 * which is what lets the judge refuse polyphonic material honestly instead of scoring whichever
 * notes it happened to detect (docs/spec.md I3).
 *
 * It applies its own [AnswerSource.latency] correction, so a `PlayedNote` it emits is already true
 * time. The Conductor must not correct again — see `InputLatency`.
 */
public interface MicAnswerSource : AnswerSource, AudioResource {
    /** Re-measures input latency for the current audio route. Bluetooth is not the built-in mic. */
    public suspend fun calibrateLatency(): InputLatencyResult
}

/**
 * How loud the phone is willing to play. The calibration click goes out as media, so the slider
 * scales it: at a low setting the app's own speaker cannot reach its own microphone, and the only
 * honest thing to do is say so rather than report that nothing was heard.
 */
public fun interface MediaVolume {
    /** 0..1 of maximum, or null when it cannot be read. */
    public fun fraction(): Double?
}

public sealed interface InputLatencyResult {
    public data class Measured(val millis: Double, val confidence: Double) : InputLatencyResult

    /** Says why, so a report can distinguish "too noisy to measure" from "never attempted". */
    public data class Unmeasurable(val reason: String) : InputLatencyResult
}

/**
 * The system's monotonic clock, and the only implementation of [NanoClock] in production. Monotonic
 * matters: wall time can step backwards, which would send the playhead back mid-bar.
 */
public interface MonotonicClock : NanoClock
