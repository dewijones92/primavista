package com.dewijones92.primavista.audio

/**
 * Maps a capture frame index to the system's monotonic timebase by extrapolating from one anchor.
 * See .claude/CODE-NOTES.md.
 */
public class FrameTimebase(public val sampleRate: Int) {
    init {
        require(sampleRate > 0) { "sample rate must be positive, was $sampleRate" }
    }

    private data class Anchor(val frame: Long, val nanos: Long, val provenance: TimestampProvenance)

    @Volatile
    private var anchor: Anchor = Anchor(0L, 0L, TimestampProvenance.ExtrapolatedFromStart)

    public val provenance: TimestampProvenance get() = anchor.provenance

    public val anchorFrame: Long get() = anchor.frame

    public val anchorNanos: Long get() = anchor.nanos

    /** Anchors on `AudioRecord`'s own timestamp. */
    public fun anchorFromDevice(frame: Long, nanos: Long) {
        anchor = Anchor(frame, nanos, TimestampProvenance.DeviceReported)
    }

    /** Anchors on the moment capture started, which contains the HAL's input latency. */
    public fun anchorFromStart(frame: Long, nanos: Long) {
        anchor = Anchor(frame, nanos, TimestampProvenance.ExtrapolatedFromStart)
    }

    public fun nanosFor(frame: Long): Long {
        val current = anchor
        return current.nanos + framesToNanos(frame - current.frame, sampleRate)
    }

    override fun toString(): String {
        val current = anchor
        return "FrameTimebase(rate=${sampleRate}Hz anchorFrame=${current.frame} " +
            "anchorNanos=${current.nanos} provenance=${current.provenance})"
    }

    public companion object {
        public const val NANOS_PER_SECOND: Long = 1_000_000_000L
        public const val NANOS_PER_MILLI: Long = 1_000_000L

        /** Floor division so the rounding direction does not flip either side of the anchor. */
        public fun framesToNanos(frames: Long, sampleRate: Int): Long =
            Math.floorDiv(frames * NANOS_PER_SECOND, sampleRate.toLong())

        public fun nanosToFrames(nanos: Long, sampleRate: Int): Long =
            Math.floorDiv(nanos * sampleRate.toLong(), NANOS_PER_SECOND)

        public fun millisToNanos(millis: Double): Long = Math.round(millis * NANOS_PER_MILLI)

        public fun nanosToMillis(nanos: Long): Double = nanos.toDouble() / NANOS_PER_MILLI

        public fun framesToMillis(frames: Long, sampleRate: Int): Double =
            nanosToMillis(framesToNanos(frames, sampleRate))
    }
}
