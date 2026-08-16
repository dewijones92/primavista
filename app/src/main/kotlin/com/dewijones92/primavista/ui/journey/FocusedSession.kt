package com.dewijones92.primavista.ui.journey

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dewijones92.primavista.di.AppContainer
import com.dewijones92.primavista.di.PracticeWiring
import com.dewijones92.primavista.practice.JudgeOutcome
import com.dewijones92.primavista.ui.practice.PracticeIntent
import com.dewijones92.primavista.ui.practice.PracticeScreen
import com.dewijones92.primavista.ui.practice.PracticeUiState
import com.dewijones92.primavista.ui.practice.PracticeViewModel
import com.dewijones92.primavista.ui.practice.ReadingProgress

/**
 * One practice session, on a [PracticeWiring] the caller chose.
 *
 * This is the app's only session driver used twice, not a second one: the view model, the judge,
 * the conductor, the answer source and the staff are the ordinary ones, and all that differs is
 * which wiring answers "what should he read". That is what makes a placement probe and a stage
 * drill measurably the same thing. See `.claude/CODE-NOTES.md`.
 *
 * [restartToken] is how the caller says "next one": changing it re-asks the wiring, which is what
 * walks a placement up its rungs.
 */
@Composable
internal fun FocusedSession(
    container: AppContainer,
    wiring: PracticeWiring,
    restartToken: Int,
    onSettled: (JudgeOutcome) -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable (PracticeUiState) -> Unit = {},
    footer: @Composable (PracticeUiState, PracticeViewModel) -> Unit = { _, _ -> },
) {
    val owner = remember(wiring) { SessionOwner() }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    val viewModel: PracticeViewModel = viewModel(viewModelStoreOwner = owner) { PracticeViewModel(wiring) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(restartToken) { viewModel.choose(PracticeIntent.Next) }

    // A settled session is reported once. `dismiss` clears the result so the same run cannot be
    // counted twice when the screen recomposes.
    LaunchedEffect(state.result, state.refusal) {
        val result = state.result
        val refusal = state.refusal
        when {
            result != null -> {
                onSettled(JudgeOutcome.Judged(result))
                viewModel.dismiss()
            }
            refusal != null -> onSettled(JudgeOutcome.Refused(refusal))
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.pause()
        }
    }

    val readSoFar by container.repertoire.parsed.collectAsState()
    Column(modifier.fillMaxSize()) {
        header(state)
        PracticeScreen(
            state = state,
            metrics = container.glyphMetrics,
            onStart = viewModel::play,
            onPause = viewModel::pause,
            onResume = viewModel::play,
            onKeyPressed = { midi, nanos -> container.tapSource.onKeyPressed(midi, nanos) },
            onFrame = viewModel::tick,
            onChange = viewModel::change,
            reading = ReadingProgress(readSoFar.size, container.repertoire.expected),
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        footer(state, viewModel)
    }
}

/**
 * A store of its own, cleared when the session leaves.
 *
 * Without it the view model would either outlive the screen — still collecting taps and holding a
 * conductor — or never have `onCleared` called at all, which is the same leak wearing a different
 * hat. Clearing here is what stops the transport of a finished probe running under the next one.
 */
private class SessionOwner : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()
}
