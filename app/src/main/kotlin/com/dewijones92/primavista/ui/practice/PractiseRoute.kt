package com.dewijones92.primavista.ui.practice

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dewijones92.primavista.di.AppContainer
import com.dewijones92.primavista.di.InputMode
import com.dewijones92.primavista.practice.SessionResult
import com.dewijones92.primavista.score.Midi
import com.dewijones92.primavista.score.Polyphony
import com.dewijones92.primavista.ui.mascot.MascotMood
import com.dewijones92.primavista.ui.mascot.Trill
import com.dewijones92.primavista.ui.repertoire.PracticeRequest
import com.dewijones92.primavista.ui.results.ResultsSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs the session, and owns the two decisions above it: **what** to read, and **what is listening**.
 *
 * What to read comes from the scheduler on every entry rather than from a hardcoded corpus piece —
 * that is the ladder (CLAUDE.md, *The ladder problem*), and it is why a fresh install opens on a
 * four-bar generated exercise rather than on Bach.
 *
 * What is listening comes from the Settings screen: the first `choose` reads the stored preference
 * before it asks for anything to read.
 */
@Composable
public fun PractiseRoute(container: AppContainer, modifier: Modifier = Modifier) {
    val stages = remember(container) { appStageSource(container) }
    val viewModel: PracticeViewModel = viewModel { PracticeViewModel(container.practiceWiring, stages) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        container.diag.event("input", "RECORD_AUDIO ${if (granted) "granted" else "refused"} at the system prompt")
        viewModel.selectInput(InputMode.Mic, granted)
    }

    // One effect, because two of them race. A separate "choose something to read" effect ran
    // alongside this one on a fresh tab, and whichever coroutine finished last won the state — so
    // asking for a piece from the Repertoire tab could land you on a generated exercise instead.
    // Found on a device on 2026-08-15; the race predates the file picker that made it easy to hit.
    LaunchedEffect(PracticeRequest.count) {
        val chosen = PracticeRequest.peek()
        when {
            chosen != null -> viewModel.openScore(PracticeRequest.count, chosen)
            state.score == null && !state.loading -> viewModel.choose(PracticeIntent.Next)
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

    val setup = remember(viewModel, micPermission, container) {
        sessionSetup(viewModel, container) { micPermission.launch(Manifest.permission.RECORD_AUDIO) }
    }

    val readSoFar by container.repertoire.parsed.collectAsState()
    Column(modifier.fillMaxSize()) {
        val showingStaff = !state.loading && (state.score != null || state.refusal != null)
        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (showingStaff) {
                PracticeScreen(
                    state = state,
                    metrics = container.glyphMetrics,
                    onStart = viewModel::play,
                    onPause = viewModel::pause,
                    onResume = viewModel::play,
                    onKeyPressed = { midi, nanos -> tapped(container, midi, nanos) },
                    onFrame = viewModel::tick,
                    onChange = viewModel::change,
                    reading = ReadingProgress(readSoFar.size, container.repertoire.expected),
                    setup = setup,
                )
            } else {
                Waiting(state.notice, viewModel::dismiss)
            }
        }

        state.result?.let { result ->
            ResultsDialog(
                result = result,
                input = state.input.polyphony,
                onDrill = { viewModel.choose(PracticeIntent.DrillWeakest) },
                onAgain = { viewModel.choose(PracticeIntent.Again) },
                onDone = viewModel::dismiss,
            )
        }
    }
}

/** Asking for the microphone is the activity's job, so the launcher arrives as [askForMicrophone]. */
private fun sessionSetup(
    viewModel: PracticeViewModel,
    container: AppContainer,
    askForMicrophone: () -> Unit,
) = SessionSetup(
    onInput = { mode ->
        if (mode == InputMode.Mic && !container.microphoneGranted()) {
            container.diag.event("input", "PLAY IT selected without RECORD_AUDIO; asking for it")
            askForMicrophone()
        } else {
            viewModel.selectInput(mode)
        }
    },
    onListen = viewModel::listen,
    onNext = { viewModel.choose(PracticeIntent.Next) },
    onDismissNotice = viewModel::dismiss,
)

/**
 * The tap boundary keeps its own log line: a report showing every note `Missed` with no
 * `input/notes-tap` count cannot otherwise say whether the touch arrived.
 */
private fun tapped(container: AppContainer, midi: Midi, nanos: Long) {
    container.diag.event(
        "input",
        "dewidebug tap key=${midi.number} at=${nanos}ns now=${System.nanoTime()}ns",
    )
    container.tapSource.onKeyPressed(midi, nanos)
}

/** One answer to "where does he stand", and it is the container's — see `.claude/CODE-NOTES.md`. */
private fun appStageSource(container: AppContainer): StageSource = {
    withContext(Dispatchers.IO) { container.standingStage() }
}

/** [input] is what was listening, so the sheet cannot offer a drill this session would refuse. */
@Composable
private fun ResultsDialog(
    result: SessionResult,
    input: Polyphony,
    onDrill: () -> Unit,
    onAgain: () -> Unit,
    onDone: () -> Unit,
) {
    Dialog(onDismissRequest = onDone) {
        // A Dialog's content is transparent by default, so without a surface the results render
        // straight over the staff and neither is readable.
        Surface(
            shape = RoundedCornerShape(RESULT_CORNER),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = RESULT_ELEVATION,
        ) {
            ResultsSheet(
                result = result,
                input = input,
                onPractiseWeakest = onDrill,
                onAgain = onAgain,
                onDone = onDone,
            )
        }
    }
}

@Composable
private fun Waiting(notice: String?, onDismiss: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Trill(MascotMood.Curious, Modifier.size(SPINNER_BIRD))
        Spacer(Modifier.height(14.dp))
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = SPINNER_STROKE,
            modifier = Modifier.size(SPINNER_SIZE),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Finding you something to read…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SessionNotice(notice, onDismiss)
    }
}

private val RESULT_CORNER = 24.dp
private val RESULT_ELEVATION = 4.dp
private val SPINNER_SIZE = 30.dp
private val SPINNER_STROKE = 3.dp
private val SPINNER_BIRD = 108.dp
