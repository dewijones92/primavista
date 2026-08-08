package com.dewijones92.primavista.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.dewijones92.primavista.practice.InputLatency
import com.dewijones92.primavista.score.Polyphony

/**
 * One practice attempt. [originKind]/[originSeed]/[originSpec] are what make a generated
 * session replayable; see `.claude/CODE-NOTES.md`.
 */
@Entity(tableName = "sessions")
public data class SessionEntity(
    @PrimaryKey val id: String,
    val scoreId: String,
    val scoreTitle: String,
    val originKind: String,
    val originSourceName: String?,
    val originLicence: String?,
    val originSeed: Long?,
    val originSpec: String?,
    val inputLabel: String,
    val polyphony: Polyphony,
    val tempoBpm: Int,
    val latencyMillis: Double,
    val latencyProvenance: InputLatency.Provenance,
    val startedAtEpochMillis: Long,
    val finishedAtEpochMillis: Long?,
    val notesExpected: Int,
    val correct: Int,
)

/**
 * One judged note. Per-note rather than a summary — see `.claude/CODE-NOTES.md`.
 *
 * [noteIndex] is null exactly when the judgement answers to no notated note, which is the
 * column-level form of [com.dewijones92.primavista.practice.NoteJudgement] being sealed.
 */
@Entity(
    tableName = "note_verdicts",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
public data class NoteVerdictEntity(
    val sessionId: String,
    val noteIndex: Int?,
    val kind: String,
    val expectedMidi: Int?,
    val heardMidi: Int?,
    val dtMillis: Double?,
    val atTicks: Long?,
    val confidence: Float,
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
)

/** [repetition] is the SM-2 rung and is stored because it cannot be re-derived — see CODE-NOTES. */
@Entity(tableName = "skill_states")
public data class SkillStateEntity(
    @PrimaryKey val skillKey: String,
    val strength: Double,
    val dueAtEpochMillis: Long,
    val attempts: Int,
    val lapses: Int,
    @ColumnInfo(defaultValue = "0") val repetition: Int = 0,
)

@Entity(tableName = "repertoire")
public data class RepertoireEntity(
    @PrimaryKey val scoreId: String,
    val title: String,
    val composer: String?,
    val licence: String,
    val source: String,
    val polyphony: Polyphony,
    val skillKeys: String,
    val bars: Int,
    val defaultTempoBpm: Int,
    val addedAtEpochMillis: Long,
)

@Entity(tableName = "settings")
public data class SettingsEntity(
    val tempoBpm: Int,
    val metronomeOn: Boolean,
    val listenFirstOn: Boolean,
    val inputLabel: String?,
    @PrimaryKey val id: Int = SINGLETON_ID,
) {
    public companion object {
        public const val SINGLETON_ID: Int = 1
    }
}

/** Latency is per audio route because a headset and a speaker are not the same path. */
@Entity(tableName = "route_latency")
public data class AudioRouteLatencyEntity(
    @PrimaryKey val route: String,
    val millis: Double,
    val provenance: InputLatency.Provenance,
    val measuredAtEpochMillis: Long,
)
