package com.dewijones92.primavista.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SQLite ignores ON DELETE CASCADE unless `foreign_keys` is ON, so all three assertions are
 * needed: the pragma, an orphan being refused, and the cascade actually firing. See
 * `.claude/CODE-NOTES.md`.
 */
@RunWith(AndroidJUnit4::class)
class ForeignKeyCascadeTest {
    private lateinit var database: PrimaVistaDatabase

    @Before
    fun setUp() {
        database = openTestDatabase()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun theInMemoryDatabaseReallyHasForeignKeysOn() {
        assertTrue(database.foreignKeysOn())
    }

    /** The in-memory builder proving the pragma says nothing about the one the app actually opens. */
    @Test
    fun theDatabaseTheAppOpensAlsoHasForeignKeysOn() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(PrimaVistaDatabase.FILE_NAME)
        val onDisk = (PrimaVistaDatabase.open(context) as DatabaseOpening.Opened).database
        try {
            assertTrue("PrimaVistaDatabase.open left foreign keys off", onDisk.foreignKeysOn())
            val orphan = runCatching {
                runBlocking { onDisk.noteVerdicts().insertAll(listOf(verdictRow("no-such-session"))) }
            }
            assertTrue("the app's database accepted an orphan verdict", orphan.isFailure)
        } finally {
            onDisk.close()
            context.deleteDatabase(PrimaVistaDatabase.FILE_NAME)
        }
    }

    @Test
    fun aVerdictForAnUnknownSessionIsRefused() = runBlocking {
        val attempt = runCatching {
            database.noteVerdicts().insertAll(listOf(verdictRow("no-such-session")))
        }

        assertTrue("an orphan verdict was accepted, so foreign keys are off", attempt.isFailure)
        assertEquals(0, database.noteVerdicts().count())
    }

    @Test
    fun deletingASessionCascadesToItsVerdicts() = runBlocking {
        val session = sampleSession()
        database.sessions().upsert(session.toEntity())
        database.noteVerdicts().insertAll(
            listOf(verdictRow(session.id.value), verdictRow(session.id.value, noteIndex = 1)),
        )
        assertEquals(2, database.noteVerdicts().count())

        database.sessions().delete(session.id.value)

        assertEquals(0, database.noteVerdicts().count())
    }

    private fun verdictRow(sessionId: String, noteIndex: Int = 0): NoteVerdictEntity = NoteVerdictEntity(
        sessionId = sessionId,
        noteIndex = noteIndex,
        kind = VerdictKinds.MISSED,
        expectedMidi = null,
        heardMidi = null,
        dtMillis = null,
        atTicks = null,
        confidence = 1f,
    )
}
