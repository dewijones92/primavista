package com.dewijones92.primavista.ui.practice

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dewijones92.primavista.score.Midi
import com.dewijones92.primavista.score.asKey
import com.dewijones92.primavista.score.spokenName
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
                    face = Brush.verticalGradient(listOf(notation.keyNatural, shade(notation.keyNatural))),
                    labelColor = notation.keySharp,
                    edgeColor = MaterialTheme.colorScheme.outlineVariant,
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
                        face = Brush.verticalGradient(listOf(lift(notation.keySharp), notation.keySharp)),
                        labelColor = notation.keyNatural,
                        edgeColor = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

/**
 * Fast attack, slower release, and a haptic on the way down — an instrument answers instantly and
 * lets go gradually, and the tick under the finger is what makes a glass keyboard feel struck.
 */
@Composable
private fun PianoKey(
    midi: Midi,
    isBlack: Boolean,
    width: Dp,
    offsetX: Dp,
    heightFraction: Float,
    onKeyPressed: (Midi, Long) -> Unit,
    face: Brush,
    labelColor: Color,
    edgeColor: Color,
) {
    var pressed by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val highlight = MaterialTheme.colorScheme.primary
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(if (pressed) KEY_ATTACK_MS else KEY_RELEASE_MS),
        label = "key-press",
    )
    val shape = RoundedCornerShape(bottomStart = KEY_CORNER, bottomEnd = KEY_CORNER)

    Box(
        modifier = Modifier
            .offset(x = offsetX)
            .width(width)
            .fillMaxHeight(heightFraction)
            .graphicsLayer {
                transformOrigin = TransformOrigin(0.5f, 0f)
                scaleY = 1f - KEY_TRAVEL * press
            }
            .clip(shape)
            .background(face)
            .background(highlight.copy(alpha = press))
            // Without an edge the naturals read as one undivided slab with the sharps floating on
            // it, and there is no way to see which key a finger is over.
            .border(width = KEY_EDGE, color = lerp(edgeColor, highlight, press), shape = shape)
            .testTag("key-${midi.number}")
            // Named for a screen reader only: the visible keyboard is deliberately unlabelled so
            // Dewi reads the notation rather than the letters (see KeyLabel below).
            .semantics { contentDescription = midi.asKey().spokenName }
            .pointerInput(midi) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val down = event.changes.firstOrNull { it.pressed && it.previousPressed.not() }
                        if (down != null) {
                            pressed = true
                            haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                            // The event's own timestamp, not the moment this coroutine resumed:
                            // scheduling delay is not part of when Dewi's finger landed.
                            onKeyPressed(midi, down.uptimeMillis * NANOS_PER_MILLI)
                        }
                        if (event.changes.none { it.pressed }) pressed = false
                    }
                }
            },
    ) {
        KeyLabel(midi, isBlack, labelColor)
    }
}

/**
 * Landmarks, not names. Every C gets a dot and middle C gets the letter as well — that is what a
 * hand finds its place from on a real keyboard. Labelling every key would let Dewi read the label
 * instead of the staff, which trains a different and lesser skill.
 */
@Composable
private fun KeyLabel(midi: Midi, isBlack: Boolean, labelColor: Color) {
    if (isBlack || midi.number % SEMITONES != 0) return
    val middle = midi.number == Midi.MIDDLE_C
    val mark = if (middle) MaterialTheme.colorScheme.primary else labelColor.copy(alpha = C_DOT_ALPHA)
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).offset(y = -KEY_LABEL_INSET),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (middle) {
                Text(
                    text = "C",
                    color = mark,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Box(Modifier.size(C_DOT_SIZE).clip(CircleShape).background(mark))
        }
    }
}

private fun shade(color: Color): Color = lerp(color, Color.Black, KEY_FACE_SHADE)

private fun lift(color: Color): Color = lerp(color, Color.White, KEY_FACE_LIFT)

private fun isBlackKey(midi: Int): Boolean = (midi % SEMITONES) in BLACK_PITCH_CLASSES

private fun whiteKeysIn(lowest: Midi, highest: Midi): List<Midi> =
    (lowest.number..highest.number).filterNot { isBlackKey(it) }.map { Midi(it) }

private const val SEMITONES = 12
private val BLACK_PITCH_CLASSES = setOf(1, 3, 6, 8, 10)
private const val BLACK_KEY_WIDTH_FRACTION = 0.62f
private const val BLACK_KEY_HEIGHT_FRACTION = 0.62f
private const val C_DOT_ALPHA = 0.32f
private const val NANOS_PER_MILLI = 1_000_000L
private const val KEY_ATTACK_MS = 45
private const val KEY_RELEASE_MS = 260
private const val KEY_TRAVEL = 0.022f
private const val KEY_FACE_SHADE = 0.09f
private const val KEY_FACE_LIFT = 0.22f
private val C_DOT_SIZE = 4.dp
private val KEY_LABEL_INSET = 7.dp
private val KEY_CORNER = 5.dp
private val KEY_EDGE = 1.dp
