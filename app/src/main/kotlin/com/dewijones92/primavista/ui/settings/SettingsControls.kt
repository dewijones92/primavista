package com.dewijones92.primavista.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dewijones92.primavista.database.RouteLatency
import com.dewijones92.primavista.database.StoredReading
import com.dewijones92.primavista.practice.InputLatency
import com.dewijones92.primavista.theme.LocalNotationColors
import com.dewijones92.primavista.theme.TabularNumeral
import com.dewijones92.primavista.ui.UnreadableNote
import kotlin.math.roundToInt

@Composable
internal fun TempoDial(tempoBpm: Int, onTempo: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = tempoBpm.toString(),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(BADGE_GAP))
        Text(
            text = "bpm",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = LABEL_LIFT),
        )
    }
    Slider(
        value = tempoBpm.toFloat(),
        onValueChange = { onTempo(it.roundToInt()) },
        valueRange = MIN_TEMPO..MAX_TEMPO,
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Scale("${MIN_TEMPO.toInt()} — slow enough to read every note")
        Scale(MAX_TEMPO.toInt().toString())
    }
}

@Composable
private fun Scale(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun SegmentedChoice(
    options: List<Pair<String, String>>,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SEGMENT_CORNER))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(SEGMENT_BORDER, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(SEGMENT_CORNER))
            .padding(SEGMENT_INSET),
        horizontalArrangement = Arrangement.spacedBy(SEGMENT_INSET),
    ) {
        options.forEach { (value, title) ->
            val chosen = value == selected
            val background by animateColorAsState(
                if (chosen) MaterialTheme.colorScheme.primary else Color.Transparent,
                label = "segment-$value",
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = if (chosen) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(SEGMENT_CORNER))
                    .background(background)
                    .clickable { onSelect(value) }
                    .padding(vertical = SEGMENT_PADDING),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Latency, per route, with its provenance in words. A route with no row is shown as unmeasured
 * rather than as zero, and a refused read is shown as a refusal — see `.claude/CODE-NOTES.md`.
 */
@Composable
internal fun LatencySection(latencies: StoredReading<List<RouteLatency>>?) {
    SettingSection("Audio timing", "How late an input arrives, and whether anyone measured it.") {
        when {
            latencies == null -> Text(
                text = "Reading…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            latencies is StoredReading.Unreadable -> UnreadableNote(latencies)
            latencies is StoredReading.Readable && latencies.value.isEmpty() -> UnmeasuredLatency()
            latencies is StoredReading.Readable -> Column(verticalArrangement = Arrangement.spacedBy(ROW_GAP)) {
                latencies.value.forEach { RouteLatencyRow(it) }
            }
        }
        Spacer(Modifier.height(ROW_GAP))
        Text(
            text = provenanceConsequence(InputLatency.Provenance.NotApplicable),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun UnmeasuredLatency() {
    val reading = latencyReading(null, null)
    Column {
        Text(
            text = reading.figure,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(BADGE_GAP))
        ProvenanceBadge(reading)
        Spacer(Modifier.height(ROW_GAP))
        Text(
            text = reading.consequence,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RouteLatencyRow(stored: RouteLatency) {
    val reading = latencyReading(stored.latency.millis, stored.latency.provenance)
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stored.route.id,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(text = reading.figure, style = TabularNumeral)
        }
        Spacer(Modifier.height(BADGE_GAP))
        ProvenanceBadge(reading)
        Spacer(Modifier.height(BADGE_GAP))
        Text(
            text = reading.consequence,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProvenanceBadge(reading: LatencyReading) {
    val notation = LocalNotationColors.current
    val tint = if (reading.measured) notation.correct else notation.offTime
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(DOT_SIZE)
                .clip(RoundedCornerShape(DOT_SIZE))
                .background(tint),
        )
        Spacer(Modifier.width(BADGE_GAP))
        Text(
            text = reading.provenance,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = tint,
        )
    }
}

private const val MIN_TEMPO = 40f
private const val MAX_TEMPO = 200f
private val ROW_GAP = 8.dp
private val BADGE_GAP = 6.dp
private val LABEL_LIFT = 8.dp
private val DOT_SIZE = 8.dp
private val SEGMENT_CORNER = 14.dp
private val SEGMENT_INSET = 4.dp
private val SEGMENT_BORDER = 1.dp
private val SEGMENT_PADDING = 10.dp
