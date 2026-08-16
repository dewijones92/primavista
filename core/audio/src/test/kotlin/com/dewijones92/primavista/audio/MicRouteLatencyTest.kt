package com.dewijones92.primavista.audio

import com.dewijones92.primavista.pitch.Hertz
import com.dewijones92.primavista.pitch.MonophonicNoteTracker
import com.dewijones92.primavista.pitch.TrackedNote
import com.dewijones92.primavista.practice.AppliedLatency
import com.dewijones92.primavista.practice.AudioRoute
import com.dewijones92.primavista.practice.InputLatency
import com.dewijones92.primavista.practice.LatencyPolicy
import com.dewijones92.primavista.practice.RouteKind
import com.dewijones92.primavista.practice.RouteLatencies
import com.dewijones92.primavista.practice.RouteLatency
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val A4_HERTZ = 440.0
private const val GOOD_CONFIDENCE = 0.95f
private const val ONSET_FRAME = 4_800L
private const val MEASURED_MILLIS = 41.5
private const val NOW = 1_700_000_000_000L

/**
 * The mic applies the figure for the path it actually opened on, and records a measurement against
 * that same path.
 *
 * Until this existed the whole calibration engine had no ignition: `LoopbackCalibrator`,
 * `LatencyPolicy` and a per-route table in Room all worked, and nothing in the app ever called
 * them, so every mic verdict silently used a 60ms assumption for ever
 * (docs/todos/measure-audio-latency.md).
 */
class MicRouteLatencyTest {

    private val builtIn = AudioRoute(RouteKind.BuiltIn, "fake built-in mic")
    private val headset = AudioRoute(RouteKind.Bluetooth, "WH-1000XM4")
    private val measuredOnBuiltIn =
        RouteLatency(builtIn, InputLatency(MEASURED_MILLIS, InputLatency.Provenance.Measured), NOW)

    @Test
    fun `opening a session applies the figure measured on that very path`() = runBlocking {
        val known = FakeRouteLatencies(measuredOnBuiltIn)
        val source = source(FakeCapture(maxReads = 1, route = builtIn), known)

        source.notes().toList()

        assertEquals(MEASURED_MILLIS, source.latency.millis, 0.0)
        assertEquals(InputLatency.Provenance.Measured, source.latency.provenance)
    }

    /**
     * The failure the whole area exists to prevent. A figure measured on the built-in mic must not
     * follow the session onto a Bluetooth headset, whose path costs several times as much.
     */
    @Test
    fun `a bluetooth session does not inherit the built-in mic figure`() = runBlocking {
        val known = FakeRouteLatencies(measuredOnBuiltIn)
        val source = source(FakeCapture(maxReads = 1, route = headset), known)

        source.notes().toList()

        assertEquals(InputLatency.Provenance.Assumed, source.latency.provenance)
        assertTrue("${source.latency}", source.latency.millis > MEASURED_MILLIS)
        assertEquals(headset, source.latencyProvenance.route)
    }

    @Test
    fun `before any capture opens the figure is assumed and says the path is unknown`() {
        val source = source(FakeCapture(), FakeRouteLatencies(null))

        assertEquals(InputLatency.Provenance.Assumed, source.latency.provenance)
        assertEquals(AudioRoute.Unidentified, source.latencyProvenance.route)
        assertTrue(source.latencyProvenance.why, source.latencyProvenance.why.contains("no capture has opened"))
    }

    /** A note is corrected by the figure for its own route, not by whatever was applied last. */
    @Test
    fun `the note leaving the mic is corrected by the route figure`() = runBlocking {
        val known = FakeRouteLatencies(measuredOnBuiltIn)
        val capture = FakeCapture(maxReads = 1, route = builtIn)
        val note = TrackedNote(Hertz(A4_HERTZ), ONSET_FRAME, GOOD_CONFIDENCE, 0)

        val played = source(capture, known, FakeTracker(listOf(note))).notes().toList()

        assertEquals(1, played.size)
        val uncorrected = capture.frameTimestampNanos(ONSET_FRAME)
        assertEquals(uncorrected - (MEASURED_MILLIS * NANOS_PER_MILLI).toLong(), played[0].atNanos)
    }

