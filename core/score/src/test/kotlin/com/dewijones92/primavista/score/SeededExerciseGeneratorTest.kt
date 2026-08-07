package com.dewijones92.primavista.score

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SeededExerciseGeneratorTest {

    private val generator = SeededExerciseGenerator()
    private val skills = DerivedScoreSkills()

    @Test
    fun `the same seed and spec give the identical score, every time`() {
        val spec = spec()
        assertEquals(generator.generate(SEED, spec), generator.generate(SEED, spec))
        assertEquals(generator.generate(SEED, spec), generator.generate(SEED, spec.copy()))
    }

    @Test
    fun `a different seed gives different music`() {
        val spec = spec()
        assertNotEquals(
            generator.generate(SEED, spec).notes.map { it.pitch },
            generator.generate(SEED + 1, spec).notes.map { it.pitch },
        )
    }

    @Test
    fun `the seed and spec that made a score travel with it`() {
        val spec = spec()
        assertEquals(ScoreOrigin.Generated(SEED, spec), generator.generate(SEED, spec).origin)
    }

    @Test
    fun `every bar of every staff adds up to exactly the bar length`() {
        val specs = listOf(
            spec(),
            spec(time = TimeSignature(3, 4)),
            spec(time = TimeSignature(6, 8), symbols = setOf(NoteSymbol.Eighth, NoteSymbol.Quarter)),
            spec(time = TimeSignature(2, 2), symbols = setOf(NoteSymbol.Half, NoteSymbol.Whole)),
            spec(maxDots = 2, allowTuplets = true),
            spec(symbols = setOf(NoteSymbol.Sixteenth, NoteSymbol.Eighth), maxDots = 1, allowTuplets = true),
        )
        for (candidate in specs) {
            for (seed in 1L..12L) {
                assertBarsAddUp(candidate, generator.generate(seed, candidate))
            }
        }
    }

    @Test
    fun `every pitch stays inside its staff's range`() {
        val spec = spec(bars = 8)
        for (seed in 1L..12L) {
            val score = generator.generate(seed, spec)
            for (note in score.notes) {
                val range = spec.range.getValue(note.staff)
                assertTrue(
                    "seed $seed: ${note.pitch} outside $range on ${note.staff}",
                    note.pitch.midi >= range.start && note.pitch.midi <= range.endInclusive,
                )
            }
        }
    }

    @Test
    fun `no leap is wider than the spec allows`() {
        val spec = spec(bars = 8, maxLeapSemitones = 5)
        for (seed in 1L..12L) {
            val score = generator.generate(seed, spec)
            for (line in score.notes.groupBy { it.staff to it.voice }.values) {
                line.zipWithNext { from, to ->
                    val leap = abs(to.pitch.midi.number - from.pitch.midi.number)
                    assertTrue("seed $seed: leap of $leap semitones", leap <= spec.maxLeapSemitones)
                }
            }
        }
    }

    @Test
    fun `motion is mostly stepwise rather than random`() {
        val score = generator.generate(SEED, spec(bars = 16))
        val intervals = score.notes
            .filter { it.staff == Staff.Upper }
            .zipWithNext { from, to -> abs(to.pitch.midi.number - from.pitch.midi.number) }
        val steps = intervals.count { it in 1..2 }
        assertTrue("only $steps of ${intervals.size} intervals were steps", steps * 2 > intervals.size)
    }

    @Test
    fun `hands-separate practice rests the lower staff throughout`() {
        val score = generator.generate(SEED, spec(bothHandsActive = false))
        assertTrue(score.notes.none { it.staff == Staff.Lower })
        assertTrue(score.events.any { it is Rest && it.staff == Staff.Lower })
        assertTrue(skills.skillsOf(score).none { it == SkillTag.HandIndependence })
        assertBarsAddUp(spec(bothHandsActive = false), score)
    }

    @Test
    fun `both hands active means both hands are read`() {
        val score = generator.generate(SEED, spec())
        assertTrue(skills.skillsOf(score).contains(SkillTag.HandIndependence))
    }

    @Test
    fun `only allowed alterations are written`() {
        val diatonic = generator.generate(SEED, spec(bars = 8))
        assertTrue(diatonic.notes.all { it.pitch.alter == Alter.Natural })
        val sharpened = generator.generate(SEED, spec(bars = 8, allowedAlterations = setOf(Alter.Sharp)))
        assertTrue(sharpened.notes.all { it.pitch.alter == Alter.Natural || it.pitch.alter == Alter.Sharp })
        assertTrue(sharpened.notes.any { it.pitch.alter == Alter.Sharp })
    }

    @Test
    fun `a spec whose note values cannot fill a bar is refused, loudly`() {
        val impossible = spec(time = TimeSignature(3, 4), symbols = setOf(NoteSymbol.Whole))
        val failure = assertThrows(IllegalArgumentException::class.java) { generator.generate(SEED, impossible) }
        assertTrue(failure.message!!.contains("cannot fill a 3/4 bar exactly"))
    }

    @Test
    fun `targeting leger lines drills exactly those leger lines`() {
        val below = SkillTag.LegerLines(Clef.Bass, 2, above = false)
        val targeted = generator.specTargeting(below, spec())
        val score = generator.generate(SEED, targeted)
        assertTrue(skills.skillsOf(score).contains(below))
        assertTrue(score.notes.filter { it.staff == Staff.Lower }.all { it.pitch.midi.number < Midi.MIDDLE_C })
    }

    @Test
    fun `leger lines above a staff are drilled above it, not at whichever end the clef leans`() {
        val above = SkillTag.LegerLines(Clef.Bass, 2, above = true)
        val score = generator.generate(SEED, generator.specTargeting(above, spec()))
        val lower = score.notes.filter { it.staff == Staff.Lower }

        assertTrue(skills.skillsOf(score).contains(above))
        assertTrue("nothing was written on the targeted staff", lower.isNotEmpty())
        assertTrue(
            "targeting above the bass staff wrote ${lower.map { it.pitch }} below it",
            lower.all { skills.legerLines(Clef.Bass, it.pitch) >= 0 },
        )
        assertTrue(lower.any { skills.legerLines(Clef.Bass, it.pitch) == 2 })
    }

    @Test
    fun `targeting a clef region moves the notes into that region`() {
        val targeted = generator.specTargeting(SkillTag.ClefRegion(Clef.Treble, PitchBand.AboveStaff), spec())
        val score = generator.generate(SEED, targeted)
        assertTrue(skills.skillsOf(score).contains(SkillTag.ClefRegion(Clef.Treble, PitchBand.AboveStaff)))
    }

    @Test
    fun `targeting an accidental forces it into the vocabulary`() {
        val targeted = generator.specTargeting(SkillTag.Accidental(Alter.Sharp), spec(bars = 8))
        assertTrue(targeted.allowedAlterations.contains(Alter.Sharp))
        val score = generator.generate(SEED, targeted)
        assertTrue(skills.skillsOf(score).contains(SkillTag.Accidental(Alter.Sharp)))
    }

    @Test
    fun `targeting a rhythm figure narrows the note values to it`() {
        val figure = SkillTag.RhythmFigure(NoteSymbol.Eighth, 0, 1)
        val targeted = generator.specTargeting(figure, spec())
        assertEquals(setOf(NoteSymbol.Eighth), targeted.symbols)
        val score = generator.generate(SEED, targeted)
        assertTrue(score.notes.all { it.duration.symbol == NoteSymbol.Eighth })
        assertTrue(skills.skillsOf(score).contains(figure))
    }

    @Test
    fun `a rhythm figure that cannot fill the bar alone is widened rather than left short`() {
        val figure = SkillTag.RhythmFigure(NoteSymbol.Whole, 0, 1)
        val targeted = generator.specTargeting(figure, spec(time = TimeSignature(3, 4)))
        assertTrue(targeted.symbols.contains(NoteSymbol.Whole))
        assertTrue(targeted.symbols.size > 1)
        assertBarsAddUp(targeted, generator.generate(SEED, targeted))
    }

    @Test
    fun `widening a rhythm figure never reaches for a shorter value than the spec asked for`() {
        val base = spec(time = TimeSignature(3, 4))
        val shortest = base.symbols.minOf { it.undottedTicks }
        for (symbol in NoteSymbol.entries) {
            val targeted = generator.specTargeting(SkillTag.RhythmFigure(symbol, 0, 1), base)
            val added = targeted.symbols - base.symbols - symbol
            assertTrue(
                "targeting $symbol widened to $added, shorter than anything in ${base.symbols}",
                added.all { it.undottedTicks >= shortest },
            )
            val score = generator.generate(SEED, targeted)
            assertBarsAddUp(targeted, score)
            assertTrue(
                "targeting $symbol wrote ${score.notes.map { it.duration.symbol }.distinct()}",
                score.notes.none { it.duration.symbol != symbol && it.duration.symbol.undottedTicks < shortest },
            )
        }
    }

    @Test
    fun `a written accidental is never a respelling of a note the key already has`() {
        val cases = listOf(
            KeySignature.C to Alter.Sharp,
            KeySignature(1) to Alter.Flat,
            KeySignature(-3) to Alter.Sharp,
        )
        for ((key, alter) in cases) {
            val targeted = spec(bars = 8, key = key, allowedAlterations = setOf(alter))
            var written = 0
            for (seed in 1L..12L) {
                val score = generator.generate(seed, targeted)
                val diatonic = keyPitchClasses(key)
                val accidentals = score.notes.filter {
                    it.pitch.alter != KeySignatureAlterations.impliedAlter(key, it.pitch.letter)
                }
                written += accidentals.size
                for (note in accidentals) {
                    assertTrue(
                        "seed $seed in ${key.fifths} fifths wrote ${note.pitch.letter}" +
                            "${note.pitch.alter.semitones} sounding the key's own note",
                        note.pitch.midi.number % Pitch.SEMITONES_PER_OCTAVE !in diatonic,
                    )
                }
            }
            assertTrue(
                "no accidental at all was written in ${key.fifths} fifths, so nothing was tested",
                written > 0,
            )
        }
    }

    @Test
    fun `targeting a key changes the key and the letters written`() {
        val targeted = generator.specTargeting(SkillTag.KeyReading(-3), spec(bars = 8))
        assertEquals(KeySignature(-3), targeted.key)
        val score = generator.generate(SEED, targeted)
        assertEquals(KeySignature(-3), score.measures.first().key)
        assertTrue(score.notes.any { it.pitch.alter == Alter.Flat })
        assertTrue(skills.skillsOf(score).contains(SkillTag.KeyReading(-3)))
    }

    @Test
    fun `targeting a leap raises the ceiling and widens the range to allow it`() {
        val targeted = generator.specTargeting(SkillTag.Leap(12), spec(maxLeapSemitones = 3))
        assertEquals(12, targeted.maxLeapSemitones)
        val score = generator.generate(SEED, targeted)
        assertTrue(skills.skillsOf(score).any { it is SkillTag.Leap && it.semitones > 3 })
    }

    @Test
    fun `targeting hand independence adds the missing hand`() {
        val oneHand = spec().let {
            it.copy(
                staves = listOf(Staff.Upper),
                clefs = mapOf(Staff.Upper to Clef.Treble),
                range = mapOf(Staff.Upper to it.range.getValue(Staff.Upper)),
                bothHandsActive = false,
            )
        }
        val targeted = generator.specTargeting(SkillTag.HandIndependence, oneHand)
        assertEquals(listOf(Staff.Upper, Staff.Lower), targeted.staves)
        assertTrue(targeted.bothHandsActive)
        val score = generator.generate(SEED, targeted)
        assertTrue(score.isGrandStaff)
        assertTrue(skills.skillsOf(score).contains(SkillTag.HandIndependence))
    }

    @Test
    fun `targeting hand independence on a grand staff just turns the hand back on`() {
        val targeted = generator.specTargeting(SkillTag.HandIndependence, spec(bothHandsActive = false))
        assertTrue(targeted.bothHandsActive)
        assertEquals(listOf(Staff.Upper, Staff.Lower), targeted.staves)
    }

    @Test
    fun `tuplets are generated as whole groups`() {
        val spec = spec(bars = 8, symbols = setOf(NoteSymbol.Quarter, NoteSymbol.Eighth), allowTuplets = true)
        val score = generator.generate(3L, spec)
        val tupletNotes = score.notes.filter { it.duration.isTuplet }
        assertTrue("no tuplet was generated", tupletNotes.isNotEmpty())
        assertEquals(0, tupletNotes.size % 3)
    }

    @Test
    fun `a generated exercise logs the seed and spec it can be rebuilt from`() {
        val diag = RecordingDiag()
        SeededExerciseGenerator(diag).generate(SEED, spec())
        val line = diag.events.single()
        for (field in listOf("seed=$SEED", "bars=4", "time=4/4", "key=0fifths", "maxLeap=7semitones", "tempo=80bpm")) {
            assertTrue("expected $field in $line", line.contains(field))
        }
    }

    @Test
    fun `measures line up with the events`() {
        val spec = spec(bars = 5, time = TimeSignature(3, 4))
        val score = generator.generate(SEED, spec)
        assertEquals(5, score.measures.size)
        assertEquals(spec.time.measureTicks * 5, score.endsAt)
        assertEquals(spec.tempoBpm, score.defaultTempoBpm)
        score.measures.forEachIndexed { index, measure ->
            assertEquals(index, measure.index)
            assertEquals(spec.time.measureTicks * index, measure.start)
            assertEquals(spec.clefs, measure.clefs)
        }
    }

    private fun assertBarsAddUp(spec: DifficultySpec, score: Score) {
        for (measure in score.measures) {
            val barEnd = measure.start + measure.time.measureTicks
            for (staff in spec.staves) {
                val inBar = score.events.filter { it.staff == staff && it.onset >= measure.start && it.onset < barEnd }
                assertTrue("bar ${measure.index + 1} of $staff is empty", inBar.isNotEmpty())
                assertEquals(
                    "bar ${measure.index + 1} of $staff",
                    measure.time.measureTicks.value,
                    inBar.sumOf { it.duration.ticks.value },
                )
            }
        }
    }

    private fun spec(
        bars: Int = 4,
        time: TimeSignature = TimeSignature.FourFour,
        key: KeySignature = KeySignature.C,
        symbols: Set<NoteSymbol> = setOf(NoteSymbol.Half, NoteSymbol.Quarter, NoteSymbol.Eighth),
        maxDots: Int = 0,
        allowTuplets: Boolean = false,
        allowedAlterations: Set<Alter> = setOf(Alter.Natural),
        maxLeapSemitones: Int = 7,
        bothHandsActive: Boolean = true,
    ) = DifficultySpec(
        staves = listOf(Staff.Upper, Staff.Lower),
        clefs = mapOf(Staff.Upper to Clef.Treble, Staff.Lower to Clef.Bass),
        key = key,
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
        tempoBpm = 80,
        bothHandsActive = bothHandsActive,
    )

    private companion object {
        const val SEED = 20260807L
    }
}
