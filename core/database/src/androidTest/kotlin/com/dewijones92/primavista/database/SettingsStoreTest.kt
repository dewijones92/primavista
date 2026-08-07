package com.dewijones92.primavista.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dewijones92.primavista.practice.InputLatency
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertNull(store.latency(AudioRoute("BLUETOOTH_A2DP")))
    }

    @Test
    fun latencyIsStoredPerRouteWithItsProvenance() = runBlocking {
        val wired = AudioRoute("WIRED_HEADSET")
        val speaker = AudioRoute("BUILTIN_SPEAKER")
        store.recordLatency(wired, InputLatency(41.0, InputLatency.Provenance.Measured), atEpochMillis = 7L)
        store.recordLatency(speaker, InputLatency(180.0, InputLatency.Provenance.Assumed), atEpochMillis = 8L)

        assertEquals(InputLatency(41.0, InputLatency.Provenance.Measured), store.latency(wired))
        assertEquals(InputLatency(180.0, InputLatency.Provenance.Assumed), store.latency(speaker))
    }

    @Test
    fun remeasuringARouteReplacesItsFigure() = runBlocking {
        val wired = AudioRoute("WIRED_HEADSET")
        store.recordLatency(wired, InputLatency(180.0, InputLatency.Provenance.Assumed), atEpochMillis = 1L)
        store.recordLatency(wired, InputLatency(39.5, InputLatency.Provenance.Measured), atEpochMillis = 2L)

        assertEquals(InputLatency(39.5, InputLatency.Provenance.Measured), store.latency(wired))
        assertEquals(1, database.routeLatency().all().size)
    }
}
