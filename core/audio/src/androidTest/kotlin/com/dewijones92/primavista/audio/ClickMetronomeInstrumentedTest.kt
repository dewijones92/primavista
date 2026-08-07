package com.dewijones92.primavista.audio

import com.dewijones92.primavista.score.MusicalTime
import com.dewijones92.primavista.score.Ticks
import com.dewijones92.primavista.score.TimeSignature
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** NOT YET EXECUTED — written without a device. Audible: it clicks. */
class ClickMetronomeInstrumentedTest {

    private lateinit var diag: RecordingDiag
    private lateinit var metronome: ClickMetronome

    @Before
    fun setUp() {
        diag = RecordingDiag()
        metronome = ClickMetronome(diag)
    }

    @After
    fun tearDown() {
        metronome.release()
    }

    @Test
    fun clicksOncePerBeatWhenDrivenByPositionsRatherThanByATimer() {
        metronome.configure(TEMPO_BPM, TimeSignature.FourFour)
        walkOneBarFrom(0L)
        Thread.sleep(SETTLE_MILLIS)

        assertEquals(1, diag.counts["audio.metronome.accents"])
        assertEquals(BEATS_PER_BAR - 1, diag.counts["audio.metronome.clicks"])
        assertNull("a driven metronome must never fail a click", diag.counts["audio.metronome.clickFailures"])
    }

    @Test
    fun accentsTheBarLineOfAPickupBarRatherThanTickZero() {
        val quarter = MusicalTime.TICKS_PER_QUARTER
        metronome.configure(TEMPO_BPM, TimeSignature.FourFour, barStart = Ticks(quarter))

        metronome.onPosition(Ticks.ZERO)
        val afterPickup = diag.counts["audio.metronome.accents"]
        metronome.onPosition(Ticks(quarter))
        Thread.sleep(SETTLE_MILLIS)

        assertNull("the pickup note is not the downbeat", afterPickup)
        assertEquals(1, diag.counts["audio.metronome.accents"])
    }

    @Test
    fun buildsBothClickTracksAndSaysSo() {
        metronome.configure(TEMPO_BPM, TimeSignature.FourFour)

        assertNotNull(
            "click tracks should be logged: ${diag.events}",
            diag.events.firstOrNull { it.contains("click tracks beat=true accent=true") },
        )
    }

    @Test
    fun staysSilentButAccountedForWhenDisabled() {
        metronome.configure(TEMPO_BPM, TimeSignature.FourFour)
        metronome.enabled = false
        walkOneBarFrom(0L)

        assertEquals(BEATS_PER_BAR, diag.counts["audio.metronome.beatsMutedByToggle"])
        assertNull(diag.counts["audio.metronome.clicks"])
    }

    @Test
    fun staysSilentButAccountedForBeforeItIsConfigured() {
        walkOneBarFrom(0L)

        assertTrue((diag.counts["audio.metronome.ticksWhileStopped"] ?: 0) > 0)
        assertNull(diag.counts["audio.metronome.clicks"])
    }

    @Test
    fun stopSilencesItUntilItIsConfiguredAgain() {
        metronome.configure(TEMPO_BPM, TimeSignature.FourFour)
        metronome.stop()

        walkOneBarFrom(0L)

        assertTrue((diag.counts["audio.metronome.ticksWhileStopped"] ?: 0) > 0)
        assertNull(diag.counts["audio.metronome.clicks"])
    }

    @Test
    fun countsTicksBetweenBeatsSoASilentTickIsExplainable() {
        metronome.configure(TEMPO_BPM, TimeSignature.FourFour)
        walkOneBarFrom(0L)

        assertTrue((diag.counts["audio.metronome.ticksBetweenBeats"] ?: 0) > 0)
    }

    private fun walkOneBarFrom(start: Long) {
        val quarter = MusicalTime.TICKS_PER_QUARTER
        var position = start
        while (position <= start + quarter * BEATS_PER_BAR - 1) {
            metronome.onPosition(Ticks(position))
            position += STRIDE_TICKS
        }
    }

    private companion object {
        const val TEMPO_BPM = 90
        const val BEATS_PER_BAR = 4
        const val STRIDE_TICKS = 140L
        const val SETTLE_MILLIS = 400L
    }
}
