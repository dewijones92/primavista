package com.dewijones92.primavista.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

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

    /** The MODE_STATIC buffer is sized from this length, so it is a contract not a detail. */
    @Test
    fun isExactlyThirtyMillisecondsLongAtEverySampleRate() {
        for (rate in listOf(LOW_RATE, CD_RATE, RATE)) {
            for (accent in listOf(false, true)) {
                assertEquals(
                    "rate=$rate accent=$accent",
                    rate * CLICK_MILLIS / MILLIS_PER_SECOND,
                    ClickSynth.render(rate, accent).size,
                )
            }
        }
    }

    @Test
    fun isASoundingTickRatherThanSilenceOrASingleSpike() {
        for (accent in listOf(false, true)) {
            val click = ClickSynth.render(RATE, accent)
            val energy = click.map { (it * it).toDouble() }
            val rms = sqrt(energy.sum() / click.size)
            val body = framesToMostOfTheEnergy(energy)

            assertTrue("accent=$accent rms=$rms", rms > MIN_RMS)
            assertTrue("accent=$accent spends its energy in $body frames", body > RATE / MILLIS_PER_SECOND)
        }
    }

    private fun framesToMostOfTheEnergy(energy: List<Double>): Int {
        val target = energy.sum() * MOST_OF_THE_ENERGY
        var running = 0.0
        return energy.indexOfFirst {
            running += it
            running >= target
        } + 1
    }

    /** A static buffer is retriggered from frame 0, so its end meets its start. */
    @Test
    fun theSeamBackToTheStartIsGentlerThanTheClicksOwnSteepestSlope() {
        for (accent in listOf(false, true)) {
            val click = ClickSynth.render(RATE, accent)
            val steepestStep = (1 until click.size).maxOf { abs(click[it] - click[it - 1]) }
            val seamStep = abs(click.first() - click.last())

            assertTrue("accent=$accent seam $seamStep against steepest step $steepestStep", seamStep < steepestStep)
        }
    }

    private companion object {
        const val RATE = 48_000
        const val CD_RATE = 44_100
        const val LOW_RATE = 8_000
        const val CLICK_MILLIS = 30
        const val MILLIS_PER_SECOND = 1_000
        const val AUDIBLE = 0.05f
        const val TAIL_FRACTION = 0.05f
        const val MIN_RMS = 0.02
        const val MOST_OF_THE_ENERGY = 0.9
    }
}
