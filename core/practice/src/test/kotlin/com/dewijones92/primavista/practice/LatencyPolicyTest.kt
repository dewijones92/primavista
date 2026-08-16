package com.dewijones92.primavista.practice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val NOW = 1_700_000_000_000L
private const val A_DAY = 86_400_000L

/**
 * Which latency figure applies to which path.
 *
 * The failure this guards is named in docs/todos/measure-audio-latency.md: a Bluetooth headset
 * quietly inheriting the built-in mic's figure. It would not crash, would not look wrong, and would
 * put every verdict out by a hundred milliseconds and more.
 */
class LatencyPolicyTest {

    private val builtIn = AudioRoute(RouteKind.BuiltIn, "Pixel built-in mic")
    private val headset = AudioRoute(RouteKind.Bluetooth, "WH-1000XM4")

    @Test
    fun `a figure measured on this very route is the one that applies`() {
        val stored = RouteLatency(builtIn, InputLatency(41.5, InputLatency.Provenance.Measured), NOW - A_DAY)

        val applied = LatencyPolicy.decide(builtIn, stored, NOW)

        assertEquals(41.5, applied.latency.millis, 0.0)
        assertEquals(InputLatency.Provenance.Measured, applied.latency.provenance)
        assertTrue(applied.isMeasured)
        assertTrue(applied.why, applied.why.contains("yesterday"))
    }

    /** The whole point of keying by route. A headset's figure is not the built-in mic's. */
    @Test
    fun `a figure measured on another route is refused and the refusal names it`() {
        val stored = RouteLatency(builtIn, InputLatency(41.5, InputLatency.Provenance.Measured), NOW)

        val applied = LatencyPolicy.decide(headset, stored, NOW)

        assertNotEquals(41.5, applied.latency.millis, 0.0)
        assertEquals(InputLatency.Provenance.Assumed, applied.latency.provenance)
        assertTrue(applied.why, applied.why.contains(builtIn.id))
    }

    @Test
    fun `a radio path never assumes what a wired one costs`() {
        val onBuiltIn = LatencyPolicy.decide(builtIn, stored = null, nowEpochMillis = NOW)
        val onHeadset = LatencyPolicy.decide(headset, stored = null, nowEpochMillis = NOW)

        assertTrue("bluetooth must assume more, not the same", onHeadset.latency.millis > onBuiltIn.latency.millis)
        assertTrue(RouteKind.Bluetooth.isRadio)
    }

    @Test
    fun `an unmeasured path says so rather than reading as zero`() {
        val applied = LatencyPolicy.decide(builtIn, stored = null, nowEpochMillis = NOW)

        assertEquals(InputLatency.Provenance.Assumed, applied.latency.provenance)
        assertTrue(applied.latency.millis > 0.0)
        assertTrue(applied.why, applied.why.isNotBlank())
        assertTrue(applied.why, applied.why.contains("nothing has ever been measured"))
    }

    /** An assumption that was stored is still an assumption; storing it does not promote it. */
    @Test
    fun `a stored figure that was itself assumed is not treated as measured`() {
        val stored = RouteLatency(builtIn, InputLatency(60.0, InputLatency.Provenance.Assumed), NOW)

        val applied = LatencyPolicy.decide(builtIn, stored, NOW)

        assertEquals(InputLatency.Provenance.Assumed, applied.latency.provenance)
        assertTrue(applied.why, applied.why.contains("not measured"))
    }

    /** Diagnostics rule: a report must be able to say why the figure is the one it is. */
    @Test
    fun `every decision carries a reason and the route in one line`() {
        val ancient = RouteLatency(builtIn, InputLatency(30.0, InputLatency.Provenance.Measured), 0L)
        val decisions = listOf(
            LatencyPolicy.decide(builtIn, null, NOW),
            LatencyPolicy.decide(headset, RouteLatency(builtIn, InputLatency.None, NOW), NOW),
            LatencyPolicy.decide(builtIn, ancient, NOW),
        )

        for (decision in decisions) {
            assertTrue("$decision", decision.toString().contains(decision.route.id))
            assertTrue("$decision", decision.toString().contains("${decision.latency.provenance}"))
            assertTrue("$decision", decision.why.isNotBlank())
        }
    }

    @Test
    fun `a clock that moved backwards is described rather than printed as a negative age`() {
        val fromTheFuture = RouteLatency(builtIn, InputLatency(30.0, InputLatency.Provenance.Measured), NOW + A_DAY)

        val applied = LatencyPolicy.decide(builtIn, fromTheFuture, NOW)

        assertTrue(applied.why, applied.why.contains("clock"))
        assertTrue(applied.why, !applied.why.contains("-1"))
    }
}

/** The id is what gets stored, so it has to survive a round trip and an unfamiliar build. */
class AudioRouteIdTest {

    @Test
    fun `every kind round-trips through the stored id`() {
        for (kind in RouteKind.entries) {
            val route = AudioRoute(kind, "some device")
            assertEquals(route, AudioRoute.of(route.id))
        }
    }

    @Test
    fun `a device name containing the separator still round-trips`() {
        val awkward = AudioRoute(RouteKind.Usb, "Focusrite Scarlett 2i2 / USB")

        assertEquals(awkward, AudioRoute.of(awkward.id))
    }

    @Test
    fun `an id written by a build this one does not know becomes Unknown rather than throwing`() {
        val fromTheFuture = AudioRoute.of("Telepathy/direct to cortex")

        assertEquals(RouteKind.Unknown, fromTheFuture.kind)
        assertEquals("Telepathy/direct to cortex", fromTheFuture.name)
    }

    @Test
    fun `an empty id is a route like any other and does not throw`() {
        assertEquals(RouteKind.Unknown, AudioRoute.of("").kind)
    }
}
