package com.dewijones92.primavista.ui.onboarding

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The shape every introduction step shares: a headline, something to do, and a way on.
 *
 * [onSkip] is always offered and never worded as a mistake. Somebody who came here to read music
 * should never be held behind a tutorial, and a skip that feels like a wrong answer is a wall with
 * a friendly face on it.
 */
@Composable
internal fun IntroStepScaffold(
    headline: String,
    body: String,
    advanceLabel: String,
    onAdvance: () -> Unit,
    modifier: Modifier = Modifier,
    advanceEnabled: Boolean = true,
    onSkip: (() -> Unit)? = null,
    dots: Pair<Int, Int>? = null,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(scheme.surfaceContainerHigh, scheme.surface)))
            .systemBarsPadding()
            .padding(EDGE),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        dots?.let { (at, total) -> StepDots(at, total) }
        Spacer(Modifier.height(GAP))
        Text(
            text = headline,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = scheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(GAP))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { content() }
        Button(
            onClick = onAdvance,
            enabled = advanceEnabled,
            modifier = Modifier.fillMaxWidth().testTag("intro-advance"),
        ) {
            Text(advanceLabel)
        }
        if (onSkip != null) {
            TextButton(onClick = onSkip, modifier = Modifier.testTag("intro-skip")) {
                Text("Skip the introduction", style = MaterialTheme.typography.labelMedium)
            }
        } else {
            Spacer(Modifier.height(SKIP_SPACE))
        }
    }
}

@Composable
private fun StepDots(at: Int, total: Int) {
    val scheme = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(DOT_GAP), verticalAlignment = Alignment.CenterVertically) {
        repeat(total) { index ->
            Box(
                Modifier
                    .size(if (index == at) DOT_HERE else DOT)
                    .clip(CircleShape)
                    .background(if (index <= at) scheme.primary else scheme.outlineVariant),
            )
        }
    }
}

private val EDGE = 20.dp
private val GAP = 12.dp
private val DOT = 7.dp
private val DOT_HERE = 11.dp
private val DOT_GAP = 6.dp
private val SKIP_SPACE = 40.dp
