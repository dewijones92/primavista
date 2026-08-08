package com.dewijones92.primavista.audio

import android.os.Build
import android.util.Log
import com.dewijones92.primavista.common.RingBufferDiag
import com.dewijones92.primavista.score.MusicalTime
import com.dewijones92.primavista.score.Ticks
import com.dewijones92.primavista.score.TimeSignature
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Audible: it clicks. Executed on an emulator, API 35, 2026-08-08. */
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
    fun bothClickTracksReachStateInitialisedRatherThanBeingDiscardedAtConstruction() {
        metronome.configure(TEMPO_BPM, TimeSignature.FourFour)

        for (name in listOf("beat", "accent")) {
            assertNotNull(
                "$name click track never reached STATE_INITIALIZED: ${diag.events}",
                diag.events.firstOrNull { it.contains("$name click track ready state=INITIALIZED") },
            )
        }
    }

    @Test
    fun theAudioDeviceRendersEveryClicksFramesInsteadOfBeatsFindingNoTrack() {
        val clickFrames = ClickSynth.render(metronome.sampleRate, accent = false).size
        metronome.configure(TEMPO_BPM, TimeSignature.FourFour)

        walkABarPausingBetweenBeats()
        metronome.release()

        assertNull("a beat found no track: ${diag.events}", diag.counts["audio.metronome.beatsWithNoTrack"])
        assertNull("a click failed: ${diag.events}", diag.counts["audio.metronome.clickFailures"])
        assertNull("a rewind failed: ${diag.events}", diag.counts["audio.metronome.reloadFailures"])
        assertEquals(BEATS_PER_BAR, diag.counts["audio.metronome.beatsPlayed"])

        val expected = (clickFrames * BEATS_PER_BAR).toLong()
        assertEquals(
            "the audio device rendered ${metronome.framesHeard} click frames, not $expected",
            expected,
            metronome.framesHeard,
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

    /** docs/spec.md I7: the production report, not the test double, must settle this. */
    @Test
    fun aProductionReportSaysWhetherTheClickWasActuallyHeard() {
        val realDiag = RingBufferDiag()
        val real = ClickMetronome(realDiag)
        val clickFrames = ClickSynth.render(real.sampleRate, accent = false).size

        real.configure(TEMPO_BPM, TimeSignature.FourFour)
        walkABarPausingBetweenBeats(real)
        real.release()
        val report = realDiag.report(mapOf("device" to Build.MODEL))
        Log.i(REPORT_TAG, report)

        assertTrue(report, report.contains("beat click track ready state=INITIALIZED"))
        assertTrue(report, report.contains("accent click track ready state=INITIALIZED"))
        assertTrue(report, report.contains("audio.metronome/beatsPlayed: $BEATS_PER_BAR"))
        assertTrue(report, report.contains("released heard=${clickFrames * BEATS_PER_BAR}frames"))
        assertFalse(report, report.contains("beatsWithNoTrack"))
    }

    /** Slower than any real tempo on purpose: each 30ms click must finish before the next. */
    private fun walkABarPausingBetweenBeats(target: ClickMetronome = metronome) {
        val quarter = MusicalTime.TICKS_PER_QUARTER
        for (beat in 0 until BEATS_PER_BAR) {
            target.onPosition(Ticks(beat * quarter))
            Thread.sleep(BEAT_GAP_MILLIS)
        }
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
        const val BEAT_GAP_MILLIS = 250L
        const val REPORT_TAG = "dewidebug"
    }
}
