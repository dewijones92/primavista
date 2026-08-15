package com.dewijones92.primavista.score

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SEEDS = 32

/** An exact melodic interval is rare enough that a thin pass would be luck. See `.claude/CODE-NOTES.md`. */
private const val DEEP_SEEDS = 256
private const val LEGER_LINE_SWEEP = 4
private const val OCTAVE_SEMITONES = 12
private const val QUINTUPLET = 5
private const val DERIVATION_SEEDS = 8

/**
 * The guard for the one thing targeting is for: a drill aimed at a skill has to contain that
 * skill. When it cannot, the outcome records no attempts, no attempts changes no state, and the
 * scheduler offers the same useless drill for ever. See `.claude/CODE-NOTES.md`.
 */
class TargetedDrillTest {

    private val generator = SeededExerciseGenerator()
    private val skills = DerivedScoreSkills()

    @Test
    fun `every skill a drill can be aimed at is one that drill can actually contain`() {
        val unreachable = drillBases.flatMap { (name, base) ->
            everyTargetableSkill().mapNotNull { target ->
                "$name aimed at $target: 0 of $DEEP_SEEDS drills contained it"
                    .takeIf { hits(target, base, SEEDS) == 0 && hits(target, base, DEEP_SEEDS) == 0 }
            }
        }

        assertEquals(emptyList<String>(), unreachable)
    }

    @Test
    fun `an everyday drill contains its target in most of its attempts`() {
        val thin = drillBases.flatMap { (name, base) ->
            everyTargetableSkill().filter(::isEveryday).mapNotNull { target ->
                val hits = hits(target, base, SEEDS)
                "$name aimed at $target: only $hits of $SEEDS drills contained it".takeIf { hits * 2 <= SEEDS }
            }
        }

        assertEquals(emptyList<String>(), thin)
    }

    @Test
    fun `a bar aimed at a rhythm figure can hold that figure`() {
        val unheld = drillBases.flatMap { (name, base) ->
            writableFigures().mapNotNull { figure ->
                val targeted = generator.specTargeting(figure, base)
                val choices = rhythmChoices(targeted)
                val placed = choices.firstOrNull { it.figure == figure }
                    ?.let { BarFill(targeted.time.measureTicks.value, choices).canPlace(it) }
                val complaint = "$name gave $figure a ${targeted.time.beats}/${targeted.time.beatUnit} " +
                    "bar of ${targeted.symbols} that cannot hold it"
                complaint.takeIf { placed != true }
            }
        }

        assertEquals(emptyList<String>(), unheld)
    }

    @Test
    fun `every targeted drill still fills its bars exactly`() {
        for ((name, base) in drillBases) {
            for (target in everyTargetableSkill()) {
                val targeted = generator.specTargeting(target, base)
                assertBarsAddUp(targeted, generator.generate(1L, targeted), "$name aimed at $target")
            }
        }
    }

    @Test
    fun `the sweep covers every skill this app can generate`() {
        val fromDrills = drillBases.values.flatMap { base ->
            (1L..DERIVATION_SEEDS).flatMap { skills.skillsOf(generator.generate(it, base)) }
        }

        assertEquals(emptySet<SkillTag>(), fromDrills.toSet() - everyTargetableSkill().toSet())
    }

    /**
     * Real repertoire asks for more than a generator should ever write — a two-octave leap, eight
     * leger lines, a septuplet — and that is the point of shipping it. What must not happen is a
     * *new kind* of skill appearing unnoticed, so the excess is characterised rather than ignored.
     */
    @Test
    fun `the shipped repertoire exceeds the generator only in ways already understood`() {
        val fromCorpus = Corpus.pieces.flatMap { piece ->
            skills.skillsOf((Corpus.parse(piece, DomMusicXmlParser()) as MusicXmlResult.Parsed).score)
        }

        val unexplained = (fromCorpus.toSet() - everyTargetableSkill().toSet()).filterNot { skill ->
            when (skill) {
                is SkillTag.Leap -> skill.semitones > OCTAVE_SEMITONES
                is SkillTag.LegerLines -> skill.count > LEGER_LINE_SWEEP
                is SkillTag.RhythmFigure -> skill.tupletNumerator > 1 || skill.dots > 0
                else -> false
            }
        }
        assertEquals(emptyList<SkillTag>(), unexplained)
    }

