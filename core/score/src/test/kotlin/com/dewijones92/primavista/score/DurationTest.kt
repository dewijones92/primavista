package com.dewijones92.primavista.score

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DurationTest {

    private companion object {
        val COMMON_TUPLETS = listOf(3 to 2, 5 to 4, 6 to 4, 7 to 4, 9 to 8, 2 to 3)
    }

    @Test
    fun `plain symbols have their textbook tick lengths`() {
        val expected = mapOf(
            NoteSymbol.DoubleWhole to 80640L,
            NoteSymbol.Whole to 40320L,
            NoteSymbol.Half to 20160L,
            NoteSymbol.Quarter to 10080L,
            NoteSymbol.Eighth to 5040L,
            NoteSymbol.Sixteenth to 2520L,
            NoteSymbol.ThirtySecond to 1260L,
        )
        assertEquals(NoteSymbol.entries.size, expected.size)
        for ((symbol, ticks) in expected) {
            assertEquals(symbol.name, ticks, Duration(symbol).ticks.value)
        }
    }

    @Test
    fun `dots add exactly half and then a quarter again`() {
        for (symbol in NoteSymbol.entries) {
            val plain = Duration(symbol).ticks.value
            assertEquals("$symbol one dot", plain * 3 / 2, Duration(symbol, dots = 1).ticks.value)
            assertEquals("$symbol two dots", plain * 7 / 4, Duration(symbol, dots = 2).ticks.value)
        }
    }

    @Test
    fun `every symbol times every dot count times every common tuplet is exact or refused`() {
        var refusals = 0
        for (symbol in NoteSymbol.entries) {
            for (dots in 0..Duration.MAX_DOTS) {
                refusals += refusedTupletsOf(symbol, dots)
            }
        }
        assertEquals("a double-dotted 32nd duplet is the only shape 10080 cannot hold", 1, refusals)
    }

    private fun refusedTupletsOf(symbol: NoteSymbol, dots: Int): Int {
        val plain = Duration(symbol, dots).ticks.value
        var refusals = 0
        for ((actual, normal) in COMMON_TUPLETS) {
            val duration = Duration(symbol, dots, actual, normal)
            val label = "$symbol dots=$dots at $actual:$normal"
            val exact = (plain * normal) % actual == 0L
            if (exact) {
                assertEquals(label, plain * normal, duration.ticks.value * actual)
                assertTrue(label, duration.ticks.value > 0)
            } else {
                refusals++
                assertThrows(label, IllegalArgumentException::class.java) { duration.ticks }
            }
        }
        return refusals
    }

    @Test
    fun `written values add up the way a bar does`() {
        val quarter = Duration(NoteSymbol.Quarter).ticks
        assertEquals(quarter.value, Duration(NoteSymbol.Eighth, 0, 3, 2).ticks.value * 3)
        assertEquals(quarter.value, Duration(NoteSymbol.Sixteenth).ticks.value * 4)
        assertEquals(
            Duration(NoteSymbol.Half).ticks.value,
            Duration(NoteSymbol.Quarter, dots = 1).ticks.value + Duration(NoteSymbol.Eighth).ticks.value,
        )
        assertEquals(
            Duration(NoteSymbol.Whole).ticks.value,
            Duration(NoteSymbol.Half, dots = 2).ticks.value + Duration(NoteSymbol.Eighth).ticks.value,
        )
    }

    @Test
    fun `an unrepresentable tuplet is refused rather than truncated`() {
        val elevenTupletOfThirtySeconds = Duration(NoteSymbol.ThirtySecond, 0, 11, 1)
        val failure = assertThrows(IllegalArgumentException::class.java) { elevenTupletOfThirtySeconds.ticks }
        assertTrue(failure.message!!.contains("not representable"))
    }

    @Test
    fun `ticksOrNull rejects exactly what ticks throws for, and agrees on everything else`() {
        val unrepresentable = Duration(NoteSymbol.ThirtySecond, 0, 11, 1)
        assertNull(unrepresentable.ticksOrNull)
        assertThrows(IllegalArgumentException::class.java) { unrepresentable.ticks }
        for (symbol in NoteSymbol.entries) {
            for (dots in 0..Duration.MAX_DOTS) {
                val duration = Duration(symbol, dots)
                assertEquals("$symbol dots=$dots", duration.ticks, duration.ticksOrNull)
            }
        }
    }

    @Test
    fun `illegal written values cannot be constructed`() {
        assertThrows(IllegalArgumentException::class.java) { Duration(NoteSymbol.Quarter, dots = 3) }
        assertThrows(IllegalArgumentException::class.java) { Duration(NoteSymbol.Quarter, 0, 0, 1) }
        assertThrows(IllegalArgumentException::class.java) { Duration(NoteSymbol.Quarter, 0, 3, 0) }
    }

    @Test
    fun `a bar knows its own length in ticks`() {
        assertEquals(40320L, TimeSignature(4, 4).measureTicks.value)
        assertEquals(30240L, TimeSignature(3, 4).measureTicks.value)
        assertEquals(30240L, TimeSignature(6, 8).measureTicks.value)
        assertEquals(17640L, TimeSignature(7, 16).measureTicks.value)
    }

    @Test
    fun `MusicalTime counts in quarters and wholes`() {
        assertEquals(MusicalTime.TICKS_PER_QUARTER, MusicalTime.quarters(1).value)
        assertEquals(40320L, MusicalTime.wholes(1).value)
        assertEquals(MusicalTime.quarters(8), MusicalTime.wholes(2))
        assertEquals(Ticks(30240), MusicalTime.quarters(2) + MusicalTime.quarters(1))
        assertEquals(Ticks(10080), MusicalTime.quarters(3) - MusicalTime.quarters(2))
        assertTrue(MusicalTime.quarters(1) > Ticks.ZERO)
    }

    @Test
    fun `a written value can be recovered from a tick length`() {
        assertEquals(Duration(NoteSymbol.Whole), durationOfTicks(Ticks(40320)))
        assertEquals(Duration(NoteSymbol.Half, dots = 1), durationOfTicks(Ticks(30240)))
        assertEquals(null, durationOfTicks(Ticks(17640 + 1)))
    }
}
