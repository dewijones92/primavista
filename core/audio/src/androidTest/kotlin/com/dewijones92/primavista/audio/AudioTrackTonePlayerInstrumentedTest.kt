package com.dewijones92.primavista.audio

import com.dewijones92.primavista.score.Midi
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** NOT YET EXECUTED — written without a device. Audible: it plays notes and clicks. */
class AudioTrackTonePlayerInstrumentedTest {

    private lateinit var diag: RecordingDiag
    private lateinit var player: AudioTrackTonePlayer

    @Before
    fun setUp() {
        diag = RecordingDiag()
        player = AudioTrackTonePlayer(diag)
    }

    @After
    fun tearDown() {
        player.release()
    }

    @Test
    fun opensAFloatTrackAndSaysSoInTheLog() {
        player.play(Midi(Midi.A4), NOTE_MILLIS)
        Thread.sleep(SETTLE_MILLIS)

        assertNotNull(
            "the open should be logged: ${diag.events}",
            diag.events.firstOrNull { it.contains("opened rate=") },
        )
        assertTrue((diag.counts["audio.tone.notes"] ?: 0) >= 1)
    }

    @Test
    fun mixesAChordWithoutOpeningASecondTrack() {
        val chord = listOf(Midi(Midi.MIDDLE_C), Midi(Midi.MIDDLE_C + MAJOR_THIRD), Midi(Midi.A4))
        player.playChord(chord, NOTE_MILLIS)
        Thread.sleep(NOTE_MILLIS + SETTLE_MILLIS)

        assertTrue(
            "one track only: ${diag.events}",
            diag.events.count { it.contains("opened rate=") } == 1,
        )
        assertTrue(diag.counts["audio.tone.notes"]!! >= chord.size)
    }

    @Test
    fun reportsWhenTheClickActuallySoundedSoLatencyCanBeMeasured() {
        player.play(Midi(CALIBRATION_MIDI), NOTE_MILLIS)
        Thread.sleep(SETTLE_MILLIS)

        // A null here is a finding. See .claude/CODE-NOTES.md.
        val moment = player.lastPlayback()
        assertNotNull("no playback anchor; counts=${diag.counts}", moment)
        assertTrue(
            "an anchor a render buffer out over-states latency: ${moment!!.uncertaintyMillis}ms",
            moment.uncertaintyMillis < RENDER_BUFFER_MILLIS,
        )
    }

    @Test
    fun stopAllRampsRatherThanCuttingSoThereIsNoClick() {
        player.play(Midi(Midi.A4), LONG_NOTE_MILLIS)
        Thread.sleep(SETTLE_MILLIS)
        player.stopAll()
        Thread.sleep(SETTLE_MILLIS)

        assertNotNull(diag.events.firstOrNull { it.contains("ramped, not cut") })
    }

    @Test
    fun releaseIsIdempotent() {
        player.play(Midi(Midi.A4), NOTE_MILLIS)
        Thread.sleep(SETTLE_MILLIS)
        player.release()
        player.release()

        assertTrue(diag.events.count { it.contains("released framesWritten=") } >= 2)
    }

    private companion object {
        const val NOTE_MILLIS = 300L
        const val LONG_NOTE_MILLIS = 4_000L
        const val SETTLE_MILLIS = 250L
        const val MAJOR_THIRD = 4
        const val CALIBRATION_MIDI = 96

        /** 512 frames at 48kHz: the error the pre-mixer anchor could carry. */
        const val RENDER_BUFFER_MILLIS = 10.666
    }
}