    /**
     * The scheduler will hand [ExerciseGenerator.specTargeting] whatever the corpus made weak, so
     * every skill real music can produce has to yield a spec that still writes complete bars — the
     * failure mode CLAUDE.md records, where a drill silently cannot contain the thing it drills.
     */
    @Test
    fun `a drill aimed at anything the shipped repertoire can teach still fills its bars`() {
        val fromCorpus = Corpus.pieces.flatMap { piece ->
            skills.skillsOf((Corpus.parse(piece, DomMusicXmlParser()) as MusicXmlResult.Parsed).score)
        }.toSet()

        for ((name, base) in drillBases) {
            for (target in fromCorpus) {
                val targeted = generator.specTargeting(target, base)
                assertBarsAddUp(targeted, generator.generate(1L, targeted), "$name aimed at $target")
            }
        }
    }

    @Test
    fun `a tuplet this generator cannot write is a stated gap rather than a broken bar`() {
        val quintuplet = SkillTag.RhythmFigure(NoteSymbol.Quarter, dots = 0, tupletNumerator = QUINTUPLET)

        for ((name, base) in drillBases) {
            val targeted = generator.specTargeting(quintuplet, base)
            val score = generator.generate(1L, targeted)

            assertTrue(
                "$name substituted a tuplet it can write for the one it was asked for",
                score.notes.none { it.duration.isTuplet },
            )
            assertBarsAddUp(targeted, score)
        }
    }

    private fun hits(target: SkillTag, base: DifficultySpec, seeds: Int): Int {
        val targeted = generator.specTargeting(target, base)
        return (1L..seeds.toLong()).count { seed ->
            target in skills.skillsOf(generator.generate(seed, targeted))
        }
    }
}

/**
 * Every skill the app can record, swept exhaustively rather than mirrored from the curriculum:
 * the scheduler targets whatever is weakest, and weakness comes from parsed pieces as well as
 * from stages. See `.claude/CODE-NOTES.md`.
 */
private fun everyTargetableSkill(): List<SkillTag> = buildList {
    for (clef in Clef.entries) {
        PitchBand.entries.forEach { add(SkillTag.ClefRegion(clef, it)) }
        for (count in 1..LEGER_LINE_SWEEP) {
            add(SkillTag.LegerLines(clef, count, above = true))
            add(SkillTag.LegerLines(clef, count, above = false))
        }
    }
    (-Alter.MAX_MAGNITUDE..Alter.MAX_MAGNITUDE).forEach { add(SkillTag.Accidental(Alter(it))) }
    (-KeySignature.MAX_FIFTHS..KeySignature.MAX_FIFTHS).forEach { add(SkillTag.KeyReading(it)) }
    addAll(writableFigures())
    (0..OCTAVE_SEMITONES).forEach { add(SkillTag.Leap(it)) }
    add(SkillTag.HandIndependence)
}

/** Asked of the generator rather than restated, so the sweep cannot claim a figure it cannot write. */
private fun writableFigures(): List<SkillTag.RhythmFigure> =
    NoteSymbol.entries
        .flatMap { symbol ->
            rhythmChoices(spec(symbols = setOf(symbol), maxDots = Duration.MAX_DOTS, allowTuplets = true))
                .map { it.figure }
        }
        .distinct()

/** Which skills a drill must hit reliably rather than merely be able to hit. See `.claude/CODE-NOTES.md`. */
private fun isEveryday(target: SkillTag): Boolean = when (target) {
    is SkillTag.ClefRegion, is SkillTag.LegerLines, is SkillTag.KeyReading, SkillTag.HandIndependence -> true
    is SkillTag.RhythmFigure -> target.dots == 0 && target.tupletNumerator == 1
    is SkillTag.Accidental, is SkillTag.Leap -> false
}
