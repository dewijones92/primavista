package com.dewijones92.primavista.ui.practice

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
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
import com.dewijones92.primavista.score.Polyphony
import com.dewijones92.primavista.ui.repertoire.PracticeRequest
import com.dewijones92.primavista.ui.results.ResultsSheet

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
    val viewModel: PracticeViewModel = viewModel { PracticeViewModel(container.practiceWiring) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        container.diag.event("input", "RECORD_AUDIO ${if (granted) "granted" else "refused"} at the system prompt")
        viewModel.selectInput(InputMode.Mic, granted)
    }

    LaunchedEffect(Unit) {
        if (state.score == null && !state.loading) viewModel.choose(PracticeIntent.Next)
    }
    LaunchedEffect(PracticeRequest.count) {
        viewModel.openPiece(PracticeRequest.count, PracticeRequest.peek())
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

    Column(modifier.fillMaxSize()) {
        SessionControls(
            state = state,
            onInput = { mode ->
                if (mode == InputMode.Mic && !container.microphoneGranted()) {
                    container.diag.event("input", "PLAY IT selected without RECORD_AUDIO; asking for it")
                    micPermission.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    viewModel.selectInput(mode)
                }
            },
            onListen = viewModel::listen,
            onNext = { viewModel.choose(PracticeIntent.Next) },
        )
        val showingStaff = !state.loading && (state.score != null || state.refusal != null)
        if (!showingStaff) Notice(state.notice, viewModel::dismiss)

        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                !showingStaff -> Waiting()
                else -> PracticeScreen(
                    state = state,
                    metrics = container.glyphMetrics,
                    onStart = viewModel::play,
                    onPause = viewModel::pause,
                    onResume = viewModel::play,
                    onKeyPressed = { midi, nanos ->
                        // dewidebug: the boundary between the keyboard and the input seam. A report
                        // showing every note Missed with no `input/notes-tap` count cannot say whether
                        // the touch never arrived or the flow never delivered it.
                        container.diag.event(
                            "input",
                            "dewidebug tap key=${midi.number} at=${nanos}ns now=${System.nanoTime()}ns",
                        )
                        container.tapSource.onKeyPressed(midi, nanos)
                    },
                    onFrame = viewModel::tick,
                    onToggle = viewModel::toggle,
                )
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

/**
 * Input, sound and what to read next — the three things a session needs that the staff itself cannot
 * offer. It sits above the screen rather than floating over it so it can never cover the notation.
 */
@Composable
private fun SessionControls(
    state: PracticeUiState,
    onInput: (InputMode) -> Unit,
    onListen: () -> Unit,
    onNext: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            InputModeChip(InputMode.Tap, "TAP", Icons.Rounded.TouchApp, state.input, onInput)
            InputModeChip(InputMode.Mic, "MIC", Icons.Rounded.Mic, state.input, onInput)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onListen, modifier = Modifier.testTag("listen")) {
                Icon(Icons.Rounded.Hearing, contentDescription = "Hear it first")
            }
            IconButton(onClick = onNext, modifier = Modifier.testTag("next")) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = "Something else to read")
            }
        }
    }
}

@Composable
private fun InputModeChip(
    mode: InputMode,
    label: String,
    icon: ImageVector,
    current: InputMode,
    onInput: (InputMode) -> Unit,
) {
    FilterChip(
        selected = mode == current,
        onClick = { onInput(mode) },
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(CHIP_ICON)) },
        shape = RoundedCornerShape(CHIP_CORNER),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = Modifier.testTag("input-${mode.name}"),
    )
}

/** Everything the session decides on Dewi's behalf says so here, in the words it would use to him. */
@Composable
private fun Notice(notice: String?, onDismiss: () -> Unit) {
    AnimatedVisibility(
        visible = notice != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(NOTICE_CORNER),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp)
                .clickable(onClick = onDismiss)
                .testTag("notice"),
        ) {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(NOTICE_ICON),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = notice.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun Waiting() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = SPINNER_STROKE,
                modifier = Modifier.size(SPINNER_SIZE),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Choosing something to read…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val RESULT_CORNER = 24.dp
private val RESULT_ELEVATION = 4.dp
private val CHIP_CORNER = 50.dp
private val CHIP_ICON = 16.dp
private val NOTICE_CORNER = 12.dp
private val NOTICE_ICON = 16.dp
private val SPINNER_SIZE = 34.dp
private val SPINNER_STROKE = 3.dp
