package com.dewijones92.primavista.di

import com.dewijones92.primavista.common.NoOpDiag
import com.dewijones92.primavista.database.PracticeSettings
import com.dewijones92.primavista.database.SettingsStore
import com.dewijones92.primavista.database.StoredReading
import com.dewijones92.primavista.practice.AudioRoute
import com.dewijones92.primavista.practice.InputLatency
import com.dewijones92.primavista.practice.RouteKind
import com.dewijones92.primavista.practice.RouteLatency
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val NOW = 1_700_000_000_000L
private const val MEASURED_MILLIS = 41.5

/**
 * The wire between the microphone and the stored measurements — the piece that was missing while
 * every other part of calibration worked (docs/todos/measure-audio-latency.md).
 */
class StoredRouteLatenciesTest {

    private val builtIn = AudioRoute(RouteKind.BuiltIn, "Pixel built-in mic")
    private val headset = AudioRoute(RouteKind.Bluetooth, "WH-1000XM4")

    @Test
    fun `a stored measurement for this route is what gets applied`() = runBlocking {
        val measured = InputLatency(MEASURED_MILLIS, InputLatency.Provenance.Measured)
        val store = FakeSettingsStore(mapOf(builtIn to RouteLatency(builtIn, measured, NOW)))

        val applied = latencies(store).applied(builtIn)

        assertEquals(MEASURED_MILLIS, applied.latency.millis, 0.0)
        assertTrue(applied.isMeasured)
    }

    @Test
    fun `a route nobody measured is assumed and says so`() = runBlocking {
        val applied = latencies(FakeSettingsStore(emptyMap())).applied(headset)

        assertEquals(InputLatency.Provenance.Assumed, applied.latency.provenance)
        assertEquals(RouteKind.Bluetooth.assumedLatencyMillis, applied.latency.millis, 0.0)
    }

    /**
     * A refusal must not read as "measured 0ms". It is treated as never measured, which assumes,
     * says why, and asks to be calibrated — the safe direction.
     */
    @Test
    fun `a store that cannot be read is treated as unmeasured rather than as zero`() = runBlocking {
        val broken = FakeSettingsStore(emptyMap(), refuseWith = "the row is corrupt")

        val applied = latencies(broken).applied(builtIn)

        assertEquals(InputLatency.Provenance.Assumed, applied.latency.provenance)
        assertTrue(applied.latency.millis > 0.0)
        assertTrue(applied.why, applied.why.contains("the row is corrupt"))
    }

    /** No database at all is the same situation, and must not crash a session that only taps. */
    @Test
    fun `no database means an assumption with a reason rather than a crash`() = runBlocking {
        val applied = StoredRouteLatencies(store = null, diag = NoOpDiag, nowEpochMillis = { NOW }).applied(builtIn)

        assertEquals(InputLatency.Provenance.Assumed, applied.latency.provenance)
        assertTrue(applied.why, applied.why.contains("database"))
    }

    @Test
    fun `a measurement is written against its own route and read back`() = runBlocking {
        val store = FakeSettingsStore(emptyMap())
        val wire = latencies(store)

        wire.record(headset, InputLatency(MEASURED_MILLIS, InputLatency.Provenance.Measured))

        assertEquals(MEASURED_MILLIS, wire.applied(headset).latency.millis, 0.0)
        assertTrue("the built-in mic was never measured", !wire.applied(builtIn).isMeasured)
    }

    @Test
    fun `recording without a database says so instead of failing silently`() = runBlocking {
        StoredRouteLatencies(store = null, diag = NoOpDiag, nowEpochMillis = { NOW })
            .record(builtIn, InputLatency(MEASURED_MILLIS, InputLatency.Provenance.Measured))
    }

    private fun latencies(store: SettingsStore) =
        StoredRouteLatencies(store, NoOpDiag, nowEpochMillis = { NOW })

    private class FakeSettingsStore(
        stored: Map<AudioRoute, RouteLatency>,
        private val refuseWith: String? = null,
    ) : SettingsStore {
        private val rows = stored.toMutableMap()

        override fun observe(): Flow<PracticeSettings> = flowOf(PracticeSettings())

        override suspend fun settings(): PracticeSettings = PracticeSettings()

        override suspend fun save(settings: PracticeSettings): Unit = Unit

        override suspend fun latency(route: AudioRoute): StoredReading<RouteLatency?> =
            refuse("the latency") ?: StoredReading.Readable(rows[route])

        override suspend fun latencies(): StoredReading<List<RouteLatency>> =
            refuse("the latencies") ?: StoredReading.Readable(rows.values.toList())

        private fun <T> refuse(what: String): StoredReading<T>? =
            refuseWith?.let { StoredReading.Unreadable(what, it) }

        override suspend fun recordLatency(route: AudioRoute, latency: InputLatency, atEpochMillis: Long) {
            rows[route] = RouteLatency(route, latency, atEpochMillis)
        }
    }
}
