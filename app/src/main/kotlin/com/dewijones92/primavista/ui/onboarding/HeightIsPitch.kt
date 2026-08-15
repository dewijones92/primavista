package com.dewijones92.primavista.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dewijones92.primavista.audio.TonePlayer
import com.dewijones92.primavista.common.Diag
import com.dewijones92.primavista.ui.mascot.MascotMood
import com.dewijones92.primavista.ui.mascot.TrillOnStaff

private const val TAG = "intro"

/**
 * The one idea everything else rests on, shown rather than explained: **height on the staff is
 * pitch**.
 *
 * Trill moves a line at a time and the note sounds as she lands, so the rule arrives through the
 * ears rather than through a paragraph. She starts on the middle line because that is where the
 * treble staff's own landmark is.
 */
@Composable
internal fun HeightIsPitchStep(
    tonePlayer: TonePlayer,
    diag: Diag,
    onAdvance: () -> Unit,
    onSkip: () -> Unit,
) {
    var perch by remember { mutableIntStateOf(0) }
    var moves by remember { mutableIntStateOf(0) }
    val move: (Int) -> Unit = { by ->
        perch = (perch + by).coerceIn(PERCH_RANGE.first, PERCH_RANGE.last)
        moves++
    }

    LaunchedEffect(perch, moves) {
        if (moves == 0) return@LaunchedEffect
        tonePlayer.play(perchPitch(perch).midi, TONE_MILLIS)
        diag.event(TAG, "perch=$perch pitch=${perchName(perch)} ${perchPlace(perch)} moves=$moves")
    }

    IntroStepScaffold(
        headline = "Higher on the stave, higher in pitch",
        body = "That is the whole trick. Move me up and down the five lines and listen to what happens.",
        advanceLabel = if (moves == 0) "I'll have a go first" else "I hear it",
        onAdvance = onAdvance,
        onSkip = onSkip,
        dots = 1 to INTRO_STEPS,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TrillOnStaff(
                mood = MascotMood.Curious,
                modifier = Modifier.fillMaxWidth().height(STAFF_HEIGHT).padding(horizontal = STAFF_INSET),
                staffStep = perch,
            )
            Spacer(Modifier.height(GAP))
            Text(
                text = perchName(perch),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = perchPlace(perch),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(GAP))
            PerchControls(onUp = { move(1) }, onDown = { move(-1) }, onHear = { move(0) })
        }
    }
}

@Composable
private fun PerchControls(onUp: () -> Unit, onDown: () -> Unit, onHear: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP), verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = onDown, modifier = Modifier.size(BUTTON).testTag("perch-down")) {
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Lower")
        }
        FilledTonalIconButton(onClick = onHear, modifier = Modifier.size(BUTTON).testTag("perch-hear")) {
            Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = "Hear this note")
        }
        FilledTonalIconButton(onClick = onUp, modifier = Modifier.size(BUTTON).testTag("perch-up")) {
            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Higher")
        }
    }
}

private const val TONE_MILLIS = 700L
private val STAFF_HEIGHT = 210.dp
private val STAFF_INSET = 28.dp
private val GAP = 10.dp
private val BUTTON = 60.dp
private val BUTTON_GAP = 16.dp
