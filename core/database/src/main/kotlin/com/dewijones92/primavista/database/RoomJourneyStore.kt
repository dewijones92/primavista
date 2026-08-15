package com.dewijones92.primavista.database

import androidx.room.withTransaction
import com.dewijones92.primavista.common.Diag
import com.dewijones92.primavista.common.NoOpDiag
import com.dewijones92.primavista.practice.StageId

/** Room's [JourneyStore]. */
public class RoomJourneyStore(
    private val database: PrimaVistaDatabase,
    private val diag: Diag = NoOpDiag,
) : JourneyStore {
    private val journeyDao: JourneyDao = database.journey()
    private val unreadableStages = UnreadableRowLog(diag, TAG, "stageRowsUnreadable")
    private val unreadablePlacements = UnreadableRowLog(diag, TAG, "placementRowsUnreadable")

    override suspend fun journey(): StoredReading<StoredJourney> =
        diag.readOrRefuse(TAG, "the journey so far") { readJourneyOrThrow() }

    override suspend fun recordStageReached(stage: StageId, atEpochMillis: Long) {
        val alreadyDated = database.withTransaction {
            val known = journeyDao.stage(stage.number)
            if (known == null) {
                journeyDao.upsert(StageMilestone(stage, firstReachedAtEpochMillis = atEpochMillis).toEntity())
            }
            known?.firstReachedAtEpochMillis
        }
        diag.event(
            TAG,
            if (alreadyDated == null) {
                "stage=${stage.number} reached for the first time at=$atEpochMillis"
            } else {
                "stage=${stage.number} reached again at=$atEpochMillis; nothing written, first " +
                    "reached at=$alreadyDated is the one on record"
            },
        )
    }

    override suspend fun recordStagePassed(stage: StageId, atEpochMillis: Long) {
        val alreadyDated = database.withTransaction {
            val known = journeyDao.stage(stage.number)
            val dated = known?.firstPassedAtEpochMillis
            if (dated == null) {
                journeyDao.upsert(
                    StageMilestone(
                        stage = stage,
                        firstReachedAtEpochMillis = known?.firstReachedAtEpochMillis ?: atEpochMillis,
                        firstPassedAtEpochMillis = atEpochMillis,
                    ).toEntity(),
                )
            }
            dated
        }
        diag.event(
            TAG,
            if (alreadyDated == null) {
                "stage=${stage.number} first passed at=$atEpochMillis, dated because the curriculum " +
                    "read the skill states and said they are solid"
            } else {
                "stage=${stage.number} passed again at=$atEpochMillis; nothing written, the first " +
                    "pass at=$alreadyDated is the one on record"
            },
        )
    }

    override suspend fun recordPlacement(record: PlacementRecord) {
        journeyDao.insert(record.toEntity())
        diag.event(TAG, describePlacement(record))
    }

    override suspend fun placements(): StoredReading<List<PlacementRecord>> =
        diag.readOrRefuse(TAG, "the placement reads") {
            journeyDao.placements().mapNotNull { row ->
                when (val reading = row.read()) {
                    is PlacementRowReading.Readable -> reading.record
                    is PlacementRowReading.Unreadable -> {
                        reportLostPlacement(reading)
                        null
                    }
                }
            }
        }

    private suspend fun readJourneyOrThrow(): StoredJourney {
        val journey = StoredJourney(readStages(), readPlacement())
        diag.event(TAG, describeJourney(journey))
        return journey
    }

    private suspend fun readStages(): List<StageMilestone> = journeyDao.stages().mapNotNull { row ->
        when (val reading = row.read()) {
            is StageRowReading.Readable -> reading.milestone
            is StageRowReading.Unreadable -> {
                unreadableStages.report(
                    reading.stageNumber.toString(),
                    "stage row kept on disk but unreadable, stage=${reading.stageNumber}: ${reading.reason}",
                )
                null
            }
        }
    }

    private suspend fun readPlacement(): PlacementReading {
        val row = journeyDao.latestPlacement() ?: return PlacementReading.NeverTaken
        return when (val reading = row.read()) {
            is PlacementRowReading.Readable -> PlacementReading.Taken(reading.record)
            is PlacementRowReading.Unreadable -> {
                reportLostPlacement(reading)
                PlacementReading.Unreadable(reading.takenAtEpochMillis, reading.reason)
            }
        }
    }

    private fun reportLostPlacement(reading: PlacementRowReading.Unreadable) {
        unreadablePlacements.report(
            reading.takenAtEpochMillis.toString(),
            "placement read at=${reading.takenAtEpochMillis} kept on disk but its conclusion is " +
                "unreadable: ${reading.reason}",
        )
    }

    private fun describeJourney(journey: StoredJourney): String {
        val placement = when (val settled = journey.placement) {
            PlacementReading.NeverTaken -> "neverTaken"
            is PlacementReading.Taken ->
                "taken at=${settled.record.takenAtEpochMillis} outcome=${settled.record.outcome}"
            is PlacementReading.Unreadable ->
                "taken at=${settled.takenAtEpochMillis} but unreadable: ${settled.reason}"
        }
        val passed = journey.stages.count { it.firstPassedAtEpochMillis != null }
        return "journey read stagesReached=${journey.stages.size} stagesEverPassed=$passed " +
            "furthest=${journey.stages.maxOfOrNull { it.stage.number } ?: "(none yet)"} placement=$placement"
    }

    private fun describePlacement(record: PlacementRecord): String {
        val outcome = when (record.outcome) {
            PlacementOutcome.Completed -> "completed"
            PlacementOutcome.Skipped -> "skipped, so it concluded nothing about his reading"
        }
        return "placement $outcome at=${record.takenAtEpochMillis} probes=${record.probesTaken} " +
            "seededSkills=${record.seededSkills} summary=[${record.summary}]"
    }

    private companion object {
        const val TAG = "db.journey"
    }
}
