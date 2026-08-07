package com.dewijones92.primavista.pitch

import kotlin.math.log2
import kotlin.math.pow

private const val CENTS_PER_OCTAVE: Int = Tuning.CENTS_PER_SEMITONE * Tuning.SEMITONES_PER_OCTAVE

/** Signed interval from [reference] to [other]; positive means [other] sounds higher. */
public fun centsBetween(reference: Hertz, other: Hertz): Double =
    CENTS_PER_OCTAVE * log2(other.value / reference.value)

public fun Hertz.shiftedByCents(cents: Double): Hertz =
    Hertz(value * 2.0.pow(cents / CENTS_PER_OCTAVE))
