package com.dewijones92.primavista.audio

import com.dewijones92.primavista.score.MusicalTime
import com.dewijones92.primavista.score.Ticks
import com.dewijones92.primavista.score.TimeSignature

/** A beat the music has just passed. [indexFromBarStart] is negative in a pickup. */
public data class Beat(val indexFromBarStart: Long, val indexInBar: Int) {
    public val isAccent: Boolean get() = indexInBar == 0
}

/**
 * Decides whether a sampled musical position has crossed a beat. Holds no clock of its own —
 * that is the point (docs/spec.md I1). See .claude/CODE-NOTES.md.
 */
public class BeatCrossing(time: TimeSignature, barStart: Ticks = Ticks.ZERO) {
    public val beatsPerBar: Int = time.beats

    public val barStartTicks: Long = barStart.value

    public val beatTicks: Long = run {
        val whole = MusicalTime.TICKS_PER_QUARTER * QUARTERS_PER_WHOLE
        require(whole % time.beatUnit == 0L) { "beat unit ${time.beatUnit} does not divide a whole note" }
        whole / time.beatUnit
    }

    private var lastBeat: Long? = null

    public fun reset() {
        lastBeat = null
    }

    /** The beat just crossed, or null. Silent on a backwards jump: a seek is not a beat. */
    public fun crossed(position: Ticks): Beat? {
        val fromBarStart = position.value - barStartTicks
        val beat = Math.floorDiv(fromBarStart, beatTicks)
        val previous = lastBeat
        lastBeat = beat
        val fires = when {
            previous == null -> withinLeadIn(fromBarStart, beat)
            else -> beat > previous
        }
        return if (fires) beatAt(beat) else null
    }

    /**
     * First sample after a start fires only if it is near the beat; a resume mid-beat stays
     * silent until the next one.
     */
    private fun withinLeadIn(fromBarStart: Long, beat: Long): Boolean {
        val intoBeat = fromBarStart - beat * beatTicks
        return intoBeat * LEAD_IN_DIVISOR <= beatTicks
    }

    private fun beatAt(beat: Long): Beat =
        Beat(beat, Math.floorMod(beat, beatsPerBar.toLong()).toInt())

    public companion object {
        public const val QUARTERS_PER_WHOLE: Long = 4L

        /** A first click is allowed within the opening quarter of its beat. */
        public const val LEAD_IN_DIVISOR: Long = 4L
    }
}
