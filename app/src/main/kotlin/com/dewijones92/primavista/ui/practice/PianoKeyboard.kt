package com.dewijones92.primavista.ui.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dewijones92.primavista.score.Midi
import com.dewijones92.primavista.theme.LocalNotationColors

/**
 * The TAP input surface: a real piano keyboard rather than seven note-name buttons.
 *
 * Chosen because the skill being trained is reading notation, and a keyboard preserves the spatial
 * relationship the staff encodes — a step up on the page is a step right on the keys. Lettered
 * buttons would train naming notes instead, which is a different and lesser skill and would also
 * make an octave error unnoticeable.
 *
 * Multi-touch, so it can answer the polyphonic grand-staff material that mic mode has to refuse.
 */
@Composable
public fun PianoKeyboard(
    lowest: Midi,
    highest: Midi,
    onKeyPressed: (Midi, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val notation = LocalNotationColors.current
    val whites = remember(lowest, highest) { whiteKeysIn(lowest, highest) }
    if (whites.isEmpty()) return

    BoxWithConstraints(modifier) {
        val whiteWidth = maxWidth / whites.size
        Box(Modifier.fillMaxSize()) {
            whites.forEachIndexed { index, midi ->
                PianoKey(
                    midi = midi,
                    isBlack = false,
                    width = whiteWidth,
                    offsetX = whiteWidth * index,
                    heightFraction = 1f,
                    onKeyPressed = onKeyPressed,
                    faceColor = notation.paper,
                    labelColor = notation.ink,
                )
            }
            // Drawn after the naturals so they win the hit test where they overlap, which is what
            // a real keyboard does: the sharps sit on top.
            whites.forEachIndexed { index, midi ->
                val sharp = midi.number + 1
                if (sharp <= highest.number && isBlackKey(sharp)) {
                    PianoKey(
                        midi = Midi(sharp),
                        isBlack = true,
                        width = whiteWidth * BLACK_KEY_WIDTH_FRACTION,
                        offsetX = whiteWidth * (index + 1) - (whiteWidth * BLACK_KEY_WIDTH_FRACTION / 2),
                        heightFraction = BLACK_KEY_HEIGHT_FRACTION,
                        onKeyPressed = onKeyPressed,
                        faceColor = notation.ink,
                        labelColor = notation.paper,
                    )
                }
            }
        }
    }
}

@Composable
private fun PianoKey(
    midi: Midi,
    isBlack: Boolean,
    width: Dp,
    offsetX: Dp,
    heightFraction: Float,
    onKeyPressed: (Midi, Long) -> Unit,
    faceColor: Color,
    labelColor: Color,
) {
    var pressed by remember { mutableStateOf(false) }
    val highlight = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .offset(x = offsetX)
            .width(width)
            .fillMaxHeight(heightFraction)
            .clip(RoundedCornerShape(bottomStart = KEY_CORNER, bottomEnd = KEY_CORNER))
            .background(if (pressed) highlight else faceColor)
            .testTag("key-${midi.number}")
            .pointerInput(midi) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val down = event.changes.firstOrNull { it.pressed && it.previousPressed.not() }
                        if (down != null) {
                            pressed = true
                            // The event's own timestamp, not the moment this coroutine resumed:
                            // scheduling delay is not part of when Dewi's finger landed.
                            onKeyPressed(midi, down.uptimeMillis * NANOS_PER_MILLI)
                        }
                        if (event.changes.none { it.pressed }) pressed = false
                    }
                }
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Transparent),
        )
        KeyLabel(midi, isBlack, labelColor)
    }
}

@Composable
private fun KeyLabel(midi: Midi, isBlack: Boolean, labelColor: Color) {
    // Only middle C is labelled. Labelling every key would let Dewi read the label instead of the
    // staff, which trains the wrong thing; one landmark is what a real keyboard gives you.
    if (isBlack || midi.number != Midi.MIDDLE_C) return
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Transparent),
    ) {
        androidx.compose.material3.Text(
            text = "C",
            color = labelColor.copy(alpha = MIDDLE_C_LABEL_ALPHA),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomCenter)
                .offset(y = (-6).dp),
        )
    }
}

private fun isBlackKey(midi: Int): Boolean = (midi % SEMITONES) in BLACK_PITCH_CLASSES

private fun whiteKeysIn(lowest: Midi, highest: Midi): List<Midi> =
    (lowest.number..highest.number).filterNot { isBlackKey(it) }.map { Midi(it) }

private const val SEMITONES = 12
private val BLACK_PITCH_CLASSES = setOf(1, 3, 6, 8, 10)
private const val BLACK_KEY_WIDTH_FRACTION = 0.62f
private const val BLACK_KEY_HEIGHT_FRACTION = 0.62f
private const val MIDDLE_C_LABEL_ALPHA = 0.55f
private const val NANOS_PER_MILLI = 1_000_000L
private val KEY_CORNER = 5.dp
