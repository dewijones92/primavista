package com.dewijones92.primavista.score

/**
 * What a *key* is called, as opposed to what a *note* is called.
 *
 * A key on a keyboard has no notation behind it, so there is nothing to say whether it is F sharp
 * or G flat — the distinction [Pitch] exists to preserve simply is not present. Naming it takes the
 * usual keyboard convention: naturals where the key has one, sharps otherwise. Nothing that reads
 * notation may use this; it is for naming the thing under a finger.
 */
public fun Midi.asKey(): Pitch {
    val octave = Math.floorDiv(number, Pitch.SEMITONES_PER_OCTAVE) - 1
    val within = Math.floorMod(number, Pitch.SEMITONES_PER_OCTAVE)
    val natural = Letter.entries.firstOrNull { it.semitonesFromC == within }
    return when (natural) {
        null -> Pitch(sharpBelow(within), Alter.Sharp, octave)
        else -> Pitch(natural, Alter.Natural, octave)
    }
}

/** The letter a semitone below, which is the one a sharp raises onto this key. */
private fun sharpBelow(within: Int): Letter =
    Letter.entries.first { it.semitonesFromC == within - 1 }

/** As written: `C4`, `F#4`. */
public val Pitch.shortName: String
    get() = "${letter.name}${accidentalMark}$octave"

/** As spoken, for a screen reader: `C sharp 4`. `#` is read aloud as "hash" or "number sign". */
public val Pitch.spokenName: String
    get() = "${letter.name}$accidentalWord $octave"

private val Pitch.accidentalMark: String
    get() = when (alter) {
        Alter.DoubleFlat -> "bb"
        Alter.Flat -> "b"
        Alter.Sharp -> "#"
        Alter.DoubleSharp -> "##"
        else -> ""
    }

private val Pitch.accidentalWord: String
    get() = when (alter) {
        Alter.DoubleFlat -> " double flat"
        Alter.Flat -> " flat"
        Alter.Sharp -> " sharp"
        Alter.DoubleSharp -> " double sharp"
        else -> ""
    }
