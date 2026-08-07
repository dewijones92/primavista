package com.dewijones92.primavista.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ClickSynthTest {

    @Test
    fun isShortPercussiveAndWithinFullScale() {
        for (accent in listOf(false, true)) {
            val click = ClickSynth.render(RATE, accent)

            assertTrue("accent=$accent should be shorter than 50ms", click.size < RATE / 20)
            assertTrue("accent=$accent should be audible", click.maxOf { abs(it) } > AUDIBLE)
            assertTrue("accent=$accent clipped", click.all { abs(it) <= 1f })
        }
    }

    @Test
    fun startsAndEndsNearSilenceSoRetriggeringDoesNotClick() {
        val click = ClickSynth.render(RATE, accent = false)
        val peak = click.maxOf { abs(it) }

        assertEquals(0f, click[0], 0f)
        assertTrue("tail ${click.last()} against peak $peak", abs(click.last()) < peak * TAIL_FRACTION)
    }

    @Test
    fun accentIsLouderThanAPlainBeat() {
        val beat = ClickSynth.render(RATE, accent = false).maxOf { abs(it) }
        val accent = ClickSynth.render(RATE, accent = true).maxOf { abs(it) }

        assertTrue("accent $accent should exceed beat $beat", accent > beat)
    }

    @Test
    fun isDeterministicSoTwoRendersAreTheSameSound() {
        val first = ClickSynth.render(RATE, accent = true)
        val second = ClickSynth.render(RATE, accent = true)

        assertEquals(first.toList(), second.toList())
    }

    @Test
    fun scalesItsLengthWithTheSampleRate() {
        val low = ClickSynth.render(LOW_RATE, accent = false).size
        val high = ClickSynth.render(RATE, accent = false).size

        assertTrue("$high should be about ${RATE / LOW_RATE}x $low", high > low * 2)
    }

    @Test
    fun rejectsANonPositiveSampleRate() {
        assertThrows(IllegalArgumentException::class.java) { ClickSynth.render(0, accent = false) }
    }

    private companion object {
        const val RATE = 48_000
        const val LOW_RATE = 8_000
        const val AUDIBLE = 0.05f
        const val TAIL_FRACTION = 0.05f
    }
}
