package com.dewijones92.primavista.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dewijones92.primavista.practice.SkillOutcome
import com.dewijones92.primavista.practice.SkillState
import com.dewijones92.primavista.score.Clef
import com.dewijones92.primavista.score.PitchBand
import com.dewijones92.primavista.score.SkillTag
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SkillStoreTest {
    private lateinit var database: PrimaVistaDatabase
    private lateinit var store: RoomSkillStore
    private val ruleCalls = mutableListOf<List<SkillState>>()

    private val ledgerLines = SkillTag.LegerLines(Clef.Bass, 2, above = false)
    private val middleTreble = SkillTag.ClefRegion(Clef.Treble, PitchBand.MiddleStaff)

    /** Stands in for `PracticeScheduler.update`; see `.claude/CODE-NOTES.md`. */
    private val halvingRule = SkillUpdateRule { states, outcomes, nowEpochMillis ->
        ruleCalls += states
        val byTag = states.associateBy { it.tag }
        outcomes.map { outcome ->
            val existing = byTag[outcome.tag]
            SkillState(
                tag = outcome.tag,
                strength = outcome.accuracy,
                dueAtEpochMillis = nowEpochMillis + 1_000L,
                attempts = (existing?.attempts ?: 0) + outcome.attempts,
                lapses = (existing?.lapses ?: 0) + (outcome.attempts - outcome.cleanAttempts),
                repetition = if (outcome.cleanAttempts == outcome.attempts) {
                    (existing?.repetition ?: 0) + 1
                } else {
                    0
                },
            )
        }
    }

    @Before
    fun setUp() {
        database = openTestDatabase()
        store = RoomSkillStore(database, halvingRule)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun recordFoldsOutcomesThroughTheSuppliedRuleAndPersistsThem() = runBlocking {
        store.record(listOf(SkillOutcome(ledgerLines, attempts = 4, cleanAttempts = 1)), nowEpochMillis = 500L)

        val stored = store.states().single()
        assertEquals(ledgerLines, stored.tag)
        assertEquals(0.25, stored.strength, 1e-9)
        assertEquals(1_500L, stored.dueAtEpochMillis)
        assertEquals(4, stored.attempts)
        assertEquals(3, stored.lapses)
    }

    @Test
    fun theRuleSeesWhatWasAlreadyStoredSoAttemptsAccumulate() = runBlocking {
        store.record(listOf(SkillOutcome(ledgerLines, attempts = 2, cleanAttempts = 2)), nowEpochMillis = 0L)
        store.record(listOf(SkillOutcome(ledgerLines, attempts = 3, cleanAttempts = 0)), nowEpochMillis = 10L)

        assertEquals(5, store.states().single().attempts)
        assertEquals(listOf(0, 1), ruleCalls.map { it.size })
    }

    /**
     * The rung is only ever read back, never recomputed, so a store that drops it hands the rule a
     * skill that looks brand new. See `.claude/CODE-NOTES.md`.
     */
    @Test
    fun theRungClimbsAcrossSessionsBecauseItIsStoredRatherThanRederived() = runBlocking {
        repeat(3) { store.record(listOf(SkillOutcome(ledgerLines, attempts = 2, cleanAttempts = 2)), 0L) }

        assertEquals(3, store.states().single().repetition)
    }

    @Test
    fun aLapseTakesTheStoredRungBackDownRatherThanLeavingItStale() = runBlocking {
        store.record(listOf(SkillOutcome(ledgerLines, attempts = 2, cleanAttempts = 2)), 0L)
        store.record(listOf(SkillOutcome(ledgerLines, attempts = 2, cleanAttempts = 0)), 10L)

        assertEquals(0, store.states().single().repetition)
    }

    @Test
    fun statesForSkillsTheSessionDidNotTouchAreLeftAlone() = runBlocking {
        store.record(listOf(SkillOutcome(middleTreble, attempts = 1, cleanAttempts = 1)), nowEpochMillis = 0L)
        store.record(listOf(SkillOutcome(ledgerLines, attempts = 1, cleanAttempts = 0)), nowEpochMillis = 0L)

        assertEquals(setOf(middleTreble, ledgerLines), store.states().map { it.tag }.toSet())
    }

    @Test
    fun noOutcomesWritesNothing() = runBlocking {
        store.record(emptyList(), nowEpochMillis = 0L)

        assertEquals(0, database.skillStates().count())
        assertTrue(ruleCalls.isEmpty())
    }

    @Test
    fun aRowWithAnUnreadableKeyIsSkippedRatherThanCrashingTheStore() = runBlocking {
        database.skillStates().upsertAll(
            listOf(
                SkillStateEntity("somethingFromTheFuture|4", 0.5, 0L, 1, 0),
                SkillStateEntity(SkillTagKeys.encode(ledgerLines), 0.5, 0L, 1, 0),
            ),
        )

        assertEquals(listOf(ledgerLines), store.states().map { it.tag })
    }

    /**
     * The row stays on disk, so it comes back on every read. See `.claude/CODE-NOTES.md` for why
     * that must be counted rather than logged each time.
     */
    @Test
    fun anUnreadableRowIsNamedOnceAndCountedEveryTimeItComesBack() = runBlocking {
        val diag = RecordingDiag()
        val counting = RoomSkillStore(database, halvingRule, diag)
        database.skillStates().upsertAll(listOf(SkillStateEntity("somethingFromTheFuture|4", 0.5, 0L, 1, 0)))

        repeat(4) { counting.states() }

        assertEquals(1, diag.lines.count { it.contains("somethingFromTheFuture") })
        assertEquals(4, diag.counts["skillRowsUnreadable"])
    }

    /**
     * A v1 leger-lines row must not be re-read as an above-the-staff skill, and must not be
     * deleted either: a later build that learns to read it can still recover the strength.
     */
    @Test
    fun aFormatV1LegerLineRowIsIgnoredAndLeftOnDisk() = runBlocking {
        database.skillStates().upsertAll(listOf(SkillStateEntity("legerLines|Bass|2", 0.9, 0L, 40, 1)))

        store.record(listOf(SkillOutcome(ledgerLines, attempts = 1, cleanAttempts = 0)), nowEpochMillis = 0L)

        assertEquals(listOf(ledgerLines), store.states().map { it.tag })
        assertEquals(0.0, store.states().single().strength, 1e-9)
        assertEquals(2, database.skillStates().count())
        assertEquals(40, database.skillStates().byKey("legerLines|Bass|2")?.attempts)
    }

    /**
     * The bulk read is now wrapped like every other one, so a read that fails outright refuses
     * instead of taking the Progress screen down with it.
     */
    @Test
    fun aReadThatFailsOutrightRefusesRatherThanCrashingTheScreen() = runBlocking {
        val diag = RecordingDiag()
        val refusing = RoomSkillStore(database, halvingRule, diag)
        database.close()

        val reading = refusing.storedStates()

        assertTrue("expected a refusal, got $reading", reading is StoredReading.Unreadable)
        assertEquals(emptyList<SkillState>(), refusing.states())
        assertTrue(diag.lines.toString(), diag.lines.any { it.contains("could not be read at all") })
        assertTrue(diag.lines.toString(), diag.lines.any { it.contains("every skill will look brand new") })
    }

    /**
     * A fold onto states this build could not read would upsert beginners' figures over mature
     * ones — losing exactly what docs/spec.md I4 exists to keep. It must write nothing at all.
     */
    @Test
    fun aFoldThatFailsWritesNothingAndLeavesTheStoredStrengthsAlone() = runBlocking {
        store.record(listOf(SkillOutcome(ledgerLines, attempts = 2, cleanAttempts = 2)), nowEpochMillis = 0L)
        val before = store.states().single()

        val diag = RecordingDiag()
        val exploding = RoomSkillStore(database, SkillUpdateRule { _, _, _ -> error("the fold blew up") }, diag)
        exploding.record(listOf(SkillOutcome(middleTreble, attempts = 1, cleanAttempts = 0)), nowEpochMillis = 10L)

        assertEquals(listOf(before), store.states())
        assertTrue(diag.lines.toString(), diag.lines.any { it.contains("nothing written") })
    }

    @Test
    fun aFirstRunDeviceReadsAsAReadableEmptyListNotARefusal() = runBlocking {
        assertEquals(StoredReading.Readable(emptyList<SkillState>()), store.storedStates())
    }
}
