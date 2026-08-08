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
import com.dewijones92.primavista.score.CorpusPiece
import com.dewijones92.primavista.score.Midi
import com.dewijones92.primavista.score.Polyphony
import com.dewijones92.primavista.score.Score
import com.dewijones92.primavista.score.SeededExerciseGenerator
import com.dewijones92.primavista.score.SkillTag
import com.dewijones92.primavista.score.Ticks
import com.dewijones92.primavista.score.TimeSignature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

private const val WRITTEN_TEMPO_BPM = 96
private const val CEILING_BPM = 50
private const val WAIT_MILLIS = 10_000L
private const val POLL_MILLIS = 5L

/**
 * docs/spec.md is about the app never saying something untrue, and a preference screen whose
 * controls a session ignores is exactly that. These are the assertions that stop the wiring
 * quietly coming undone again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionReadsSettingsTest {

    @Before
    fun useUnconfinedMain() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun releaseMain() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a piece written faster than the stored tempo is read at the stored tempo`() {
        val wiring = FakeWiring(PracticeSettings(tempoBpm = CEILING_BPM))
        val viewModel = PracticeViewModel(wiring)

        viewModel.choose(PracticeIntent.Next)
        viewModel.awaitLoaded()

        assertEquals(WRITTEN_TEMPO_BPM, wiring.score.defaultTempoBpm)
        assertEquals(CEILING_BPM, viewModel.state.value.tempoBpm)
    }

    @Test
    fun `a stored tempo above the written one leaves the piece alone`() {
        val wiring = FakeWiring(PracticeSettings(tempoBpm = 200))
        val viewModel = PracticeViewModel(wiring)

        viewModel.choose(PracticeIntent.Next)
        viewModel.awaitLoaded()

        assertEquals(WRITTEN_TEMPO_BPM, viewModel.state.value.tempoBpm)
    }

    @Test
    fun `the stored metronome preference decides whether the session opens with it on`() {
        val wiring = FakeWiring(PracticeSettings(metronomeOn = false))
        val viewModel = PracticeViewModel(wiring)

        viewModel.choose(PracticeIntent.Next)
        viewModel.awaitLoaded()

        assertFalse(viewModel.state.value.metronomeOn)
        assertFalse("the metronome itself was left armed", wiring.metronome.enabled)
    }

    @Test
    fun `the stored input decides what is listening`() {
        val wiring = FakeWiring(PracticeSettings(inputLabel = "mic"), micGranted = true)
        val viewModel = PracticeViewModel(wiring)

        viewModel.choose(PracticeIntent.Next)
        viewModel.awaitLoaded()

        assertEquals(InputMode.Mic, viewModel.state.value.input)
        assertEquals("mic", viewModel.state.value.inputLabel)
    }

    @Test
    fun `a revoked microphone opens on tap, says so, and does not overwrite the preference`() {
        val wiring = FakeWiring(PracticeSettings(inputLabel = "mic"), micGranted = false)
        val viewModel = PracticeViewModel(wiring)

        viewModel.choose(PracticeIntent.Next)
        viewModel.awaitLoaded()

        assertEquals(InputMode.Tap, viewModel.state.value.input)
        val notice = viewModel.state.value.notice
        assertNotNull("opening on TAP against a stored MIC has to be stated", notice)
        assertTrue("the notice does not name the microphone: $notice", notice!!.contains("microphone"))
        assertEquals("mic", wiring.settings.inputLabel)
    }

    @Test
    fun `listen first plays the piece through before it is read`() {
        val wiring = FakeWiring(PracticeSettings(listenFirstOn = true))
        val viewModel = PracticeViewModel(wiring)

        viewModel.choose(PracticeIntent.Next)
        viewModel.awaitLoaded()
        viewModel.await("the piece was not played through") { viewModel.state.value.previewing }

        assertTrue(viewModel.state.value.previewing)
    }

    @Test
    fun `listen first left off starts the session silent`() {
        val wiring = FakeWiring(PracticeSettings(listenFirstOn = false))
        val viewModel = PracticeViewModel(wiring)

        viewModel.choose(PracticeIntent.Next)
        viewModel.awaitLoaded()

        assertFalse(viewModel.state.value.previewing)
    }

    @Test
    fun `turning the metronome off mid-session is remembered`() {
        val wiring = FakeWiring(PracticeSettings(metronomeOn = true))
        val viewModel = PracticeViewModel(wiring)
        viewModel.choose(PracticeIntent.Next)
        viewModel.awaitLoaded()

        viewModel.toggle(PracticeToggle.Metronome)
        viewModel.await("the metronome preference was not saved") { !wiring.settings.metronomeOn }

        assertFalse(wiring.settings.metronomeOn)
    }

    @Test
    fun `switching input mid-session is remembered`() {
        val wiring = FakeWiring(PracticeSettings(inputLabel = null), micGranted = true)
        val viewModel = PracticeViewModel(wiring)
        viewModel.choose(PracticeIntent.Next)
        viewModel.awaitLoaded()

        viewModel.selectInput(InputMode.Mic)
        viewModel.await("the input preference was not saved") { wiring.settings.inputLabel == "mic" }

        assertEquals("mic", wiring.settings.inputLabel)
    }

    /** The mute is for the run, not a preference: it must not rewrite what Dewi asked for. */
    @Test
    fun `the mic muting the metronome does not turn his metronome preference off`() {
        val wiring = FakeWiring(PracticeSettings(metronomeOn = true), micGranted = true)
        val viewModel = PracticeViewModel(wiring)
        viewModel.choose(PracticeIntent.Next)
        viewModel.awaitLoaded()

        viewModel.selectInput(InputMode.Mic)
        viewModel.await("the input switch never completed") { viewModel.state.value.input == InputMode.Mic }

        assertFalse("the mic session should be silent", viewModel.state.value.metronomeOn)
        assertTrue("the stored preference was overwritten by a mute", wiring.settings.metronomeOn)
    }
}

