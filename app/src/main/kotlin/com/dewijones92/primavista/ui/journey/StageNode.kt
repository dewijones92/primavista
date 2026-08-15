package com.dewijones92.primavista.ui.journey

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dewijones92.primavista.ui.mascot.MascotMood
import com.dewijones92.primavista.ui.mascot.Trill

/**
 * One rung: a numbered disc on a rail, with the stage's name beside it and its card underneath when
 * it is the one you are looking at.
 *
 * A passed disc is filled and ticked, and it means the curriculum said its skills were solid — never
 * that enough sessions happened. That is the whole difference between this path and a progress bar.
 */
@Composable
internal fun StageNode(
    row: PathRow,
    expanded: Boolean,
    first: Boolean,
    last: Boolean,
    onSelect: () -> Unit,
    onStart: () -> Unit,
    onUseKeyboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onSelect).testTag("stage-${row.stage.id.number}")) {
            Rail(row, first, last)
            Spacer(Modifier.width(RAIL_GAP))
            Column(Modifier.weight(1f).padding(vertical = TITLE_PADDING)) {
                Text(
                    text = row.stage.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (row.standing == Standing.Current) FontWeight.Bold else FontWeight.Medium,
                    color = titleInk(row.standing),
                )
                Text(
                    text = standingWords(row),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            StageCard(
                row = row,
                onStart = onStart,
                onUseKeyboard = onUseKeyboard,
                modifier = Modifier.padding(start = RAIL_WIDTH + RAIL_GAP, bottom = CARD_GAP),
            )
        }
    }
}

/** The rail is drawn behind the disc so the line passes through it, like a stem through a notehead. */
@Composable
private fun Rail(row: PathRow, first: Boolean, last: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val behind = if (row.standing == Standing.Ahead) scheme.outlineVariant else scheme.primary
    Box(
        Modifier
            .width(RAIL_WIDTH)
            .height(DISC + DISC_MARGIN * 2)
            .drawBehind {
                val x = size.width / 2f
                val weight = RAIL_STROKE.toPx()
                if (!first) drawLine(behind, Offset(x, 0f), Offset(x, size.height / 2f), weight, StrokeCap.Round)
                if (!last) {
                    drawLine(behind, Offset(x, size.height / 2f), Offset(x, size.height), weight, StrokeCap.Round)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Disc(row)
    }
}

@Composable
private fun Disc(row: PathRow) {
    val scheme = MaterialTheme.colorScheme
    val halo by rememberInfiniteTransition(label = "rung").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(HALO_MILLIS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "halo",
    )
    val fill = when (row.standing) {
        Standing.Passed -> scheme.primary
        Standing.Current -> scheme.primaryContainer
        Standing.Ahead -> scheme.surfaceContainerHigh
    }
    Box(Modifier.size(DISC), contentAlignment = Alignment.Center) {
        if (row.standing == Standing.Current) {
            Box(
                Modifier
                    .size(DISC + HALO_SPREAD * halo)
                    .clip(CircleShape)
                    .background(scheme.primary.copy(alpha = HALO_ALPHA * (1f - halo))),
            )
        }
        Box(
            Modifier
                .size(DISC)
                .clip(CircleShape)
                .background(fill)
                .border(DISC_EDGE, edgeInk(row.standing), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            DiscFace(row)
        }
    }
}

@Composable
private fun DiscFace(row: PathRow) {
    when (row.standing) {
        Standing.Passed -> Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = "passed",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(TICK),
        )
        Standing.Current -> Trill(MascotMood.Idle, Modifier.size(PERCHED).offset(y = PERCH_LIFT))
        Standing.Ahead -> Text(
            text = "${row.stage.id.number}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun titleInk(standing: Standing): Color = when (standing) {
    Standing.Ahead -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.onSurface
}

@Composable
private fun edgeInk(standing: Standing): Color = when (standing) {
    Standing.Ahead -> MaterialTheme.colorScheme.outlineVariant
    else -> MaterialTheme.colorScheme.primary
}

private const val HALO_MILLIS = 1800
private const val HALO_ALPHA = 0.35f
private val DISC = 56.dp
private val DISC_MARGIN = 6.dp
private val DISC_EDGE = 2.dp
private val HALO_SPREAD = 18.dp
private val TICK = 26.dp

/** Inside the disc rather than spilling over its rim, which reads as a badge she is stuck to. */
private val PERCHED = 42.dp
private val PERCH_LIFT = 2.dp
private val RAIL_WIDTH = 68.dp
private val RAIL_GAP = 4.dp
private val RAIL_STROKE = 4.dp
private val TITLE_PADDING = 14.dp
private val CARD_GAP = 10.dp
