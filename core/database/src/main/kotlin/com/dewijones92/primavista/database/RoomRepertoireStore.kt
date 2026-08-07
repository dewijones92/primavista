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

    override suspend fun upsert(entry: RepertoireEntry) {
        repertoire.upsert(entry.toEntity())
        diag.event(
            TAG,
            "score=${entry.summary.id.value} known title=${entry.summary.title} " +
                "poly=${entry.summary.polyphony} bars=${entry.summary.bars} " +
                "skills=${entry.summary.skills.size} licence=${entry.licence} source=${entry.source}",
        )
    }

    override suspend fun all(): List<RepertoireEntry> = repertoire.all().map { row ->
        row.toEntry().also { warnIfSkillsLost(row) }
    }

    override suspend fun summaries(): List<ScoreSummary> = all().map { it.summary }

    override suspend fun forget(id: ScoreId) {
        repertoire.delete(id.value)
        diag.event(TAG, "score=${id.value} forgotten")
    }

    private fun warnIfSkillsLost(row: RepertoireEntity) {
        SkillTagKeys.readSet(row.skillKeys).filterIsInstance<SkillKeyReading.Unreadable>().forEach {
            diag.event(TAG, "score=${row.scoreId} dropped skill key '${it.key}': ${it.reason}")
        }
    }

    private companion object {
        const val TAG = "db.repertoire"
    }
}
