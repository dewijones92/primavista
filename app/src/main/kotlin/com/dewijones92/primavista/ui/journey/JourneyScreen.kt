package com.dewijones92.primavista.ui.journey

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dewijones92.primavista.database.StoredReading
import com.dewijones92.primavista.ui.UnreadableNote

/**
 * The whole journey on one scroll: where you are, what is behind you, what each rung teaches.
 *
 * The expanded rung starts as the one you are on, so opening the app already shows the next thing
 * and its button. Everything else is one tap away and nothing is hidden behind a lock.
 */
@Composable
internal fun JourneyScreen(
    state: PathState,
    refusals: List<StoredReading.Unreadable>,
    actions: PathActions,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(state.current.id.number) { mutableStateOf(state.current.id.number) }
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("path"),
        contentPadding = PaddingValues(SIDE_PADDING, TOP_PADDING, SIDE_PADDING, BOTTOM_PADDING),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            Column {
                PathHero(state)
                refusals.forEach {
                    Spacer(Modifier.height(GAP))
                    UnreadableNote(it)
                }
                Spacer(Modifier.height(GAP))
                if (!state.everPlaced) PlacementOffer(actions.onPlacement)
                Spacer(Modifier.height(GAP))
            }
        }
        items(state.rows, key = { it.stage.id.number }) { row ->
            StageNode(
                row = row,
                expanded = expanded == row.stage.id.number,
                first = row.stage.id.number == state.rows.first().stage.id.number,
                last = row.stage.id.number == state.rows.last().stage.id.number,
                onSelect = { expanded = if (expanded == row.stage.id.number) 0 else row.stage.id.number },
                onStart = { actions.onStage(row.stage) },
                onUseKeyboard = actions.onUseKeyboard,
            )
        }
        item { PathFooter(actions) }
    }
}

/** Offered once, and only until a placement has been recorded either way. */
@Composable
private fun PlacementOffer(onPlacement: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = "Already read a bit? A two-minute check will start you where you actually are.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onPlacement, modifier = Modifier.testTag("take-placement")) {
            Text("Check where I am")
        }
    }
}

/**
 * The honest line, at the bottom where it belongs, and the way to the diagnostics report — which is
 * a tool for when something is wrong rather than a place to visit, so it is not a tab.
 */
@Composable
private fun PathFooter(actions: PathActions) {
    Column(
        Modifier.fillMaxWidth().padding(top = FOOTER_TOP),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "A rung is passed when its reading is solid — never for turning up.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(GAP))
        TextButton(onClick = actions.onIntroduction, modifier = Modifier.testTag("replay-intro")) {
            Text("Meet Trill again", style = MaterialTheme.typography.labelMedium)
        }
        TextButton(onClick = actions.onDiagnostics, modifier = Modifier.testTag("open-diagnostics")) {
            Text("Something wrong? Open diagnostics", style = MaterialTheme.typography.labelSmall)
        }
    }
}

private val SIDE_PADDING = 16.dp
private val TOP_PADDING = 12.dp
private val BOTTOM_PADDING = 28.dp
private val GAP = 10.dp
private val FOOTER_TOP = 18.dp
