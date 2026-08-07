package com.dewijones92.primavista.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
public interface SkillStateDao {
    @Query("SELECT * FROM skill_states")
    public suspend fun all(): List<SkillStateEntity>

    @Query("SELECT * FROM skill_states WHERE skillKey = :skillKey")
    public suspend fun byKey(skillKey: String): SkillStateEntity?

    @Upsert
    public suspend fun upsertAll(rows: List<SkillStateEntity>)

    @Query("SELECT COUNT(*) FROM skill_states")
    public suspend fun count(): Int
}
