package com.dewijones92.primavista.di

import com.dewijones92.primavista.database.PlacementOutcome
import com.dewijones92.primavista.database.PlacementReading
import com.dewijones92.primavista.database.PlacementRecord
import com.dewijones92.primavista.database.PracticeSettings
import com.dewijones92.primavista.database.StageMilestone
import com.dewijones92.primavista.database.StoredReading
import com.dewijones92.primavista.practice.Curriculum
import com.dewijones92.primavista.practice.Placement
import com.dewijones92.primavista.practice.PlacementRead
import com.dewijones92.primavista.practice.SkillOutcome
import com.dewijones92.primavista.practice.SkillState
import com.dewijones92.primavista.practice.Streak
import com.dewijones92.primavista.score.Polyphony
import com.dewijones92.primavista.score.SkillTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "journey"

/**
 * A skill a mono input can never be credited for, so nothing may claim it read one.
 *
 * One definition, asked by the drill route (which must not propose it) and by the path (which
 * must say out loud that a rung is unreachable). Two spellings of this rule is how a mic reader
 * ends up at a wall with no explanation — docs/spec.md I3 applied to progress.
 */
internal fun SkillTag.isHearableBy(input: Polyphony): Boolean =
    input == Polyphony.Poly || this != SkillTag.HandIndependence

/**
 * Everything the path draws itself from, read in one pass so two halves cannot disagree about
 * where Dewi stands.
 *
 * There is no stored "current stage" here and there must not be: `Curriculum.currentStage(states)`
 * is the only answer, and [states] is what it is asked about.
 */
public data class JourneyReading(
    val states: List<SkillState>,
    val milestones: List<StageMilestone>,
    val streak: Streak,
    val placement: PlacementReading,
    val input: InputMode,
    /** Reads that refused. The path still draws, and says out loud what it could not read. */
    val refusals: List<StoredReading.Unreadable> = emptyList(),
)

/** Reading and dating the journey. The path's whole dependency, so a screen never holds a store. */
public interface JourneyWiring {
    public val curriculum: Curriculum

    public val placementRead: PlacementRead

    public fun nowEpochMillis(): Long

    public suspend fun read(): StoredReading<JourneyReading>

    /**
     * Seeds the skill store from what a placement measured, and records that it happened.
     *
     * A skipped read seeds nothing and is still recorded: the row is what stops the introduction
     * being offered again, and declining must cost nothing (docs/journey.md).
     */
    public suspend fun settle(placement: Placement, evidence: List<SkillOutcome>, outcome: PlacementOutcome)

    /** Dates the stage Dewi now stands on, and every stage the curriculum says is passed. */
    public suspend fun markStanding()

    /** Switches the stored input to the tapped keyboard, which is the only one that hears two hands. */
    public suspend fun chooseTappedKeyboard()
}

internal class AppJourneyWiring(private val container: AppContainer) : JourneyWiring {

    private val diag = container.diag

    override val curriculum: Curriculum get() = container.curriculum

    override val placementRead: PlacementRead get() = container.placementRead

    override fun nowEpochMillis(): Long = System.currentTimeMillis()

    override suspend fun read(): StoredReading<JourneyReading> = withContext(Dispatchers.IO) {
        val skills = container.skillStore?.storedStates() ?: unopened("what you have practised")
        when (skills) {
            is StoredReading.Unreadable -> skills
            is StoredReading.Readable -> StoredReading.Readable(assemble(skills.value))
        }
    }

    override suspend fun settle(
        placement: Placement,
        evidence: List<SkillOutcome>,
        outcome: PlacementOutcome,
    ) {
        val now = nowEpochMillis()
        container.seedSkills(placement.states, evidence, now)
        val store = container.journeyStore
        if (store == null) {
            diag.event(
                TAG,
                "placement NOT recorded: the database could not be opened, so the introduction " +
                    "will be offered again next launch [${placement.summary}]",
            )
            return
        }
        store.recordPlacement(PlacementRecord.of(placement, now, outcome))
        markStanding()
    }

    override suspend fun markStanding() {
        val store = container.journeyStore ?: return
        val now = nowEpochMillis()
        val states = withContext(Dispatchers.IO) { container.skillStore?.states().orEmpty() }
        val standing = curriculum.currentStage(states)
        curriculum.stages
            .filter { it.id.number <= standing.id.number }
            .forEach { store.recordStageReached(it.id, now) }
        curriculum.stages
            .filter { curriculum.isPassed(it, states) }
            .forEach { store.recordStagePassed(it.id, now) }
    }

    override suspend fun chooseTappedKeyboard() {
        container.practiceWiring.preferences.remember { it.copy(inputLabel = InputMode.Tap.label) }
        diag.event(TAG, "input switched to ${InputMode.Tap.label}: the path asked, because a mic cannot hear two hands")
    }

    private suspend fun assemble(states: List<SkillState>): JourneyReading {
        val refusals = mutableListOf<StoredReading.Unreadable>()
        val journey = (container.journeyStore?.journey() ?: unopened("the path so far")).orNull(refusals)
        val streak = (
            container.sessionStore?.streak(container.zone, nowEpochMillis())
                ?: unopened("the days you have practised")
            ).orNull(refusals)
        val settings = container.settingsStore?.settings() ?: PracticeSettings()
        val reading = JourneyReading(
            states = states,
            milestones = journey?.stages.orEmpty(),
            streak = streak ?: Streak.None,
            placement = journey?.placement ?: PlacementReading.NeverTaken,
            input = openingInput(settings, container.microphoneGranted()).mode,
            refusals = refusals,
        )
        diag.event(TAG, describe(reading))
        return reading
    }

    private fun describe(reading: JourneyReading): String {
        val standing = curriculum.currentStage(reading.states)
        val everPassed = reading.milestones.count { it.firstPassedAtEpochMillis != null }
        return "path read stage=${standing.id.number}/${curriculum.stages.size} '${standing.title}' " +
            "skills=${reading.states.size} solid=${reading.states.count { it.isSolid }} " +
            "reached=${reading.milestones.size} passed=$everPassed " +
            "streak=${reading.streak.currentDays}d best=${reading.streak.bestDays}d " +
            "days=${reading.streak.daysPractised} input=${reading.input.label} " +
            "placement=${reading.placement::class.simpleName} refused=${reading.refusals.size}"
    }
}

private fun <T> StoredReading<T>.orNull(into: MutableList<StoredReading.Unreadable>): T? = when (this) {
    is StoredReading.Readable -> value
    is StoredReading.Unreadable -> {
        into += this
        null
    }
}

private fun unopened(what: String): StoredReading.Unreadable =
    StoredReading.Unreadable(what, "the practice database could not be opened")
