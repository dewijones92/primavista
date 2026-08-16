package com.dewijones92.primavista.ui.practice

import com.dewijones92.primavista.audio.Metronome
import com.dewijones92.primavista.database.PracticeSettings
import com.dewijones92.primavista.di.InputMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val CEILING_BPM = 50

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

        viewModel.change(PracticeChange.Metronome)
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

    /**
     * Opening a piece from the Repertoire tab is a session starting, so it has to settle what is
     * listening. It did not for one commit: a saved PLAY IT preference opened silently on TAP,
     * which is a wrong answer given quietly — what docs/spec.md I3 exists to prevent.
     */
    @Test
    fun `a piece opened from the repertoire still honours the stored input`() {
        val wiring = FakeWiring(PracticeSettings(inputLabel = "mic"), micGranted = true)
        val viewModel = PracticeViewModel(wiring)

        viewModel.openScore(requestId = 1, score = wiring.score)
        viewModel.awaitLoaded()

        assertEquals(InputMode.Mic, viewModel.state.value.input)
    }

    /** And a revoked permission must still surface, rather than the session opening as if granted. */
    @Test
    fun `a piece opened from the repertoire falls back to tap when the mic is revoked`() {
        val wiring = FakeWiring(PracticeSettings(inputLabel = "mic"), micGranted = false)
        val viewModel = PracticeViewModel(wiring)

        viewModel.openScore(requestId = 1, score = wiring.score)
        viewModel.awaitLoaded()

        assertEquals(InputMode.Tap, viewModel.state.value.input)
        assertNotNull(viewModel.state.value.notice)
    }
}
