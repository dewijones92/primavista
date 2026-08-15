package com.dewijones92.primavista.practice

import com.dewijones92.primavista.score.Clef
import com.dewijones92.primavista.score.PitchBand
import com.dewijones92.primavista.score.Polyphony
import com.dewijones92.primavista.score.SeededExerciseGenerator
import com.dewijones92.primavista.score.SkillTag
import com.dewijones92.primavista.score.Staff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val FOCUS_NOW = 1_700_000_000_000L
private const val DAY = 24L * 60L * 60L * 1000L

class SchedulerFocusTest {
    private val generator = SeededExerciseGenerator()
    private val scheduler = SpacedPracticeScheduler(generator::specTargeting)
    private val curriculum = Curriculum.Standard

    @Test
    fun `no focus leaves the choice exactly as it always was`() {
        val states = listOf(
            state(bassLeger, strength = 0.1, dueAtEpochMillis = FOCUS_NOW),
            state(trebleMiddle, strength = 0.4, dueAtEpochMillis = FOCUS_NOW),
        )
        val available = listOf(summaryOf("etude", setOf(bassLeger, trebleMiddle)))

        val unfocused = scheduler.next(available, states, Polyphony.Poly, FOCUS_NOW, seed = 11L)
        val explicitlyNone = scheduler.next(available, states, Polyphony.Poly, FOCUS_NOW, 11L, PracticeFocus.None)

        assertEquals(unfocused, explicitlyNone)
    }

    @Test
    fun `a stage narrows what is drilled to the skills it has in play`() {
        val stage = curriculum.stage(StageId(2)) ?: error("no stage two")
        val states = listOf(
            state(bassLeger, strength = 0.0, dueAtEpochMillis = FOCUS_NOW, attempts = 6, lapses = 5),
            state(trebleUpper, strength = 0.3, dueAtEpochMillis = FOCUS_NOW),
        )

        val unfocused = scheduler.next(emptyList(), states, Polyphony.Poly, FOCUS_NOW, seed = 3L)
        val focused = scheduler.next(emptyList(), states, Polyphony.Poly, FOCUS_NOW, 3L, stage.focus)

        assertEquals("the weakest skill overall is off-stage", setOf(bassLeger), unfocused.targeting())
        assertTrue("chose $focused", focused.targeting().all { it in stage.skills })
    }

    @Test
    fun `a skill the stage names but nobody has ever read is a candidate, not an omission`() {
        val stage = curriculum.stage(StageId(3)) ?: error("no stage three")
        val alreadySolid = listOf(state(trebleMiddle, strength = 1.0, dueAtEpochMillis = FOCUS_NOW))

        val choice = scheduler.next(emptyList(), alreadySolid, Polyphony.Poly, FOCUS_NOW, 4L, stage.focus)

        assertTrue("chose $choice", choice.targeting().single() in stage.skills)
        assertEquals(Clef.Bass, (choice as PracticeChoice.Generated).spec.clefs.getValue(Staff.Upper))
    }

    @Test
    fun `the stage sets the level a generated drill starts from`() {
        val stage = curriculum.stage(StageId(7)) ?: error("no stage seven")
        val states = listOf(state(trebleMiddle, strength = 0.1, dueAtEpochMillis = FOCUS_NOW))

        val bottomRung = scheduler.next(emptyList(), states, Polyphony.Poly, FOCUS_NOW, 5L, PracticeFocus(stage.skills))
        val atTheStage = scheduler.next(emptyList(), states, Polyphony.Poly, FOCUS_NOW, 5L, stage.focus)

        val fromDefault = (bottomRung as PracticeChoice.Generated).spec
        val fromStage = (atTheStage as PracticeChoice.Generated).spec
        assertEquals("without a base it is still the bottom of the ladder", 1, fromDefault.staves.size)
        assertEquals(listOf(Staff.Upper, Staff.Lower), fromStage.staves)
        assertEquals(stage.spec.bars, fromStage.bars)
        assertEquals(stage.spec.tempoBpm, fromStage.tempoBpm)
    }

    @Test
    fun `a stage narrows the field and still never picks the material`() {
        val stage = curriculum.stage(StageId(2)) ?: error("no stage two")
        val states = listOf(state(trebleUpper, strength = 0.1, dueAtEpochMillis = FOCUS_NOW))
        val available = listOf(
            summaryOf("scales", setOf(trebleUpper, quarterRhythm)),
            summaryOf("bass-etude", setOf(bassLeger)),
        )

        val choice = scheduler.next(available, states, Polyphony.Poly, FOCUS_NOW, 6L, stage.focus)

        assertTrue("the scheduler still chooses, and it chose $choice", choice is PracticeChoice.Piece)
        assertEquals("scales", (choice as PracticeChoice.Piece).id.value)
    }

    @Test
    fun `a focused mono input is still never handed two-handed material`() {
        val stage = curriculum.stage(StageId(4)) ?: error("no stage four")

        val choice = scheduler.next(emptyList(), emptyList(), Polyphony.Mono, FOCUS_NOW, 7L, stage.focus)

        val generated = choice as PracticeChoice.Generated
        assertFalse("stage four is the grand staff, and a mic cannot hear it", generated.spec.bothHandsActive)
        assertFalse(SkillTag.HandIndependence in generated.targeting)
    }

    @Test
    fun `a focus of skills already solid still gives the weakest of them, not something off-stage`() {
        val stage = curriculum.stage(StageId(1)) ?: error("no stage one")
        val states = stage.skills.mapIndexed { index, tag ->
            state(tag, strength = 0.9 - index * 0.05, dueAtEpochMillis = FOCUS_NOW + DAY)
        } + state(bassLeger, strength = 0.0, dueAtEpochMillis = FOCUS_NOW)

        val choice = scheduler.next(emptyList(), states, Polyphony.Poly, FOCUS_NOW, 8L, stage.focus)

        assertTrue("chose $choice", choice.targeting().all { it in stage.skills })
    }
}

private val bassLeger = SkillTag.LegerLines(Clef.Bass, count = 2, above = false)
private val trebleUpper = SkillTag.ClefRegion(Clef.Treble, PitchBand.UpperStaff)

private fun PracticeChoice.targeting(): Set<SkillTag> = when (this) {
    is PracticeChoice.Piece -> targeting
    is PracticeChoice.Generated -> targeting
}
