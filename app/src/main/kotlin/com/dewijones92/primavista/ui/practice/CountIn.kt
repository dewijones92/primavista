package com.dewijones92.primavista.ui.practice

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dewijones92.primavista.theme.CountInNumeral
import com.dewijones92.primavista.ui.mascot.MascotMood
import com.dewijones92.primavista.ui.mascot.Trill

/**
 * Trill counts you in: she dips on every beat, and goes still on the last one because you are next.
 *
 * A count-in that can only be heard is useless with the volume down, and one that shows a static
 * number gives no sense of pace. It sits **above** the music rather than over it — those beats are
 * exactly when a sight-reader takes in the first bar. See `.claude/CODE-NOTES.md`.
 */
@Composable
internal fun CountInOverlay(beatsRemaining: Int, totalBeats: Int) {
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(beatsRemaining) {
        if (beatsRemaining > 0) {
            pulse.snapTo(0f)
            pulse.animateTo(1f, tween(COUNT_IN_PULSE_MS, easing = LinearOutSlowInEasing))
        }
    }
    if (beatsRemaining <= 0) return

    val last = beatsRemaining == 1
    val progress = pulse.value
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Card(
            shape = RoundedCornerShape(CARD_CORNER),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = SCRIM_ALPHA),
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = CARD_ELEVATION),
            modifier = Modifier.testTag("count-in"),
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Counter(beatsRemaining, last, progress)
                Spacer(Modifier.width(10.dp))
                Trill(
                    mood = if (last) MascotMood.Listening else MascotMood.Curious,
                    modifier = Modifier
                        .size(BIRD_SIZE)
                        .graphicsLayer { translationY = BIRD_DIP * (1f - progress) },
                )
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = if (last) "Bar 1 on the next beat" else "Counting you in",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    BeatDots(totalBeats, beatsRemaining)
                }
            }
        }
    }
}

/** The beat, as a number inside its own emptying ring. Violet on the last one — see CODE-NOTES. */
@Composable
private fun Counter(beatsRemaining: Int, last: Boolean, progress: Float) {
    val accent = if (last) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
    val ring = MaterialTheme.colorScheme.surfaceContainerLowest
    Box(
        Modifier
            .size(COUNTER_SIZE)
            .graphicsLayer {
                val scale = MAX_SCALE - (MAX_SCALE - MIN_SCALE) * progress
                scaleX = scale
                scaleY = scale
            }
            .drawBehind { drawCounter(ring, accent, 1f - progress, last) },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = beatsRemaining.toString(), style = CountInNumeral, color = accent)
    }
}

private fun DrawScope.drawCounter(ring: Color, accent: Color, sweep: Float, last: Boolean) {
    val stroke = STROKE_FRACTION * size.minDimension
    val radius = (size.minDimension - stroke) / 2f
    drawCircle(color = ring, radius = radius)
    if (last) drawCircle(color = accent.copy(alpha = DISC_ALPHA), radius = radius)
    drawArc(
        color = accent,
        startAngle = ARC_START,
        sweepAngle = if (last) FULL_TURN_DEGREES else FULL_TURN_DEGREES * sweep,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2, radius * 2),
        style = Stroke(width = stroke, cap = StrokeCap.Round),
    )
}

/** How many beats are left, at a glance, without reading the digit. */
@Composable
private fun BeatDots(totalBeats: Int, beatsRemaining: Int) {
    val total = maxOf(totalBeats, beatsRemaining)
    if (total <= 0) return
    val elapsed = total - beatsRemaining
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(total) { index ->
            val colour = when {
                index == elapsed -> MaterialTheme.colorScheme.primary
                index < elapsed -> MaterialTheme.colorScheme.primary.copy(alpha = SPENT_ALPHA)
                else -> MaterialTheme.colorScheme.outlineVariant
            }
            Box(
                Modifier
                    .size(if (index == elapsed) DOT_NOW else DOT)
                    .clip(CircleShape)
                    .background(colour),
            )
        }
    }
}

private const val COUNT_IN_PULSE_MS = 380
private const val MAX_SCALE = 1.18f
private const val MIN_SCALE = 0.92f
private const val STROKE_FRACTION = 0.07f
private const val DISC_ALPHA = 0.22f
private const val SCRIM_ALPHA = 0.97f
private const val SPENT_ALPHA = 0.35f
private const val ARC_START = -90f
private const val FULL_TURN_DEGREES = 360f
private const val BIRD_DIP = 10f

private val COUNTER_SIZE = 64.dp
private val BIRD_SIZE = 44.dp
private val CARD_CORNER = 28.dp
private val CARD_ELEVATION = 8.dp
private val DOT = 7.dp
private val DOT_NOW = 11.dp
