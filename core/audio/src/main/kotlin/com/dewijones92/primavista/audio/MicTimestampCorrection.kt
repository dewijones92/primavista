package com.dewijones92.primavista.audio

import com.dewijones92.primavista.practice.InputLatency

/** A mic onset's correction, with the reported-but-not-applied analysis delay kept beside it. */
public data class TimestampCorrection(
    val onsetNanos: Long,
    val detectionDelayNanos: Long,
    val inputLatencyNanos: Long,
    val provenance: InputLatency.Provenance,
) {
    public val correctedNanos: Long get() = onsetNanos - inputLatencyNanos

    override fun toString(): String =
        "onset=${FrameTimebase.nanosToMillis(onsetNanos)}ms " +
            "detect=${FrameTimebase.nanosToMillis(detectionDelayNanos)}ms(reported) " +
            "lat=-${FrameTimebase.nanosToMillis(inputLatencyNanos)}ms($provenance)"
}

/**
 * The single boundary where capture frames become the Conductor's timebase (docs/spec.md I2).
 * See .claude/CODE-NOTES.md.
 */
public object MicTimestampCorrection {
    public fun correct(
        onsetNanos: Long,
        detectionDelayFrames: Int,
        sampleRate: Int,
        latency: InputLatency,
    ): TimestampCorrection = TimestampCorrection(
        onsetNanos = onsetNanos,
        detectionDelayNanos = FrameTimebase.framesToNanos(detectionDelayFrames.toLong(), sampleRate),
        inputLatencyNanos = FrameTimebase.millisToNanos(latency.millis),
        provenance = latency.provenance,
    )
}
