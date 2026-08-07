package com.dewijones92.primavista.audio

import com.dewijones92.primavista.pitch.Hertz
import com.dewijones92.primavista.pitch.Tuning
import com.dewijones92.primavista.score.Midi

/** A detected frequency read as a sounding note plus how far off equal temperament it was. */
public data class PitchEstimate(val midi: Midi, val centsOff: Double)

/**
 * Hertz to [Midi], the one place `:lib:pitch`'s frequencies become notes.
 * See .claude/CODE-NOTES.md.
 */
public object PitchMapping {
    /** Null rather than a throw when the frequency lands outside [Midi.MIN]..[Midi.MAX]. */
    public fun estimate(hertz: Hertz): PitchEstimate? {
        val fractional = Tuning.midiOf(hertz)
        if (!fractional.isFinite()) return null
        val nearest = Math.round(fractional)
        if (nearest < Midi.MIN || nearest > Midi.MAX) return null
        val centsOff = (fractional - nearest) * Tuning.CENTS_PER_SEMITONE
        return PitchEstimate(Midi(nearest.toInt()), centsOff)
    }
}
