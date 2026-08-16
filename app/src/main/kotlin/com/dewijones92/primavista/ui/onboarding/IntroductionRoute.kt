package com.dewijones92.primavista.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dewijones92.primavista.database.PlacementOutcome
import com.dewijones92.primavista.di.AppContainer
import com.dewijones92.primavista.di.InputMode
import com.dewijones92.primavista.practice.Placement
import com.dewijones92.primavista.practice.Stage
import com.dewijones92.primavista.practice.StageId
import com.dewijones92.primavista.ui.mascot.MascotMood
import com.dewijones92.primavista.ui.mascot.Trill
import kotlinx.coroutines.launch

private const val TAG = "intro"

/** How many dotted steps the introduction shows before the choice at the end. */
internal const val INTRO_STEPS: Int = 3

/** Where the introduction begins. The path can re-enter it at either door. */
public enum class IntroEntry { Beginning, PlacementOnly }

private enum class IntroStep { Meet, Height, FirstNote, Offer, Placement, Placed }

/**
 * The first minute of the app, and the only thing standing between a fresh install and reading.
 *
 * It teaches exactly one idea — height on the stave is pitch — gives a win that was actually earned,
 * and then asks the only question worth asking: can you already read a bit? Both answers are one
 * tap and neither is a wrong one (docs/journey.md).
 *
 * Every exit records a placement row, which is what stops it running twice. See
 * `.claude/CODE-NOTES.md`.
 */
@Composable
public fun IntroductionRoute(
    container: AppContainer,
    entry: IntroEntry,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by remember(entry) {
        mutableStateOf(if (entry == IntroEntry.PlacementOnly) IntroStep.Placement else IntroStep.Meet)
    }
    var placed: Placement? by remember { mutableStateOf(null) }
    var landedOn: Stage? by remember { mutableStateOf(null) }
    val scope = rememberCoroutineScope()
    val input by produceState(InputMode.Tap, container) { value = container.currentInput() }

    // dewidebug: which step is on screen, and why the next one arrived. A report that says only
    // "the introduction ended" cannot tell a step that advanced from one that was declined.
    LaunchedEffect(step) { container.diag.event(TAG, "dewidebug step=$step entry=$entry input=$input") }

    val decline: () -> Unit = {
        scope.launch {
            container.diag.event(TAG, "introduction left without a placement read; starting at the first rung")
            container.journeyWiring.settle(
                placement = container.placementRead.skipped(container.placementRequest(input.polyphony)),
                evidence = emptyList(),
                outcome = PlacementOutcome.Skipped,
            )
            onDone()
        }
    }

    // The introduction is the whole window, so it owns its own ground and its own insets. Inside the
    // shell a Scaffold does both; here nothing does, and the bare window is dark ink under a light
    // theme — which is exactly how it looked before this Surface existed.
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        when (step) {
            IntroStep.Meet -> MeetStep(onAdvance = { step = IntroStep.Height }, onSkip = decline)
            IntroStep.Height -> HeightIsPitchStep(
                tonePlayer = container.tonePlayer,
                diag = container.diag,
                onAdvance = { step = IntroStep.FirstNote },
                onSkip = decline,
            )
            IntroStep.FirstNote -> FirstNoteStep(
                tonePlayer = container.tonePlayer,
                tapSource = container.tapSource,
                diag = container.diag,
                onAdvance = { step = IntroStep.Offer },
                onSkip = decline,
            )
            IntroStep.Offer -> OfferStep(onPlacement = { step = IntroStep.Placement }, onBeginning = decline)
            IntroStep.Placement -> PlacementRun(
                container = container,
                input = input.polyphony,
                onFinding = { finding ->
                    scope.launch {
                        container.journeyWiring.settle(finding.placement, finding.evidence, PlacementOutcome.Completed)
                        placed = finding.placement
                        landedOn = container.standingStage()
                        step = IntroStep.Placed
                    }
                },
                onAbandon = decline,
                modifier = Modifier.systemBarsPadding(),
            )
            IntroStep.Placed -> PlacedStep(placed, landedOn, onDone)
        }
    }
}

@Composable
private fun MeetStep(onAdvance: () -> Unit, onSkip: () -> Unit) {
    IntroStepScaffold(
        headline = "Hello. I'm Trill.",
        body = "I live on the stave — the five lines music is written on. Stay a minute and I'll show " +
            "you the one thing everything else is built from.",
        advanceLabel = "Hello, Trill",
        onAdvance = onAdvance,
        onSkip = onSkip,
        dots = 0 to INTRO_STEPS,
    ) {
        Trill(MascotMood.Idle, Modifier.size(HERO))
    }
}

@Composable
private fun OfferStep(onPlacement: () -> Unit, onBeginning: () -> Unit) {
    IntroStepScaffold(
        headline = "Can you already read a bit?",
        body = "If you can, a two-minute read will start you where you actually are instead of at " +
            "the beginning. If you can't, the beginning is exactly the right place.",
        advanceLabel = "Yes — check where I am",
        onAdvance = onPlacement,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Trill(MascotMood.Curious, Modifier.size(HERO))
            Spacer(Modifier.height(GAP))
            OutlinedButton(
                onClick = onBeginning,
                modifier = Modifier.fillMaxWidth().testTag("start-at-the-beginning"),
            ) {
                Text("Start at the first rung")
            }
            Text(
                text = "You can take the check any time from the path.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PlacedStep(placement: Placement?, stage: Stage?, onDone: () -> Unit) {
    IntroStepScaffold(
        headline = stage?.let { "You start at stage ${it.id.number}" } ?: "That's your read done",
        body = placedWords(placement, stage),
        advanceLabel = "Show me the path",
        onAdvance = onDone,
    ) {
        Trill(placedMood(stage), Modifier.size(HERO))
    }
}

/**
 * Impressed is for a read that actually climbed, and nothing else.
 *
 * Having *measured* four skills is not a result — a read where every note was missed measures four
 * skills too, and the first version of this sparkled at it. Landing above the first rung is the one
 * thing here that had to be earned by reading.
 */
private fun placedMood(stage: Stage?): MascotMood =
    if ((stage?.id?.number ?: StageId.FIRST) > StageId.FIRST) MascotMood.Impressed else MascotMood.Idle

/** States what was measured and what it did **not** settle. An over-generous read corrects itself. */
private fun placedWords(placement: Placement?, stage: Stage?): String {
    val probes = placement?.probesTaken ?: 0
    val seeded = placement?.states?.size ?: 0
    val where = stage?.let { "\"${it.title}\" — ${it.blurb}" } ?: "the first rung."
    return "$probes read${if (probes == 1) "" else "s"}, $seeded reading skill" +
        "${if (seeded == 1) "" else "s"} measured. That puts you on $where\n\n" +
        "Nothing here is fixed: what you read next is chosen from how you actually do."
}

private val HERO = 260.dp
private val GAP = 10.dp
