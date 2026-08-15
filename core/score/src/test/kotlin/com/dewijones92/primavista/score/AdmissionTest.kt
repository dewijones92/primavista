package com.dewijones92.primavista.score

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SEEDS = 40L
private const val TWO_SHARPS = 2
private const val THREE_FLATS = -3
private const val THIRD_SEMITONES = 4
private const val FOUR_ACCIDENTALS = 4
private const val FIVE_ACCIDENTALS = 5

class AdmissionTest {

    private val generator = SeededExerciseGenerator()

    /**
     * The property that makes this type worth having: the generator writes inside a spec's dials,
     * so the spec must admit what it wrote. A disagreement here is the `specTargeting` family of
     * defect recorded in CLAUDE.md, caught by the compiler's nearest neighbour rather than by Dewi.
     */
    @Test
    fun `every exercise a spec generates is admitted by that spec`() {
        val specs = mapOf(
            "grand staff" to spec(),
            "one sharp" to spec(key = KeySignature(1)),
            "dotted" to spec(maxDots = 1),
            "hands separate" to spec(bothHandsActive = false),
            "three four" to spec(time = TimeSignature(3, 4)),
            "sharps and flats" to spec(allowedAlterations = setOf(Alter.Natural, Alter.Sharp, Alter.Flat)),
        )
        val refused = specs.flatMap { (name, spec) ->
            (1L..SEEDS).mapNotNull { seed ->
                when (val verdict = spec.admits(generator.generate(seed, spec))) {
                    is Admission.Admitted -> null
                    is Admission.Refused -> "$name seed $seed: ${verdict.reasons}"
                }
            }
        }
        assertEquals(emptyList<String>(), refused)
    }

    /**
     * A writing dial, not a reading ceiling. Every pitch in an admitted passage is already one this
     * level reads, so the jump between two of them asks nothing new of the eye — and read as a gate
     * it put *Ode to Joy* on the tenth rung. See the note on `admits`.
     */
    @Test
    fun `a leap wider than the level would write is not a refusal`() {
        val stepwise = spec(maxLeapSemitones = THIRD_SEMITONES)
        val leaping = scoreOf(Pitch(Letter.C, Alter.Natural, 4), Pitch(Letter.C, Alter.Natural, 5))
        assertTrue(stepwise.admits(leaping).isAdmitted)
    }

    @Test
    fun `a leap out of the level's range is still refused, because the note itself is`() {
        val stepwise = spec(maxLeapSemitones = THIRD_SEMITONES)
        val offTheEnd = scoreOf(Pitch(Letter.C, Alter.Natural, 4), Pitch(Letter.C, Alter.Natural, 7))
        assertFalse(stepwise.admits(offTheEnd).isAdmitted)
    }

    /** In G major an F sharp is the key signature doing its job, not something extra to read. */
    @Test
    fun `a note the key signature implies is not an accidental`() {
        val naturalsOnly = spec(key = KeySignature(1), allowedAlterations = setOf(Alter.Natural))
        val inG = scoreOf(Pitch(Letter.F, Alter.Sharp, 5), Pitch(Letter.G, Alter.Natural, 5))
            .let { score -> score.copy(measures = score.measures.map { it.copy(key = KeySignature(1)) }) }
        assertTrue(naturalsOnly.admits(inG).isAdmitted)
    }

    /**
     * The dial that stopped the whole ten-stage path capping at one sharp. [DifficultySpec.key] is
     * what a level *writes* in; this is what it can *read*, and real music is in every key.
     */
    @Test
    fun `a level reads up to its ceiling, not up to the key it writes in`() {
        val writesInG = spec(key = KeySignature(1)).copy(maxKeyAccidentals = FOUR_ACCIDENTALS)
        assertTrue(writesInG.admits(inKey(KeySignature(-FOUR_ACCIDENTALS))).isAdmitted)
        assertFalse(writesInG.admits(inKey(KeySignature(FIVE_ACCIDENTALS))).isAdmitted)
    }

