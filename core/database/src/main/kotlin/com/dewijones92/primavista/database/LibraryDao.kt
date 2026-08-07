package com.dewijones92.primavista.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
public interface RepertoireDao {
    @Upsert
    public suspend fun upsert(entry: RepertoireEntity)

    @Query("SELECT * FROM repertoire ORDER BY title")
    public suspend fun all(): List<RepertoireEntity>

    @Query("SELECT * FROM repertoire WHERE scoreId = :scoreId")
    public suspend fun byId(scoreId: String): RepertoireEntity?

    @Query("DELETE FROM repertoire WHERE scoreId = :scoreId")
    public suspend fun delete(scoreId: String)
}

@Dao
public interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = :id")
    public suspend fun get(id: Int): SettingsEntity?

    @Query("SELECT * FROM settings WHERE id = :id")
    public fun observe(id: Int): Flow<SettingsEntity?>

    @Upsert
    public suspend fun upsert(settings: SettingsEntity)

    @Query("SELECT COUNT(*) FROM settings")
    public suspend fun count(): Int
}

@Dao
public interface AudioRouteLatencyDao {
    @Upsert
    public suspend fun upsert(latency: AudioRouteLatencyEntity)

    @Query("SELECT * FROM route_latency WHERE route = :route")
    public suspend fun byRoute(route: String): AudioRouteLatencyEntity?

    @Query("SELECT * FROM route_latency")
    public suspend fun all(): List<AudioRouteLatencyEntity>
}
