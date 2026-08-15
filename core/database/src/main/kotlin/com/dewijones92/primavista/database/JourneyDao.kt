package com.dewijones92.primavista.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert

@Dao
public interface JourneyDao {
    @Query("SELECT * FROM stage_progress ORDER BY stageNumber")
    public suspend fun stages(): List<StageProgressEntity>

    @Query("SELECT * FROM stage_progress WHERE stageNumber = :stageNumber")
    public suspend fun stage(stageNumber: Int): StageProgressEntity?

    @Upsert
    public suspend fun upsert(row: StageProgressEntity)

    /** Insert, never upsert: a second placement read is a second event, not a correction. */
    @Insert
    public suspend fun insert(row: PlacementReadEntity)

    @Query("SELECT * FROM placement_reads ORDER BY takenAtEpochMillis DESC, id DESC LIMIT 1")
    public suspend fun latestPlacement(): PlacementReadEntity?

    @Query("SELECT * FROM placement_reads ORDER BY takenAtEpochMillis DESC, id DESC")
    public suspend fun placements(): List<PlacementReadEntity>

    @Query("SELECT COUNT(*) FROM stage_progress")
    public suspend fun stageCount(): Int
}
