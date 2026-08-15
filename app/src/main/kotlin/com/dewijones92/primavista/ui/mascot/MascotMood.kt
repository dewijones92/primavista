package com.dewijones92.primavista.ui.mascot

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Trill, the songbird who lives on the staff.
 *
 * Birds on telegraph wires genuinely look like notes on a stave, which is the whole idea: she
 * perches on the line the note actually sits on, so the mascot is demonstrating the one thing a
 * beginner finds hardest — that height on the staff *is* pitch.
 *
 * **She never contradicts the score.** The app's ethic is that it tells Dewi the truth about his
 * playing; Trill is allowed to be kind about a bad run and must never be pleased about one. A
 * [Delighted] bird over a 40% session would undo the thing the whole app is for.
 */
public enum class MascotMood {
    /** Nothing happening. Breathing, blinking, occasionally looking about. The resting state. */
    Idle,

    /**
     * A session is running and notation is scrolling. She goes deliberately still and quiet here:
     * this is the one moment Dewi's eyes must be on the staff, and a moving bird beside it competes
     * for exactly the attention the app is training.
     */
    Listening,

    /** Earned, and only earned: a genuinely good run. */
    Delighted,

    /** A wrong note, or a poor session. Sympathetic, never mocking, never disappointed-in-you. */
    Wincing,

    /** Nothing practised for a while, or an empty screen. Dozing, not scolding. */
    Sleepy,

    /** A personal best, or a skill reaching Solid. Rarer than [Delighted]. */
    Impressed,

    /** Waiting on Dewi to choose something — a prompt, a question, an empty repertoire. */
    Curious,
}

/**
 * How a mascot is drawn. A plain composable lambda rather than an interface, so a mascot is one
 * top-level function and a screen can take one without knowing which.
 *
 * An implementation must fill the [Modifier]'s bounds, be square-ish, and read correctly at 40dp
 * (a small in-line companion) as well as at 200dp (the hero on a results screen).
 */
public typealias MascotPainter = @Composable (mood: MascotMood, modifier: Modifier) -> Unit

/** Every mood, in a fixed order, so a row of them is stable between builds. */
public val MascotMoods: List<MascotMood> = MascotMood.entries.toList()
