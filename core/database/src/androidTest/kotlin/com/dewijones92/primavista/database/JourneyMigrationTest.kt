package com.dewijones92.primavista.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dewijones92.primavista.practice.StageId
import com.dewijones92.primavista.score.Clef
import com.dewijones92.primavista.score.SkillTag
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Schema v3 adds the journey. docs/spec.md I4 says what was practised is not lost, so the upgrade
 * is run against a file built from the shipped schema rather than against a fresh one.
 */
@RunWith(AndroidJUnit4::class)
class JourneyMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val ledgerLines = SkillTag.LegerLines(Clef.Bass, 2, above = false)
    private val keepStates = SkillUpdateRule { states, _, _ -> states }

    @Before
    fun setUp() {
        context.deleteDatabase(PrimaVistaDatabase.FILE_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(PrimaVistaDatabase.FILE_NAME)
    }

    /** A v2 file is what is on Dewi's phone today, so the upgrade is run against one. */
    @Test
    fun aVersionTwoFileKeepsEverySessionVerdictAndSkillItHeld() = runBlocking {
        createDatabaseAtVersion(context, PrimaVistaDatabase.FILE_NAME, version = 2) {
            seedSession()
            seedVerdict()
            seedSkill(repetition = 5)
        }

        opened().use { database ->
            val session = RoomSessionStore(database).recent().readOrFail().single()
            assertEquals("Study in A minor", session.scoreTitle)
            assertEquals(9, session.correct)
            assertEquals(1, RoomSessionStore(database).judgements(session.id).readOrFail().size)

            val skill = RoomSkillStore(database, keepStates).states().single()
            assertEquals(ledgerLines, skill.tag)
            assertEquals(0.8, skill.strength, 1e-9)
            assertEquals(5, skill.repetition)
        }
    }

    @Test
    fun aVersionTwoFileGainsAJourneyThatStartsEmptyRatherThanRefusing() = runBlocking {
        createDatabaseAtVersion(context, PrimaVistaDatabase.FILE_NAME, version = 2) { seedSession() }

        opened().use { database ->
            val store = RoomJourneyStore(database)
            assertEquals(StoredJourney(), store.journey().readOrFail())

            store.recordStageReached(StageId(1), atEpochMillis = 1_000L)
            assertEquals(listOf(StageId(1)), store.journey().readOrFail().stages.map { it.stage })
        }
    }

    /** Two migrations in a row: a phone that skipped an update must not be stranded. */
    @Test
    fun aVersionOneFileMigratesAllTheWayAndKeepsItsStrength() = runBlocking {
        createDatabaseAtVersion(context, PrimaVistaDatabase.FILE_NAME, version = 1) {
            execSQL(
                "INSERT INTO skill_states (skillKey, strength, dueAtEpochMillis, attempts, lapses) " +
                    "VALUES (?, ?, ?, ?, ?)",
                arrayOf<Any>(SkillTagKeys.encode(ledgerLines), 0.8, 1_700_000_000_000L, 40, 3),
            )
        }

        opened().use { database ->
            val skill = RoomSkillStore(database, keepStates).states().single()
            assertEquals(0.8, skill.strength, 1e-9)
            assertEquals(0, skill.repetition)

            val store = RoomJourneyStore(database)
            store.recordPlacement(PlacementRecord(2_000L, PlacementOutcome.Skipped))
            assertTrue(store.journey().readOrFail().placement is PlacementReading.Taken)
            assertEquals(1, database.journey().stageCount() + store.placements().readOrFail().size)
        }
    }

    private fun SQLiteDatabase.seedSession() = execSQL(
        "INSERT INTO sessions (id, scoreId, scoreTitle, originKind, originSourceName, originLicence, " +
            "originSeed, originSpec, inputLabel, polyphony, tempoBpm, latencyMillis, latencyProvenance, " +
            "startedAtEpochMillis, finishedAtEpochMillis, notesExpected, correct) " +
            "VALUES ('session-1', 'score-1', 'Study in A minor', 'parsed', 'study.musicxml', 'Public Domain', " +
            "NULL, NULL, 'tap', 'Mono', 76, 61.5, 'Measured', 1700000000000, 1700000060000, 12, 9)",
    )

    private fun SQLiteDatabase.seedVerdict() = execSQL(
        "INSERT INTO note_verdicts (sessionId, noteIndex, kind, expectedMidi, heardMidi, dtMillis, " +
            "atTicks, confidence) VALUES ('session-1', 0, 'correct', NULL, NULL, -12.5, NULL, 0.91)",
    )

    private fun SQLiteDatabase.seedSkill(repetition: Int) = execSQL(
        "INSERT INTO skill_states (skillKey, strength, dueAtEpochMillis, attempts, lapses, repetition) " +
            "VALUES (?, ?, ?, ?, ?, ?)",
        arrayOf<Any>(SkillTagKeys.encode(ledgerLines), 0.8, 1_700_000_000_000L, 40, 3, repetition),
    )

    private fun opened(): PrimaVistaDatabase = openRealDatabase(context)
}
