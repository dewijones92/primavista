package com.dewijones92.primavista.practice

import com.dewijones92.primavista.score.DerivedScoreSkills
import com.dewijones92.primavista.score.KeySignature
import com.dewijones92.primavista.score.NoteSymbol
import com.dewijones92.primavista.score.PitchBand
import com.dewijones92.primavista.score.SeededExerciseGenerator
import com.dewijones92.primavista.score.SkillTag
import com.dewijones92.primavista.score.Staff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val NOW_EPOCH_MILLIS = 1_700_000_000_000L
private const val PROBE_SEEDS = 32L

/** What stage six writes in: G, F, D and B flat. Stated here so the test names the expectation. */
private val sharpAndFlatFifths = listOf(-2, -1, 1, 2)

class CurriculumTest {
    private val curriculum = Curriculum.Standard
    private val generator = SeededExerciseGenerator()
    private val skills = DerivedScoreSkills()

    @Test
    fun `the path is the ten rungs of the journey, numbered as a beginner would count them`() {
        assertEquals(10, curriculum.stages.size)
        assertEquals((1..10).toList(), curriculum.stages.map { it.id.number })
        assertEquals("The five lines", curriculum.stages.first().title)
        assertEquals("Onwards", curriculum.stages.last().title)
        assertTrue("every rung needs a sentence a beginner can read", curriculum.stages.all { it.blurb.isNotBlank() })
    }

    @Test
    fun `a learner with nothing to their name is on the first stage`() {
        assertEquals(StageId(1), curriculum.currentStage(emptyList()).id)
    }

    @Test
    fun `mastering a stage's skills moves you on to the next one`() {
        val first = curriculum.stages.first()

        val after = curriculum.currentStage(allSolid(first.skills))

        assertTrue(curriculum.isPassed(first, allSolid(first.skills)))
        assertEquals(StageId(2), after.id)
    }

    @Test
    fun `a gap in an earlier stage pulls you back rather than being skipped`() {
        val stages = curriculum.stages
        val skippedFirst = allSolid(stages[1].skills + stages[2].skills)

        assertEquals("stage one was never read", StageId(1), curriculum.currentStage(skippedFirst).id)

        val orderly = allSolid(stages[0].skills + stages[1].skills)
        assertEquals(StageId(3), curriculum.currentStage(orderly).id)
    }

    @Test
    fun `sessions do not pass a stage - only solid reading does`() {
        val first = curriculum.stages.first()
        val busyButUnread = first.skills.map {
            state(it, strength = 0.79, dueAtEpochMillis = NOW_EPOCH_MILLIS, attempts = 40, lapses = 12)
        }

        assertFalse("forty sessions is not a reading", curriculum.isPassed(first, busyButUnread))
        assertEquals(StageId(1), curriculum.currentStage(busyButUnread).id)
    }

    @Test
    fun `solid has one definition and every consumer reads it off the state`() {
        val tag = curriculum.stages.first().skills.first()

        assertFalse(state(tag, strength = 0.79, dueAtEpochMillis = NOW_EPOCH_MILLIS).isSolid)
        assertTrue(state(tag, strength = SkillState.SOLID_STRENGTH, dueAtEpochMillis = NOW_EPOCH_MILLIS).isSolid)
        assertTrue(
            "a solid skill falling due is still solid - due is about spacing, not about reading",
            state(tag, strength = 0.9, dueAtEpochMillis = NOW_EPOCH_MILLIS - 1).isSolid,
        )
    }

    @Test
    fun `everything solid leaves you on the last stage, which has no top`() {
        val everything = allSolid(curriculum.stages.flatMap { it.skills })

        assertEquals(StageId(10), curriculum.currentStage(everything).id)
    }

