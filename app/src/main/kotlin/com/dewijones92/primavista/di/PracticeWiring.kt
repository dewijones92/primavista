package com.dewijones92.primavista.di

import com.dewijones92.primavista.audio.Metronome
import com.dewijones92.primavista.audio.TonePlayer
import com.dewijones92.primavista.common.Diag
import com.dewijones92.primavista.database.StoredSession
import com.dewijones92.primavista.notation.GlyphMetrics
import com.dewijones92.primavista.notation.StaffLayout
import com.dewijones92.primavista.practice.AnswerSource
import com.dewijones92.primavista.practice.Conductor
import com.dewijones92.primavista.practice.NoteJudgement
import com.dewijones92.primavista.practice.PerformanceJudge
import com.dewijones92.primavista.practice.SkillOutcome
import com.dewijones92.primavista.score.CorpusPiece
import com.dewijones92.primavista.score.Polyphony
import com.dewijones92.primavista.score.Score
import com.dewijones92.primavista.score.SkillTag

/**
 * Something to read, and the reason it was chosen.
 *
 * [summary] is the sentence the screen shows. It exists because a trainer that silently decides what
 * you practise is indistinguishable from one that always hands you the same thing — the choice has
 * to be visible to be trusted (docs/spec.md I5).
 */
public data class PracticeSelection(
    val score: Score,
    val targeting: Set<SkillTag>,
    val summary: String,
)

/**
 * Everything one practice session needs from the app's object graph, behind one port.
 *
 * A port rather than the container itself so the view model can be driven in a test without an
 * Android context, a database or an audio device; and one port rather than fourteen constructor
 * parameters because the session needs all of it or none of it.
 */
public interface PracticeWiring {
    public val diag: Diag

    public val layout: StaffLayout

    public val metrics: GlyphMetrics

    public val metronome: Metronome

    public val tonePlayer: TonePlayer

    /**
     * What Dewi asked for, from the one store that holds it. A session applies these when it loads
     * rather than observing them, because they decide how a run *starts*.
     */
    public val preferences: SessionPreferences

    public fun nowEpochMillis(): Long

    /** Asked before opening on the stored PLAY IT: a permission granted once can be taken back. */
    public fun microphoneGranted(): Boolean

    public fun conductorFor(score: Score, tempoCeilingBpm: Int): Conductor

    public fun judgeFor(score: Score): PerformanceJudge

    public fun sourceFor(mode: InputMode): AnswerSource

    /** The scheduler's own answer to "what now", weighted by what is weak and due. */
    public suspend fun chooseNext(input: Polyphony, seed: Long): PracticeSelection

    /** Material synthesised to drill one skill. The corpus is not consulted: this is the ladder. */
    public suspend fun chooseDrill(target: SkillTag, input: Polyphony, seed: Long): PracticeSelection

    /** Null when the piece does not parse; the reason is logged rather than shown as an empty staff. */
    public suspend fun open(piece: CorpusPiece): PracticeSelection?

    public suspend fun save(session: StoredSession, judgements: List<NoteJudgement>)

    public suspend fun recordSkills(outcomes: List<SkillOutcome>)
}
