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
import com.dewijones92.primavista.practice.PracticeChoice
import com.dewijones92.primavista.practice.PracticeFocus
import com.dewijones92.primavista.practice.SkillOutcome
import com.dewijones92.primavista.practice.SpacedPracticeScheduler
import com.dewijones92.primavista.score.DifficultySpec
import com.dewijones92.primavista.score.ExerciseGenerator
import com.dewijones92.primavista.score.Polyphony
import com.dewijones92.primavista.score.Score
import com.dewijones92.primavista.score.ScoreSkills
import com.dewijones92.primavista.score.ScoreSummary
import com.dewijones92.primavista.score.SkillTag
import com.dewijones92.primavista.ui.progress.describe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "ladder"

/** [PracticeWiring] over the real [AppContainer]. */
public class AppPracticeWiring(private val container: AppContainer) : PracticeWiring, StageAware {

    override val diag: Diag get() = container.diag
    override val layout: StaffLayout get() = container.staffLayout
    override val metrics: GlyphMetrics get() = container.glyphMetrics
    override val metronome: Metronome get() = container.metronome
    override val tonePlayer: TonePlayer get() = container.tonePlayer
    override val preferences: SessionPreferences = StoredPreferences(container, container.diag)

    private val shipped get() = container.shippedRepertoire

    override fun nowEpochMillis(): Long = System.currentTimeMillis()

    override fun microphoneGranted(): Boolean = container.microphoneGranted()

    override fun conductorFor(score: Score, tempoCeilingBpm: Int): Conductor =
        container.conductorFor(score, tempoCeilingBpm)

    override fun judgeFor(score: Score): PerformanceJudge = container.judgeFor(score)

    override fun sourceFor(mode: InputMode): AnswerSource = container.sourceFor(mode)

    override suspend fun chooseNext(input: Polyphony, seed: Long): PracticeSelection =
        choose(input, seed, focus = null)

    override suspend fun chooseWithin(focus: PracticeFocus, input: Polyphony, seed: Long): PracticeSelection =
        choose(input, seed, focus)

    private suspend fun choose(input: Polyphony, seed: Long, focus: PracticeFocus?): PracticeSelection {
        val scores = shipped.passages()
        val summaries = scores.map { it.summarise(container.scoreSkills) }
        val states = withContext(Dispatchers.IO) { container.skillStore?.states().orEmpty() }
        val now = nowEpochMillis()
        val standing = container.curriculum.currentStage(states)
        val narrowing = focus ?: standing.focus
        val choice = container.scheduler.next(summaries, states, input, now, seed, narrowing)
        diag.event(
            TAG,
            "next -> ${describeChoice(choice)} [input=$input corpus=${summaries.size} " +
                "skills=${states.size} due=${states.count { it.isDue(now) }} " +
                "weakest=${states.minByOrNull { it.strength }?.strength} seed=$seed now=$now " +
                "stage=${standing.id.number}'${standing.title}' " +
                "focus=${if (focus == null) "the rung he stands on" else "asked for"}:${narrowing.skills.size}skills]",
        )
        return when (choice) {
            is PracticeChoice.Generated ->
                container.exerciseGenerator.generated(choice.seed, choice.spec, choice.targeting)
            is PracticeChoice.Piece -> scores.firstOrNull { it.id == choice.id }
                ?.let { PracticeSelection(it, choice.targeting, pieceSummary(choice.targeting)) }
                ?: run {
                    diag.event(TAG, "piece ${choice.id.value} was chosen but is not loaded; generating instead")
                    container.exerciseGenerator.generated(
                        seed,
                        monoSafe(SpacedPracticeScheduler.DefaultBase, input),
                        choice.targeting,
                    )
                }
        }
    }

    override suspend fun chooseDrill(target: SkillTag, input: Polyphony, seed: Long): PracticeSelection {
        val states = withContext(Dispatchers.IO) { container.skillStore?.states().orEmpty() }
        val base = container.curriculum.currentStage(states).spec
        val hearable = target.takeIf { it.isHearableBy(input) }
        if (hearable == null) {
            diag.event(TAG, "drill target $target dropped: a $input input cannot hear both hands (spec I3)")
        }
        val spec = monoSafe(hearable?.let { container.exerciseGenerator.specTargeting(it, base) } ?: base, input)
        return container.exerciseGenerator.generated(seed, spec, setOfNotNull(hearable))
    }

