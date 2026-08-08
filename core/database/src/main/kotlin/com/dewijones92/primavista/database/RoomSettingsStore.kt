package com.dewijones92.primavista.database

import com.dewijones92.primavista.common.Diag
import com.dewijones92.primavista.common.NoOpDiag
import com.dewijones92.primavista.practice.InputLatency
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Room's [SettingsStore]. One row, so a save replaces rather than accumulates. */
public class RoomSettingsStore(
    database: PrimaVistaDatabase,
    private val diag: Diag = NoOpDiag,
) : SettingsStore {
    private val settingsDao: SettingsDao = database.settings()
    private val latencyDao: AudioRouteLatencyDao = database.routeLatency()

    override suspend fun settings(): PracticeSettings =
        settingsDao.get(SettingsEntity.SINGLETON_ID)?.toSettings() ?: PracticeSettings()

    override fun observe(): Flow<PracticeSettings> =
        settingsDao.observe(SettingsEntity.SINGLETON_ID).map { it?.toSettings() ?: PracticeSettings() }

    override suspend fun save(settings: PracticeSettings) {
        settingsDao.upsert(settings.toEntity())
        diag.event(
            TAG,
            "settings saved tempo=${settings.tempoBpm}bpm metronome=${settings.metronomeOn} " +
                "listenFirst=${settings.listenFirstOn} src=${settings.inputLabel ?: "(unchosen)"}",
        )
    }

    override suspend fun latency(route: AudioRoute): StoredReading<InputLatency?> =
        diag.readOrRefuse(TAG, "the latency of route=${route.id}") {
            val stored = latencyDao.byRoute(route.id)
            if (stored == null) {
                diag.event(TAG, "no latency stored for route=${route.id}: verdicts on it carry an unmeasured bias")
            }
            stored?.toLatency()
        }

    override suspend fun latencies(): StoredReading<List<RouteLatency>> =
        diag.readOrRefuse(TAG, "the stored audio-route latencies") {
            latencyDao.all().map { it.toRouteLatency() }.also { diag.event(TAG, describeRoutes(it)) }
        }

    override suspend fun recordLatency(route: AudioRoute, latency: InputLatency, atEpochMillis: Long) {
        latencyDao.upsert(
            AudioRouteLatencyEntity(
                route = route.id,
                millis = latency.millis,
                provenance = latency.provenance,
                measuredAtEpochMillis = atEpochMillis,
            ),
        )
        diag.event(
            TAG,
            "latency stored route=${route.id} lat=${latency.millis}ms/${latency.provenance} at=$atEpochMillis",
        )
    }

    private fun describeRoutes(routes: List<RouteLatency>): String {
        val measured = routes.count { it.latency.provenance == InputLatency.Provenance.Measured }
        return "read routes=${routes.size} measured=$measured " +
            "figures=[${routes.joinToString { "${it.route.id}=${it.latency.millis}ms/${it.latency.provenance}" }}]"
    }

    private companion object {
        const val TAG = "db.settings"
    }
}
