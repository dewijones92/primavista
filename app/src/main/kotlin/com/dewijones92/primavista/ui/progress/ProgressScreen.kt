package com.dewijones92.primavista.ui.progress

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dewijones92.primavista.database.StoredReading
import com.dewijones92.primavista.practice.SkillState
import com.dewijones92.primavista.theme.TabularNumeral

/**
 * What the app believes about Dewi's reading, skill by skill, arranged so it answers the three
 * questions worth asking: what is due, what is improving, what is solid.
 *
 * Deliberately not a streak counter or a total-minutes figure. Those measure showing up, which he
 * does not need help with; this measures what he can actually read. Due-and-weak comes first
 * because that is what the scheduler will hand him next, so the screen doubles as an explanation of
 * *why* he is being given what he is being given.
 *
 * Every figure here is derived from stored evidence. Where the store cannot support a claim — a
 * direction of travel with fewer than four finished sessions — the screen says so instead of
 * drawing a line through one point.
 */
@Composable
public fun ProgressScreen(
    states: List<SkillState>,
    sessions: StoredReading<List<SessionPoint>>?,
    nowEpochMillis: Long,
    describe: (SkillState) -> String,
    modifier: Modifier = Modifier,
) {
    if (states.isEmpty()) {
        EmptyProgress(sessions, modifier)
        return
    }

    val buckets = remember(states, nowEpochMillis) {
        ordered(states, nowEpochMillis).groupBy { bucketOf(it, nowEpochMillis) }
    }
    var showSolid by remember { mutableStateOf(false) }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(SCREEN_PADDING),
        verticalArrangement = Arrangement.spacedBy(ROW_GAP),
    ) {
        item { ProgressHeader(states) }
        item { StrengthHero(states, buckets) }
        item { TrendStrip(sessions) }

        listOf(SkillBucket.Due, SkillBucket.Building).forEach { bucket ->
            val rows = buckets[bucket].orEmpty()
            if (rows.isNotEmpty()) {
                item(bucket.name) { BucketHeader(bucket, rows.size) }
                items(rows, key = { "${bucket.name}-${it.tag}" }) { state ->
                    SkillStateRow(state, nowEpochMillis, describe(state))
                }
            }
        }

        val solid = buckets[SkillBucket.Mastered].orEmpty()
        if (solid.isNotEmpty()) {
            item(SkillBucket.Mastered.name) {
                Box(Modifier.clickable { showSolid = !showSolid }.animateContentSize()) {
                    BucketHeader(SkillBucket.Mastered, solid.size, expandable = true, expanded = showSolid)
                }
            }
            if (showSolid) {
                items(solid, key = { "solid-${it.tag}" }) { state ->
                    SkillStateRow(state, nowEpochMillis, describe(state))
                }
            }
        }
    }
}

@Composable
private fun ProgressHeader(states: List<SkillState>) {
    Column {
        Text("Progress", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(2.dp))
        Text(
            text = "${states.size} reading skill${if (states.size == 1) "" else "s"} tracked, " +
                "every one of them from notes this app put in front of you.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BucketHeader(
    bucket: SkillBucket,
    count: Int,
    expandable: Boolean = false,
    expanded: Boolean = false,
) {
    Row(Modifier.fillMaxWidth().padding(top = SECTION_GAP), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "${bucket.title} · $count",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = bucket.blurb,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expandable) {
            Text(
                text = if (expanded) "hide" else "show",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun SkillStateRow(state: SkillState, nowEpochMillis: Long, label: String) {
    val due = state.isDue(nowEpochMillis)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (due) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                text = "${attemptsText(state)} · ${relativeDue(state, nowEpochMillis)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(GAP))
        StrengthMeter(
            value = state.strength,
            tint = meterTint(state.strength, SOLID_STRENGTH, SHAKY_STRENGTH),
            modifier = Modifier.width(METER_WIDTH),
        )
        Spacer(Modifier.width(GAP))
        Text(
            text = "${percent(state.strength)}%",
            style = TabularNumeral,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val SHAKY_STRENGTH = 0.5
private val SCREEN_PADDING = 16.dp
private val SECTION_GAP = 8.dp
private val ROW_GAP = 10.dp
private val GAP = 10.dp
private val METER_WIDTH = 80.dp
