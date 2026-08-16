package com.dewijones92.primavista.practice

import com.dewijones92.primavista.score.Alter
import com.dewijones92.primavista.score.Clef
import com.dewijones92.primavista.score.DifficultySpec
import com.dewijones92.primavista.score.KeySignature
import com.dewijones92.primavista.score.NoteSymbol
import com.dewijones92.primavista.score.PitchBand
import com.dewijones92.primavista.score.SkillTag
import com.dewijones92.primavista.score.Staff
import com.dewijones92.primavista.score.TimeSignature

private const val OPENING_BARS = 4
private const val LONGER_BARS = 6
private const val PIECE_LENGTH_BARS = 8

private const val WALKING_TEMPO_BPM = 60
private const val KEYS_TEMPO_BPM = 66
private const val LEGER_TEMPO_BPM = 72
private const val QUICKER_TEMPO_BPM = 80
private const val REAL_MUSIC_TEMPO_BPM = 84
private const val ONWARDS_TEMPO_BPM = 92

private const val STEPWISE_LEAP_SEMITONES = 4
private const val FIFTH_SEMITONES = 7
private const val OCTAVE_SEMITONES = 12
private const val TRIPLET = 3

/** How large a key signature each rung can READ, which is not the key it writes in. */
private const val TWO_ACCIDENTALS = 2

/** G, F, D and B flat: the four keys nearest home, two sharpwards and two flatwards. */
private val sharpAndFlatKeys = setOf(
    KeySignature(1),
    KeySignature(-1),
    KeySignature(TWO_ACCIDENTALS),
    KeySignature(-TWO_ACCIDENTALS),
)
private const val FOUR_ACCIDENTALS = 4

private val trebleStaff = staffMidiRange(Clef.Treble, KeySignature.C, WHOLE_STAFF_STEPS)
private val bassStaff = staffMidiRange(Clef.Bass, KeySignature.C, WHOLE_STAFF_STEPS)
private val trebleOffStaff = staffMidiRange(Clef.Treble, KeySignature.C, ONE_LEGER_LINE_STEPS)
private val bassOffStaff = staffMidiRange(Clef.Bass, KeySignature.C, ONE_LEGER_LINE_STEPS)

private val grandStaffClefs = mapOf(Staff.Upper to Clef.Treble, Staff.Lower to Clef.Bass)

/** The bottom rung's own material. Every later stage is this with dials turned. */
private val firstRung = DifficultySpec(
    staves = listOf(Staff.Upper),
    clefs = mapOf(Staff.Upper to Clef.Treble),
    keys = setOf(KeySignature.C),
    time = TimeSignature.FourFour,
    bars = OPENING_BARS,
    range = mapOf(Staff.Upper to staffMidiRange(Clef.Treble, KeySignature.C, bandSteps(PitchBand.MiddleStaff))),
    symbols = setOf(NoteSymbol.Whole, NoteSymbol.Half),
    maxDots = 0,
    allowTuplets = false,
    allowedAlterations = setOf(Alter.Natural),
    maxLeapSemitones = STEPWISE_LEAP_SEMITONES,
    tempoBpm = WALKING_TEMPO_BPM,
    bothHandsActive = false,
)

private class StageDraft(
    val title: String,
    val blurb: String,
    val skills: Set<SkillTag>,
    val evolve: (DifficultySpec) -> DifficultySpec,
)

/**
 * The ten rungs of docs/journey.md's table, each one the previous with dials turned. Written as
 * deltas so the cumulative property is structural: nothing can quietly drop quarter notes at
 * stage seven, because stage seven never restates the symbol set.
 */
