package com.dewijones92.primavista.ui.results

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dewijones92.primavista.practice.SessionResult
import com.dewijones92.primavista.practice.SkillOutcome
import com.dewijones92.primavista.theme.LocalNotationColors
import com.dewijones92.primavista.theme.TabularNumeral
import com.dewijones92.primavista.ui.progress.describe

/**
 * What just happened, and — the part that makes it a trainer — *which reading skills* let you down.
 *
 * A single accuracy percentage is the easy thing to show and the least useful: "68%" gives Dewi
 * nothing to practise. "Bass clef below the staff: 3 of 9" tells him exactly what to do next, and
 * is only possible because verdicts are stored per note with their skills attached rather than
 * summarised (docs/spec.md I5).
 */
@Composable
public fun ResultsSheet(
    result: SessionResult,
    onPractiseWeakest: () -> Unit,
    onAgain: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val notation = LocalNotationColors.current
    val weakest = result.skillOutcomes
        .filter { it.attempts > 0 }
        .sortedBy { it.accuracy }
        .take(WEAKEST_SHOWN)

    Column(modifier.padding(20.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "${(result.accuracy * PERCENT_SCALE).toInt()}%",
                style = MaterialTheme.typography.displayMedium,
                color = accuracyColor(result.accuracy, notation.correct, notation.offTime, notation.wrongPitch),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.padding(bottom = 6.dp)) {
                Text(
                    text = "${result.correct} of ${result.notesExpected} notes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Shown only when they happened, because a performance that plays every written note
                // correctly plus twenty that were not written is not a clean one — and by accuracy
                // alone it scores 100%.
                if (result.extras > 0) {
                    Text(
                        text = "+${result.extras} not in the music",
                        style = MaterialTheme.typography.labelMedium,
                        color = notation.wrongPitch,
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        if (weakest.isEmpty()) {
            Text(
                text = "No skills were exercised — nothing to report.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = "What held you up",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(weakest) { outcome -> SkillRow(outcome) }
            }
        }

        Spacer(Modifier.height(22.dp))

        Button(
            onClick = onPractiseWeakest,
            enabled = weakest.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Drill the weakest")
        }
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
        ) {
            TextButton(onClick = onAgain) { Text("Again") }
            TextButton(onClick = onDone) { Text("Done") }
        }
    }
}

@Composable
private fun SkillRow(outcome: SkillOutcome) {
    val notation = LocalNotationColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = describe(outcome.tag),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .width(BAR_WIDTH)
                .height(BAR_HEIGHT)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(outcome.accuracy.toFloat())
                    .height(BAR_HEIGHT)
                    .clip(RoundedCornerShape(50))
                    .background(
                        accuracyColor(
                            outcome.accuracy,
                            notation.correct,
                            notation.offTime,
                            notation.wrongPitch,
                        ),
                    ),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = "${outcome.cleanAttempts}/${outcome.attempts}",
            style = TabularNumeral,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun accuracyColor(accuracy: Double, good: Color, middling: Color, bad: Color): Color = when {
    accuracy >= GOOD_THRESHOLD -> good
    accuracy >= MIDDLING_THRESHOLD -> middling
    else -> bad
}

private const val PERCENT_SCALE = 100
private const val WEAKEST_SHOWN = 6
private const val GOOD_THRESHOLD = 0.85
private const val MIDDLING_THRESHOLD = 0.6
private val BAR_WIDTH = 84.dp
private val BAR_HEIGHT = 8.dp