    /**
     * A whole song is not a unit of practice, so what opens is the most of it the rung he stands on
     * can hold. The piece keeps its name either way; the passage says which bars.
     */
    /**
     * A whole song is not a unit of practice, so what opens is the most of it the rung he stands on
     * can hold. The piece keeps its name either way; the passage says which bars.
     */
    override suspend fun open(score: Score): PracticeSelection {
        val states = withContext(Dispatchers.IO) { container.skillStore?.states().orEmpty() }
        val standing = container.curriculum.currentStage(states)
        val passage = shipped.passageFor(score, standing)
        if (passage == null) {
            diag.event(
                TAG,
                "'${score.title}' offers nothing any rung admits, so it opens whole " +
                    "[bars=${score.measures.size} stage=${standing.id.number}]",
            )
            return PracticeSelection(score, emptySet(), "Chosen from the repertoire")
        }
        diag.event(
            TAG,
            "'${score.title}' opens as ${passage.id.value} [bars=${passage.measures.size} " +
                "of ${score.measures.size} stage=${standing.id.number}'${standing.title}' " +
                "rung=${shipped.rungFor(passage)?.number}]",
        )
        return PracticeSelection(passage, emptySet(), "Chosen from the repertoire")
    }

    override suspend fun save(session: StoredSession, judgements: List<NoteJudgement>) {
        val store = withContext(Dispatchers.IO) { container.sessionStore }
        if (store == null) {
            diag.event(TAG, "session ${session.id.value} NOT saved: the practice database could not be opened")
            return
        }
        store.save(session, judgements)
    }

    /** Dating the path is part of folding, so a stage cannot be passed without the day being recorded. */
    override suspend fun recordSkills(outcomes: List<SkillOutcome>) {
        val store = withContext(Dispatchers.IO) { container.skillStore }
        if (store == null) {
            diag.event(TAG, "${outcomes.size} skill outcomes NOT folded: the database could not be opened")
            return
        }
        store.record(outcomes, nowEpochMillis())
        container.journeyWiring.markStanding()
    }
}

private fun ExerciseGenerator.generated(
    seed: Long,
    spec: DifficultySpec,
    targeting: Set<SkillTag>,
): PracticeSelection = PracticeSelection(generate(seed, spec), targeting, drillSummary(targeting))

private fun describeChoice(choice: PracticeChoice): String = when (choice) {
    is PracticeChoice.Piece ->
        "piece=${choice.id.value} tempo=${choice.tempoBpm}bpm targeting=${choice.targeting}"
    is PracticeChoice.Generated ->
        "generated seed=${choice.seed} bars=${choice.spec.bars} keys=${choice.spec.keys.map { it.fifths }} " +
            "tempo=${choice.spec.tempoBpm}bpm staves=${choice.spec.staves.size} targeting=${choice.targeting}"
}

/**
 * Nothing offered to a mono input may need two hands at once, generated material included. The
 * scheduler applies the same rule to its own choices; this is the drill route arriving at the same
 * honest answer rather than letting the judge refuse what the app just proposed.
 */
private fun monoSafe(spec: DifficultySpec, input: Polyphony): DifficultySpec {
    if (input == Polyphony.Poly) return spec
    val staff = spec.staves.first()
    return spec.copy(
        staves = listOf(staff),
        clefs = spec.clefs.filterKeys { it == staff },
        range = spec.range.filterKeys { it == staff },
        bothHandsActive = false,
    )
}

private fun Score.summarise(skills: ScoreSkills): ScoreSummary = ScoreSummary(
    id = id,
    title = title,
    composer = composer,
    polyphony = polyphony,
    skills = attackedNotes.indices.flatMapTo(mutableSetOf()) { skills.skillsOf(this, it) },
    bars = measures.size,
    defaultTempoBpm = defaultTempoBpm,
)

private fun drillSummary(targeting: Set<SkillTag>): String {
    val target = targeting.firstOrNull() ?: return "Written for you, at the bottom rung"
    return "Written for you, to drill ${describe(target).lowercase()}"
}

private fun pieceSummary(targeting: Set<SkillTag>): String {
    val target = targeting.firstOrNull() ?: return "From the repertoire"
    return "From the repertoire, for ${describe(target).lowercase()}"
}
