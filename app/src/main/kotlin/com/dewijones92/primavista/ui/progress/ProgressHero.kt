package com.dewijones92.primavista.ui.progress

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dewijones92.primavista.practice.SkillState
import com.dewijones92.primavista.theme.LocalNotationColors

/** Average strength across every tracked skill, drawn as an arc that fills once on arrival. */
@Composable
internal fun StrengthHero(states: List<SkillState>, buckets: Map<SkillBucket, List<SkillState>>) {
    val strength = remember(states) { readingStrength(states) }
    val due = buckets[SkillBucket.Due]?.size ?: 0
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(CARD_CORNER),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(CARD_PADDING), verticalAlignment = Alignment.CenterVertically) {
            StrengthArc(strength)
            Spacer(Modifier.width(CARD_PADDING))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (due == 0) "Nothing due" else "$due due to read again",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(TIGHT_GAP))
                Text(
                    text = "Average strength across ${states.size} skill" +
                        "${if (states.size == 1) "" else "s"}, due first and weakest first.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(GAP))
                BucketPills(buckets)
            }
        }
    }
}

@Composable
private fun StrengthArc(strength: Double) {
    var target by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(strength) { target = strength.toFloat().coerceIn(0f, 1f) }
    val sweep by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(ARC_MILLIS, easing = FastOutSlowInEasing),
        label = "strength-arc",
    )
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val tint = meterTint(strength, SOLID_STRENGTH, SHAKY_STRENGTH)
    Box(Modifier.size(ARC_SIZE), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = ARC_STROKE.toPx(), cap = StrokeCap.Round)
            val inset = stroke.width / 2
            val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
            drawArc(track, ARC_START, FULL_SWEEP, false, Offset(inset, inset), arcSize, style = stroke)
            drawArc(tint, ARC_START, FULL_SWEEP * sweep, false, Offset(inset, inset), arcSize, style = stroke)
        }
        Text(
            text = "${percent(strength)}%",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun BucketPills(buckets: Map<SkillBucket, List<SkillState>>) {
    val notation = LocalNotationColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(TIGHT_GAP)) {
        Pill(SkillBucket.Due.title, buckets[SkillBucket.Due]?.size ?: 0, notation.wrongPitch)
        Pill(SkillBucket.Building.title, buckets[SkillBucket.Building]?.size ?: 0, notation.offTime)
        Pill(SkillBucket.Mastered.title, buckets[SkillBucket.Mastered]?.size ?: 0, notation.correct)
    }
}

@Composable
private fun Pill(title: String, count: Int, tint: Color) {
    Row(
        Modifier
            .clip(RoundedCornerShape(PILL_CORNER))
            .background(tint.copy(alpha = PILL_ALPHA))
            .padding(horizontal = PILL_PADDING, vertical = TIGHT_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(PILL_GAP))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val SHAKY_STRENGTH = 0.5
private const val ARC_START = 135f
private const val FULL_SWEEP = 270f
private const val ARC_MILLIS = 900
private const val PILL_ALPHA = 0.22f
private val ARC_SIZE = 92.dp
private val ARC_STROKE = 10.dp
private val CARD_CORNER = 18.dp
private val CARD_PADDING = 14.dp
private val GAP = 10.dp
private val TIGHT_GAP = 4.dp
private val PILL_CORNER = 10.dp
private val PILL_PADDING = 8.dp
private val PILL_GAP = 4.dp
