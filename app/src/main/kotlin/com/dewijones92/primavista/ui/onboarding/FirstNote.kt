package com.dewijones92.primavista.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dewijones92.primavista.audio.TonePlayer
import com.dewijones92.primavista.common.Diag
import com.dewijones92.primavista.practice.KeyboardTapSource
import com.dewijones92.primavista.score.Midi
import com.dewijones92.primavista.ui.mascot.MascotMood
import com.dewijones92.primavista.ui.mascot.TrillOnStaff
import com.dewijones92.primavista.ui.practice.PianoKeyboard

private const val TAG = "intro"

/**
 * The win in the first minute: one note, off a real stave, read correctly.
 *
 * The celebration is **earned or absent**. A wrong key says which key it was and which way Trill
 * actually is, and nothing about it is dressed up as progress — a mascot pleased about a wrong note
 * would undo the thing the rest of the app is for.
 */
@Composable
internal fun FirstNoteStep(
    tonePlayer: TonePlayer,
    tapSource: KeyboardTapSource,
    diag: Diag,
    onAdvance: () -> Unit,
    onSkip: () -> Unit,
) {
    val perch = remember { FIRST_NOTE_PERCH }
    val target = remember(perch) { perchPitch(perch).midi }
    var heard: Midi? by remember { mutableStateOf(null) }
    var tries by remember { mutableIntStateOf(0) }
    val correct = heard == target

    LaunchedEffect(Unit) { tonePlayer.play(target, TONE_MILLIS) }

    IntroStepScaffold(
        headline = if (correct) "That is sight-reading" else "Where am I sitting?",
        body = message(correct, heard, perch, target),
        advanceLabel = if (correct) "On we go" else "I'd rather move on",
        onAdvance = onAdvance,
        onSkip = onSkip,
        dots = 2 to INTRO_STEPS,
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            TrillOnStaff(
                mood = mood(correct, tries),
                modifier = Modifier.fillMaxWidth().height(STAFF_HEIGHT).padding(horizontal = STAFF_INSET),
                staffStep = perch,
            )
            Spacer(Modifier.height(GAP))
            if (correct) {
                Text(
                    text = perchName(perch),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(GAP))
            PianoKeyboard(
                lowest = Midi(KEYBOARD_LOW),
                highest = Midi(KEYBOARD_HIGH),
                onKeyPressed = { midi, nanos ->
                    tapSource.onKeyPressed(midi, nanos)
                    heard = midi
                    tries++
                    diag.event(
                        TAG,
                        "first note key=${midi.number} target=${target.number} " +
                            "${if (midi == target) "read" else "wrong"} tries=$tries",
                    )
                    if (midi != target) tonePlayer.play(target, TONE_MILLIS)
                },
                modifier = Modifier.fillMaxWidth().height(KEYBOARD_HEIGHT),
            )
        }
    }
}

private fun mood(correct: Boolean, tries: Int): MascotMood = when {
    correct -> MascotMood.Delighted
    tries > 0 -> MascotMood.Wincing
    else -> MascotMood.Curious
}

/** Never says "close" and never says "nearly". It says what was played and which way she is. */
private fun message(correct: Boolean, heard: Midi?, perch: Int, target: Midi): String = when {
    correct ->
        "You read a pitch off a stave and found it on a keyboard. That is the whole skill, and " +
            "everything after this is the same thing faster."
    heard == null -> "I am ${perchPlace(perch)}. Tap the key I am sitting on — that note just sounded."
    heard.number < target.number ->
        "That was lower than me. I am ${perchPlace(perch)} — listen again and try further right."
    else -> "That was higher than me. I am ${perchPlace(perch)} — listen again and try further left."
}

/** The second space, so the answer is a white key inside the staff rather than a leger-line trick. */
private const val FIRST_NOTE_PERCH = -1

private const val KEYBOARD_LOW = 60
private const val KEYBOARD_HIGH = 77
private const val TONE_MILLIS = 900L
private val STAFF_HEIGHT = 168.dp
private val STAFF_INSET = 40.dp
private val KEYBOARD_HEIGHT = 150.dp
private val GAP = 8.dp