    @Test
    fun `a successful measurement is stored against the route it was measured on`() = runBlocking {
        val known = FakeRouteLatencies(null)
        val source = source(
            FakeCapture(clickAtFrame = CLICK_FRAME, route = headset),
            known,
            player = AnchoredTonePlayer(0L)
        )

        val result = source.calibrateLatency()

        assertTrue("$result", result is InputLatencyResult.Measured)
        assertEquals(listOf(headset), known.recorded.map { it.first })
        assertEquals(InputLatency.Provenance.Measured, known.recorded.single().second.provenance)
    }

    /** A measurement that failed stores nothing — a stored guess reads as fact next time. */
    @Test
    fun `a failed measurement stores nothing and falls back to the route assumption`() = runBlocking {
        val known = FakeRouteLatencies(null)
        val silent = FakeCapture(clickAtFrame = null, route = headset)
        val source = source(silent, known, player = AnchoredTonePlayer(0L))

        val result = source.calibrateLatency()

        assertTrue("$result", result is InputLatencyResult.Unmeasurable)
        assertTrue("nothing should have been stored: ${known.recorded}", known.recorded.isEmpty())
        assertEquals(InputLatency.Provenance.Assumed, source.latency.provenance)
        assertEquals(headset, source.latencyProvenance.route)
    }

    /**
     * Android reroutes a live capture when a headset connects. A note arriving over a radio hop
     * after that must not be corrected by the figure measured on the built-in mic.
     */
    @Test
    fun `a headset connecting mid-session moves the figure with it`() = runBlocking {
        val diag = RecordingDiag()
        val moving = FakeCapture(maxReads = 2, route = builtIn, rerouteTo = headset, rerouteAfterReads = 1)
        val note = TrackedNote(Hertz(A4_HERTZ), ONSET_FRAME, GOOD_CONFIDENCE, 0)
        val heard = FakeTracker(listOf(note), listOf(note))
        val source = source(moving, FakeRouteLatencies(measuredOnBuiltIn), heard, diag = diag)

        val played = source.notes().toList()

        assertEquals(2, played.size)
        assertEquals(headset, source.latencyProvenance.route)
        assertEquals(InputLatency.Provenance.Assumed, source.latency.provenance)
        assertNotNull(
            "a report must be able to see the path move: ${diag.events}",
            diag.events.firstOrNull { it.contains("rerouted") && it.contains(headset.id) },
        )
    }

    /** The store is read once per path, not once per note — a note is a hot path. */
    @Test
    fun `staying on one path does not re-read the stored figure for every note`() = runBlocking {
        val known = FakeRouteLatencies(measuredOnBuiltIn)
        val steady = FakeCapture(maxReads = 3, route = builtIn)
        val note = TrackedNote(Hertz(A4_HERTZ), ONSET_FRAME, GOOD_CONFIDENCE, 0)

        source(steady, known, FakeTracker(listOf(note), listOf(note), listOf(note))).notes().toList()

        assertEquals(1, known.reads)
    }

    @Test
    fun `the listening line names the route and what its figure is worth`() = runBlocking {
        val diag = RecordingDiag()
        val onHeadset = FakeCapture(maxReads = 1, route = headset)
        val source = source(onHeadset, FakeRouteLatencies(null), diag = diag)

        source.notes().toList()

        assertNotNull(
            "a report must be able to say which path a session used: ${diag.events}",
            diag.events.firstOrNull { it.contains(headset.id) && it.contains("Assumed") },
        )
    }

    private fun source(
        capture: FakeCapture,
        latencies: RouteLatencies,
        tracker: MonophonicNoteTracker = FakeTracker(emptyList()),
        player: TonePlayer? = null,
        diag: RecordingDiag = RecordingDiag(),
    ) = MicPitchAnswerSource(
        capture = capture,
        trackerFor = { tracker },
        tonePlayer = player,
        routeLatencies = latencies,
        diag = diag,
        bufferFrames = FAKE_FRAMES_PER_READ,
    )

    private class FakeRouteLatencies(private val stored: RouteLatency?) : RouteLatencies {
        val recorded = mutableListOf<Pair<AudioRoute, InputLatency>>()
        var reads = 0
            private set

        override suspend fun applied(route: AudioRoute): AppliedLatency {
            reads++
            return LatencyPolicy.decide(route, recordedFor(route) ?: stored, NOW)
        }

        override suspend fun record(route: AudioRoute, latency: InputLatency) {
            recorded += route to latency
        }

        private fun recordedFor(route: AudioRoute): RouteLatency? =
            recorded.lastOrNull { it.first == route }?.let { RouteLatency(it.first, it.second, NOW) }
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000.0
    }
}
