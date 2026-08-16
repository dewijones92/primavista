package com.dewijones92.primavista.score

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

private const val DEFAULT_TEMPO_BPM = 80
private const val WALKING_TEMPO_BPM = 60
private const val DEFAULT_LEAP_SEMITONES = 7
private const val STEPWISE_LEAP_SEMITONES = 4
private const val SHARP_KEY_FIFTHS = 2

/** The grand-staff default every generator test starts from. Shared so there is one of it. */
internal fun spec(
    bars: Int = 4,
    time: TimeSignature = TimeSignature.FourFour,
    keys: Set<KeySignature> = setOf(KeySignature.C),
    symbols: Set<NoteSymbol> = setOf(NoteSymbol.Half, NoteSymbol.Quarter, NoteSymbol.Eighth),
    maxDots: Int = 0,
    allowTuplets: Boolean = false,
    allowedAlterations: Set<Alter> = setOf(Alter.Natural),
    maxLeapSemitones: Int = DEFAULT_LEAP_SEMITONES,
    bothHandsActive: Boolean = true,
): DifficultySpec = DifficultySpec(
    staves = listOf(Staff.Upper, Staff.Lower),
    clefs = mapOf(Staff.Upper to Clef.Treble, Staff.Lower to Clef.Bass),
    keys = keys,
    time = time,
    bars = bars,
    range = mapOf(
        Staff.Upper to Midi(60)..Midi(79),
        Staff.Lower to Midi(41)..Midi(60),
    ),
    symbols = symbols,
    maxDots = maxDots,
    allowTuplets = allowTuplets,
    allowedAlterations = allowedAlterations,
    maxLeapSemitones = maxLeapSemitones,
    tempoBpm = DEFAULT_TEMPO_BPM,
    bothHandsActive = bothHandsActive,
)

private fun oneStaff(
    time: TimeSignature = TimeSignature.FourFour,
    keys: Set<KeySignature> = setOf(KeySignature.C),
    symbols: Set<NoteSymbol>,
): DifficultySpec = DifficultySpec(
    staves = listOf(Staff.Upper),
    clefs = mapOf(Staff.Upper to Clef.Treble),
    keys = keys,
    time = time,
    bars = 4,
    range = mapOf(Staff.Upper to Midi(60)..Midi(79)),
    symbols = symbols,
    maxDots = 0,
    allowTuplets = false,
    allowedAlterations = setOf(Alter.Natural),
    maxLeapSemitones = STEPWISE_LEAP_SEMITONES,
    tempoBpm = WALKING_TEMPO_BPM,
    bothHandsActive = false,
)

/**
 * The shapes a drill is actually built from, spread across the metres and note vocabularies the
 * curriculum walks through. A targeting bug shows up in one metre and not another, which is how
 * the dotted-half defect survived — see `.claude/CODE-NOTES.md`.
 */
internal val drillBases: Map<String, DifficultySpec> = mapOf(
    "grand 4/4" to spec(),
    "one staff 4/4, long values" to oneStaff(symbols = setOf(NoteSymbol.Whole, NoteSymbol.Half)),
    "one staff 3/4" to oneStaff(
        time = TimeSignature(3, 4),
        symbols = setOf(NoteSymbol.Quarter, NoteSymbol.Half),
    ),
    "one staff 6/8" to oneStaff(
        time = TimeSignature(6, 8),
        symbols = setOf(NoteSymbol.Eighth, NoteSymbol.Quarter),
    ),
    "one staff in two sharps" to oneStaff(
        keys = setOf(KeySignature(SHARP_KEY_FIFTHS)),
        symbols = setOf(NoteSymbol.Quarter, NoteSymbol.Half),
    ),
)

/** A short bar is inherited by the layout engine, the judge and the scroll offset alike. */
internal fun assertBarsAddUp(spec: DifficultySpec, score: Score, what: String = "") {
    for (measure in score.measures) {
        val barEnd = measure.start + measure.time.measureTicks
        for (staff in spec.staves) {
            val inBar = score.events.filter { it.staff == staff && it.onset >= measure.start && it.onset < barEnd }
            assertTrue("$what bar ${measure.number} of $staff is empty", inBar.isNotEmpty())
            assertEquals(
                "$what bar ${measure.number} of $staff",
                measure.time.measureTicks.value,
                inBar.sumOf { it.duration.ticks.value },
            )
        }
    }
}
