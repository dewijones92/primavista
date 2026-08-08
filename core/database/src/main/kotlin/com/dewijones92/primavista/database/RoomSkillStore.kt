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
    private val unreadable = UnreadableRowLog(diag, TAG, "skillRowsUnreadable")

    /**
     * `SkillStore` is a `:core:practice` port returning a plain list, so a refusal degrades to no
     * known skills here — logged, and visible to a caller that wants it through [storedStates].
     */
    override suspend fun states(): List<SkillState> = when (val reading = storedStates()) {
        is StoredReading.Readable -> reading.value
        is StoredReading.Unreadable -> {
            diag.event(TAG, "scheduling on no skill history at all: every skill will look brand new")
            emptyList()
        }
    }

    /** What [states] would not say: a refused read is not the same thing as a first-run device. */
    public suspend fun storedStates(): StoredReading<List<SkillState>> =
        diag.readOrRefuse(TAG, "the stored skill states") { readStatesOrThrow() }

    override suspend fun record(outcomes: List<SkillOutcome>, nowEpochMillis: Long) {
        if (outcomes.isEmpty()) {
            diag.event(TAG, "no skill states folded at now=$nowEpochMillis: the session produced no outcomes")
            return
        }
        val folded = diag.readOrRefuse(TAG, "the skill states to fold ${outcomes.size} outcomes into") {
            database.withTransaction {
                val before = readStatesOrThrow()
                val after = updateRule.update(before, outcomes, nowEpochMillis)
                skillStates.upsertAll(after.map { it.toEntity() })
                after
            }
        }
        when (folded) {
            is StoredReading.Readable -> diag.event(TAG, describeFold(outcomes, folded.value, nowEpochMillis))
            is StoredReading.Unreadable -> diag.event(
                TAG,
                "nothing folded at now=$nowEpochMillis and nothing written: folding onto states this " +
                    "build cannot read would overwrite them with beginners' figures",
            )
        }
    }

    private suspend fun readStatesOrThrow(): List<SkillState> {
        val readings = skillStates.all().map { it.read() }
        readings.filterIsInstance<SkillRowReading.Unreadable>().forEach {
            unreadable.report(it.key, "skill row kept on disk but unreadable, key='${it.key}': ${it.reason}")
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
