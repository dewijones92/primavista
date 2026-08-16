package com.dewijones92.primavista.audio

import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTimestamp
import com.dewijones92.primavista.common.Diag
import com.dewijones92.primavista.common.NoOpDiag
import com.dewijones92.primavista.practice.AudioRoute
import kotlin.math.abs

/**
 * Whether RECORD_AUDIO is granted right now. A port so the capture can refuse before touching the
 * platform: on modern Android a denied mic yields silence rather than an error.
 * See .claude/CODE-NOTES.md.
 */
public fun interface MicrophonePermission {
    public fun isGranted(): Boolean
}

/**
 * Mono float PCM from the microphone, timestamped from `AudioRecord`'s own clock.
 * See .claude/CODE-NOTES.md.
 */
public class AudioRecordPcmCapture(
    private val micPermission: MicrophonePermission,
    private val diag: Diag = NoOpDiag,
    private val clock: MonotonicClock = SystemMonotonicClock,
    audioManager: AudioManager? = null,
    private val preferredSampleRates: List<Int> = DEFAULT_SAMPLE_RATES,
) : PcmCapture {

    /** The source and rate that actually opened, which is the line a report needs. */
    public data class Negotiated(
        val sourceName: String,
        val sampleRate: Int,
        val bufferFrames: Int,
        val route: AudioRoute,
    )

    private val negotiator = MicRouteNegotiator(diag, audioManager)

    @Volatile
    private var record: AudioRecord? = null

    @Volatile
    private var negotiated: Negotiated? = null

    @Volatile
    private var timebase: FrameTimebase = FrameTimebase(preferredSampleRates.first())

    @Volatile
    private var framePosition = 0L

    @Volatile
    private var released = false

    private var readsSinceTimestamp = 0
    private var everMeasured = false
    private var worstDriftFrames = 0L

    override val sampleRate: Int get() = negotiated?.sampleRate ?: preferredSampleRates.first()

    public val timestampProvenance: TimestampProvenance get() = timebase.provenance

    public val activeCapture: Negotiated? get() = negotiated

    override fun start(): CaptureStart {
        if (released) return refuse(CaptureStart.Refused.NoUsableConfiguration("this capture was released"))
        if (record != null) {
            diag.event(TAG, "start ignored: already capturing on ${negotiated?.sourceName}")
            return started()
        }
        if (!micPermission.isGranted()) return refuse(CaptureStart.Refused.PermissionDenied)
        return when (val outcome = negotiator.open(preferredSampleRates)) {
            is MicRouteNegotiator.Outcome.Opened -> {
                install(outcome)
                started()
            }

            MicRouteNegotiator.Outcome.PermissionDenied -> refuse(CaptureStart.Refused.PermissionDenied)

            is MicRouteNegotiator.Outcome.NoneUsable ->
                refuse(CaptureStart.Refused.NoUsableConfiguration(outcome.reason))
        }
    }

    override fun stop() {
        val active = record ?: run {
            diag.event(TAG, "stop ignored: not capturing")
            return
        }
        record = null
        runCatching { active.stop() }.onFailure { diag.event(TAG, "stop failed: ${it.message}") }
        active.release()
        diag.event(
            TAG,
            "stopped source=${negotiated?.sourceName} route=${negotiated?.route?.id} frames=$framePosition " +
                "timebase=${timebase.provenance} deviceEverReported=$everMeasured " +
                "worstDrift=${worstDriftFrames}frames",
        )
    }

    override fun release() {
        stop()
        released = true
        diag.event(TAG, "released")
    }

    override fun read(into: FloatArray): CaptureRead {
        val active = record ?: run {
            diag.counted(TAG, "readsWhileStopped")
            return CaptureRead(0, framePosition)
        }
        val firstFrame = framePosition
        val frames = active.read(into, 0, into.size, AudioRecord.READ_BLOCKING)
        if (frames < 0) {
            diag.counted(TAG, "readErrors")
            diag.event(TAG, "read failed code=$frames at frame=$firstFrame")
            return CaptureRead(0, firstFrame)
        }
        framePosition = firstFrame + frames
        diag.counted(TAG, "reads")
        diag.counted(TAG, "framesRead", frames)
        refreshAnchor(active)
        return CaptureRead(frames, firstFrame)
    }

    override fun frameTimestampNanos(frame: Long): Long = timebase.nanosFor(frame)

    private fun started(): CaptureStart.Started = CaptureStart.Started(
        sampleRate = sampleRate,
        audioSourceName = negotiated?.sourceName ?: UNKNOWN_SOURCE,
        timestampProvenance = timebase.provenance,
        route = negotiated?.route ?: AudioRoute.Unidentified,
    )

    private fun refuse(refusal: CaptureStart.Refused): CaptureStart.Refused {
        diag.event(TAG, "capture refused: ${refusal.reason}")
        return refusal
    }

    private fun install(opened: MicRouteNegotiator.Outcome.Opened) {
        record = opened.record
        val route = DeviceAudioRoutes.routeOf(opened.record.routedDevice)
        negotiated = Negotiated(opened.sourceName, opened.sampleRate, opened.bufferFrames, route)
        framePosition = 0L
        readsSinceTimestamp = 0
        everMeasured = false
        worstDriftFrames = 0L
        timebase = FrameTimebase(opened.sampleRate).apply { anchorFromStart(0L, clock.nowNanos()) }
        diag.event(
            TAG,
            "capturing source=${opened.sourceName} route=${route.id} rate=${opened.sampleRate}Hz " +
                "encoding=float mono buffer=${opened.bufferFrames}frames " +
                "timebase=ExtrapolatedFromStart (until the device reports one)",
        )
        diag.state(TAG) {
            "source=${negotiated?.sourceName} route=${negotiated?.route?.id} frames=$framePosition " +
                "timebase=${timebase.provenance} worstDrift=${worstDriftFrames}frames"
        }
    }

    private fun refreshAnchor(active: AudioRecord) {
        readsSinceTimestamp++
        if (readsSinceTimestamp < TIMESTAMP_REFRESH_READS) return
        readsSinceTimestamp = 0
        val timestamp = AudioTimestamp()
        val status = active.getTimestamp(timestamp, AudioTimestamp.TIMEBASE_MONOTONIC)
        if (status != AudioRecord.SUCCESS || timestamp.framePosition <= 0L) {
            diag.counted(TAG, "timestampMisses")
            return
        }
        timebase.anchorFromDevice(timestamp.framePosition, timestamp.nanoTime)
        diag.counted(TAG, "timestampAnchors")
        reportFrameDrift(timestamp.framePosition)
        if (!everMeasured) {
            everMeasured = true
            diag.event(
                TAG,
                "timebase upgraded to DeviceReported at frame=${timestamp.framePosition} " +
                    "nanos=${timestamp.nanoTime} (read frame=$framePosition)",
            )
        }
    }

    /**
     * The device counts frames it captured; we count frames we read. A gap means an overrun
     * dropped audio, which no correction can undo. See .claude/CODE-NOTES.md.
     */
    private fun reportFrameDrift(deviceFrame: Long) {
        val drift = deviceFrame - framePosition
        if (abs(drift) <= DRIFT_TOLERANCE_FRAMES) return
        diag.counted(TAG, "frameDrifts")
        if (abs(drift) <= abs(worstDriftFrames)) return
        worstDriftFrames = drift
        diag.event(
            TAG,
            "frame drift ${drift}frames (device=$deviceFrame read=$framePosition) — an overrun " +
                "dropped audio, so mic timestamps on this route are off by up to that much",
        )
    }

    public companion object {
        public val DEFAULT_SAMPLE_RATES: List<Int> = listOf(48_000, 44_100)

        /** Reads between `getTimestamp` calls; the anchor drifts slowly, so this need not be hot. */
        public const val TIMESTAMP_REFRESH_READS: Int = 8

        /** A read in flight legitimately puts the two counters a buffer or two apart. */
        public const val DRIFT_TOLERANCE_FRAMES: Long = 4_096L

        private const val TAG = "audio.capture"
        private const val UNKNOWN_SOURCE = "unknown"
    }
}
