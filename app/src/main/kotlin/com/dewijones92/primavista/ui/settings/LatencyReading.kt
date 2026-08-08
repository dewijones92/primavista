package com.dewijones92.primavista.ui.settings

import com.dewijones92.primavista.practice.InputLatency
import java.util.Locale

/**
 * How an audio route's latency is put into words.
 *
 * Pure and separately tested, because this is the one screen where the wrong wording *is* the bug:
 * docs/todos/measure-audio-latency.md exists to stop an assumed figure being presented as a
 * measured one, and a number with no provenance beside it is exactly that failure.
 */
internal data class LatencyReading(
    val figure: String,
    val provenance: String,
    val consequence: String,
    val measured: Boolean,
)

internal const val NO_FIGURE: String = "not measured"

internal fun latencyReading(millis: Double?, provenance: InputLatency.Provenance?): LatencyReading =
    LatencyReading(
        figure = latencyFigure(millis, provenance),
        provenance = provenanceWord(provenance),
        consequence = provenanceConsequence(provenance),
        measured = provenance == InputLatency.Provenance.Measured,
    )

private fun latencyFigure(millis: Double?, provenance: InputLatency.Provenance?): String = when {
    millis == null || provenance == null -> NO_FIGURE
    provenance == InputLatency.Provenance.NotApplicable -> "0 ms"
    else -> String.format(Locale.UK, "%.1f ms", millis)
}

internal fun provenanceWord(provenance: InputLatency.Provenance?): String = when (provenance) {
    null -> "no measurement on record"
    InputLatency.Provenance.Measured -> "measured"
    InputLatency.Provenance.PlatformReported -> "reported by the platform"
    InputLatency.Provenance.Assumed -> "assumed"
    InputLatency.Provenance.NotApplicable -> "nothing to correct"
}

internal fun provenanceConsequence(provenance: InputLatency.Provenance?): String = when (provenance) {
    null ->
        "Nothing has been measured on this route, so every millisecond figure a mic verdict " +
            "quotes carries a bias of unknown size."
    InputLatency.Provenance.Measured ->
        "Taken from a real loopback on this device, so mic timings are corrected by a figure " +
            "somebody actually checked."
    InputLatency.Provenance.PlatformReported ->
        "The platform stated this; it has not been confirmed against a loopback here, so treat " +
            "it as an estimate rather than a measurement."
    InputLatency.Provenance.Assumed ->
        "This figure was never measured. Mic verdicts on this route carry a systematic timing " +
            "bias of unknown size, which reads exactly like playing behind the beat."
    InputLatency.Provenance.NotApplicable ->
        "Taps are stamped by the input system at the touch itself, so there is nothing to correct."
}
