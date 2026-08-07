package com.dewijones92.primavista.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert

@Dao
public interface SessionDao {
    /** Upsert, because a session is written at pause and again at finish (docs/spec.md I4). */
    @Upsert
    public suspend fun upsert(session: SessionEntity)

    @Query("SELECT * FROM sessions ORDER BY startedAtEpochMillis DESC LIMIT :limit")
    public suspend fun recent(limit: Int): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE finishedAtEpochMillis IS NULL ORDER BY startedAtEpochMillis DESC")
    public suspend fun unfinished(): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE id = :id")
    public suspend fun byId(id: String): SessionEntity?

    @Query("DELETE FROM sessions WHERE id = :id")
    public suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM sessions")
    public suspend fun count(): Int
}

@Dao
public interface NoteVerdictDao {
    @Insert
    public suspend fun insertAll(rows: List<NoteVerdictEntity>)

    @Query("SELECT * FROM note_verdicts WHERE sessionId = :sessionId ORDER BY id")
    public suspend fun forSession(sessionId: String): List<NoteVerdictEntity>

    @Query("DELETE FROM note_verdicts WHERE sessionId = :sessionId")
    public suspend fun deleteForSession(sessionId: String)

    @Query("SELECT COUNT(*) FROM note_verdicts")
    public suspend fun count(): Int
}
