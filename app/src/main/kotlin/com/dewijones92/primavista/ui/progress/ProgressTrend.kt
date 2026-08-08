package com.dewijones92.primavista.ui.progress

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dewijones92.primavista.database.StoredReading
import com.dewijones92.primavista.ui.UnreadableNote

/**
 * Accuracy of each stored session, oldest to newest. One bar per session and nothing smoothed or
 * interpolated: a strip that fills in the gaps between two sessions would be drawing practice that
 * never happened.
 */
@Composable
internal fun TrendStrip(sessions: StoredReading<List<SessionPoint>>?) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(CARD_CORNER),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(CARD_PADDING)) {
            Text(
                text = "Recent sessions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(TIGHT_GAP))
            when {
                sessions == null -> Caption("Reading…")
                sessions is StoredReading.Unreadable -> UnreadableNote(sessions)
                sessions is StoredReading.Readable && sessions.value.isEmpty() -> Caption(
                    "No finished session has been stored yet, so there is no direction to report.",
                )
                sessions is StoredReading.Readable -> {
                    val shown = sessions.value.takeLast(MAX_BARS)
                    SessionBars(shown)
                    Spacer(Modifier.height(GAP))
                    Caption(trendText(trendOf(shown)))
                }
            }
        }
    }
}

@Composable
private fun SessionBars(shown: List<SessionPoint>) {
    Row(
        Modifier.fillMaxWidth().height(STRIP_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(BAR_GAP),
        verticalAlignment = Alignment.Bottom,
    ) {
        shown.forEachIndexed { index, point ->
            SessionBar(point, index, Modifier.weight(1f))
        }
    }
    Spacer(Modifier.height(TIGHT_GAP))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Caption("${shown.size} shown · oldest left")
        Caption("${percent(shown.last().accuracy)}% latest")
    }
    Spacer(Modifier.height(TIGHT_GAP))
    Caption(WHAT_THE_BARS_ARE)
}

@Composable
private fun SessionBar(point: SessionPoint, index: Int, modifier: Modifier) {
    var target by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(point) { target = point.accuracy.toFloat().coerceIn(MIN_VISIBLE, 1f) }
    val height by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(BAR_MILLIS, delayMillis = index * BAR_STAGGER, easing = FastOutSlowInEasing),
        label = "session-bar",
    )
    Box(modifier.fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(height)
                .clip(RoundedCornerShape(topStart = BAR_CORNER, topEnd = BAR_CORNER))
                .background(meterTint(point.accuracy, GOOD_ACCURACY, MIDDLING_ACCURACY)),
        )
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Nothing read yet, said deliberately rather than as a blank screen. */
@Composable
internal fun EmptyProgress(sessions: StoredReading<List<SessionPoint>>?, modifier: Modifier) {
    Column(
        modifier.fillMaxSize().padding(SCREEN_PADDING),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Nothing read yet", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(TIGHT_GAP))
        Text(
            text = "Play a piece through and every note you read will be graded into a reading " +
                "skill here — which clef, which region, which rhythm.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(CARD_PADDING))
        TrendStrip(sessions)
    }
}

/** The stored session keeps no count of unwritten notes. See `.claude/CODE-NOTES.md`. */
private const val WHAT_THE_BARS_ARE =
    "Each bar is the written notes of that session played correctly. Notes you added are not " +
        "stored per session, so a noisy run reads higher here than it did on its results sheet."

private const val MAX_BARS = 12
private const val BAR_MILLIS = 520
private const val BAR_STAGGER = 40
private const val MIN_VISIBLE = 0.03f
private const val GOOD_ACCURACY = 0.85
private const val MIDDLING_ACCURACY = 0.6
private val STRIP_HEIGHT = 72.dp
private val BAR_GAP = 4.dp
private val BAR_CORNER = 4.dp
private val CARD_CORNER = 18.dp
private val CARD_PADDING = 14.dp
private val GAP = 10.dp
private val TIGHT_GAP = 4.dp
private val SCREEN_PADDING = 20.dp
