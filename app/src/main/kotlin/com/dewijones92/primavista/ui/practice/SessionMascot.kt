package com.dewijones92.primavista.ui.practice

import com.dewijones92.primavista.practice.TransportState
import com.dewijones92.primavista.ui.mascot.MascotMood
import com.dewijones92.primavista.ui.results.moodFor
import com.dewijones92.primavista.ui.results.toneOf

/**
 * What Trill is doing right now. Pure, so "never pleased about a bad run" is a unit test.
 *
 * A finished run goes through `ui/results/moodFor`, which is the app's only mapping from a
 * verdict to a face — see `.claude/CODE-NOTES.md`.
 */
internal fun sessionMood(state: PracticeUiState): MascotMood = when {
    state.refusal != null -> MascotMood.Wincing
    state.loading -> MascotMood.Curious
    state.transport == TransportState.Running || state.transport == TransportState.CountingIn ->
        MascotMood.Listening
    state.transport == TransportState.Finished ->
        state.lastRun?.let { moodFor(toneOf(it)) } ?: MascotMood.Curious
    state.score == null -> MascotMood.Sleepy
    else -> MascotMood.Curious
}
