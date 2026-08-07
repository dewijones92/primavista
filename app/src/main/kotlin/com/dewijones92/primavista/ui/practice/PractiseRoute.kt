package com.dewijones92.primavista.ui.practice

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dewijones92.primavista.di.AppContainer
import com.dewijones92.primavista.di.InputMode
import com.dewijones92.primavista.score.Corpus
import com.dewijones92.primavista.score.MusicXmlResult
import com.dewijones92.primavista.ui.results.ResultsSheet

/**
 * Loads something to read and runs the session.
 *
 * The Practise tab opens on the first corpus piece for now, so the app has something real on screen
 * the moment it launches. Choosing *what* to practise from the scheduler is wired through the
 * Repertoire tab; making the scheduler the default entry point waits on there being enough stored
 * history for its choice to mean anything.
 */
@Composable
public fun PractiseRoute(container: AppContainer, modifier: Modifier = Modifier) {
    val viewModel: PracticeViewModel = viewModel {
        PracticeViewModel(
            layout = container.staffLayout,
            metrics = container.glyphMetrics,
            diag = container.diag,
            conductorFor = container::conductorFor,
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        val piece = Corpus.pieces.first()
        when (val parsed = Corpus.parse(piece, container.musicXmlParser)) {
            is MusicXmlResult.Parsed -> {
                if (parsed.dropped.isNotEmpty()) {
                    container.diag.event(
                        "corpus",
                        "'${piece.title}' parsed with ${parsed.dropped.size} dropped: " +
                            parsed.dropped.joinToString("; ") { it.toString() },
                    )
                }
                viewModel.load(
                    score = parsed.score,
                    source = container.sourceFor(InputMode.Tap),
                    judge = container.judgeFor(parsed.score),
                )
            }
            is MusicXmlResult.Failed ->
                container.diag.event("corpus", "'${piece.title}' failed to parse: ${parsed.reason}")
        }
    }

    // Backgrounding pauses the session rather than letting the Conductor run on unwatched — see
    // .claude/CODE-NOTES.md. It has to be an explicit lifecycle observer, because "the effect
    // stopped" is not the same as "the transport stopped".
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.onBackgrounded()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier.fillMaxSize()) {
        if (state.score == null && state.refusal == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading…", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            PracticeScreen(
                state = state,
                metrics = container.glyphMetrics,
                onStart = viewModel::start,
                onPause = viewModel::onBackgrounded,
                onResume = viewModel::resume,
                onKeyPressed = { midi, nanos -> container.tapSource.onKeyPressed(midi, nanos) },
                onFrame = viewModel::tick,
                onDismissRefusal = viewModel::dismissRefusal,
            )
        }

        state.result?.let { result ->
            Dialog(onDismissRequest = viewModel::dismissResult) {
                ResultsSheet(
                    result = result,
                    onPractiseWeakest = viewModel::dismissResult,
                    onAgain = viewModel::restart,
                    onDone = viewModel::dismissResult,
                )
            }
        }
    }
}
