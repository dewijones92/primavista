package com.dewijones92.primavista.database

import com.dewijones92.primavista.score.Clef
import com.dewijones92.primavista.score.Midi
import com.dewijones92.primavista.score.NoteSymbol
import com.dewijones92.primavista.score.PitchBand
import com.dewijones92.primavista.score.Staff

/**
 * Shared by [SkillTagKeys] and [DifficultyCodec] so the two stored formats cannot disagree about
 * how an enum or a MIDI range is spelled. Every function throws [IllegalArgumentException] on
 * something unreadable; the codecs turn that into a null at their boundary.
 */
internal const val MIDI_SPAN_SEPARATOR: String = "-"

internal fun intOf(text: String): Int = requireNotNull(text.toIntOrNull()) { "'$text' is not an integer" }

internal fun boolOf(text: String): Boolean =
    requireNotNull(text.toBooleanStrictOrNull()) { "'$text' is not true or false" }

internal fun staffOf(name: String): Staff =
    requireNotNull(Staff.entries.firstOrNull { it.name == name }) { "no staff named '$name'" }

internal fun clefOf(name: String): Clef =
    requireNotNull(Clef.entries.firstOrNull { it.name == name }) { "no clef named '$name'" }

internal fun bandOf(name: String): PitchBand =
    requireNotNull(PitchBand.entries.firstOrNull { it.name == name }) { "no pitch band named '$name'" }

internal fun symbolOf(name: String): NoteSymbol =
    requireNotNull(NoteSymbol.entries.firstOrNull { it.name == name }) { "no note symbol named '$name'" }

internal fun encodeMidiRange(range: ClosedRange<Midi>): String =
    "${range.start.number}$MIDI_SPAN_SEPARATOR${range.endInclusive.number}"

internal fun midiRangeOf(encoded: String): ClosedRange<Midi> {
    val bounds = encoded.split(MIDI_SPAN_SEPARATOR)
    require(bounds.size == MIDI_RANGE_BOUNDS) { "'$encoded' is not a MIDI range" }
    return Midi(intOf(bounds.first()))..Midi(intOf(bounds.last()))
}

private const val MIDI_RANGE_BOUNDS = 2
