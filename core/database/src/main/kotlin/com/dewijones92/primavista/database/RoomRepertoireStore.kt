package com.dewijones92.primavista.database

import com.dewijones92.primavista.common.Diag
import com.dewijones92.primavista.common.NoOpDiag
import com.dewijones92.primavista.score.ScoreId
import com.dewijones92.primavista.score.ScoreSummary

/** Room's [RepertoireStore]. */
public class RoomRepertoireStore(
    database: PrimaVistaDatabase,
    private val diag: Diag = NoOpDiag,
) : RepertoireStore {
    private val repertoire: RepertoireDao = database.repertoire()
    private val unreadable = UnreadableRowLog(diag, TAG, "skillKeysDropped")

    override suspend fun upsert(entry: RepertoireEntry) {
        repertoire.upsert(entry.toEntity())
        diag.event(
            TAG,
            "score=${entry.summary.id.value} known title=${entry.summary.title} " +
                "poly=${entry.summary.polyphony} bars=${entry.summary.bars} " +
                "skills=${entry.summary.skills.size} licence=${entry.licence} source=${entry.source}",
        )
    }

    override suspend fun all(): StoredReading<List<RepertoireEntry>> = diag.readOrRefuse(TAG, "the repertoire") {
        repertoire.all().map { row -> row.toEntry().also { warnIfSkillsLost(row) } }
    }

    override suspend fun summaries(): StoredReading<List<ScoreSummary>> =
        all().map { entries -> entries.map { it.summary } }

    override suspend fun forget(id: ScoreId) {
        repertoire.delete(id.value)
        diag.event(TAG, "score=${id.value} forgotten")
    }

    private fun warnIfSkillsLost(row: RepertoireEntity) {
        SkillTagKeys.readSet(row.skillKeys).filterIsInstance<SkillKeyReading.Unreadable>().forEach {
            unreadable.report(
                "${row.scoreId}/${it.key}",
                "score=${row.scoreId} dropped skill key '${it.key}': ${it.reason}",
            )
        }
    }

    private companion object {
        const val TAG = "db.repertoire"
    }
}
