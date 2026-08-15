package com.dewijones92.primavista.ui.mascot

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The two shapes Trill takes when a screen has something to say, held once so every screen says it
 * the same way.
 *
 * [TrillAside] is the inline form — a small bird beside one sentence, for a refusal or a caveat
 * inside a card that has other things to say. [TrillPanel] is the whole-screen form, for an empty
 * screen, where she *is* the content until there is some.
 *
 * Neither of them decides a mood. The caller does, from something it can actually prove.
 */
@Composable
public fun TrillAside(
    mood: MascotMood,
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    size: Dp = ASIDE_BIRD,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Trill(mood, Modifier.size(size))
        Spacer(Modifier.width(GAP))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = color.takeOrElse { MaterialTheme.colorScheme.onSurfaceVariant },
            modifier = Modifier.weight(1f),
        )
    }
}

/** An empty screen, with Trill as the reason it is worth staying on. */
@Composable
public fun TrillPanel(
    mood: MascotMood,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = PANEL_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Arriving { Trill(mood, Modifier.size(PANEL_BIRD)) }
        Spacer(Modifier.height(GAP))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(TIGHT_GAP))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** She fades up on arrival rather than appearing mid-blink, which reads as a rendering glitch. */
@Composable
private fun Arriving(content: @Composable () -> Unit) {
    var shown by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { shown = 1f }
    val fade by animateFloatAsState(shown, tween(ARRIVE_MILLIS, easing = FastOutSlowInEasing), label = "arrive")
    Column(Modifier.alpha(fade)) { content() }
}

private const val ARRIVE_MILLIS = 520
private val ASIDE_BIRD = 44.dp
private val PANEL_BIRD = 132.dp
private val PANEL_PADDING = 8.dp
private val GAP = 10.dp
private val TIGHT_GAP = 4.dp
