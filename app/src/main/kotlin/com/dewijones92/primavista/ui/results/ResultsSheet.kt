package com.dewijones92.primavista.ui.results

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dewijones92.primavista.practice.SessionResult
import com.dewijones92.primavista.practice.SkillOutcome
import com.dewijones92.primavista.theme.LocalNotationColors
import com.dewijones92.primavista.theme.TabularNumeral
import com.dewijones92.primavista.ui.progress.StrengthMeter
import com.dewijones92.primavista.ui.progress.describe
import com.dewijones92.primavista.ui.progress.meterTint
import kotlin.math.roundToInt

/**
 * What just happened, and — the part that makes it a trainer — *which reading skills* let you down.
 *
 * A single accuracy percentage is the easy thing to show and the least useful: "68%" gives Dewi
 * nothing to practise. "Bass clef below the staff: 3 of 9" tells him exactly what to do next, and
 * is only possible because verdicts are stored per note with their skills attached rather than
 * summarised (docs/spec.md I5).
 *
 * The percentage counts up and the bars fill, but only a genuinely clean run gets a flourish, and
 * the verdict itself is legible from the first frame. See `.claude/CODE-NOTES.md`.
 */
@Composable
public fun ResultsSheet(
    result: SessionResult,
    onPractiseWeakest: () -> Unit,
    onAgain: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tone = remember(result) { toneOf(result) }
    val weakest = remember(result) {
        result.skillOutcomes.filter { it.attempts > 0 }.sortedBy { it.accuracy }.take(WEAKEST_SHOWN)
    }

    Column(modifier.verticalScroll(rememberScrollState()).padding(SHEET_PADDING)) {
        Verdict(result, tone)
        Spacer(Modifier.height(SECTION_GAP))
        WhatHeldYouUp(weakest)
        Spacer(Modifier.height(SECTION_GAP))
        Actions(weakest.firstOrNull(), onPractiseWeakest, onAgain, onDone)
    }
}

@Composable
private fun Verdict(result: SessionResult, tone: ResultTone) {
    val notation = LocalNotationColors.current
    val tint = meterTint(result.cleanliness, GOOD_THRESHOLD, MIDDLING_THRESHOLD)
    Text(
        text = tone.headline,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = tint,
    )
    Spacer(Modifier.height(TIGHT_GAP))
    Box(contentAlignment = Alignment.CenterStart) {
        if (tone.celebrates) Bloom(notation.correct)
        Row(verticalAlignment = Alignment.Bottom) {
            CountingPercent(result.accuracy, tint)
            Spacer(Modifier.width(GAP))
            Column(Modifier.padding(bottom = BASELINE_LIFT)) {
                Text(
                    text = "${result.correct} of ${result.notesExpected} notes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = supportOf(result, tone),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    extrasNote(result)?.let {
        Spacer(Modifier.height(TIGHT_GAP))
        Text(text = it, style = MaterialTheme.typography.labelMedium, color = notation.wrongPitch)
    }
    Spacer(Modifier.height(GAP))
    StrengthMeter(result.cleanliness, tint, Modifier.fillMaxWidth())
}

/**
 * Counts up, but the target is on screen within [COUNT_MILLIS] and the tone above it never moves —
 * an animation that delays a verdict is an animation that hides it.
 */
@Composable
private fun CountingPercent(accuracy: Double, tint: Color) {
    val target = (accuracy * PERCENT_SCALE).roundToInt()
    var start by remember { mutableIntStateOf(0) }
    LaunchedEffect(target) { start = target }
    val shown by animateIntAsState(
        targetValue = start,
        animationSpec = tween(COUNT_MILLIS, easing = FastOutSlowInEasing),
        label = "accuracy",
    )
    Text(text = "$shown%", style = MaterialTheme.typography.displayMedium, color = tint)
}

/** One soft bloom, once, behind the number. Reserved for [ResultTone.Excellent]. */
@Composable
private fun Bloom(tint: Color) {
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(BLOOM_MILLIS, easing = FastOutSlowInEasing),
        label = "bloom",
    )
    Canvas(Modifier.size(BLOOM_SIZE)) {
        val radius = size.minDimension / 2 * (BLOOM_MIN_SCALE + progress)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(tint.copy(alpha = BLOOM_ALPHA * (1f - progress)), Color.Transparent),
                radius = radius,
            ),
            radius = radius,
        )
    }
}

@Composable
private fun WhatHeldYouUp(weakest: List<SkillOutcome>) {
    if (weakest.isEmpty()) {
        Text(
            text = "No reading skill was exercised enough to grade, so there is nothing to drill.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Text(
        text = "What held you up",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(GAP))
    Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
        weakest.forEachIndexed { index, outcome -> SkillRow(outcome, index) }
    }
}

@Composable
private fun SkillRow(outcome: SkillOutcome, index: Int) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = describe(outcome.tag),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(GAP))
        StrengthMeter(
            value = outcome.accuracy,
            tint = meterTint(outcome.accuracy, GOOD_THRESHOLD, MIDDLING_THRESHOLD),
            modifier = Modifier.width(METER_WIDTH),
            delayMillis = index * ROW_STAGGER,
        )
        Spacer(Modifier.width(GAP))
        Text(
            text = "${outcome.cleanAttempts}/${outcome.attempts}",
            style = TabularNumeral,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Actions(
    weakest: SkillOutcome?,
    onPractiseWeakest: () -> Unit,
    onAgain: () -> Unit,
    onDone: () -> Unit,
) {
    Button(
        onClick = onPractiseWeakest,
        enabled = weakest != null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(weakest?.let { "Drill ${describe(it.tag).lowercase()}" } ?: "Nothing to drill")
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GAP, Alignment.End),
    ) {
        TextButton(onClick = onAgain) { Text("Again") }
        TextButton(onClick = onDone) { Text("Done") }
    }
}

private const val PERCENT_SCALE = 100
private const val WEAKEST_SHOWN = 6
private const val GOOD_THRESHOLD = 0.85
private const val MIDDLING_THRESHOLD = 0.6
private const val COUNT_MILLIS = 800
private const val BLOOM_MILLIS = 1400
private const val BLOOM_ALPHA = 0.45f
private const val BLOOM_MIN_SCALE = 0.4f
private const val ROW_STAGGER = 60
private val SHEET_PADDING = 20.dp
private val SECTION_GAP = 18.dp
private val GAP = 10.dp
private val TIGHT_GAP = 4.dp
private val BASELINE_LIFT = 6.dp
private val METER_WIDTH = 84.dp
private val BLOOM_SIZE = 180.dp
