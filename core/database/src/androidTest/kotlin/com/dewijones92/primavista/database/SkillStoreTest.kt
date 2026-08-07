package com.dewijones92.primavista.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dewijones92.primavista.practice.SkillOutcome
import com.dewijones92.primavista.practice.SkillState
import com.dewijones92.primavista.practice.SkillStore
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
    private lateinit var store: SkillStore
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
}
