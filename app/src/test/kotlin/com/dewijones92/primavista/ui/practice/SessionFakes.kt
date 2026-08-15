package com.dewijones92.primavista.ui.practice

import com.dewijones92.primavista.audio.Metronome
import com.dewijones92.primavista.audio.TonePlayer
import com.dewijones92.primavista.common.Diag
import com.dewijones92.primavista.common.NoOpDiag
import com.dewijones92.primavista.database.PracticeSettings
import com.dewijones92.primavista.database.StoredSession
import com.dewijones92.primavista.di.InputMode
import com.dewijones92.primavista.di.PracticeSelection
import com.dewijones92.primavista.di.PracticeWiring
import com.dewijones92.primavista.di.SessionPreferences
import com.dewijones92.primavista.di.sessionTempoBpm
import com.dewijones92.primavista.notation.BravuraGlyphMetrics
import com.dewijones92.primavista.notation.ClassicalStaffLayout
import com.dewijones92.primavista.notation.GlyphMetrics
import com.dewijones92.primavista.notation.GlyphMetricsSource
import com.dewijones92.primavista.notation.StaffLayout
import com.dewijones92.primavista.practice.AnswerSource
import com.dewijones92.primavista.practice.Conductor
import com.dewijones92.primavista.practice.InputLatency
import com.dewijones92.primavista.practice.KeyboardTapSource
import com.dewijones92.primavista.practice.NoteJudgement
import com.dewijones92.primavista.practice.PerformanceJudge
import com.dewijones92.primavista.practice.PlayedNote
import com.dewijones92.primavista.practice.SkillOutcome
import com.dewijones92.primavista.practice.SpacedPracticeScheduler
import com.dewijones92.primavista.practice.TempoConductor
import com.dewijones92.primavista.practice.Tolerances
import com.dewijones92.primavista.practice.WindowedJudge
import com.dewijones92.primavista.score.Midi
import com.dewijones92.primavista.score.Polyphony
import com.dewijones92.primavista.score.Score
import com.dewijones92.primavista.score.SeededExerciseGenerator
import com.dewijones92.primavista.score.SkillTag
import com.dewijones92.primavista.score.Ticks
import com.dewijones92.primavista.score.TimeSignature
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertTrue
import java.io.File

/** Shared by every session test in this package, so there is one fake graph rather than three. */
internal const val WRITTEN_TEMPO_BPM: Int = 96

private const val WAIT_MILLIS = 10_000L
private const val POLL_MILLIS = 5L

internal fun PracticeViewModel.awaitLoaded() =
    await("no score was ever loaded") { state.value.score != null && !state.value.loading }

internal fun PracticeViewModel.await(what: String, until: () -> Boolean) {
    val deadline = System.currentTimeMillis() + WAIT_MILLIS
    while (!until() && System.currentTimeMillis() < deadline) Thread.sleep(POLL_MILLIS)
    assertTrue("$what (waited ${WAIT_MILLIS}ms)", until())
}

/**
 * The whole session graph, faked. Real layout, real generator, real conductor and real judge — the
 * only things replaced are the two that touch audio hardware and the store behind the preferences.
 */
internal class FakeWiring(
    initial: PracticeSettings,
    private val micGranted: Boolean = false,
) : PracticeWiring {

    var settings: PracticeSettings = initial
        private set

    val score: Score = SeededExerciseGenerator().generate(
        seed = 7,
        spec = SpacedPracticeScheduler.DefaultBase.copy(tempoBpm = WRITTEN_TEMPO_BPM),
    )

    override val diag: Diag = NoOpDiag
    override val layout: StaffLayout = ClassicalStaffLayout()
    override val metrics: GlyphMetrics = BRAVURA
    override val metronome: Metronome = FakeMetronome()
    override val tonePlayer: TonePlayer = FakeTonePlayer()

    override val preferences: SessionPreferences = object : SessionPreferences {
        override suspend fun settings(): PracticeSettings = settings

        override suspend fun remember(change: (PracticeSettings) -> PracticeSettings) {
            settings = change(settings)
        }
    }

    override fun nowEpochMillis(): Long = 0

    override fun microphoneGranted(): Boolean = micGranted

    override fun conductorFor(score: Score, tempoCeilingBpm: Int): Conductor = TempoConductor(
        clock = { 0L },
        tempoBpm = sessionTempoBpm(score.defaultTempoBpm, tempoCeilingBpm),
        countInBeats = 0,
        time = score.measures.firstOrNull()?.time ?: TimeSignature.FourFour,
    )

    override fun judgeFor(score: Score): PerformanceJudge = WindowedJudge(Tolerances())

    /** Held rather than made per call, so a test can push a tap into the one a session collects. */
    val tapSource: KeyboardTapSource = KeyboardTapSource()

    override fun sourceFor(mode: InputMode): AnswerSource = when (mode) {
        InputMode.Tap -> tapSource
        InputMode.Mic -> FakeMicSource
    }

    override suspend fun chooseNext(input: Polyphony, seed: Long): PracticeSelection =
        PracticeSelection(score, emptySet(), "For the test")

    override suspend fun chooseDrill(target: SkillTag, input: Polyphony, seed: Long): PracticeSelection =
        chooseNext(input, seed)

    override suspend fun open(score: Score): PracticeSelection = chooseNext(Polyphony.Poly, 0)

    override suspend fun save(session: StoredSession, judgements: List<NoteJudgement>) = Unit

    override suspend fun recordSkills(outcomes: List<SkillOutcome>) = Unit

    private companion object {
        // BRAVURA reads this at init.
        val ASSETS = File("src/main/assets/smufl")

        val BRAVURA: BravuraGlyphMetrics = BravuraGlyphMetrics.from(
            object : GlyphMetricsSource {
                override fun bravuraMetadataJson() = File(ASSETS, "bravura_metadata.json").readText()
                override fun glyphNamesJson() = File(ASSETS, "glyphnames.json").readText()
            },
        )
    }
}

internal object FakeMicSource : AnswerSource {
    override val label: String = "mic"
    override val polyphony: Polyphony = Polyphony.Mono
    override val latency: InputLatency = InputLatency.None
    override fun notes(): Flow<PlayedNote> = emptyFlow()
}

internal class FakeMetronome : Metronome {
    override var enabled: Boolean = true
    override fun configure(tempoBpm: Int, time: TimeSignature, barStart: Ticks) = Unit
    override fun onPosition(position: Ticks) = Unit
    override fun stop() = Unit
    override fun release() = Unit
}

internal class FakeTonePlayer : TonePlayer {
    override fun play(midi: Midi, durationMillis: Long) = Unit
    override fun playChord(midis: List<Midi>, durationMillis: Long) = Unit
    override fun stopAll() = Unit
    override fun release() = Unit
}
