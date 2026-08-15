package com.dewijones92.primavista.database

import com.dewijones92.primavista.practice.StageId

/** Literal strings for the same reason as [OriginKinds]: adding a case must not move a stored row. */
public object PlacementKinds {
    public const val COMPLETED: String = "completed"
    public const val SKIPPED: String = "skipped"
}

internal sealed interface StageRowReading {
    data class Readable(val milestone: StageMilestone) : StageRowReading

    data class Unreadable(val stageNumber: Int, val reason: String) : StageRowReading
}

internal sealed interface PlacementRowReading {
    data class Readable(val record: PlacementRecord) : PlacementRowReading

    data class Unreadable(val takenAtEpochMillis: Long, val reason: String) : PlacementRowReading
}

internal fun StageMilestone.toEntity(): StageProgressEntity = StageProgressEntity(
    stageNumber = stage.number,
    firstReachedAtEpochMillis = firstReachedAtEpochMillis,
    firstPassedAtEpochMillis = firstPassedAtEpochMillis,
)

internal fun StageProgressEntity.read(): StageRowReading = runCatching {
    StageRowReading.Readable(
        StageMilestone(StageId(stageNumber), firstReachedAtEpochMillis, firstPassedAtEpochMillis),
    )
}.getOrElse { StageRowReading.Unreadable(stageNumber, it.message ?: it.toString()) }

internal fun PlacementRecord.toEntity(): PlacementReadEntity = PlacementReadEntity(
    takenAtEpochMillis = takenAtEpochMillis,
    outcomeKind = when (outcome) {
        PlacementOutcome.Completed -> PlacementKinds.COMPLETED
        PlacementOutcome.Skipped -> PlacementKinds.SKIPPED
    },
    probesTaken = probesTaken,
    seededSkills = seededSkills,
    summary = summary,
)

internal fun PlacementReadEntity.read(): PlacementRowReading = runCatching {
    PlacementRowReading.Readable(
        PlacementRecord(takenAtEpochMillis, outcomeOrThrow(), probesTaken, seededSkills, summary),
    )
}.getOrElse { PlacementRowReading.Unreadable(takenAtEpochMillis, it.message ?: it.toString()) }

private fun PlacementReadEntity.outcomeOrThrow(): PlacementOutcome = when (outcomeKind) {
    PlacementKinds.COMPLETED -> PlacementOutcome.Completed
    PlacementKinds.SKIPPED -> PlacementOutcome.Skipped
    else -> error("unrecognised placement outcome '$outcomeKind'")
}
