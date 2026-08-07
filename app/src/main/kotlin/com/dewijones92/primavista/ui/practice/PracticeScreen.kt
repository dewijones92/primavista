package com.dewijones92.primavista.ui.practice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.dewijones92.primavista.notation.GlyphMetrics
import com.dewijones92.primavista.notation.StaffSpaces
import com.dewijones92.primavista.practice.RefusalReason
import com.dewijones92.primavista.practice.TransportState
import com.dewijones92.primavista.practice.Verdict
import com.dewijones92.primavista.score.Midi
import com.dewijones92.primavista.theme.LocalNotationColors
import com.dewijones92.primavista.theme.NotationColors
import com.dewijones92.primavista.theme.TabularNumeral
import com.dewijones92.primavista.ui.staff.StaffCanvas

/**
 * The app's one screen that matters: music moving past a playhead while you try to keep up.
 */
@Composable
public fun PracticeScreen(
    state: PracticeUiState,
    metrics: GlyphMetrics,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onKeyPressed: (Midi, Long) -> Unit,
    onFrame: () -> Unit,
    onDismissRefusal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The frame clock drives the session. It is the UI's job to decide *when to look*; the
    // Conductor remains the only thing that knows what time it is (see .claude/CODE-NOTES.md).
    LaunchedEffect(state.transport) {
        while (state.transport == TransportState.Running || state.transport == TransportState.CountingIn) {
            androidx.compose.runtime.withFrameNanos { }
            onFrame()
        }
    }

    Column(modifier.fillMaxSize()) {
        PracticeHeader(state)

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            ScrollingStaff(state, metrics)
            CountInOverlay(state.countInBeatsRemaining)
        }

        TransportBar(state, onStart, onPause, onResume)

        if (state.refusal == null) {
            PianoKeyboard(
                lowest = Midi(KEYBOARD_LOWEST),
                highest = Midi(KEYBOARD_HIGHEST),
                onKeyPressed = onKeyPressed,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(KEYBOARD_HEIGHT)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
        }
    }

    state.refusal?.let { RefusalDialog(it, onDismissRefusal) }
}

/**
 * The staff scrolls; the playhead stays put at [PLAYHEAD_SCREEN_FRACTION] from the left.
 *
 * That is the whole point of the exercise: sight-reading is reading *ahead*, so most of the visible
 * width has to be music you have not played yet. A playhead that travelled rightwards instead would
 * show the least of what matters at the moment you most need it.
 */
@Composable
private fun ScrollingStaff(state: PracticeUiState, metrics: GlyphMetrics) {
    val notation = LocalNotationColors.current
    val system = state.system

    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = notation.paper),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        if (system == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (state.refusal != null) "" else "Nothing loaded",
                    color = notation.ink.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            return@Card
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val staffSpace = (maxHeight.value / system.height.value.toFloat().coerceAtLeast(1f))
                .coerceIn(MIN_STAFF_SPACE_DP, MAX_STAFF_SPACE_DP)
            val viewportSpaces = maxWidth.value / staffSpace
            val playhead = state.playheadX.value
            val scroll = (playhead - viewportSpaces * PLAYHEAD_SCREEN_FRACTION).coerceAtLeast(0.0)

            StaffCanvas(
                system = system,
                metrics = metrics,
                modifier = Modifier.fillMaxSize().testTag("staff"),
                staffSpace = staffSpace.dp,
                scrollX = StaffSpaces(scroll),
                playheadX = state.playheadX,
                noteTint = { index -> index?.let { state.verdicts[it] }?.let { tintFor(it, notation) } },
            )
        }
    }
}

private fun tintFor(verdict: Verdict, notation: NotationColors): Color =
    when (verdict) {
        is Verdict.Correct -> notation.correct
        is Verdict.WrongPitch -> notation.wrongPitch
        is Verdict.Early, is Verdict.Late -> notation.offTime
        Verdict.Missed -> notation.missed
        is Verdict.Extra -> notation.wrongPitch
    }

@Composable
private fun PracticeHeader(state: PracticeUiState) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = state.score?.title ?: "PrimaVista",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            state.score?.composer?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        InputChip(state.inputLabel)
        Spacer(Modifier.width(10.dp))
        Text(
            text = "${state.tempoBpm} bpm",
            style = TabularNumeral,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun InputChip(label: String) {
    if (label.isEmpty()) return
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (label == "mic") {
            Icon(
                Icons.Rounded.Mic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/**
 * The count-in is shown, not just heard. A purely audible count-in is useless with the volume down,
 * and starting to read without knowing when bar 1 arrives makes the first note late every time —
 * which the judge would faithfully record as Dewi's mistake.
 */
@Composable
private fun CountInOverlay(beatsRemaining: Int) {
    AnimatedVisibility(visible = beatsRemaining > 0) {
        val scale by animateFloatAsState(
            targetValue = if (beatsRemaining > 0) 1f else 0.7f,
            label = "count-in",
        )
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = beatsRemaining.toString(),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f * scale),
            )
        }
    }
}

@Composable
private fun TransportBar(
    state: PracticeUiState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ScoreReadout(state)
        FilledIconButton(
            onClick = {
                when (state.transport) {
                    TransportState.Idle, TransportState.Finished -> onStart()
                    TransportState.Running, TransportState.CountingIn -> onPause()
                    TransportState.Paused -> onResume()
                }
            },
            enabled = state.refusal == null && state.system != null,
            modifier = Modifier.size(56.dp).testTag("transport"),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            val running = state.transport == TransportState.Running ||
                state.transport == TransportState.CountingIn
            Icon(
                imageVector = if (running) Icons.Rounded.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (running) "Pause" else "Start",
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun ScoreReadout(state: PracticeUiState) {
    val judged = state.verdicts.size
    val expected = state.score?.attackedNotes?.size ?: 0
    val clean = state.verdicts.values.count { it.isClean }
    Column {
        Text(
            text = if (judged == 0) "—" else "$clean / $judged",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = if (expected == 0) "" else "of $expected notes",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The honest refusal, made useful. It names the bar and offers the thing a pianist would actually
 * do about it — practise one hand at a time (docs/spec.md I3).
 */
@Composable
private fun RefusalDialog(reason: RefusalReason, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(22.dp)) {
                Text(
                    text = when (reason) {
                        is RefusalReason.PolyphonicScoreOnMonoInput -> "Can't hear both hands"
                        is RefusalReason.EmptyScore -> "Nothing to read"
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = when (reason) {
                        is RefusalReason.PolyphonicScoreOnMonoInput ->
                            "Bar ${reason.firstPolyphonicBar} has more than one note at once, and " +
                                "${reason.inputLabel} input can only follow a single line. Rather " +
                                "than guess and mark you wrong, it's stopping here — switch to TAP, " +
                                "or practise one hand at a time."
                        is RefusalReason.EmptyScore ->
                            "\"${reason.scoreTitle}\" has no notes to play."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private const val PLAYHEAD_SCREEN_FRACTION = 0.28
private const val MIN_STAFF_SPACE_DP = 4f
private const val MAX_STAFF_SPACE_DP = 13f
private const val KEYBOARD_LOWEST = 48
private const val KEYBOARD_HIGHEST = 84
private val KEYBOARD_HEIGHT = 150.dp