    @Test
    fun `a stage claims what it adds, and its material carries everything before it`() {
        val offTheStaff = curriculum.stage(StageId(7)) ?: error("no stage seven")

        assertEquals("leger lines are what stage seven adds", 4, offTheStaff.skills.size)
        assertTrue(offTheStaff.skills.all { it is SkillTag.LegerLines })
        assertTrue("stage two's quarters survive", NoteSymbol.Quarter in offTheStaff.spec.symbols)
        assertTrue("stage four's second hand survives", offTheStaff.spec.bothHandsActive)
        assertEquals(listOf(Staff.Upper, Staff.Lower), offTheStaff.spec.staves)
        assertEquals(sharpAndFlatFifths, offTheStaff.spec.keys.map { it.fifths }.sorted())
    }

    @Test
    fun `skillsThrough gathers every claim up to a rung and no claim beyond it`() {
        val throughThree = curriculum.skillsThrough(StageId(3))

        assertTrue(curriculum.stages.take(3).flatMap { it.skills }.all { it in throughThree })
        assertFalse(SkillTag.HandIndependence in throughThree)
        assertTrue(SkillTag.HandIndependence in curriculum.skillsThrough(StageId(4)))
    }

    /**
     * The path used to cap at one sharp for all ten rungs, so a reader could finish it having never
     * met a B flat — and 44,335 passages of the shipped corpus were refused on a difficulty stage
     * six claims to teach. What a rung writes in and what it can read are different questions.
     */
    @Test
    fun `the rungs read wider keys than they write in, and the ceiling only ever rises`() {
        val ceilings = curriculum.stages.map { it.spec.readableKeyAccidentals }

        assertEquals(ceilings.sorted(), ceilings)
        assertEquals(0, curriculum.stages.first().spec.readableKeyAccidentals)
        assertTrue(
            "the last rung stops at ${ceilings.last()} accidentals, so some real keys are unreadable",
            ceilings.last() >= KeySignature.MAX_FIFTHS,
        )
        val keys = curriculum.stages.first { it.title == "Keys" }
        assertTrue("Keys reads only ${keys.spec.readableKeyAccidentals}", keys.spec.readableKeyAccidentals > 1)
    }

    @Test
    fun `every skill a stage claims is one its own material actually tests`() {
        curriculum.stages.forEach { stage ->
            val produced = (1L..PROBE_SEEDS)
                .flatMap { seed -> skills.skillsOf(generator.generate(seed, stage.spec)) }
                .toSet()

            assertEquals(
                "${stage.id} (${stage.title}) claims skills its own material never asks for",
                emptySet<SkillTag>(),
                stage.skills - produced,
            )
        }
    }

    @Test
    fun `a drill aimed at a claimed skill contains it more often than not`() {
        val seeds = 1L..8L
        curriculum.stages.forEach { stage ->
            stage.skills.forEach { tag ->
                val spec = generator.specTargeting(tag, stage.spec)
                val tested = seeds.count { seed -> tag in skills.skillsOf(generator.generate(seed, spec)) }

                assertTrue(
                    "${stage.id} cannot be climbed: only $tested of ${seeds.count()} drills aimed at $tag contain it",
                    tested * 2 > seeds.count(),
                )
            }
        }
    }

    @Test
    fun `a stage that could be passed without reading anything cannot be built`() {
        val first = curriculum.stages.first()
        val claimsNothing = runCatching { first.copy(skills = emptySet()) }
        val outOfOrder = runCatching { StagedCurriculum(listOf(curriculum.stages[1], first)) }

        assertTrue(claimsNothing.exceptionOrNull() is IllegalArgumentException)
        assertTrue(outOfOrder.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { StageId(0) }.exceptionOrNull() is IllegalArgumentException)
        assertNull(curriculum.stage(StageId(11)))
    }

    @Test
    fun `a band that is not part of the staff has no step range and says so`() {
        assertEquals(3..5, bandSteps(PitchBand.MiddleStaff))
        assertTrue(runCatching { bandSteps(PitchBand.AboveStaff) }.exceptionOrNull() is IllegalArgumentException)
    }

    private fun allSolid(tags: Collection<SkillTag>): List<SkillState> =
        tags.map { state(it, strength = 0.9, dueAtEpochMillis = NOW_EPOCH_MILLIS) }
}
