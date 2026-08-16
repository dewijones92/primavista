package com.dewijones92.primavista.ui.practice

import com.dewijones92.primavista.database.PracticeSettings
import com.dewijones92.primavista.practice.ReadingLead
import com.dewijones92.primavista.practice.TransportState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

private const val ONE_BEAT = 1

/**
 * When the reading-ahead card comes off.
 *
 * The review page rewinds the playhead to the opening and pins the scroll at zero, so a cover left
 * frozen at the *end* of the piece paints the whole staff — and the review page is the one that
 * exists to show verdict-coloured noteheads. `tick()` cannot clear it, because it stops running the
 * moment the transport does, so `finish()` has to.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReadingCoverLifetimeTest {

    @Before
    fun useUnconfinedMain() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    /**
     * The session is stopped before Main is released. A view model left running keeps launching on
     * `viewModelScope`, and the next class's `Dispatchers.setMain` then throws "used concurrently
     * with setting it" — a failure that lands in whichever class happens to run next, not in the
     * one that caused it.
     */
    @After
    fun releaseMain() {
        started?.pause()
        started = null
        Dispatchers.resetMain()
    }

    private var started: PracticeViewModel? = null

    @Test
    fun `a finished run leaves no cover over the review page`() {
        val session = started()

        assertNotNull("the cover never appeared, so this proves nothing", session.model.state.value.coverX)
        session.playOut()

        assertEquals(TransportState.Finished, session.model.state.value.transport)
        assertNull("the cover survived the end of the run", session.model.state.value.coverX)
    }

    @Test
    fun `turning reading ahead off takes the cover away at once`() {
        val session = started()

        session.model.change(PracticeChange.ReadAhead(ReadingLead.Off))

        assertNull(session.model.state.value.coverX)
    }

    /** A cover belonging to the last piece must not be painted over the next one. */
    @Test
    fun `loading the next piece starts with no cover`() {
        val session = started()

        session.model.choose(PracticeIntent.Next)
        session.model.awaitLoaded()

        assertNull(session.model.state.value.coverX)
    }

    private class Session(val model: PracticeViewModel, private val wiring: FakeWiring) {
        /** Plays the piece out a frame at a time, in fake time, as the UI's frame clock would. */
        fun playOut() {
            repeat(FRAMES) {
                wiring.clock.advanceMillis(FRAME_MILLIS)
                model.tick()
            }
        }
    }

    private fun started(): Session {
        val wiring = FakeWiring(PracticeSettings(readingLeadBeats = ONE_BEAT))
        val model = PracticeViewModel(wiring)
        model.choose(PracticeIntent.Next)
        model.awaitLoaded()
        model.play()
        // One frame is enough for the cover to exist; the run is played out where a test wants it.
        wiring.clock.advanceMillis(FRAME_MILLIS)
        model.tick()
        started = model
        return Session(model, wiring)
    }

    private companion object {
        const val FRAMES = 900
        const val FRAME_MILLIS = 16.0
    }
}
