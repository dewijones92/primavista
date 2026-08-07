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
 * docs/spec.md I4 on the real file. The JVM tests prove the migration registry is coherent; only
 * a device proves what actually happens when the file on disk is a version this build cannot read.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseOpeningTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        context.deleteDatabase(PrimaVistaDatabase.FILE_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(PrimaVistaDatabase.FILE_NAME)
    }

    @Test
    fun openingAFreshInstallSucceeds() {
        val opening = PrimaVistaDatabase.open(context)

        assertTrue("$opening", opening is DatabaseOpening.Opened)
        (opening as DatabaseOpening.Opened).database.close()
    }

    @Test
    fun aSessionSurvivesTheDatabaseBeingClosedAndReopened() = runBlocking {
        val first = (PrimaVistaDatabase.open(context) as DatabaseOpening.Opened).database
        RoomSessionStore(first).save(sampleSession(), emptyList())
        first.close()

        val second = (PrimaVistaDatabase.open(context) as DatabaseOpening.Opened).database
        try {
            assertEquals(listOf(SessionId("session-1")), RoomSessionStore(second).recent().map { it.id })
        } finally {
            second.close()
        }
    }

    /**
     * A file from a build this one cannot migrate must come back as a refusal with the rows still
     * on disk. Silently wiping it is what spec I4 forbids, and a crash loop is unrecoverable.
     */
    @Test
    fun aFutureVersionOnDiskIsRefusedAndTheRowsAreLeftAlone() = runBlocking {
        val seeded = (PrimaVistaDatabase.open(context) as DatabaseOpening.Opened).database
        RoomSessionStore(seeded).save(sampleSession(), emptyList())
        seeded.openHelper.writableDatabase.version = DATABASE_VERSION + 1
        seeded.close()

        val opening = PrimaVistaDatabase.open(context)

        assertTrue("expected a refusal, got $opening", opening is DatabaseOpening.Unreadable)
        assertTrue((opening as DatabaseOpening.Unreadable).reason.isNotBlank())
        assertTrue(
            "the history file was removed by a failed open",
            context.getDatabasePath(PrimaVistaDatabase.FILE_NAME).exists(),
        )
    }

    /** The destructive path exists, is separate, and is never what [PrimaVistaDatabase.open] does. */
    @Test
    fun resettingIsAnExplicitCallThatEmptiesTheHistory() = runBlocking {
        val seeded = (PrimaVistaDatabase.open(context) as DatabaseOpening.Opened).database
        RoomSessionStore(seeded).save(sampleSession(), emptyList())
        seeded.close()

        val fresh = PrimaVistaDatabase.resetDiscardingHistory(
            context,
            RecordingDiag(),
            reason = "test",
        )
        try {
            assertEquals(0, fresh.sessions().count())
        } finally {
            fresh.close()
        }
    }

    @Test
    fun resettingSaysSoInTheDiagnosticsLog() {
        val diag = RecordingDiag()

        PrimaVistaDatabase.resetDiscardingHistory(context, diag, reason = "Dewi chose it").close()

        assertTrue("the wipe was not logged: ${diag.lines}", diag.lines.any { it.contains("DISCARDING") })
        assertTrue(diag.lines.any { it.contains("Dewi chose it") })
    }
}
