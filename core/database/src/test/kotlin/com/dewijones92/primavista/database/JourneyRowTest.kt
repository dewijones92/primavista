package com.dewijones92.primavista.database

import com.dewijones92.primavista.practice.Placement
import com.dewijones92.primavista.practice.SkillState
import com.dewijones92.primavista.practice.StageId
import com.dewijones92.primavista.score.SkillTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The stored form of the journey, without a device: a row either reads or says why not. */
class JourneyRowTest {
    private val stage = StageId(4)

    @Test
    fun aCompletedPlacementRoundTripsThroughItsRow() {
        val record = PlacementRecord(
            takenAtEpochMillis = 1_700_000_000_000L,
            outcome = PlacementOutcome.Completed,
            probesTaken = 3,
            seededSkills = 9,
            summary = "placement seed=7 input=Mono probes=3 seeded=9skills",
        )

        assertEquals(PlacementRowReading.Readable(record), record.toEntity().read())
    }

    @Test
    fun aSkippedPlacementIsStoredAsSkipped() {
        val record = PlacementRecord(takenAtEpochMillis = 1L, outcome = PlacementOutcome.Skipped)

        val row = record.toEntity()

        assertEquals(PlacementKinds.SKIPPED, row.outcomeKind)
        assertEquals(PlacementRowReading.Readable(record), row.read())
    }

    /** A kind this build does not know must not read as "he never took it". */
    @Test
    fun anUnrecognisedOutcomeKindIsUnreadableAndKeepsTheDateItHappened() {
        val row = PlacementRecord(99L, PlacementOutcome.Skipped).toEntity().copy(outcomeKind = "graduated")

        val read = row.read()

        assertTrue("read as $read", read is PlacementRowReading.Unreadable)
        assertEquals(99L, (read as PlacementRowReading.Unreadable).takenAtEpochMillis)
        assertTrue(read.reason.contains("graduated"))
    }

    /** The seeded states are the placement's real output, so the row counts them rather than copying them. */
    @Test
    fun aPlacementRecordTakesItsFiguresFromThePlacementItself() {
        val placement = Placement(
            states = listOf(
                SkillState(SkillTag.HandIndependence, strength = 0.6, dueAtEpochMillis = 5L, attempts = 3, lapses = 1)
            ),
            probesTaken = 2,
            summary = "placement seed=1 probes=2 seeded=1skills",
        )

        val record = PlacementRecord.of(placement, takenAtEpochMillis = 42L, outcome = PlacementOutcome.Completed)

        assertEquals(2, record.probesTaken)
        assertEquals(1, record.seededSkills)
        assertEquals(placement.summary, record.summary)
        assertEquals(42L, record.takenAtEpochMillis)
    }

    @Test
    fun aStageRowRoundTripsWithItsDates() {
        val milestone = StageMilestone(stage, firstReachedAtEpochMillis = 10L, firstPassedAtEpochMillis = 20L)

        assertEquals(StageRowReading.Readable(milestone), milestone.toEntity().read())
    }

    @Test
    fun aStageNeverPassedKeepsANullDateRatherThanAZero() {
        val milestone = StageMilestone(stage, firstReachedAtEpochMillis = 10L)

        assertEquals(null, milestone.toEntity().firstPassedAtEpochMillis)
        assertEquals(StageRowReading.Readable(milestone), milestone.toEntity().read())
    }

    /** Stage numbers are 1-based, so a zero is a corrupt row rather than a stage before the first. */
    @Test
    fun aStageNumberBelowTheFirstRungIsUnreadable() {
        val row = StageProgressEntity(stageNumber = 0, firstReachedAtEpochMillis = 1L, firstPassedAtEpochMillis = null)

        val read = row.read()

        assertTrue("read as $read", read is StageRowReading.Unreadable)
        assertEquals(0, (read as StageRowReading.Unreadable).stageNumber)
    }

    @Test
    fun aPlacementCannotHaveTakenANegativeNumberOfProbes() {
        val refused = runCatching { PlacementRecord(1L, PlacementOutcome.Skipped, probesTaken = -1) }

        assertTrue(refused.isFailure)
    }
}
