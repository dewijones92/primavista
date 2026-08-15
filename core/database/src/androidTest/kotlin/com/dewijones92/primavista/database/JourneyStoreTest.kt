package com.dewijones92.primavista.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dewijones92.primavista.practice.StageId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JourneyStoreTest {
    private lateinit var database: PrimaVistaDatabase
    private lateinit var store: JourneyStore
    private val fiveLines = StageId(1)
    private val bothHands = StageId(4)

    @Before
    fun setUp() {
        database = openTestDatabase()
        store = RoomJourneyStore(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    /** A device that has never started is a true "nothing here yet", not a refusal. */
    @Test
    fun aFirstRunReadsAsNoStagesYetRatherThanARefusal() = runBlocking {
        val journey = store.journey().readOrFail()

        assertEquals(emptyList<StageMilestone>(), journey.stages)
        assertEquals(PlacementReading.NeverTaken, journey.placement)
    }

    @Test
    fun reachingAStageDatesIt() = runBlocking {
        store.recordStageReached(fiveLines, atEpochMillis = 1_000L)

        assertEquals(listOf(StageMilestone(fiveLines, 1_000L)), store.journey().readOrFail().stages)
    }

    @Test
    fun reachingAStageAgainKeepsTheDayHeFirstReachedIt() = runBlocking {
        store.recordStageReached(fiveLines, atEpochMillis = 1_000L)

        store.recordStageReached(fiveLines, atEpochMillis = 9_000L)

        assertEquals(1_000L, store.journey().readOrFail().stages.single().firstReachedAtEpochMillis)
    }

    /**
     * A pass is dated once. Passing again must not restamp it, or a report cannot say when the
     * reading actually became solid.
     */
    @Test
    fun passingAStageIsDatedOnceAndTheEarliestDateWins() = runBlocking {
        store.recordStageReached(fiveLines, atEpochMillis = 1_000L)
        store.recordStagePassed(fiveLines, atEpochMillis = 4_000L)

        store.recordStagePassed(fiveLines, atEpochMillis = 8_000L)

        assertEquals(4_000L, store.journey().readOrFail().stages.single().firstPassedAtEpochMillis)
    }

    @Test
    fun passingAStageKeepsTheDayItWasReached() = runBlocking {
        store.recordStageReached(bothHands, atEpochMillis = 1_000L)

        store.recordStagePassed(bothHands, atEpochMillis = 6_000L)

        assertEquals(StageMilestone(bothHands, 1_000L, 6_000L), store.journey().readOrFail().stages.single())
    }

    @Test
    fun aStagePassedWithoutAReachOnRecordIsStillDated() = runBlocking {
        store.recordStagePassed(bothHands, atEpochMillis = 5_000L)

        assertEquals(StageMilestone(bothHands, 5_000L, 5_000L), store.journey().readOrFail().stages.single())
    }

    /** A skill can lapse, so the curriculum may put him back. The dates already earned stay. */
    @Test
    fun beingPutBackToAnEarlierStageKeepsWhatWasAlreadyPassed() = runBlocking {
        store.recordStageReached(fiveLines, atEpochMillis = 1_000L)
        store.recordStagePassed(fiveLines, atEpochMillis = 2_000L)
        store.recordStageReached(bothHands, atEpochMillis = 3_000L)

        store.recordStageReached(fiveLines, atEpochMillis = 4_000L)

        val stages = store.journey().readOrFail().stages
        assertEquals(listOf(fiveLines, bothHands), stages.map { it.stage })
        assertEquals(2_000L, stages.first { it.stage == fiveLines }.firstPassedAtEpochMillis)
        assertNull(stages.first { it.stage == bothHands }.firstPassedAtEpochMillis)
    }

    @Test
    fun aPlacementReadKeepsWhatItTookAndWhatItSeeded() = runBlocking {
        val record = PlacementRecord(
            takenAtEpochMillis = 1_700_000_000_000L,
            outcome = PlacementOutcome.Completed,
            probesTaken = 3,
            seededSkills = 9,
            summary = "placement seed=7 probes=3 seeded=9skills",
        )

        store.recordPlacement(record)

        assertEquals(PlacementReading.Taken(record), store.journey().readOrFail().placement)
        assertEquals(listOf(record), store.placements().readOrFail())
    }

    @Test
    fun aSkippedPlacementIsRecordedAsHavingConcludedNothing() = runBlocking {
        store.recordPlacement(PlacementRecord(1L, PlacementOutcome.Skipped))

        val placement = store.journey().readOrFail().placement

        assertTrue("read as $placement", placement is PlacementReading.Taken)
        assertEquals(PlacementOutcome.Skipped, (placement as PlacementReading.Taken).record.outcome)
    }

    /** Re-taking the placement is a second event; the history keeps both. */
    @Test
    fun aSecondPlacementReadIsKeptAndTheLatestIsTheOneRead() = runBlocking {
        store.recordPlacement(PlacementRecord(1_000L, PlacementOutcome.Skipped))
        val retaken = PlacementRecord(2_000L, PlacementOutcome.Completed, probesTaken = 4, seededSkills = 7)

        store.recordPlacement(retaken)

        assertEquals(PlacementReading.Taken(retaken), store.journey().readOrFail().placement)
        assertEquals(2, store.placements().readOrFail().size)
    }

    /** Offering the placement read again to someone who took it is a lie about his history. */
    @Test
    fun aPlacementThisBuildCannotReadIsNotTheSameAsNeverHavingTakenOne() = runBlocking {
        val diag = RecordingDiag()
        store = RoomJourneyStore(database, diag)
        store.recordPlacement(PlacementRecord(1_234L, PlacementOutcome.Skipped))
        writable().execSQL("UPDATE placement_reads SET outcomeKind = 'graduated'")

        val placement = store.journey().readOrFail().placement

        assertTrue("read as $placement", placement is PlacementReading.Unreadable)
        assertEquals(1_234L, (placement as PlacementReading.Unreadable).takenAtEpochMillis)
        assertTrue(placement.reason.contains("graduated"))
        assertTrue(diag.lines.any { it.contains("kept on disk") && it.contains("1234") })
    }

    @Test
    fun aStageRowThisBuildCannotReadIsNamedAndTheRestOfThePathStillReads() = runBlocking {
        val diag = RecordingDiag()
        store = RoomJourneyStore(database, diag)
        store.recordStageReached(fiveLines, atEpochMillis = 1_000L)
        store.recordStageReached(bothHands, atEpochMillis = 2_000L)
        writable().execSQL("UPDATE stage_progress SET stageNumber = 0 WHERE stageNumber = 1")

        val journey = store.journey().readOrFail()

        assertEquals(listOf(bothHands), journey.stages.map { it.stage })
        assertTrue(diag.lines.any { it.contains("stage row kept on disk") && it.contains("stage=0") })
    }

    /** Placement seeds the skill states; the journey must not become a second opinion. */
    @Test
    fun recordingTheJourneyWritesNoSkillStateOfItsOwn() = runBlocking {
        store.recordStageReached(bothHands, atEpochMillis = 1_000L)
        store.recordStagePassed(bothHands, atEpochMillis = 2_000L)
        store.recordPlacement(PlacementRecord(3_000L, PlacementOutcome.Completed, 4, 8, "seeded"))

        assertEquals(0, database.skillStates().count())
    }

    private fun writable() = database.openHelper.writableDatabase
}
