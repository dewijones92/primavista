package com.dewijones92.primavista.ui.practice

import com.dewijones92.primavista.database.PracticeSettings
import com.dewijones92.primavista.score.Midi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * docs/spec.md I2: a verdict is about a note Dewi actually played — *in this run*.
 *
 * `KeyboardTapSource` is a buffered queue shared by the whole app, so a key pressed while the
 * transport was idle waits there and is delivered the instant a run starts. Judged, it becomes an
 * extra note he never played, which is the app inventing a fault.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StaleTapsTest {

    @Before
    fun useUnconfinedMain() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun releaseMain() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a key pressed before the run started is not judged as a note in it`() {
        val wiring = FakeWiring(PracticeSettings())
        val viewModel = PracticeViewModel(wiring)
        viewModel.choose(PracticeIntent.Next)
        viewModel.awaitLoaded()

        wiring.tapSource.onKeyPressed(Midi(60), eventTimeNanos = BEFORE_THE_CLOCK_STARTED)
        viewModel.play()

        assertEquals("a tap from before the run was counted against it", 0, viewModel.state.value.extras)
        assertEquals(emptyMap<Int, Any>(), viewModel.state.value.verdicts)
    }

    @Test
    fun `a key pressed once the run is under way is still judged`() {
        val wiring = FakeWiring(PracticeSettings())
        val viewModel = PracticeViewModel(wiring)
        viewModel.choose(PracticeIntent.Next)
        viewModel.awaitLoaded()

        viewModel.play()
        wiring.tapSource.onKeyPressed(Midi(60), eventTimeNanos = 0L)
        viewModel.await("the tap never reached the judge") {
            viewModel.state.value.extras > 0 || viewModel.state.value.verdicts.isNotEmpty()
        }
    }
}

/** The fake conductor's clock reads zero, so anything negative predates the run. */
private const val BEFORE_THE_CLOCK_STARTED = -1_000_000L
