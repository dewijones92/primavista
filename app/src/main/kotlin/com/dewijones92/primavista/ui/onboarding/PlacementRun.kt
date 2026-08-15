package com.dewijones92.primavista.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.dewijones92.primavista.di.AppContainer
import com.dewijones92.primavista.di.PracticeSelection
import com.dewijones92.primavista.practice.JudgeOutcome
import com.dewijones92.primavista.practice.Placement
import com.dewijones92.primavista.practice.PlacementProbe
import com.dewijones92.primavista.practice.PlacementProbeResult
import com.dewijones92.primavista.practice.PlacementRequest
import com.dewijones92.primavista.practice.PlacementStep
import com.dewijones92.primavista.practice.SkillOutcome
import com.dewijones92.primavista.score.Polyphony
import com.dewijones92.primavista.ui.journey.FocusedSession
import com.dewijones92.primavista.ui.mascot.MascotMood
import com.dewijones92.primavista.ui.mascot.Trill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "placement"

/** What the read concluded, and the evidence it concluded it from. */
internal data class PlacementFinding(val placement: Placement, val evidence: List<SkillOutcome>)

/**
 * The placement read, driven for real: each probe becomes a `Score`, and that score is read through
 * the ordinary session — same conductor, same answer source, same judge.
 *
 * That identity is the point. A placement judged by anything other than the app's own judge would be
 * measuring a different thing from the sessions it is about to seed, and the reader would be placed
 * against a standard that never applies again.
 *
 * It climbs while it goes well and stops when it stops, so it is over in a couple of minutes.
 */
@Composable
internal fun PlacementRun(
    container: AppContainer,
    input: Polyphony,
    onFinding: (PlacementFinding) -> Unit,
    onAbandon: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val request = remember(input) { container.placementRequest(input) }
    val held = remember(request) { HeldProbe() }
    val wiring = remember(request) { container.probeWiring { held.selection } }
    var history: List<PlacementProbeResult> by remember(request) { mutableStateOf(emptyList()) }
    var probe: PlacementProbe? by remember(request) { mutableStateOf(null) }
    var token by remember(request) { mutableIntStateOf(-1) }

    LaunchedEffect(request, history.size) {
        when (val next = container.placementRead.next(request, history)) {
            is PlacementStep.Complete -> onFinding(PlacementFinding(next.placement, evidenceOf(history)))
            is PlacementStep.Probe -> {
                held.selection = withContext(Dispatchers.Default) { selectionFor(container, next.probe) }
                probe = next.probe
                token = history.size
                container.diag.event(
                    TAG,
                    "probe ${next.probe.ordinal + 1} stage=${next.probe.stage.id.number} " +
                        "'${next.probe.stage.title}' seed=${next.probe.seed} input=$input",
                )
            }
        }
    }

    val current = probe
    if (token < 0 || current == null) {
        Preparing(modifier)
        return
    }
    FocusedSession(
        container = container,
        wiring = wiring,
        restartToken = token,
        onSettled = { outcome -> history = history + PlacementProbeResult(current, outcome) },
        modifier = modifier,
        header = { ProbeBanner(current, onAbandon) },
    )
}

@Composable
private fun ProbeBanner(probe: PlacementProbe, onAbandon: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(BANNER_PADDING),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Trill(MascotMood.Listening, Modifier.size(BANNER_TRILL))
            Column(Modifier.weight(1f).padding(horizontal = BANNER_PADDING)) {
                Text(
                    text = "Where you are — read ${probe.ordinal + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Stage ${probe.stage.id.number}: ${probe.stage.title}. It gets harder " +
                        "while it goes well, and stops when it stops.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onAbandon, modifier = Modifier.testTag("abandon-placement")) {
                Text("Stop")
            }
        }
    }
}

@Composable
private fun Preparing(modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Trill(MascotMood.Curious, Modifier.size(PREPARING_TRILL))
            Text(
                text = "Writing you something to read…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One construction of the request, so a skipped read and a taken one are the same question asked at
 * the same moment. The seed is the clock, which is what makes a read replayable from a report.
 */
internal fun AppContainer.placementRequest(input: Polyphony): PlacementRequest {
    val now = journeyWiring.nowEpochMillis()
    return PlacementRequest(seed = now, input = input, nowEpochMillis = now)
}

private class HeldProbe {
    @Volatile
    var selection: PracticeSelection? = null
}

private fun selectionFor(container: AppContainer, probe: PlacementProbe): PracticeSelection = PracticeSelection(
    score = container.exerciseGenerator.generate(probe.seed, probe.spec),
    targeting = probe.stage.skills,
    summary = "Placement read — stage ${probe.stage.id.number}, ${probe.stage.title.lowercase()}",
)

/**
 * The probes' outcomes, concatenated rather than merged.
 *
 * Merging would be a second tally of the numbers `AdaptivePlacementRead` has already tallied, and
 * the only consumer sums them — so the totals are identical and there is one aggregation, not two.
 */
private fun evidenceOf(history: List<PlacementProbeResult>): List<SkillOutcome> = history
    .mapNotNull { (it.outcome as? JudgeOutcome.Judged)?.result }
    .flatMap { it.skillOutcomes }
    .filter { it.attempts > 0 }

private val BANNER_PADDING = 8.dp
private val BANNER_TRILL = 44.dp
private val PREPARING_TRILL = 120.dp
