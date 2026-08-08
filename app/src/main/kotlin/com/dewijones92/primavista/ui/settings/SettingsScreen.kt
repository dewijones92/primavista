package com.dewijones92.primavista.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dewijones92.primavista.database.PracticeSettings
import com.dewijones92.primavista.database.RouteLatency
import com.dewijones92.primavista.database.StoredReading
import com.dewijones92.primavista.theme.LocalNotationColors

/** [atLeast] is capped by the probe limit, so [capped] is what stops "200" reading as "exactly 200". */
internal data class SessionCount(val atLeast: Int, val capped: Boolean)

/**
 * A refusal must never render as "No sessions stored yet" — see `.claude/CODE-NOTES.md`.
 */
internal fun storedSessionsText(reading: StoredReading<SessionCount>?): String = when (reading) {
    null -> "Reading…"
    is StoredReading.Unreadable -> "Couldn't read ${reading.what}: ${reading.reason}."
    is StoredReading.Readable -> countText(reading.value)
}

private fun countText(count: SessionCount): String = when {
    count.capped -> "${count.atLeast}+ sessions stored."
    count.atLeast == 0 -> "No sessions stored yet."
    count.atLeast == 1 -> "1 session stored."
    else -> "${count.atLeast} sessions stored."
}

/**
 * The preferences the app stores, and the honest state of each.
 *
 * The audio-latency panel is the reason this screen exists at all: a mic verdict's millisecond
 * figure is only as good as the latency correction behind it, and presenting an assumed number as a
 * measured one is the exact failure docs/todos/measure-audio-latency.md is written to prevent. So
 * provenance is shown beside every figure, in words, with its consequence spelled out.
 */
@Composable
internal fun SettingsScreen(
    settings: PracticeSettings,
    latencies: StoredReading<List<RouteLatency>>?,
    storedSessions: StoredReading<SessionCount>?,
    appliedYet: Boolean,
    onSettings: (PracticeSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SCREEN_PADDING),
    ) {
        Spacer(Modifier.height(SCREEN_PADDING))
        Text("Settings", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(2.dp))
        Text(
            text = "Stored on this device and nowhere else.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(SECTION_GAP))

        SessionSection(settings, appliedYet, onSettings)
        Spacer(Modifier.height(SECTION_GAP))
        InputSection(settings, onSettings)
        Spacer(Modifier.height(SECTION_GAP))
        LatencySection(latencies)
        Spacer(Modifier.height(SECTION_GAP))
        HistorySection(storedSessions)
        Spacer(Modifier.height(SCREEN_PADDING))
    }
}

@Composable
private fun SessionSection(
    settings: PracticeSettings,
    appliedYet: Boolean,
    onSettings: (PracticeSettings) -> Unit,
) {
    SettingSection("Session", "How a practice run is set up.") {
        TempoDial(settings.tempoBpm) { onSettings(settings.copy(tempoBpm = it)) }
        Spacer(Modifier.height(ROW_GAP))
        SwitchRow(
            title = "Metronome",
            detail = "A click on every beat, accented on the downbeat.",
            checked = settings.metronomeOn,
        ) { onSettings(settings.copy(metronomeOn = it)) }
        SwitchRow(
            title = "Listen first",
            detail = "Play the piece through once before you are asked to read it.",
            checked = settings.listenFirstOn,
        ) { onSettings(settings.copy(listenFirstOn = it)) }
        if (!appliedYet) {
            Spacer(Modifier.height(ROW_GAP))
            Caveat(
                "Saved and restored here, but a session does not read them yet — it uses each " +
                    "piece's own tempo, and the metronome and input are chosen on the practice " +
                    "screen itself.",
            )
        }
    }
}

@Composable
private fun InputSection(settings: PracticeSettings, onSettings: (PracticeSettings) -> Unit) {
    SettingSection("Input", "What the app listens to.") {
        SegmentedChoice(
            options = InputChoice.entries.map { it.label to it.title },
            selected = settings.inputLabel,
        ) { onSettings(settings.copy(inputLabel = it)) }
        Spacer(Modifier.height(ROW_GAP))
        val chosen = InputChoice.entries.firstOrNull { it.label == settings.inputLabel }
        Text(
            text = chosen?.detail ?: "Nothing chosen yet, so a session opens on the on-screen keyboard.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HistorySection(storedSessions: StoredReading<SessionCount>?) {
    SettingSection("Practice history", "What has been kept.") {
        Text(text = storedSessionsText(storedSessions), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(ROW_GAP))
        Text(
            text = "Nothing here is ever deleted to recover from a problem — an unreadable history " +
                "is reported instead, and the file is left alone.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun SettingSection(title: String, subtitle: String, content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(CARD_CORNER),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(CARD_PADDING)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(CARD_PADDING))
            content()
        }
    }
}

@Composable
private fun SwitchRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = ROW_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = detail,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(ROW_GAP))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Amber rather than red: a stated limitation is not an error, and colouring it as one cries wolf. */
@Composable
internal fun Caveat(text: String) {
    val notation = LocalNotationColors.current
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            Modifier
                .width(CAVEAT_RULE)
                .fillMaxHeight()
                .clip(RoundedCornerShape(CAVEAT_RULE))
                .background(notation.offTime),
        )
        Spacer(Modifier.width(ROW_GAP))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal enum class InputChoice(val label: String, val title: String, val detail: String) {
    Tap(
        label = "tap",
        title = "Tap",
        detail = "The on-screen keyboard. Both hands at once, and the touch is timestamped by the " +
            "input system, so its timing needs no correction.",
    ),
    Mic(
        label = "mic",
        title = "Play it",
        detail = "The microphone, following one line at a time. Two-hand material is refused with " +
            "the bar that needs both hands, rather than half-heard and mis-scored.",
    ),
}

private val SCREEN_PADDING = 16.dp
private val SECTION_GAP = 12.dp
private val CARD_PADDING = 14.dp
private val CARD_CORNER = 18.dp
private val ROW_GAP = 8.dp
private val CAVEAT_RULE = 3.dp
