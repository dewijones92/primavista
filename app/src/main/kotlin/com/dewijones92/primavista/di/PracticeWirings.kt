package com.dewijones92.primavista.di

import com.dewijones92.primavista.database.PracticeSettings
import com.dewijones92.primavista.practice.SkillOutcome
import com.dewijones92.primavista.practice.Stage
import com.dewijones92.primavista.score.Polyphony
import com.dewijones92.primavista.score.SkillTag

private const val TAG = "journey"

/**
 * One rung's worth of the app, so tapping a stage practises **that** stage.
 *
 * Delegation rather than a flag inside [AppPracticeWiring]: the narrowing is the only difference,
 * and everything else — the judge, the conductor, the stores — has to be the same objects or the
 * session would be measuring something the rest of the app does not.
 */
public class StagePracticeWiring(
    private val delegate: AppPracticeWiring,
    private val stage: Stage,
) : PracticeWiring by delegate {

    override suspend fun chooseNext(input: Polyphony, seed: Long): PracticeSelection =
        delegate.chooseWithin(stage.focus, input, seed)
}

/**
 * The placement read's session, which is an ordinary session with three deliberate differences.
 *
 * It reads a probe the [com.dewijones92.primavista.practice.PlacementRead] chose rather than asking
 * the scheduler; it never plays the music first, because a placement measures reading and hearing it
 * first measures memory; and it does **not** fold the probe into the skill store, because the
 * placement's own conclusion is what seeds it and two foldings of one performance would count it
 * twice. See `.claude/CODE-NOTES.md`.
 *
 * Everything that decides a verdict is untouched: same judge, same conductor, same answer source.
 */
public class ProbeWiring(
    private val delegate: PracticeWiring,
    private val probe: () -> PracticeSelection?,
) : PracticeWiring by delegate {

    override val preferences: SessionPreferences = NeverListensFirst(delegate.preferences)

    override suspend fun chooseNext(input: Polyphony, seed: Long): PracticeSelection =
        probe() ?: delegate.chooseNext(input, seed)

    override suspend fun chooseDrill(target: SkillTag, input: Polyphony, seed: Long): PracticeSelection =
        chooseNext(input, seed)

    override suspend fun recordSkills(outcomes: List<SkillOutcome>) {
        delegate.diag.event(
            TAG,
            "placement probe NOT folded into the skill store: ${outcomes.size} outcomes " +
                "(attempted=${outcomes.sumOf { it.attempts }} clean=${outcomes.sumOf { it.cleanAttempts }}) " +
                "are evidence for the placement, which seeds the store once at the end",
        )
    }
}

private class NeverListensFirst(private val delegate: SessionPreferences) : SessionPreferences {

    override suspend fun settings(): PracticeSettings = delegate.settings().copy(listenFirstOn = false)

    override suspend fun remember(change: (PracticeSettings) -> PracticeSettings): Unit = delegate.remember(change)
}
