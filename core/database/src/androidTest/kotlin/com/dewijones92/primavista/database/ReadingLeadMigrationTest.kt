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

private const val STORED_TEMPO_BPM = 84
private const val ONE_BAR_OF_BEATS = 4

/**
 * Schema v4 adds the reading-ahead lead. A preference nobody set must not start covering the music,
 * so an upgraded row reads as **off** — and everything Dewi had already chosen has to survive
 * (docs/spec.md I4).
 */
@RunWith(AndroidJUnit4::class)
class ReadingLeadMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        context.deleteDatabase(PrimaVistaDatabase.FILE_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(PrimaVistaDatabase.FILE_NAME)
    }

    /** A v3 file is what is on Dewi's phone today, so the upgrade is run against one. */
    @Test
    fun aVersionThreeSettingsRowKeepsItsChoicesAndReadsAheadOff() = runBlocking {
        createDatabaseAtVersion(context, PrimaVistaDatabase.FILE_NAME, version = 3) {
            execSQL(
                "INSERT INTO settings (id, tempoBpm, metronomeOn, listenFirstOn, inputLabel) " +
                    "VALUES (?, ?, ?, ?, ?)",
                arrayOf<Any>(SettingsEntity.SINGLETON_ID, STORED_TEMPO_BPM, 0, 1, "PLAY IT"),
            )
        }

        val reloaded = opened().use { RoomSettingsStore(it).settings() }

        assertEquals(STORED_TEMPO_BPM, reloaded.tempoBpm)
        assertEquals(false, reloaded.metronomeOn)
        assertEquals(true, reloaded.listenFirstOn)
        assertEquals("PLAY IT", reloaded.inputLabel)
        assertEquals(0, reloaded.readingLeadBeats)
    }

    @Test
    fun aChosenLeadSurvivesAReopen() = runBlocking {
        opened().use { RoomSettingsStore(it).save(PracticeSettings(readingLeadBeats = ONE_BAR_OF_BEATS)) }

        val reloaded = opened().use { RoomSettingsStore(it).settings() }

        assertEquals(ONE_BAR_OF_BEATS, reloaded.readingLeadBeats)
    }

    private fun opened(): PrimaVistaDatabase = openRealDatabase(context)

    @Test
    fun noStoredVersionIsStrandedByTheNewStep() {
        assertTrue(
            PrimaVistaMigrations.strandedVersions().toString(),
            PrimaVistaMigrations.strandedVersions().isEmpty()
        )
    }
}
