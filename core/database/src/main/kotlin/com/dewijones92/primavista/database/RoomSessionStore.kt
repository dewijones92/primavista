package com.dewijones92.primavista.database

import androidx.room.withTransaction
import com.dewijones92.primavista.common.Diag
import com.dewijones92.primavista.common.NoOpDiag
import com.dewijones92.primavista.practice.NoteJudgement

/**
 * Room's [SessionStore]. The write is one transaction: a session whose verdicts were half
 * written would claim a score it cannot show the working for.
 */
public class RoomSessionStore(
    private val database: PrimaVistaDatabase,
    private val diag: Diag = NoOpDiag,
) : SessionStore {
    private val sessions: SessionDao = database.sessions()
    private val noteVerdicts: NoteVerdictDao = database.noteVerdicts()
    private val lostOrigins = UnreadableRowLog(diag, TAG, "sessionOriginsUnreadable")
    private val lostVerdicts = UnreadableRowLog(diag, TAG, "verdictRowsUnreadable")

    override suspend fun save(session: StoredSession, judgements: List<NoteJudgement>) {
        database.withTransaction {
            sessions.upsert(session.toEntity())
            noteVerdicts.deleteForSession(session.id.value)
            noteVerdicts.insertAll(judgements.map { it.toEntity(session.id) })
        }
        diag.event(TAG, describeSaved(session, judgements))
        diag.counted(TAG, "verdictRowsWritten", judgements.size)
    }

    override suspend fun recent(limit: Int): StoredReading<List<StoredSession>> =
        diag.readOrRefuse(TAG, "the $limit most recent sessions") {
            sessions.recent(limit).map { it.toStored() }.onEach { warnIfOriginLost(it) }
        }

    override suspend fun unfinished(): StoredReading<List<StoredSession>> =
        diag.readOrRefuse(TAG, "the unfinished sessions") {
            sessions.unfinished().map { it.toStored() }.onEach { warnIfOriginLost(it) }
        }

    override suspend fun judgements(id: SessionId): StoredReading<List<NoteJudgement>> = diag.readOrRefuse(
        TAG,
        "the verdicts of session=${id.value}",
    ) {
        val readings = noteVerdicts.forSession(id.value).map { it.read() }
        readings.filterIsInstance<VerdictRowReading.Unreadable>().forEach {
            lostVerdicts.report(
                "${id.value}/${it.rowId}",
                "session=${id.value} row=${it.rowId} unreadable and skipped: ${it.reason}",
            )
        }
        readings.filterIsInstance<VerdictRowReading.Readable>().map { it.judgement }
    }

    override suspend fun delete(id: SessionId) {
        sessions.delete(id.value)
        diag.event(TAG, "session=${id.value} deleted, verdicts cascade")
    }

    private fun warnIfOriginLost(session: StoredSession) {
        if (session.origin == null) {
            lostOrigins.report(
                session.id.value,
                "session=${session.id.value} origin unreadable: ${session.originDescriptor}",
            )
        }
    }

    private fun describeSaved(session: StoredSession, judgements: List<NoteJudgement>): String = with(session) {
        "session=${id.value} saved score=$scoreTitle id=${scoreId.value} origin=$originDescriptor " +
            "src=$inputLabel poly=$polyphony tempo=${tempoBpm}bpm " +
            "lat=${latency.millis}ms/${latency.provenance} " +
            "notes=$notesExpected correct=$correct verdicts=${judgements.size} " +
            "extras=${judgements.count { it is NoteJudgement.Unexpected }} " +
            "started=$startedAtEpochMillis finished=${finishedAtEpochMillis ?: "(paused)"}"
    }

    private companion object {
        const val TAG = "db.session"
    }
}
