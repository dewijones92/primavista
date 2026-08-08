package com.dewijones92.primavista.ui.progress

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dewijones92.primavista.theme.LocalNotationColors

/**
 * One bar, used by both the Progress screen and the results sheet, because a skill's strength and
 * a skill's accuracy in one session are the same quantity drawn the same way — two copies would
 * eventually disagree about what "good" looks like.
 *
 * The fill grows from empty on first appearance. That is deliberate motion rather than decoration:
 * it draws the eye down the list in order, and it is over in [FILL_MILLIS] so it cannot delay the
 * number it is illustrating, which is already on screen in full.
 */
@Composable
public fun StrengthMeter(
    value: Double,
    tint: Color,
    modifier: Modifier = Modifier,
    delayMillis: Int = 0,
) {
    var target by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(value) { target = value.toFloat().coerceIn(0f, 1f) }
    val fill by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(FILL_MILLIS, delayMillis = delayMillis, easing = FastOutSlowInEasing),
        label = "meter",
    )

    Box(
        modifier
            .height(METER_HEIGHT)
            .clip(RoundedCornerShape(METER_HEIGHT))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fill)
                .fillMaxHeight()
                .clip(RoundedCornerShape(METER_HEIGHT))
                .background(tint),
        )
    }
}

/** Green, amber, red — the same three meanings the noteheads use, so the vocabulary is one. */
@Composable
public fun meterTint(value: Double, goodAt: Double, middlingAt: Double): Color {
    val notation = LocalNotationColors.current
    return when {
        value >= goodAt -> notation.correct
        value >= middlingAt -> notation.offTime
        else -> notation.wrongPitch
    }
}

private const val FILL_MILLIS = 620
private val METER_HEIGHT = 8.dp
