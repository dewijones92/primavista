package com.dewijones92.primavista.ui.journey

import com.dewijones92.primavista.database.PlacementReading
import com.dewijones92.primavista.database.StageMilestone
import com.dewijones92.primavista.di.InputMode
import com.dewijones92.primavista.di.JourneyReading
import com.dewijones92.primavista.di.isHearableBy
import com.dewijones92.primavista.practice.Curriculum
import com.dewijones92.primavista.practice.Stage
import com.dewijones92.primavista.practice.StageId
import com.dewijones92.primavista.practice.Streak
import com.dewijones92.primavista.score.SkillTag
import com.dewijones92.primavista.ui.mascot.MascotMood

/** Where a rung sits relative to Dewi. Deliberately three states: behind, here, not yet. */
internal enum class Standing { Passed, Current, Ahead }

/**
 * One rung as the path draws it.
 *
 * [solidSkills] is progress **inside** a stage and it is the honest kind: it counts skills the
 * store says are solid, never sessions attempted. [passedOnEpochMillis] is a dated event and never
 * a claim about today — [standing] is what is true now.
 */
internal data class PathRow(
    val stage: Stage,
    val standing: Standing,
    val solidSkills: Int,
    val passedOnEpochMillis: Long?,
    val unreachableByInput: Boolean,
) {
    val totalSkills: Int get() = stage.skills.size
}

/** What the path can ask for beyond starting a stage. */
internal data class PathActions(
    val onStage: (Stage) -> Unit,
    val onUseKeyboard: () -> Unit,
    val onPlacement: () -> Unit,
    val onIntroduction: () -> Unit,
    val onDiagnostics: () -> Unit,
)

internal data class PathState(
    val rows: List<PathRow>,
    val current: Stage,
    val streak: Streak,
    val input: InputMode,
    val everPlaced: Boolean,
)

/**
 * The path, derived and never stored.
 *
 * Pure so the two things most easily got wrong are ordinary unit tests: that a stage is passed only
 * when the curriculum says its skills are solid, and that a rung a mono input can never be credited
 * for says so rather than becoming a silent wall.
 */
internal fun pathOf(curriculum: Curriculum, reading: JourneyReading): PathState {
    val current = curriculum.currentStage(reading.states)
    val solid = reading.states.filter { it.isSolid }.map { it.tag }.toSet()
    val dated = reading.milestones.associateBy { it.stage.number }
    return PathState(
        rows = curriculum.stages.map { stage -> row(stage, current.id, solid, dated, reading.input) },
        current = current,
        streak = reading.streak,
        input = reading.input,
        everPlaced = reading.placement != PlacementReading.NeverTaken,
    )
}

private fun row(
    stage: Stage,
    current: StageId,
    solid: Set<SkillTag>,
    dated: Map<Int, StageMilestone>,
    input: InputMode,
): PathRow {
    val passed = stage.skills.all { it in solid }
    return PathRow(
        stage = stage,
        standing = when {
            stage.id.number == current.number -> Standing.Current
            passed -> Standing.Passed
            else -> Standing.Ahead
        },
        solidSkills = stage.skills.count { it in solid },
        passedOnEpochMillis = dated[stage.id.number]?.firstPassedAtEpochMillis,
        unreachableByInput = stage.skills.any { !it.isHearableBy(input.polyphony) },
    )
}

/**
 * Trill's mood on the path, which is about the **calendar** and never about how well he plays.
 *
 * Quality belongs to the results of a session, where a verdict backs it up. A bird who looked
 * pleased on the path would be pleased about nothing in particular, which is the flattery the whole
 * app exists not to do.
 */
internal fun pathMood(streak: Streak): MascotMood = when {
    streak.daysPractised == 0 -> MascotMood.Curious
    streak.currentDays == 0 -> MascotMood.Sleepy
    else -> MascotMood.Idle
}

/** Never a threat, never a thing you have lost. See docs/journey.md. */
internal fun streakWords(streak: Streak): String = when {
    streak.daysPractised == 0 -> "No days read yet. Today can be the first."
    streak.currentDays == 0 -> "${streak.daysPractised} days read in all. Today would start a new run."
    streak.currentDays == 1 -> "Read today. That's a run of one."
    else -> "${streak.currentDays} days in a row read."
}

internal fun standingWords(row: PathRow): String =
    if (row.standing == Standing.Passed) {
        "Passed — all ${row.totalSkills} skills solid"
    } else {
        "${row.solidSkills} of ${row.totalSkills} skills solid"
    }
