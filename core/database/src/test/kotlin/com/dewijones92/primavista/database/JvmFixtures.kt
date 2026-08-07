package com.dewijones92.primavista.database

import com.dewijones92.primavista.score.Alter
import com.dewijones92.primavista.score.Clef
import com.dewijones92.primavista.score.DifficultySpec
import com.dewijones92.primavista.score.KeySignature
import com.dewijones92.primavista.score.Midi
import com.dewijones92.primavista.score.NoteSymbol
import com.dewijones92.primavista.score.Staff
import com.dewijones92.primavista.score.TimeSignature

/** Deliberately duplicated from the androidTest fixtures; see `.claude/CODE-NOTES.md`. */
internal fun sampleSpec(): DifficultySpec = DifficultySpec(
    staves = listOf(Staff.Upper, Staff.Lower),
    clefs = mapOf(Staff.Upper to Clef.Treble, Staff.Lower to Clef.Bass),
    key = KeySignature(-3),
    time = TimeSignature(3, 4),
    bars = 8,
    range = mapOf(
        Staff.Upper to Midi(60)..Midi(84),
        Staff.Lower to Midi(36)..Midi(60),
    ),
    symbols = setOf(NoteSymbol.Quarter, NoteSymbol.Eighth, NoteSymbol.Half),
    maxDots = 1,
    allowTuplets = true,
    allowedAlterations = setOf(Alter.Flat, Alter.Natural, Alter.Sharp),
    maxLeapSemitones = 7,
    tempoBpm = 76,
    bothHandsActive = true,
)
