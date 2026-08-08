package com.dewijones92.primavista.ui.settings

import com.dewijones92.primavista.database.StoredReading
import com.dewijones92.primavista.practice.InputLatency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * docs/todos/measure-audio-latency.md exists to stop an assumed figure reading as a measured one.
 * These assertions are that document made executable at the screen that shows the number.
 */
class LatencyPresentationTest {

    @Test
    fun `an unmeasured route is not shown as zero`() {
        val reading = latencyReading(null, null)

        assertEquals(NO_FIGURE, reading.figure)
        assertFalse(reading.measured)
        assertTrue(reading.consequence.contains("unknown size"))
    }

    @Test
    fun `only a measured figure is called measured`() {
        InputLatency.Provenance.entries
            .filter { it != InputLatency.Provenance.Measured }
            .forEach { provenance ->
                val reading = latencyReading(61.0, provenance)
                assertFalse("$provenance is flagged as trustworthy", reading.measured)
                assertEquals("$provenance reads as 'measured'", false, reading.provenance == "measured")
            }

        assertTrue(latencyReading(61.0, InputLatency.Provenance.Measured).measured)
    }

    @Test
    fun `an assumed figure states the bias it carries`() {
        val reading = latencyReading(45.0, InputLatency.Provenance.Assumed)

        assertEquals("45.0 ms", reading.figure)
        assertTrue(reading.consequence.contains("never measured"))
        assertTrue(reading.consequence.contains("behind the beat"))
    }

    @Test
    fun `taps report nothing to correct rather than a suspiciously perfect zero`() {
        val reading = latencyReading(0.0, InputLatency.Provenance.NotApplicable)

        assertEquals("nothing to correct", reading.provenance)
        assertFalse(reading.measured)
    }

    @Test
    fun `a capped session count says so rather than reading as an exact total`() {
        assertEquals("200+ sessions stored.", storedSessionsText(readable(200, capped = true)))
        assertEquals("No sessions stored yet.", storedSessionsText(readable(0, capped = false)))
        assertEquals("1 session stored.", storedSessionsText(readable(1, capped = false)))
        assertEquals("Reading…", storedSessionsText(null))
    }

    @Test
    fun `a refused history is never worded as an empty one`() {
        val text = storedSessionsText(StoredReading.Unreadable(HISTORY, "an unknown provenance 'Guessed'"))

        assertTrue(text.contains("Couldn't read"))
        assertTrue(text.contains("Guessed"))
        assertFalse("a refusal reads as nothing practised", text.contains("No sessions stored"))
    }

    private fun readable(atLeast: Int, capped: Boolean) =
        StoredReading.Readable(SessionCount(atLeast, capped))
}
