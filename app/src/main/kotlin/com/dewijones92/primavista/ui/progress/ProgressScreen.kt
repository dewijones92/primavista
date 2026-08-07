package com.dewijones92.primavista.ui.progress

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dewijones92.primavista.practice.SkillState
import com.dewijones92.primavista.theme.LocalNotationColors
import com.dewijones92.primavista.theme.TabularNumeral

/**
 * What the app believes about Dewi's reading, skill by skill.
 *
 * Deliberately not a streak counter or a total-minutes figure. Those measure showing up, which he
 * does not need help with; this measures what he can actually read, which is the thing he asked to
 * get fluent at. Due-and-weak comes first because that is what the scheduler will hand him next, so
 * the screen doubles as an explanation of *why* he is being given what he is being given.
 */
@Composable
public fun ProgressScreen(
    states: List<SkillState>,
    nowEpochMillis: Long,
    describe: (SkillState) -> String,
    modifier: Modifier = Modifier,
) {
    if (states.isEmpty()) {
        EmptyProgress(modifier)
        return
    }

    val ordered = states.sortedWith(
        compareByDescending<SkillState> { it.isDue(nowEpochMillis) }.thenBy { it.strength },
    )
    val solid = states.count { it.strength >= SOLID_THRESHOLD }

    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text("Progress", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(2.dp))
        Text(
            text = "$solid of ${states.size} reading skills solid",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(ordered) { state -> SkillStateRow(state, nowEpochMillis, describe(state)) }
        }
    }
}

@Composable
private fun SkillStateRow(state: SkillState, nowEpochMillis: Long, label: String) {
    val notation = LocalNotationColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (state.isDue(nowEpochMillis)) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                text = buildString {
                    append("${state.attempts} attempt${if (state.attempts == 1) "" else "s"}")
                    if (state.lapses > 0) append(" · ${state.lapses} lapse${if (state.lapses == 1) "" else "s"}")
                    if (state.isDue(nowEpochMillis)) append(" · due")
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .width(BAR_WIDTH)
                .height(BAR_HEIGHT)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(state.strength.toFloat())
                    .height(BAR_HEIGHT)
                    .clip(RoundedCornerShape(50))
                    .background(
                        when {
                            state.strength >= SOLID_THRESHOLD -> notation.correct
                            state.strength >= SHAKY_THRESHOLD -> notation.offTime
                            else -> notation.wrongPitch
                        },
                    ),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = "${(state.strength * PERCENT_SCALE).toInt()}%",
            style = TabularNumeral,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyProgress(modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Nothing read yet", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Play something and this fills in.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val SOLID_THRESHOLD = 0.8
private const val SHAKY_THRESHOLD = 0.5
private const val PERCENT_SCALE = 100
private val BAR_WIDTH = 80.dp
private val BAR_HEIGHT = 8.dp
