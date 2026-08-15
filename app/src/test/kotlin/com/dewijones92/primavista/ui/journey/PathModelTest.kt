package com.dewijones92.primavista.ui.journey

import com.dewijones92.primavista.database.PlacementReading
import com.dewijones92.primavista.database.PlacementRecord
import com.dewijones92.primavista.database.StageMilestone
import com.dewijones92.primavista.di.InputMode
import com.dewijones92.primavista.di.JourneyReading
import com.dewijones92.primavista.practice.Curriculum
import com.dewijones92.primavista.practice.SkillState
import com.dewijones92.primavista.practice.StageId
import com.dewijones92.primavista.practice.Streak
import com.dewijones92.primavista.score.SkillTag
import com.dewijones92.primavista.ui.mascot.MascotMood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val NOW = 1_700_000_000_000L

class PathModelTest {

    private val curriculum = Curriculum.Standard

    @Test
    fun `a fresh reader stands on the first rung with nothing behind him`() {
        val path = pathOf(curriculum, reading(states = emptyList()))

        assertEquals(StageId.FIRST, path.current.id.number)
        assertEquals(curriculum.stages.size, path.rows.size)
        assertEquals(Standing.Current, path.rows.first().standing)
        assertTrue(path.rows.drop(1).all { it.standing == Standing.Ahead })
    }

    @Test
    fun `a stage is passed only when its skills are solid, never by turning up`() {
        val first = curriculum.stages.first()
        val allButOne = first.skills.drop(1)

        val nearly = pathOf(curriculum, reading(states = allButOne.map(::solid)))
        assertEquals(Standing.Current, nearly.rows.first().standing)
        assertEquals(allButOne.size, nearly.rows.first().solidSkills)

        val done = pathOf(curriculum, reading(states = first.skills.map(::solid)))
        assertEquals(Standing.Passed, done.rows.first().standing)
        assertEquals(2, done.current.id.number)
    }

    @Test
    fun `a skill that has been read but is not solid does not pass its stage`() {
        val first = curriculum.stages.first()
        val shaky = first.skills.map { SkillState(it, SkillState.SOLID_STRENGTH - 0.01, NOW, 9, 1) }

        val path = pathOf(curriculum, reading(states = shaky))

        assertEquals(Standing.Current, path.rows.first().standing)
        assertEquals(0, path.rows.first().solidSkills)
    }

    @Test
    fun `the both-hands rung says out loud that a microphone can never pass it`() {
        val mic = pathOf(curriculum, reading(states = emptyList(), input = InputMode.Mic))
        val tap = pathOf(curriculum, reading(states = emptyList(), input = InputMode.Tap))

        val handsRow = mic.rows.single { SkillTag.HandIndependence in it.stage.skills }
        assertTrue(handsRow.unreachableByInput)
        assertTrue(mic.rows.filterNot { SkillTag.HandIndependence in it.stage.skills }.none { it.unreachableByInput })
        assertTrue(tap.rows.none { it.unreachableByInput })
    }

    @Test
    fun `a passed date is carried from the milestones and is never invented`() {
        val first = curriculum.stages.first()
        val dated = StageMilestone(first.id, firstReachedAtEpochMillis = NOW, firstPassedAtEpochMillis = NOW + 5)

        val path = pathOf(curriculum, reading(states = first.skills.map(::solid), milestones = listOf(dated)))

        assertEquals(NOW + 5, path.rows.first().passedOnEpochMillis)
        assertEquals(null, path.rows[1].passedOnEpochMillis)
    }

    @Test
    fun `a stage passed out of order still reads as passed while the current rung stays the first gap`() {
        val third = curriculum.stages[2]

        val path = pathOf(curriculum, reading(states = third.skills.map(::solid)))

        assertEquals(1, path.current.id.number)
        assertEquals(Standing.Passed, path.rows[2].standing)
        assertEquals(Standing.Current, path.rows[0].standing)
    }

    @Test
    fun `the placement is only offered until one has been recorded either way`() {
        val never = pathOf(curriculum, reading(states = emptyList()))
        assertFalse(never.everPlaced)

        val skipped = pathOf(
            curriculum,
            reading(
                states = emptyList(),
                placement = PlacementReading.Taken(
                    PlacementRecord(NOW, com.dewijones92.primavista.database.PlacementOutcome.Skipped),
                ),
            ),
        )
        assertTrue(skipped.everPlaced)
    }

    @Test
    fun `Trill's mood on the path is about the calendar and never about how well he played`() {
        assertEquals(MascotMood.Curious, pathMood(Streak.None))
        assertEquals(MascotMood.Sleepy, pathMood(Streak(currentDays = 0, bestDays = 4, daysPractised = 9)))
        assertEquals(MascotMood.Idle, pathMood(Streak(currentDays = 3, bestDays = 4, daysPractised = 9)))
    }

    @Test
    fun `a lapsed run is never worded as something lost`() {
        val words = streakWords(Streak(currentDays = 0, bestDays = 6, daysPractised = 11))

        assertTrue(words.contains("11 days"))
        assertFalse(words.contains("lost"))
        assertFalse(words.contains("broke"))
    }

    private fun solid(tag: SkillTag) = SkillState(tag, 1.0, NOW, attempts = 10, lapses = 0)

    private fun reading(
        states: List<SkillState>,
        milestones: List<StageMilestone> = emptyList(),
        input: InputMode = InputMode.Tap,
        placement: PlacementReading = PlacementReading.NeverTaken,
    ) = JourneyReading(states, milestones, Streak.None, placement, input)
}
