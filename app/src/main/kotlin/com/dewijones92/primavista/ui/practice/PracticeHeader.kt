package com.dewijones92.primavista.ui.practice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dewijones92.primavista.practice.Stage
import com.dewijones92.primavista.theme.TabularNumeral

/**
 * Before a piece is chosen there is no piece, and the header used to say otherwise: the title fell
 * back to the app's own name and the tempo printed unconditionally, so a session opened on
 * *PrimaVista — 0 bpm*, which reads as a broken piece rather than as one being chosen. Nought beats
 * a minute is not a tempo, for the same reason an unmeasured latency must not read as 0ms.
 */
internal fun headingFor(state: PracticeUiState): String = state.score?.title ?: "Getting a piece ready…"

/** Null while there is nothing to be a tempo *of*. */
internal fun tempoLabelFor(state: PracticeUiState): String? =
    state.tempoBpm.takeIf { state.score != null && it > 0 }?.let { "$it bpm" }

/** Where you are on the path, what you are reading, why, and how far through it you have got. */
@Composable
internal fun PracticeHeader(state: PracticeUiState) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
        state.stage?.let {
            StagePill(it)
            Spacer(Modifier.height(6.dp))
        }
        Row {
            Column(Modifier.weight(1f)) {
                Text(
                    text = headingFor(state),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Reason(state)
            }
            tempoLabelFor(state)?.let {
                Spacer(Modifier.width(10.dp))
                Text(text = it, style = TabularNumeral, color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(8.dp))
        ProgressRail(state)
    }
}

/**
 * The scheduler's own sentence, at two lines rather than one: it was being cut mid-skill, which is
 * the evidence docs/spec.md I5 asks Dewi to be able to see.
 */
@Composable
private fun Reason(state: PracticeUiState) {
    val lines = listOfNotNull(
        state.score?.composer?.takeIf { it.isNotEmpty() },
        state.choiceSummary.takeIf { it.isNotEmpty() },
    )
    lines.forEach { line ->
        Text(
            text = line,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("reason"),
        )
    }
}

/** Absent rather than guessed: an unreadable skill store must not present as "stage 1". */
@Composable
private fun StagePill(stage: Stage) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)
            .testTag("stage"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(STAGE_DOT)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondary),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "STAGE ${stage.id.number}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stage.title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** How far through the piece, taken straight from the position rather than animated separately. */
@Composable
private fun ProgressRail(state: PracticeUiState) {
    val end = state.score?.endsAt?.value ?: 0L
    val fraction = if (end <= 0L) 0f else (state.position.value.toFloat() / end).coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(RAIL_HEIGHT)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.outlineVariant),
    ) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.secondary,
                                MaterialTheme.colorScheme.primary,
                            ),
                        ),
                    ),
            )
        }
    }
}

/** Everything the session decides on Dewi's behalf says so here, in the words it would use to him. */
@Composable
internal fun SessionNotice(notice: String?, onDismiss: () -> Unit) {
    AnimatedVisibility(
        visible = notice != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(NOTICE_CORNER),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clickable(onClick = onDismiss)
                .testTag("notice"),
        ) {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(NOTICE_ICON),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = notice.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

private val STAGE_DOT = 7.dp
private val RAIL_HEIGHT = 5.dp
private val NOTICE_CORNER = 12.dp
private val NOTICE_ICON = 16.dp
