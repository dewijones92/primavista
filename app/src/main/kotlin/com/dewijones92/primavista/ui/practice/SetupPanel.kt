package com.dewijones92.primavista.ui.practice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dewijones92.primavista.di.InputMode
import com.dewijones92.primavista.practice.TransportState

/**
 * Everything you change *between* runs, and nothing you would want during one.
 *
 * It folds away while the music is moving. See `.claude/CODE-NOTES.md` for why that is a
 * simplification rather than a hidden control.
 */
@Composable
internal fun SetupPanel(
    state: PracticeUiState,
    setup: SessionSetup?,
    onToggle: ((PracticeToggle) -> Unit)?,
) {
    if (setup == null && onToggle == null) return
    val stopped = state.transport != TransportState.Running &&
        state.transport != TransportState.CountingIn
    AnimatedVisibility(
        visible = stopped,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (setup != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Answer with",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(10.dp))
                    InputModeChip(InputMode.Tap, "TAP", Icons.Rounded.TouchApp, state.input, setup.onInput)
                    Spacer(Modifier.width(6.dp))
                    InputModeChip(InputMode.Mic, "MIC", Icons.Rounded.Mic, state.input, setup.onInput)
                }
                Spacer(Modifier.height(8.dp))
            }
            SoundsAndActions(state, setup, onToggle)
        }
    }
}

@Composable
private fun SoundsAndActions(
    state: PracticeUiState,
    setup: SessionSetup?,
    onToggle: ((PracticeToggle) -> Unit)?,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (onToggle != null) {
            ToggleChip("Click", Icons.Rounded.MusicNote, state.metronomeOn) {
                onToggle(PracticeToggle.Metronome)
            }
            Spacer(Modifier.width(6.dp))
            ToggleChip("Echo", Icons.AutoMirrored.Rounded.VolumeUp, state.echoOn) {
                onToggle(PracticeToggle.Echo)
            }
        }
        Spacer(Modifier.weight(1f))
        if (setup != null) {
            ActionButton(Icons.Rounded.Hearing, "Hear it", "listen", setup.onListen)
            Spacer(Modifier.width(6.dp))
            ActionButton(Icons.Rounded.AutoAwesome, "Try another", "next", setup.onNext)
        }
    }
}

@Composable
private fun InputModeChip(
    mode: InputMode,
    label: String,
    icon: ImageVector,
    current: InputMode,
    onInput: (InputMode) -> Unit,
) {
    FilterChip(
        selected = mode == current,
        onClick = { onInput(mode) },
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(CHIP_ICON)) },
        shape = RoundedCornerShape(CHIP_CORNER),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = Modifier.testTag("input-${mode.name}"),
    )
}

@Composable
private fun ToggleChip(label: String, icon: ImageVector, on: Boolean, onClick: () -> Unit) {
    // Brass, never mint: mint means *correct* on a notehead, and a toggle is not a verdict.
    val container = if (on) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val content = if (on) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        Modifier
            .clip(CircleShape)
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp)
            .testTag("toggle-$label"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(CHIP_ICON))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = content)
    }
}

/** Labelled rather than a bare icon: a beginner cannot guess what an ear or a sparkle does. */
@Composable
private fun ActionButton(icon: ImageVector, label: String, tag: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(ACTION_CORNER))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(CHIP_ICON),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private val CHIP_ICON = 16.dp
private val CHIP_CORNER = 50.dp
private val ACTION_CORNER = 14.dp
