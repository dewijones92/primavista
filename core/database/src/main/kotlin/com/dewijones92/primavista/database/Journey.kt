package com.dewijones92.primavista.database

import com.dewijones92.primavista.practice.Placement
import com.dewijones92.primavista.practice.StageId

/**
 * When a stage first happened, dated.
 *
 * Dates are the whole content of this type: **where Dewi stands is
 * `Curriculum.currentStage(states)` and only ever that**, so nothing here answers it.
 * [firstPassedAtEpochMillis] is the day the curriculum first said the stage's skills were solid,
 * never a claim about today. See `.claude/CODE-NOTES.md`.
 */
public data class StageMilestone(
    val stage: StageId,
    val firstReachedAtEpochMillis: Long,
    val firstPassedAtEpochMillis: Long? = null,
)

/** Whether the placement read was taken or declined. Declining is free (docs/journey.md). */
public sealed interface PlacementOutcome {
    public data object Completed : PlacementOutcome

    public data object Skipped : PlacementOutcome
}

/**
 * One placement read that happened, and the line a report reads it from.
 *
 * It **seeded** skill states and then stopped mattering: the ordinary scheduler corrects an
 * over-generous placement within a session or two, so nothing here is ever consulted to decide
 * what Dewi can read. See `.claude/CODE-NOTES.md`.
 */
public data class PlacementRecord(
    val takenAtEpochMillis: Long,
    val outcome: PlacementOutcome,
    val probesTaken: Int = 0,
    val seededSkills: Int = 0,
    val summary: String = "",
) {
    init {
        require(probesTaken >= 0 && seededSkills >= 0) {
            "a placement read cannot have taken probes=$probesTaken seeded=$seededSkills"
        }
    }

    public companion object {
        /** The one place a [Placement] becomes a stored row, so the fields cannot be read twice. */
        public fun of(
            placement: Placement,
            takenAtEpochMillis: Long,
            outcome: PlacementOutcome,
        ): PlacementRecord = PlacementRecord(
            takenAtEpochMillis = takenAtEpochMillis,
            outcome = outcome,
            probesTaken = placement.probesTaken,
            seededSkills = placement.states.size,
            summary = placement.summary,
        )
    }
}

/**
 * What the stored placement read turned out to be. Never taken and unreadable are opposite
 * statements — the first offers Dewi the read, the second must not pretend he never took it.
 */
public sealed interface PlacementReading {
    public data object NeverTaken : PlacementReading

    public data class Taken(val record: PlacementRecord) : PlacementReading

    /** The read happened on [takenAtEpochMillis]; what it concluded is what could not be read. */
    public data class Unreadable(val takenAtEpochMillis: Long, val reason: String) : PlacementReading
}

/** The dated history behind the path: which stages have happened, and the placement read. */
public data class StoredJourney(
    val stages: List<StageMilestone> = emptyList(),
    val placement: PlacementReading = PlacementReading.NeverTaken,
)

/**
 * The journey's dated history — and deliberately nothing else.
 *
 * This store holds no opinion about Dewi's reading: the skill states do, the curriculum reads
 * them, and this only records when what they said first happened. See `.claude/CODE-NOTES.md`.
 */
public interface JourneyStore {
    /** Refuses rather than reading as a first run when the stored history cannot be trusted. */
    public suspend fun journey(): StoredReading<StoredJourney>

    /** Dates the first time the curriculum put Dewi on [stage]. Re-reaching it keeps the date. */
    public suspend fun recordStageReached(stage: StageId, atEpochMillis: Long)

    /** Dates a pass the curriculum has already decided on. The earliest date wins. */
    public suspend fun recordStagePassed(stage: StageId, atEpochMillis: Long)

    public suspend fun recordPlacement(record: PlacementRecord)

    /** Every placement read that has happened, newest first. */
    public suspend fun placements(): StoredReading<List<PlacementRecord>>
}
