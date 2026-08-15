package com.dewijones92.primavista.ui.journey

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.dewijones92.primavista.di.AppContainer
import com.dewijones92.primavista.practice.JudgeOutcome
import com.dewijones92.primavista.practice.SessionResult
import com.dewijones92.primavista.practice.Stage
import com.dewijones92.primavista.ui.practice.PracticeIntent
import com.dewijones92.primavista.ui.results.ResultsSheet

/**
 * A session on one rung of the path, with the way back to it in the corner.
 *
 * The stage narrows what the scheduler may choose from and nothing more — it still picks the weakest
 * and most due skill of those, and the generator still writes the notes (docs/journey.md).
 */
@Composable
internal fun StagePractice(
    container: AppContainer,
    stage: Stage,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val wiring = remember(stage) { container.practiceWiringFor(stage) }
    var restart by remember(stage) { mutableIntStateOf(0) }
    var finished: SessionResult? by remember(stage) { mutableStateOf(null) }

    FocusedSession(
        container = container,
        wiring = wiring,
        restartToken = restart,
        onSettled = { outcome -> finished = (outcome as? JudgeOutcome.Judged)?.result },
        modifier = modifier,
        header = { StageBanner(stage, onBack) },
    ) { state, viewModel ->
        finished?.let { result ->
            Dialog(onDismissRequest = { finished = null }) {
                Surface(
                    shape = RoundedCornerShape(SHEET_CORNER),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = SHEET_ELEVATION,
                ) {
                    ResultsSheet(
                        result = result,
                        input = state.input.polyphony,
                        onPractiseWeakest = {
                            finished = null
                            viewModel.choose(PracticeIntent.DrillWeakest)
                        },
                        onAgain = {
                            finished = null
                            restart++
                        },
                        onDone = {
                            finished = null
                            onBack()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StageBanner(stage: Stage, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = EDGE, end = EDGE, top = EDGE),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.testTag("back-to-path")) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to the path")
        }
        Column {
            Text(
                text = "Stage ${stage.id.number} — ${stage.title}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stage.blurb,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val EDGE = 4.dp
private val SHEET_CORNER = 24.dp
private val SHEET_ELEVATION = 4.dp
