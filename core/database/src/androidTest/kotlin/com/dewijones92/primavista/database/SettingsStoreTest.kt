package com.dewijones92.primavista.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dewijones92.primavista.practice.AudioRoute
import com.dewijones92.primavista.practice.InputLatency
import com.dewijones92.primavista.practice.RouteKind
import com.dewijones92.primavista.practice.RouteLatency
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
        assertEquals(StoredReading.Readable(null), store.latency(AudioRoute(RouteKind.Bluetooth, "a headset")))
    }

    @Test
    fun latencyIsStoredPerRouteWithItsProvenance() = runBlocking {
        val wired = AudioRoute(RouteKind.Wired, "a wired headset")
        val speaker = AudioRoute(RouteKind.BuiltIn, "the built-in mic")
        val measured = InputLatency(41.0, InputLatency.Provenance.Measured)
        val assumed = InputLatency(180.0, InputLatency.Provenance.Assumed)
        store.recordLatency(wired, measured, atEpochMillis = 7L)
        store.recordLatency(speaker, assumed, atEpochMillis = 8L)

        assertEquals(RouteLatency(wired, measured, 7L), store.latency(wired).valueOrNull())
        assertEquals(RouteLatency(speaker, assumed, 8L), store.latency(speaker).valueOrNull())
    }

    @Test
    fun remeasuringARouteReplacesItsFigure() = runBlocking {
        val wired = AudioRoute(RouteKind.Wired, "a wired headset")
        val remeasured = InputLatency(39.5, InputLatency.Provenance.Measured)
        store.recordLatency(wired, InputLatency(180.0, InputLatency.Provenance.Assumed), atEpochMillis = 1L)
        store.recordLatency(wired, remeasured, atEpochMillis = 2L)

        assertEquals(RouteLatency(wired, remeasured, 2L), store.latency(wired).valueOrNull())
        assertEquals(1, database.routeLatency().all().size)
    }

    @Test
    fun everyStoredRouteComesBackWithItsFigureProvenanceAndDate() = runBlocking {
        val wired = AudioRoute(RouteKind.Wired, "a wired headset")
        val speaker = AudioRoute(RouteKind.BuiltIn, "the built-in mic")
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
        val wired = AudioRoute(RouteKind.Wired, "a wired headset")
        store.recordLatency(wired, InputLatency(41.0, InputLatency.Provenance.Measured), atEpochMillis = 7L)
        corruptStoredProvenance(wired)

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
        val wired = AudioRoute(RouteKind.Wired, "a wired headset")
        store.recordLatency(wired, InputLatency(41.0, InputLatency.Provenance.Measured), atEpochMillis = 7L)
        corruptStoredProvenance(wired)

        assertTrue(store.latency(wired) is StoredReading.Unreadable)
    }

    /** Names why the accessor has to exist: the DAO the app used to call still takes it down. */
    @Test
    fun theRawDaoStillThrowsWhichIsWhyTheStoreAccessorExists() = runBlocking {
        val wired = AudioRoute(RouteKind.Wired, "a wired headset")
        store.recordLatency(wired, InputLatency(41.0, InputLatency.Provenance.Measured), atEpochMillis = 7L)
        corruptStoredProvenance(wired)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { database.routeLatency().all() }
        }
        Unit
    }

    /** Takes the route rather than naming its id, so the SQL cannot drift from the stored key. */
    private fun corruptStoredProvenance(route: AudioRoute) {
        database.openHelper.writableDatabase.execSQL(
            "UPDATE route_latency SET provenance = 'Guessed' WHERE route = ?",
            arrayOf(route.id),
        )
    }
}
