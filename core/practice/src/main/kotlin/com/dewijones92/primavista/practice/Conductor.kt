package com.dewijones92.primavista.practice

import com.dewijones92.primavista.score.Ticks

/**
 * The only source of "now". A port so every test runs in fake time and no test sleeps.
 * Must be monotonic — wall-clock time can jump backwards, which would make the playhead
 * travel backwards mid-bar.
 */
public fun interface NanoClock {
    public fun nowNanos(): Long
}

/**
 * A pure mapping between musical position and wall time, with no clock attached.
 *
 * Split out from [Conductor] deliberately: [PerformanceJudge] needs the mapping but has no business
 * being able to ask what time it is. A pure judge is a reproducible judge, which is what lets a
 * diagnostics report re-derive the same verdicts later (docs/spec.md I2).
 *
 * **Nanos are absolute readings of the same monotonic clock [PlayedNote.atNanos] came from**, not
 * durations since the start. They have to be, or an input's timestamp could not be compared against
 * the music at all.
 *
 * **Implementations must be immutable snapshots.** The first implementation handed the live
 * `Conductor` to the judge, and a session containing a pause then re-judged *differently from a
 * report of itself* — every Correct became an Extra plus a Missed, because the mapping had moved on
 * since the notes were played. A snapshot has to record the pauses that happened, not the transport
 * that is still happening. A session that pauses gets a *newer* snapshot through
 * [PerformanceJudge.retime]; it never gets a map that keeps moving.
 */
public interface TickTiming {
    public fun nanosFor(position: Ticks): Long

    public fun ticksAt(nanos: Long): Ticks

    /** Music sounded up to [position], excluding pauses. Every judged interval. See .claude/CODE-NOTES.md. */
    public fun elapsedNanosAt(position: Ticks): Long
}

public enum class TransportState { Idle, CountingIn, Running, Paused, Finished }

/**
 * Owns musical↔wall time for a session, and is the **only** thing in the app that does.
 * The scroll offset, the judging window and the metronome click all read from here, because
 * three independent derivations of "now" is precisely the bug class docs/spec.md I1 exists to
 * prevent.
 *
 * Read by **sampling** [position] on a frame clock. Do not expose this as a flow of state and
 * collect it: a `StateFlow` conflates equal values, so "the position has not moved" becomes
 * indistinguishable from "no emission arrived", and Totum lost a week to a stall watchdog that
 * could never fire for that exact reason.
 */
public interface Conductor : TickTiming {
    public val state: TransportState

    public val tempoBpm: Int

    /** Where the music is, sampled now. Negative during count-in. */
    public fun position(): Ticks

    /** Beats of count-in remaining, or 0 once running. */
    public fun countInBeatsRemaining(): Int

    public fun start(fromPosition: Ticks = Ticks.ZERO)

    public fun pause()

    public fun resume()

    /** Ends the session at the current position, which stays readable: "how far did he get" matters. */
    public fun stop()

    /**
     * An immutable record of the tempo map **including every pause that actually happened**, safe to
     * hand to [PerformanceJudge] and to store for later re-judging.
     *
     * This is the only thing the judge may be given. See [TickTiming] for what went wrong when it
     * was handed the live transport instead.
     */
    public fun timingSnapshot(): TickTiming
}

/**
 * How late an input arrives relative to the moment it physically happened, and — as important —
 * whether that figure was actually measured. An assumed latency presented as a measured one is the
 * failure mode docs/todos/measure-audio-latency.md is about, so the provenance travels with the
 * number and into every log line.
 *
 * **The adapter applies its own correction, and the Conductor never does.** Latency is a property
 * of one input device, so an app with two sources attached would need the Conductor to know which
 * source each note came from — which it deliberately cannot. So a [PlayedNote.atNanos] is already
 * true time by the moment it exists, and this value is carried for reporting, not for a second
 * subtraction. Correcting in both places was a live double-count until 2026-08-07.
 */
public data class InputLatency(
    val millis: Double,
    val provenance: Provenance,
) {
    public enum class Provenance { Measured, PlatformReported, Assumed, NotApplicable }

    public companion object {
        /** Taps are stamped by the input system at the touch; there is nothing to correct. */
        public val None: InputLatency = InputLatency(0.0, Provenance.NotApplicable)
    }
}