private fun PracticeViewModel.awaitLoaded() =
    await("no score was ever loaded") { state.value.score != null && !state.value.loading }

private fun PracticeViewModel.await(what: String, until: () -> Boolean) {
    val deadline = System.currentTimeMillis() + WAIT_MILLIS
    while (!until() && System.currentTimeMillis() < deadline) Thread.sleep(POLL_MILLIS)
    assertTrue("$what (waited ${WAIT_MILLIS}ms)", until())
}

/**
 * The whole session graph, faked. Real layout, real generator, real conductor and real judge — the
 * only things replaced are the two that touch audio hardware and the store behind the preferences.
 */
private class FakeWiring(
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

    override fun sourceFor(mode: InputMode): AnswerSource = when (mode) {
        InputMode.Tap -> KeyboardTapSource()
        InputMode.Mic -> FakeMicSource
    }

    override suspend fun chooseNext(input: Polyphony, seed: Long): PracticeSelection =
        PracticeSelection(score, emptySet(), "For the test")

    override suspend fun chooseDrill(target: SkillTag, input: Polyphony, seed: Long): PracticeSelection =
        chooseNext(input, seed)

    override suspend fun open(piece: CorpusPiece): PracticeSelection = chooseNext(Polyphony.Poly, 0)

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

private object FakeMicSource : AnswerSource {
    override val label: String = "mic"
    override val polyphony: Polyphony = Polyphony.Mono
    override val latency: InputLatency = InputLatency.None
    override fun notes(): Flow<PlayedNote> = emptyFlow()
}

private class FakeMetronome : Metronome {
    override var enabled: Boolean = true
    override fun configure(tempoBpm: Int, time: TimeSignature, barStart: Ticks) = Unit
    override fun onPosition(position: Ticks) = Unit
    override fun stop() = Unit
    override fun release() = Unit
}

private class FakeTonePlayer : TonePlayer {
    override fun play(midi: Midi, durationMillis: Long) = Unit
    override fun playChord(midis: List<Midi>, durationMillis: Long) = Unit
    override fun stopAll() = Unit
    override fun release() = Unit
}
