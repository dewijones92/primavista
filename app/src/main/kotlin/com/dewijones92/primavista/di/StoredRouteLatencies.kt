package com.dewijones92.primavista.di

import com.dewijones92.primavista.common.Diag
import com.dewijones92.primavista.database.SettingsStore
import com.dewijones92.primavista.database.StoredReading
import com.dewijones92.primavista.practice.AppliedLatency
import com.dewijones92.primavista.practice.AudioRoute
import com.dewijones92.primavista.practice.InputLatency
import com.dewijones92.primavista.practice.LatencyPolicy
import com.dewijones92.primavista.practice.RouteLatencies

/**
 * The wire between the microphone and the stored measurements. See .claude/CODE-NOTES.md.
 */
public class StoredRouteLatencies(
    private val store: SettingsStore?,
    private val diag: Diag,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) : RouteLatencies {

    override suspend fun applied(route: AudioRoute): AppliedLatency {
        val store = store ?: return unstored(route, "the practice database could not be opened")
        return when (val reading = store.latency(route)) {
            is StoredReading.Unreadable -> unstored(route, "the stored figure could not be read: ${reading.reason}")

            is StoredReading.Readable ->
                LatencyPolicy.decide(route, reading.value, nowEpochMillis()).also {
                    diag.event(TAG, "applying $it")
                }
        }
    }

    override suspend fun record(route: AudioRoute, latency: InputLatency) {
        val store = store ?: run {
            diag.event(TAG, "measured ${latency.millis}ms on ${route.id} and could not store it: no database")
            return
        }
        store.recordLatency(route, latency, nowEpochMillis())
    }

    /**
     * A route whose figure could not be read is treated as one that was never measured, which is
     * the safe direction: it assumes, says so, and asks to be calibrated.
     */
    private fun unstored(route: AudioRoute, why: String): AppliedLatency {
        val decided = LatencyPolicy.decide(route, stored = null, nowEpochMillis = nowEpochMillis())
        val applied = decided.copy(why = "$why, so ${decided.why}")
        diag.event(TAG, "applying $applied")
        return applied
    }

    private companion object {
        const val TAG = "audio.latency"
    }
}