private val drafts = listOf(
    StageDraft(
        title = "The five lines",
        blurb = "The middle of the treble staff, in long slow notes. Nowhere to get lost.",
        skills = setOf(
            SkillTag.ClefRegion(Clef.Treble, PitchBand.MiddleStaff),
            SkillTag.RhythmFigure(NoteSymbol.Whole, dots = 0, tupletNumerator = 1),
            SkillTag.RhythmFigure(NoteSymbol.Half, dots = 0, tupletNumerator = 1),
            SkillTag.KeyReading(fifths = 0),
        ),
        evolve = { it },
    ),
    StageDraft(
        title = "Stepping out",
        blurb = "The whole treble staff now, with quarter notes to keep you moving.",
        skills = setOf(
            SkillTag.ClefRegion(Clef.Treble, PitchBand.LowerStaff),
            SkillTag.ClefRegion(Clef.Treble, PitchBand.UpperStaff),
            SkillTag.RhythmFigure(NoteSymbol.Quarter, dots = 0, tupletNumerator = 1),
        ),
        evolve = {
            it.copy(
                range = mapOf(Staff.Upper to trebleStaff),
                symbols = it.symbols + NoteSymbol.Quarter,
            )
        },
    ),
    StageDraft(
        title = "The other clef",
        blurb = "The bass clef — the left hand's staff — learned the same way as the first.",
        skills = setOf(
            SkillTag.ClefRegion(Clef.Bass, PitchBand.LowerStaff),
            SkillTag.ClefRegion(Clef.Bass, PitchBand.MiddleStaff),
            SkillTag.ClefRegion(Clef.Bass, PitchBand.UpperStaff),
        ),
        evolve = {
            it.copy(
                clefs = mapOf(Staff.Upper to Clef.Bass),
                range = mapOf(Staff.Upper to bassStaff),
            )
        },
    ),
    StageDraft(
        title = "Both hands",
        blurb = "Two staves at once. This is what piano music actually looks like.",
        skills = setOf(SkillTag.HandIndependence),
        evolve = {
            it.copy(
                staves = listOf(Staff.Upper, Staff.Lower),
                clefs = grandStaffClefs,
                range = mapOf(Staff.Upper to trebleStaff, Staff.Lower to bassStaff),
                bothHandsActive = true,
            )
        },
    ),
    StageDraft(
        title = "Sharps and flats",
        blurb = "An accidental printed in front of the note, changing it for that bar.",
        skills = setOf(SkillTag.Accidental(Alter.Sharp), SkillTag.Accidental(Alter.Flat)),
        evolve = { it.copy(allowedAlterations = setOf(Alter.Natural, Alter.Sharp, Alter.Flat)) },
    ),
    StageDraft(
        title = "Keys",
        blurb = "Sharps and flats at the front of the line, implied on every note they name.",
        skills = setOf(
            SkillTag.KeyReading(fifths = 1),
            SkillTag.KeyReading(fifths = -1),
            SkillTag.KeyReading(fifths = TWO_ACCIDENTALS),
            SkillTag.KeyReading(fifths = -TWO_ACCIDENTALS),
        ),
        evolve = {
            it.copy(
                // It writes in all four, so it may claim all four: a stage's claims are exactly
                // what its own material tests, and CurriculumTest holds that line.
                keys = sharpAndFlatKeys,
                maxKeyAccidentals = TWO_ACCIDENTALS,
                tempoBpm = KEYS_TEMPO_BPM,
            )
        },
    ),
    StageDraft(
        title = "Off the staff",
        blurb = "Notes above and below the staff, on their own short lines.",
        skills = setOf(
            SkillTag.LegerLines(Clef.Treble, count = 1, above = true),
            SkillTag.LegerLines(Clef.Treble, count = 1, above = false),
            SkillTag.LegerLines(Clef.Bass, count = 1, above = true),
            SkillTag.LegerLines(Clef.Bass, count = 1, above = false),
        ),
        evolve = {
            it.copy(
                range = mapOf(Staff.Upper to trebleOffStaff, Staff.Lower to bassOffStaff),
                bars = LONGER_BARS,
                tempoBpm = LEGER_TEMPO_BPM,
            )
        },
    ),
    StageDraft(
        title = "Quicker",
        blurb = "Eighths, sixteenths and dots. The same reading, with less time to do it.",
        skills = setOf(
            SkillTag.RhythmFigure(NoteSymbol.Eighth, dots = 0, tupletNumerator = 1),
            SkillTag.RhythmFigure(NoteSymbol.Sixteenth, dots = 0, tupletNumerator = 1),
            SkillTag.RhythmFigure(NoteSymbol.Quarter, dots = 1, tupletNumerator = 1),
        ),
        evolve = {
            it.copy(
                symbols = it.symbols + NoteSymbol.Eighth + NoteSymbol.Sixteenth,
                maxDots = 1,
                tempoBpm = QUICKER_TEMPO_BPM,
            )
        },
    ),
    StageDraft(
        title = "Real music",
        blurb = "Longer lines at a real tempo, in the keys real music is actually written in.",
        skills = setOf(SkillTag.Leap(FIFTH_SEMITONES)),
        evolve = {
            it.copy(
                bars = PIECE_LENGTH_BARS,
                maxKeyAccidentals = FOUR_ACCIDENTALS,
                maxLeapSemitones = FIFTH_SEMITONES,
                tempoBpm = REAL_MUSIC_TEMPO_BPM,
            )
        },
    ),
    StageDraft(
        title = "Onwards",
        blurb = "Every key, triplets, wider leaps, faster tempi. There is no top to this one.",
        skills = setOf(
            SkillTag.Leap(OCTAVE_SEMITONES),
            SkillTag.RhythmFigure(NoteSymbol.Quarter, dots = 0, tupletNumerator = TRIPLET),
        ),
        evolve = {
            it.copy(
                allowTuplets = true,
                maxKeyAccidentals = KeySignature.MAX_FIFTHS,
                maxLeapSemitones = OCTAVE_SEMITONES,
                tempoBpm = ONWARDS_TEMPO_BPM,
            )
        },
    ),
)

internal fun standardStages(): List<Stage> =
    drafts.foldIndexed(emptyList<Stage>()) { index, built, draft ->
        built + Stage(
            id = StageId(index + StageId.FIRST),
            title = draft.title,
            blurb = draft.blurb,
            skills = draft.skills,
            spec = draft.evolve(built.lastOrNull()?.spec ?: firstRung),
        )
    }
