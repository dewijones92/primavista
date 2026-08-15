package com.dewijones92.primavista.ui.journey

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dewijones92.primavista.database.DatabaseOpening
import com.dewijones92.primavista.database.StoredReading
import com.dewijones92.primavista.di.AppContainer
import com.dewijones92.primavista.di.JourneyReading
import com.dewijones92.primavista.practice.Stage
import com.dewijones92.primavista.ui.UnreadablePanel
import kotlinx.coroutines.launch

/**
 * Home. Where Dewi is on the path, and the one tap that starts the next thing.
 *
 * Sessions started here run **inside** this route rather than throwing him at a tab, so finishing a
 * rung puts him back where he chose it — which is the whole reason the path feels like a place.
 */
@Composable
public fun JourneyRoute(
    container: AppContainer,
    onIntroduction: () -> Unit,
    onPlacement: () -> Unit,
    onDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var practising: Stage? by remember { mutableStateOf(null) }
    var refreshToken by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    val stage = practising
    if (stage != null) {
        BackHandler { practising = null }
        StagePractice(
            container = container,
            stage = stage,
            onBack = {
                practising = null
                refreshToken++
            },
            modifier = modifier,
        )
        return
    }

    when (val opening = container.databaseOpening) {
        is DatabaseOpening.Unreadable -> UnreadablePanel(PATH, opening.reason, modifier)
        is DatabaseOpening.Opened -> {
            val reading by produceState<StoredReading<JourneyReading>?>(null, refreshToken) {
                value = container.journeyWiring.read()
            }
            when (val settled = reading) {
                null -> Waiting(modifier)
                is StoredReading.Unreadable -> UnreadablePanel(settled.what, settled.reason, modifier)
                is StoredReading.Readable -> JourneyScreen(
                    state = remember(settled) { pathOf(container.curriculum, settled.value) },
                    refusals = settled.value.refusals,
                    actions = PathActions(
                        onStage = { practising = it },
                        onUseKeyboard = {
                            scope.launch {
                                container.journeyWiring.chooseTappedKeyboard()
                                refreshToken++
                            }
                        },
                        onPlacement = onPlacement,
                        onIntroduction = onIntroduction,
                        onDiagnostics = onDiagnostics,
                    ),
                    modifier = modifier,
                )
            }
        }
    }
}

@Composable
private fun Waiting(modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column {
            Text(
                text = "Reading the path…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val PATH = "the path"
