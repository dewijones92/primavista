package com.dewijones92.primavista.database

import com.dewijones92.primavista.practice.InputLatency
import com.dewijones92.primavista.practice.NoteJudgement
import com.dewijones92.primavista.score.Polyphony
import com.dewijones92.primavista.score.ScoreId
import com.dewijones92.primavista.score.ScoreOrigin
import com.dewijones92.primavista.score.ScoreSummary
import kotlinx.coroutines.flow.Flow

@JvmInline
public value class SessionId(public val value: String)

/** An audio route as the platform names it — a speaker and a headset have different latency. */
@JvmInline
public value class AudioRoute(public val id: String)

/**
 * A practice attempt as it is stored and read back. One type in both directions, so a session
 * written at pause and re-read after a reboot cannot disagree with itself (docs/spec.md I4).
 *
 * [origin] is null only when this build could not rebuild what was stored; [originDescriptor]
 * always says what the row actually held. See `.claude/CODE-NOTES.md`.
 */
public data class StoredSession(
    val id: SessionId,
    val scoreId: ScoreId,
    val scoreTitle: String,
    val origin: ScoreOrigin?,
    val inputLabel: String,
    val polyphony: Polyphony,
    val tempoBpm: Int,
    val latency: InputLatency,
    val startedAtEpochMillis: Long,
    val finishedAtEpochMillis: Long?,
    val notesExpected: Int,
    val correct: Int,
    val originDescriptor: String = "",
) {
    public val isFinished: Boolean get() = finishedAtEpochMillis != null

    public val accuracy: Double
        get() = if (notesExpected == 0) 0.0 else correct.toDouble() / notesExpected
}

/** A known score. [ScoreSummary] is reused rather than re-declared: the scheduler reads it directly. */
public data class RepertoireEntry(
    val summary: ScoreSummary,
    val licence: String,
    val source: String,
    val addedAtEpochMillis: Long,
)

public data class PracticeSettings(
    val tempoBpm: Int = DEFAULT_TEMPO_BPM,
    val metronomeOn: Boolean = true,
    val listenFirstOn: Boolean = false,
    /** [com.dewijones92.primavista.practice.AnswerSource.label] of the chosen input; null until chosen. */
    val inputLabel: String? = null,
) {
    public companion object {
        public const val DEFAULT_TEMPO_BPM: Int = 72
    }
}

/**
 * Writes a session and its per-note judgements atomically, and reads them back.
 *
 * [NoteJudgement] is stored as the judge produced it rather than re-modelled here: a second
 * shape would need its own answer to "which note was that extra note?", and the judge's sealed
 * one already refuses to invent a sentinel index. See `.claude/CODE-NOTES.md`.
 *
 * [save] is called at **pause as well as at finish**: docs/spec.md I4 says a phone reboot must
 * not lose what was practised, and a session only written at the end is lost by definition when
 * the process dies mid-piece. Calling it twice for the same [StoredSession.id] replaces the
 * earlier write rather than duplicating it.
 */
public interface SessionStore {
    public suspend fun save(session: StoredSession, judgements: List<NoteJudgement>)

    public suspend fun recent(limit: Int = DEFAULT_RECENT_LIMIT): List<StoredSession>

    /** Sessions saved at pause and never finished — what to offer resuming after a reboot. */
    public suspend fun unfinished(): List<StoredSession>

    public suspend fun judgements(id: SessionId): List<NoteJudgement>

    public suspend fun delete(id: SessionId)

    public companion object {
        public const val DEFAULT_RECENT_LIMIT: Int = 20
    }
}

public interface RepertoireStore {
    public suspend fun upsert(entry: RepertoireEntry)

    public suspend fun all(): List<RepertoireEntry>

    /** Exactly what `PracticeScheduler.next` wants for its `available` argument. */
    public suspend fun summaries(): List<ScoreSummary>

    public suspend fun forget(id: ScoreId)
}

public interface SettingsStore {
    public suspend fun settings(): PracticeSettings

    public fun observe(): Flow<PracticeSettings>

    public suspend fun save(settings: PracticeSettings)

    /** Null when this route has never been measured — an unmeasured latency must not read as 0ms. */
    public suspend fun latency(route: AudioRoute): InputLatency?

    public suspend fun recordLatency(route: AudioRoute, latency: InputLatency, atEpochMillis: Long)
}
