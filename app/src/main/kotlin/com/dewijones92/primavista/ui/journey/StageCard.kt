package com.dewijones92.primavista.ui.journey

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * What a rung teaches, whether it is behind you, and the button that starts it.
 *
 * A rung ahead is startable rather than locked. Locking would be the Duolingo instinct and the wrong
 * one here: reading harder music early is allowed, it simply will not pass a stage until the reading
 * is solid, and that rule is stated on the card rather than enforced by a padlock.
 */
@Composable
internal fun StageCard(
    row: PathRow,
    onStart: () -> Unit,
    onUseKeyboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CARD_CORNER))
            .background(scheme.surfaceContainerLow)
            .padding(CARD_PADDING),
    ) {
        Text(row.stage.blurb, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
        Spacer(Modifier.height(GAP))
        SkillMeter(row)
        row.passedOnEpochMillis?.let {
            Spacer(Modifier.height(GAP))
            Text(
                text = "First passed ${onDay(it)}.",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }
        if (row.unreachableByInput) {
            Spacer(Modifier.height(GAP))
            KeyboardWall(onUseKeyboard)
        }
        Spacer(Modifier.height(CARD_PADDING))
        Button(onClick = onStart, modifier = Modifier.testTag("start-${row.stage.id.number}")) {
            Text(if (row.standing == Standing.Passed) "Read it again" else "Read this")
        }
    }
}

/**
 * Solid skills, not sessions. The bar can go **down**, because a skill that lapses is not solid.
 *
 * The count is not repeated here: the node's own subtitle already says it, and saying it twice on
 * one card made the card look like it was arguing with itself.
 */
@Composable
private fun SkillMeter(row: PathRow) {
    val scheme = MaterialTheme.colorScheme
    val fraction = if (row.totalSkills == 0) 0f else row.solidSkills.toFloat() / row.totalSkills
    Box(
        Modifier
            .fillMaxWidth()
            .height(METER)
            .clip(RoundedCornerShape(METER))
            .background(scheme.outlineVariant),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(METER)
                .clip(RoundedCornerShape(METER))
                .background(scheme.primary),
        )
    }
}

/**
 * docs/spec.md I3, applied to progress: the microphone genuinely cannot hear two hands at once, so
 * a mic reader can never be credited for this rung. Saying so is the difference between an honest
 * refusal and a wall with no sign on it.
 */
@Composable
private fun KeyboardWall(onUseKeyboard: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(WALL_CORNER))
            .background(scheme.secondaryContainer)
            .padding(WALL_PADDING),
        verticalArrangement = Arrangement.spacedBy(GAP),
    ) {
        Text(
            text = "This rung needs the tapped keyboard",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSecondaryContainer,
        )
        Text(
            text = "A microphone hears one line at a time, so it can never tell whether both hands " +
                "were right. Rather than guess, the app leaves this one unmarked — so you can read " +
                "it, but it will not pass while PLAY IT is listening.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSecondaryContainer,
        )
        OutlinedButton(onClick = onUseKeyboard, modifier = Modifier.testTag("use-keyboard")) {
            Text("Use the tapped keyboard")
        }
    }
}

private fun onDay(epochMillis: Long): String =
    DAY.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

private val CARD_CORNER = 18.dp
private val CARD_PADDING = 14.dp
private val WALL_CORNER = 14.dp
private val WALL_PADDING = 12.dp
private val GAP = 8.dp
private val METER = 8.dp
