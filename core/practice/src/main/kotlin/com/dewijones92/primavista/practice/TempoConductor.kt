package com.dewijones92.primavista.practice

import com.dewijones92.primavista.score.MusicalTime
import com.dewijones92.primavista.score.Ticks
import com.dewijones92.primavista.score.TimeSignature

/**
 * The one implementation of the app's musical↔wall time mapping.
 *
 * Sample [position]; never cache it and never wrap it in a flow of state. See CLAUDE.md's timing
 * rules and docs/spec.md I1.
 */
public class TempoConductor(
    private val clock: NanoClock,
    override val tempoBpm: Int,
    private val countInBeats: Int = 0,
    time: TimeSignature = TimeSignature.FourFour,
) : Conductor {
    init {
        require(tempoBpm > 0) { "$tempoBpm bpm is not a tempo" }
        require(countInBeats >= 0) { "a count-in of $countInBeats beats is not a count-in" }
    }

    private enum class Phase { Idle, Active, Paused, Ended }

    private val map = TempoTickMap(tempoBpm, time.beatUnit)

    private var phase: Phase = Phase.Idle
    private var frozenTicks: Long = 0L
    private var pausedAtNanos: Long = 0L
    private var countInEndsAt: Long = 0L
    private var legs: List<TempoLeg> = listOf(TempoLeg(0L, 0L))
    private var timeline: TempoTimeline = TempoTimeline(map, legs)

    private val currentTicks: Long
        get() = when (phase) {
            Phase.Idle, Phase.Paused, Phase.Ended -> frozenTicks
            Phase.Active -> timeline.ticksAt(clock.nowNanos()).value
        }

    override val state: TransportState
        get() = when (phase) {
            Phase.Idle -> TransportState.Idle
            Phase.Paused -> TransportState.Paused
            Phase.Ended -> TransportState.Finished
            Phase.Active -> if (currentTicks < countInEndsAt) TransportState.CountingIn else TransportState.Running
        }

    override fun position(): Ticks = Ticks(currentTicks)

    override fun countInBeatsRemaining(): Int {
        val remaining = countInEndsAt - currentTicks
        return if (remaining <= 0) 0 else ceilDiv(remaining, map.ticksPerBeat).toInt()
    }

    override fun start(fromPosition: Ticks) {
        val begin = fromPosition.value - countInBeats * map.ticksPerBeat
        frozenTicks = begin
        countInEndsAt = fromPosition.value
        phase = Phase.Active
        relayTo(listOf(TempoLeg(begin, clock.nowNanos() - map.nanosOfTicks(begin))))
    }

    override fun pause() {
        if (phase != Phase.Active) return
        frozenTicks = currentTicks
        pausedAtNanos = clock.nowNanos()
        phase = Phase.Paused
    }

    override fun resume() {
        if (phase != Phase.Paused) return
        val held = clock.nowNanos() - pausedAtNanos
        relayTo(legs + TempoLeg(frozenTicks, legs.last().originNanos + held))
        phase = Phase.Active
    }

    override fun stop() {
        if (phase == Phase.Idle) return
        frozenTicks = currentTicks
        phase = Phase.Ended
    }

    override fun timingSnapshot(): TickTiming = timeline

    override fun nanosFor(position: Ticks): Long = timeline.nanosFor(position)

    override fun ticksAt(nanos: Long): Ticks {
        val ticks = timeline.ticksAt(nanos)
        return if (phase == Phase.Paused && ticks.value > frozenTicks) Ticks(frozenTicks) else ticks
    }

    override fun elapsedNanosAt(position: Ticks): Long = timeline.elapsedNanosAt(position)

    private fun relayTo(next: List<TempoLeg>) {
        legs = next.toList()
        timeline = TempoTimeline(map, legs)
    }
}

/** One stretch of the tempo map: from [fromTicks] onwards, position 0 sits at [originNanos]. */
internal data class TempoLeg(val fromTicks: Long, val originNanos: Long)

/**
 * An immutable tempo map, piecewise over the pauses that actually happened.
 * See .claude/CODE-NOTES.md.
 */
internal class TempoTimeline(
    private val map: TempoTickMap,
    private val legs: List<TempoLeg>,
) : TickTiming {
    override fun nanosFor(position: Ticks): Long =
        legAt(position.value).originNanos + map.nanosOfTicks(position.value)

    override fun ticksAt(nanos: Long): Ticks {
        val index = legs.indices.lastOrNull { nanos >= startNanosOf(legs[it]) } ?: 0
        val ticks = map.ticksOfNanos(nanos - legs[index].originNanos)
        val next = legs.getOrNull(index + 1) ?: return Ticks(ticks)
        return Ticks(minOf(ticks, next.fromTicks))
    }

    override fun elapsedNanosAt(position: Ticks): Long = map.nanosOfTicks(position.value)

    private fun startNanosOf(leg: TempoLeg): Long = leg.originNanos + map.nanosOfTicks(leg.fromTicks)

    private fun legAt(ticks: Long): TempoLeg = legs.lastOrNull { it.fromTicks < ticks } ?: legs.first()
}

/** Exact integer tick↔nanosecond arithmetic at one tempo. See .claude/CODE-NOTES.md. */
internal class TempoTickMap(tempoBpm: Int, beatUnit: Int) {
    val ticksPerBeat: Long = MusicalTime.TICKS_PER_QUARTER * MusicalTime.QUARTERS_PER_WHOLE / beatUnit

    private val ticksPerMinute: Long = ticksPerBeat * tempoBpm

    fun nanosOfTicks(ticks: Long): Long {
        val minutes = ticks / ticksPerMinute
        val remainder = ticks % ticksPerMinute
        return minutes * Nanos.PER_MINUTE + remainder * Nanos.PER_MINUTE / ticksPerMinute
    }

    fun ticksOfNanos(nanos: Long): Long {
        val minutes = nanos / Nanos.PER_MINUTE
        val remainder = nanos % Nanos.PER_MINUTE
        return minutes * ticksPerMinute + roundedDiv(remainder * ticksPerMinute, Nanos.PER_MINUTE)
    }
}

private fun roundedDiv(numerator: Long, denominator: Long): Long =
    if (numerator >= 0) {
        (numerator + denominator / 2) / denominator
    } else {
        -((-numerator + denominator / 2) / denominator)
    }

private fun ceilDiv(numerator: Long, denominator: Long): Long = (numerator + denominator - 1) / denominator