    /** `copy(key = …)` does not re-evaluate defaults, so the floor has to be derived, not required. */
    @Test
    fun `a level can always read the key it writes in, even after a copy`() {
        val copied = spec(key = KeySignature.C).copy(key = KeySignature(FIVE_ACCIDENTALS))
        assertEquals(0, copied.maxKeyAccidentals)
        assertEquals(FIVE_ACCIDENTALS, copied.readableKeyAccidentals)
        assertTrue(copied.admits(inKey(KeySignature(FIVE_ACCIDENTALS))).isAdmitted)
    }

    @Test
    fun `a signature is judged on how many accidentals it carries, not which ones`() {
        val twoSharps = spec(key = KeySignature(TWO_SHARPS))
        assertTrue(
            "two flats is the same size as two sharps",
            twoSharps.admits(inKey(KeySignature(-TWO_SHARPS))).isAdmitted
        )
        assertFalse("three flats is bigger", twoSharps.admits(inKey(KeySignature(THREE_FLATS))).isAdmitted)
    }

    /** Metre is not a skill this app teaches, so a spec's time signature must not gate real music. */
    @Test
    fun `a different time signature is not a refusal`() {
        assertTrue(spec(time = TimeSignature.FourFour).admits(inTime(TimeSignature(3, 4))).isAdmitted)
        assertTrue(spec(time = TimeSignature(3, 4)).admits(inTime(TimeSignature(6, 8))).isAdmitted)
    }

    @Test
    fun `a refusal names every dial the music exceeded, not just the first`() {
        val narrow = spec(symbols = setOf(NoteSymbol.Quarter), allowedAlterations = setOf(Alter.Natural))
        val verdict = narrow.admits(
            scoreOf(Pitch(Letter.C, Alter.Natural, 4), Pitch(Letter.F, Alter.Sharp, 4), symbol = NoteSymbol.Whole),
        ) as Admission.Refused
        assertEquals(listOf("Whole notes", "an accidental of 1 semitones"), verdict.reasons)
    }

    @Test
    fun `hands-separate material refuses a piece that uses both`() {
        val separate = spec(bothHandsActive = false)
        val both = scoreOf(
            Pitch(Letter.C, Alter.Natural, 5),
            Pitch(Letter.C, Alter.Natural, 3),
            lowerStaffForSecond = true,
        )
        assertFalse(separate.admits(both).isAdmitted)
    }

    private fun scoreOf(
        first: Pitch,
        second: Pitch,
        lowerStaffForSecond: Boolean = false,
        symbol: NoteSymbol = NoteSymbol.Half,
    ): Score {
        val duration = Duration(symbol)
        val secondStaff = if (lowerStaffForSecond) Staff.Lower else Staff.Upper
        return Score(
            id = ScoreId("admission-test"),
            title = "test",
            composer = null,
            origin = ScoreOrigin.Parsed("test", "n/a"),
            staves = listOf(Staff.Upper, Staff.Lower),
            measures = listOf(
                Measure(
                    index = 0,
                    start = Ticks.ZERO,
                    time = TimeSignature.FourFour,
                    key = KeySignature.C,
                    clefs = mapOf(Staff.Upper to Clef.Treble, Staff.Lower to Clef.Bass),
                ),
            ),
            events = listOf(
                Note(Ticks.ZERO, duration, Staff.Upper, voice = 1, pitch = first),
                Note(duration.ticks, duration, secondStaff, voice = 1, pitch = second),
            ),
            defaultTempoBpm = 80,
        )
    }

    private fun inKey(key: KeySignature): Score =
        scoreOf(Pitch(Letter.C, Alter.Natural, 4), Pitch(Letter.D, Alter.Natural, 4)).let { score ->
            score.copy(measures = score.measures.map { it.copy(key = key) })
        }

    private fun inTime(time: TimeSignature): Score =
        scoreOf(Pitch(Letter.C, Alter.Natural, 4), Pitch(Letter.D, Alter.Natural, 4)).let { score ->
            score.copy(measures = score.measures.map { it.copy(time = time) })
        }
}
