package com.dewijones92.primavista.ui.settings

import com.dewijones92.primavista.audio.InputLatencyResult
import kotlin.math.roundToInt

/**
 * What a calibration attempt is doing, and what came of it.
 *
 * Modelled as data with a pure reader below so the wording — especially the wording of a failure —
 * is unit-testable without a device. A refusal Dewi cannot read is as bad as no refusal at all.
 */
internal sealed interface Calibration {
    data object Idle : Calibration

    data object Running : Calibration

    data class Finished(val result: InputLatencyResult) : Calibration
}

internal data class CalibrationPrompt(val action: String, val detail: String, val enabled: Boolean)

private const val NO_MICROPHONE =
    "Measuring plays a click and listens for it, so it needs the microphone. Grant it in Play it, " +
        "then come back."

/**
 * The button and the sentence under it. Both come from here so they cannot disagree — a live button
 * beside "the microphone is off" is the sort of thing that gets shipped.
 */
internal fun calibrationPrompt(state: Calibration, micGranted: Boolean): CalibrationPrompt = when {
    !micGranted -> CalibrationPrompt("Measure it", NO_MICROPHONE, enabled = false)

    state is Calibration.Running -> CalibrationPrompt(
        action = "Listening…",
        detail = "Playing a click and timing how long it takes to come back. Keep the phone still " +
            "and the room quiet for a second.",
        enabled = false,
    )

    state is Calibration.Finished -> CalibrationPrompt("Measure again", finishedDetail(state.result), enabled = true)

    else -> CalibrationPrompt(
        action = "Measure it",
        detail = "Plays a short click through the speaker and listens for it, which is the only way " +
            "to know what this phone's microphone path actually costs.",
        enabled = true,
    )
}

private fun finishedDetail(result: InputLatencyResult): String = when (result) {
    is InputLatencyResult.Measured ->
        "Measured ${result.millis.roundToInt()}ms on this path${confidenceText(result.confidence)}. " +
            "Timing on Play it is corrected by that from now on."

    is InputLatencyResult.Unmeasurable ->
        "Couldn't measure it: ${result.reason}. Play it still works — its timing keeps the assumed " +
            "figure, which is why the badge above says so."
}

/**
 * A measurement is only as good as how tightly the click was located, so the number never appears
 * without it. Below the threshold the figure is still used — it beats an assumption — but it is
 * presented as rough rather than as fact.
 */
private fun confidenceText(confidence: Double): String = when {
    confidence >= FIRM -> ""
    confidence >= ROUGH -> ", roughly"
    else -> ", though the click was too smeared to place it tightly, so treat it as a first guess"
}

private const val FIRM = 0.8
private const val ROUGH = 0.5
