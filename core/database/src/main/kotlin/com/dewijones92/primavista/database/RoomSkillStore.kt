package com.dewijones92.primavista.database

import androidx.room.withTransaction
import com.dewijones92.primavista.common.Diag
import com.dewijones92.primavista.common.NoOpDiag
import com.dewijones92.primavista.practice.PracticeScheduler
import com.dewijones92.primavista.practice.SkillOutcome
import com.dewijones92.primavista.practice.SkillState
import com.dewijones92.primavista.practice.SkillStore

/**
 * The spaced-repetition fold, supplied rather than reimplemented. `PracticeScheduler.update` is
 * the one copy of the rule; this store only persists what it is handed.
 */
public fun interface SkillUpdateRule {
    public fun update(
        states: List<SkillState>,
        outcomes: List<SkillOutcome>,
        nowEpochMillis: Long,
    ): List<SkillState>
}

/** Room's [SkillStore]. */
public class RoomSkillStore(
    private val database: PrimaVistaDatabase,
    private val updateRule: SkillUpdateRule,
    private val diag: Diag = NoOpDiag,
) : SkillStore {
    public constructor(
        database: PrimaVistaDatabase,
        scheduler: PracticeScheduler,
        diag: Diag = NoOpDiag,
    ) : this(database, SkillUpdateRule(scheduler::update), diag)

    private val skillStates: SkillStateDao = database.skillStates()

    override suspend fun states(): List<SkillState> = readStates()

    override suspend fun record(outcomes: List<SkillOutcome>, nowEpochMillis: Long) {
        if (outcomes.isEmpty()) {
            diag.event(TAG, "no skill states folded at now=$nowEpochMillis: the session produced no outcomes")
            return
        }
        val folded = database.withTransaction {
            val before = readStates()
            val after = updateRule.update(before, outcomes, nowEpochMillis)
            skillStates.upsertAll(after.map { it.toEntity() })
            after
        }
        diag.event(TAG, describeFold(outcomes, folded, nowEpochMillis))
    }

    private suspend fun readStates(): List<SkillState> {
        val readings = skillStates.all().map { it.read() }
        readings.filterIsInstance<SkillRowReading.Unreadable>().forEach {
            diag.event(TAG, "skill row kept on disk but unreadable, key='${it.key}': ${it.reason}")
        }
        return readings.filterIsInstance<SkillRowReading.Readable>().map { it.state }
    }

    private fun describeFold(
        outcomes: List<SkillOutcome>,
        folded: List<SkillState>,
        nowEpochMillis: Long,
    ): String {
        val worst = folded.minByOrNull { it.strength }
        return "folded outcomes=${outcomes.size} states=${folded.size} now=$nowEpochMillis " +
            "attempted=${outcomes.sumOf { it.attempts }} clean=${outcomes.sumOf { it.cleanAttempts }} " +
            "weakest=${worst?.let { "${SkillTagKeys.encode(it.tag)}@${it.strength}" } ?: "(none)"}"
    }

    private companion object {
        const val TAG = "db.skills"
    }
}
