package com.dewijones92.primavista.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These are the tests for docs/spec.md I7's foundation. The buffer is what every other invariant is
 * checked *through*, so a fault here is invisible in every other test and fatal to all of them.
 */
class RingBufferDiagTest {

    private var now = 0L
    private fun diag(capacity: Int = 10, flushEvery: Int = 5) =
        RingBufferDiag(capacity = capacity, countedFlushEvery = flushEvery, nanoTime = { now })

    @Test
    fun `events appear in the report oldest first`() {
        val diag = diag()
        diag.event("a", "first")
        now += 5_000_000
        diag.event("b", "second")

        val report = diag.report()
        assertTrue(report.contains("[a] first"))
        assertTrue(report.contains("[b] second"))
        assertTrue("oldest must come first", report.indexOf("first") < report.indexOf("second"))
    }

    @Test
    fun `timestamps are relative to the first event and stated in milliseconds`() {
        val diag = diag()
        diag.event("a", "start")
        now += 42_000_000
        diag.event("a", "later")

        assertTrue(diag.report().contains("+42ms [a] later"))
    }

    /**
     * The property the whole design turns on. A buffer that silently drops its oldest entries is
     * indistinguishable from one that was never written to, which is how a report comes to be
     * believed about something it no longer contains.
     */
    @Test
    fun `overflow drops the oldest and says how many it dropped`() {
        val diag = diag(capacity = 3)
        repeat(5) { diag.event("tag", "event$it") }

        val report = diag.report()
        assertFalse("event0 should have been evicted", report.contains("event0"))
        assertFalse("event1 should have been evicted", report.contains("event1"))
        assertTrue(report.contains("event4"))
        assertTrue("the count of dropped entries must be stated", report.contains("2 dropped"))
    }

    /**
     * The rule that stops a hot path destroying the history it is meant to preserve: many
     * occurrences must cost a bounded number of entries, and none may be lost from the tally.
     */
    @Test
    fun `a hot counted event costs few entries and loses no count`() {
        val diag = diag(capacity = 10, flushEvery = 100)
        repeat(1000) { diag.counted("audio", "frames") }

        val report = diag.report()
        assertTrue("the total must survive in full", report.contains("audio/frames: 1000"))
        val lines = report.lines().count { it.contains("[audio]") }
        assertTrue("1000 occurrences must not become 1000 lines, was $lines", lines <= 10)
        assertTrue("the periodic flush should have logged something", lines > 0)
    }

    @Test
    fun `counted tallies are kept per key`() {
        val diag = diag()
        repeat(3) { diag.counted("input", "tap") }
        repeat(2) { diag.counted("input", "mic") }

        val report = diag.report()
        assertTrue(report.contains("input/tap: 3"))
        assertTrue(report.contains("input/mic: 2"))
    }

    @Test
    fun `a non-positive increment is ignored rather than corrupting the tally`() {
        val diag = diag()
        diag.counted("x", "y", increment = 0)
        diag.counted("x", "y", increment = -5)

        assertFalse(diag.report().contains("x/y"))
    }

    /** State is evaluated at report time, so a block registered once stays current. */
    @Test
    fun `state reflects the value at report time, not at registration`() {
        val diag = diag()
        var position = 0
        diag.state("transport") { "pos=$position" }
        position = 480

        assertTrue(diag.report().contains("[transport] pos=480"))
    }

    @Test
    fun `state is replaced in place rather than accumulating`() {
        val diag = diag()
        diag.state("transport") { "stale" }
        diag.state("transport") { "current" }

        val lines = diag.report().lines().filter { it.contains("[transport]") }
        assertEquals("a tag must contribute exactly one state line", 1, lines.size)
        assertTrue(lines.single().contains("current"))
        assertFalse(lines.single().contains("stale"))
    }

    /**
     * A snapshot that throws must cost its own line and nothing more. Losing the whole report to one
     * bad lambda would be the worst possible trade, since the report is the only evidence there is.
     */
    @Test
    fun `a throwing state block degrades to a note instead of losing the report`() {
        val diag = diag()
        diag.state("bad") { error("boom") }
        diag.event("good", "still here")

        val report = diag.report()
        assertTrue(report.contains("<threw IllegalStateException>"))
        assertTrue(report.contains("still here"))
    }

    @Test
    fun `the header travels with the report`() {
        val report = diag().report(mapOf("build" to "abc1234", "device" to "Pixel 7"))

        assertTrue(report.contains("build: abc1234"))
        assertTrue(report.contains("device: Pixel 7"))
    }

    @Test
    fun `NoOpDiag discards everything and says so`() {
        NoOpDiag.event("a", "b")
        NoOpDiag.counted("a", "b")
        NoOpDiag.state("a") { "c" }

        assertEquals("(diagnostics disabled)", NoOpDiag.report())
    }

    /** Audio callbacks, the UI and the judge all write to one buffer. */
    @Test
    fun `concurrent writers do not lose or corrupt entries`() {
        val diag = RingBufferDiag(capacity = 10_000, countedFlushEvery = 1_000_000, nanoTime = { 0 })
        val threads = (0 until 8).map { thread ->
            Thread {
                repeat(500) { i ->
                    diag.event("t$thread", "e$i")
                    diag.counted("shared", "hits")
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        val report = diag.report()
        assertTrue("every count must be present", report.contains("shared/hits: 4000"))
        assertEquals(4000, report.lines().count { it.contains("] e") })
    }
}
