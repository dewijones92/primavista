package com.dewijones92.primavista.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dewijones92.primavista.practice.InputLatency
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsStoreTest {
    private lateinit var database: PrimaVistaDatabase
    private lateinit var store: SettingsStore

    @Before
    fun setUp() {
        database = openTestDatabase()
        store = RoomSettingsStore(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun anEmptyDatabaseReadsAsTheDefaults() = runBlocking {
        assertEquals(PracticeSettings(), store.settings())
        assertEquals(PracticeSettings(), store.observe().first())
    }

    @Test
    fun savingRepeatedlyKeepsExactlyOneRow() = runBlocking {
        store.save(PracticeSettings(tempoBpm = 60, metronomeOn = false, listenFirstOn = true, inputLabel = "mic"))
        store.save(PracticeSettings(tempoBpm = 92, metronomeOn = true, listenFirstOn = false, inputLabel = "tap"))

        assertEquals(1, database.settings().count())
        assertEquals(
            PracticeSettings(tempoBpm = 92, metronomeOn = true, listenFirstOn = false, inputLabel = "tap"),
            store.settings(),
        )
    }

    @Test
    fun anUnmeasuredRouteReadsAsNullRatherThanZeroLatency() = runBlocking {
        assertEquals(StoredReading.Readable(null), store.latency(AudioRoute("BLUETOOTH_A2DP")))
    }

    @Test
    fun latencyIsStoredPerRouteWithItsProvenance() = runBlocking {
        val wired = AudioRoute("WIRED_HEADSET")
        val speaker = AudioRoute("BUILTIN_SPEAKER")
        store.recordLatency(wired, InputLatency(41.0, InputLatency.Provenance.Measured), atEpochMillis = 7L)
        store.recordLatency(speaker, InputLatency(180.0, InputLatency.Provenance.Assumed), atEpochMillis = 8L)

        assertEquals(InputLatency(41.0, InputLatency.Provenance.Measured), store.latency(wired).valueOrNull())
        assertEquals(InputLatency(180.0, InputLatency.Provenance.Assumed), store.latency(speaker).valueOrNull())
    }

    @Test
    fun remeasuringARouteReplacesItsFigure() = runBlocking {
        val wired = AudioRoute("WIRED_HEADSET")
        store.recordLatency(wired, InputLatency(180.0, InputLatency.Provenance.Assumed), atEpochMillis = 1L)
        store.recordLatency(wired, InputLatency(39.5, InputLatency.Provenance.Measured), atEpochMillis = 2L)

        assertEquals(InputLatency(39.5, InputLatency.Provenance.Measured), store.latency(wired).valueOrNull())
        assertEquals(1, database.routeLatency().all().size)
    }

    @Test
    fun everyStoredRouteComesBackWithItsFigureProvenanceAndDate() = runBlocking {
        val wired = AudioRoute("WIRED_HEADSET")
        val speaker = AudioRoute("BUILTIN_SPEAKER")
        store.recordLatency(wired, InputLatency(41.0, InputLatency.Provenance.Measured), atEpochMillis = 7L)
        store.recordLatency(speaker, InputLatency(180.0, InputLatency.Provenance.Assumed), atEpochMillis = 8L)

        val read = store.latencies().valueOrNull().orEmpty()

        assertEquals(
            setOf(
                RouteLatency(wired, InputLatency(41.0, InputLatency.Provenance.Measured), 7L),
                RouteLatency(speaker, InputLatency(180.0, InputLatency.Provenance.Assumed), 8L),
            ),
            read.toSet(),
        )
    }

    @Test
    fun noRoutesMeasuredYetIsAReadableEmptyListNotARefusal() = runBlocking {
        assertEquals(StoredReading.Readable(emptyList<RouteLatency>()), store.latencies())
    }

    /**
     * The blocker fix: this is the query the Settings screen runs, and a `@TypeConverter` fails
     * the whole cursor. See `.claude/CODE-NOTES.md`.
     */
    @Test
    fun anEnumNameThisBuildDoesNotKnowRefusesTheRouteLatenciesInsteadOfCrashingSettings() = runBlocking {
        val diag = RecordingDiag()
        store = RoomSettingsStore(database, diag)
        store.recordLatency(
            AudioRoute("WIRED_HEADSET"),
            InputLatency(41.0, InputLatency.Provenance.Measured),
            atEpochMillis = 7L,
        )
        corruptStoredProvenance()

        val reading = store.latencies()

        assertTrue("expected a refusal, got $reading", reading is StoredReading.Unreadable)
        assertNull(reading.valueOrNull())
        assertTrue(
            "the refusal did not name the value: ${diag.lines}",
            diag.lines.any { it.contains("could not be read at all") && it.contains("Guessed") },
        )
    }

    @Test
    fun theSameCorruptRowRefusesTheSingleRouteReadRatherThanReadingAsUnmeasured() = runBlocking {
        val wired = AudioRoute("WIRED_HEADSET")
        store.recordLatency(wired, InputLatency(41.0, InputLatency.Provenance.Measured), atEpochMillis = 7L)
        corruptStoredProvenance()

        assertTrue(store.latency(wired) is StoredReading.Unreadable)
    }

    /** Names why the accessor has to exist: the DAO the app used to call still takes it down. */
    @Test
    fun theRawDaoStillThrowsWhichIsWhyTheStoreAccessorExists() = runBlocking {
        store.recordLatency(
            AudioRoute("WIRED_HEADSET"),
            InputLatency(41.0, InputLatency.Provenance.Measured),
            atEpochMillis = 7L,
        )
        corruptStoredProvenance()

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { database.routeLatency().all() }
        }
        Unit
    }

    private fun corruptStoredProvenance() {
        database.openHelper.writableDatabase
            .execSQL("UPDATE route_latency SET provenance = 'Guessed' WHERE route = 'WIRED_HEADSET'")
    }
}
