package com.dewijones92.primavista.ui.journey

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dewijones92.primavista.ui.mascot.Trill

/**
 * The first thing the app says every time it opens: who is here, how long you have been at it, and
 * what the next thing is.
 *
 * The streak is stated in days and never as something at risk, because this is an app one person
 * uses alone after work and motivation by shame is both effective and unpleasant (docs/journey.md).
 */
@Composable
internal fun PathHero(state: PathState, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(HERO_CORNER))
            .background(Brush.linearGradient(listOf(scheme.surfaceContainerHigh, scheme.surfaceContainerLow)))
            .padding(HERO_PADDING),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Trill(pathMood(state.streak), Modifier.size(HERO_TRILL))
            Spacer(Modifier.width(HERO_GAP))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Stage ${state.current.id.number} — ${state.current.title}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                )
                Spacer(Modifier.height(HERO_LINE_GAP))
                Text(
                    text = streakWords(state.streak),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                if (state.streak.daysPractised > 0) {
                    Spacer(Modifier.height(HERO_LINE_GAP))
                    DayRibbon(state.streak.currentDays, state.streak.bestDays)
                }
            }
        }
    }
}

/**
 * Days as pips rather than a number with a flame on it. Filled means read, and the row stops at
 * seven because a longer one turns into a chart nobody asked for.
 */
@Composable
private fun DayRibbon(currentDays: Int, bestDays: Int) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PIP_GAP)) {
        repeat(RIBBON_DAYS) { index ->
            val lit = index < currentDays.coerceAtMost(RIBBON_DAYS)
            Box(
                Modifier
                    .size(if (lit) PIP_LIT else PIP_DIM)
                    .clip(CircleShape)
                    .background(if (lit) scheme.primary else scheme.outlineVariant),
            )
        }
        if (bestDays > 0) {
            Spacer(Modifier.width(PIP_GAP))
            Text(
                text = "best $bestDays",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

private const val RIBBON_DAYS = 7
private val HERO_CORNER = 26.dp
private val HERO_PADDING = 16.dp
private val HERO_TRILL = 84.dp
private val HERO_GAP = 14.dp
private val HERO_LINE_GAP = 6.dp
private val PIP_GAP = 6.dp
private val PIP_LIT = 12.dp
private val PIP_DIM = 8.dp
