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
import com.dewijones92.primavista.score.Polyphony
import com.dewijones92.primavista.theme.LocalNotationColors
import com.dewijones92.primavista.theme.TabularNumeral
import com.dewijones92.primavista.ui.mascot.Trill
import com.dewijones92.primavista.ui.progress.StrengthMeter
import com.dewijones92.primavista.ui.progress.describe
import com.dewijones92.primavista.ui.progress.meterTint
import com.dewijones92.primavista.ui.progress.percent

/**
 * What just happened, and — the part that makes it a trainer — *which reading skills* let you down.
 *
 * A single percentage is the easy thing to show and the least useful: "68%" gives Dewi nothing to
 * practise. "Bass clef below the staff: 3 of 9" tells him exactly what to do next, and is only
 * possible because verdicts are stored per note with their skills attached rather than summarised
 * (docs/spec.md I5).
 *
 * The number, the headline, the tint and the meter are one quantity — [SessionResult.cleanliness] —
 * and accuracy is stated beside it rather than hidden. See `.claude/CODE-NOTES.md`.
 */
@Composable
public fun ResultsSheet(
    result: SessionResult,
    onPractiseWeakest: () -> Unit,
    onAgain: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    input: Polyphony = Polyphony.Poly,
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
        Actions(drillTarget(result, input), onPractiseWeakest, onAgain, onDone)
    }
}

@Composable
private fun Verdict(result: SessionResult, tone: ResultTone) {
    val notation = LocalNotationColors.current
    val tint = toneTint(tone)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = tone.headline,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = tint,
            )
            Spacer(Modifier.height(TIGHT_GAP))
            Box(contentAlignment = Alignment.CenterStart) {
                if (tone.celebrates) Bloom(notation.correct)
                if (tone == ResultTone.Nothing) NoPercent(tint) else CountingPercent(result.cleanliness, tint)
            }
        }
        Spacer(Modifier.width(GAP))
        Trill(moodFor(tone), Modifier.size(TRILL_SIZE))
    }
    Text(
        text = headlineBasis(result),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(GAP))
    Text(
        text = supportOf(result, tone),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    extrasNote(result)?.let {
        Spacer(Modifier.height(TIGHT_GAP))
        Text(text = it, style = MaterialTheme.typography.labelMedium, color = notation.wrongPitch)
    }
    if (tone != ResultTone.Nothing) {
        Spacer(Modifier.height(GAP))
        StrengthMeter(result.cleanliness, tint, Modifier.fillMaxWidth())
    }
}

/**
 * Counts up, but the target is on screen within [COUNT_MILLIS] and the tone above it never moves —
 * an animation that delays a verdict is an animation that hides it.
 */
@Composable
private fun CountingPercent(cleanliness: Double, tint: Color) {
    val target = percent(cleanliness)
    var start by remember { mutableIntStateOf(0) }
    LaunchedEffect(target) { start = target }
    val shown by animateIntAsState(
        targetValue = start,
        animationSpec = tween(COUNT_MILLIS, easing = FastOutSlowInEasing),
        label = "cleanliness",
    )
    Text(text = "$shown%", style = MaterialTheme.typography.displayMedium, color = tint)
}

/** A session with nothing to judge scored nothing; a big 0% would say he got everything wrong. */
@Composable
private fun NoPercent(tint: Color) {
    Text(text = "—", style = MaterialTheme.typography.displayMedium, color = tint)
}

/** One decision colours the headline, the number and the meter. See `.claude/CODE-NOTES.md`. */
@Composable
private fun toneTint(tone: ResultTone): Color {
    val notation = LocalNotationColors.current
    return when (tone) {
        ResultTone.Excellent, ResultTone.Good -> notation.correct
        ResultTone.Mixed -> notation.offTime
        ResultTone.Rough -> notation.wrongPitch
        ResultTone.Nothing -> MaterialTheme.colorScheme.onSurfaceVariant
    }
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
            text = "No reading skill was exercised, so there is nothing to drill.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val heldUp = weakest.any { it.accuracy < 1.0 }
    Text(
        text = if (heldUp) "What held you up" else "Nothing held you up",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    if (!heldUp) {
        Text(
            text = "Every reading skill this piece exercised came out clean.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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
            tint = meterTint(outcome.accuracy, GOOD_AT, MIXED_AT),
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
    target: SkillOutcome?,
    onPractiseWeakest: () -> Unit,
    onAgain: () -> Unit,
    onDone: () -> Unit,
) {
    Button(
        onClick = onPractiseWeakest,
        enabled = target != null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(target?.let { "Drill ${describe(it.tag).lowercase()}" } ?: "Nothing to drill")
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GAP, Alignment.End),
    ) {
        TextButton(onClick = onAgain) { Text("Again") }
        TextButton(onClick = onDone) { Text("Done") }
    }
}

private const val WEAKEST_SHOWN = 6
private const val COUNT_MILLIS = 800
private const val BLOOM_MILLIS = 1400
private const val BLOOM_ALPHA = 0.45f
private const val BLOOM_MIN_SCALE = 0.4f
private const val ROW_STAGGER = 60
private val SHEET_PADDING = 20.dp
private val SECTION_GAP = 18.dp
private val GAP = 10.dp
private val TIGHT_GAP = 4.dp
private val TRILL_SIZE = 96.dp
private val METER_WIDTH = 84.dp
private val BLOOM_SIZE = 180.dp
