package com.dewijones92.primavista.di

import com.dewijones92.primavista.practice.ClaimedVerdict
import com.dewijones92.primavista.practice.InputLatency
import com.dewijones92.primavista.practice.PauseLeg
import com.dewijones92.primavista.practice.PlayedNote
import com.dewijones92.primavista.practice.ReplayReading
import com.dewijones92.primavista.practice.ScoreRef
import com.dewijones92.primavista.practice.SessionReplay
import com.dewijones92.primavista.practice.SessionReplayCodec
import com.dewijones92.primavista.score.Alter
import com.dewijones92.primavista.score.Clef
import com.dewijones92.primavista.score.DifficultySpec
import com.dewijones92.primavista.score.KeySignature
import com.dewijones92.primavista.score.Midi
import com.dewijones92.primavista.score.NoteSymbol
import com.dewijones92.primavista.score.Polyphony
import com.dewijones92.primavista.score.Staff
import com.dewijones92.primavista.score.TimeSignature
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SEED = 4242L
private const val TEMPO_BPM = 84
private const val LATENCY_MS = 61.0
private const val MIDDLE_C = 60

/**
 * The app half of docs/spec.md **I7**: the pure seam is proven in `:core:practice`, and this proves
 * the wiring — that the real difficulty codec, the one `:core:database` already owns, carries a
 * generated exercise's spec through a shared report intact.
 */
class LastSessionTest {

    @After
    fun tearDown() {
        LastSession.forget()
    }

    @Test
    fun `a remembered session round-trips through the real spec codec`() {
        LastSession.remember(session())

        val reading = SessionReplayCodec.read(LastSession.block(), StoredSpecText)

        assertTrue("$reading", reading is ReplayReading.Readable)
        assertEquals(session(), (reading as ReplayReading.Readable).replay)
    }

    @Test
    fun `the spec of a generated exercise survives, so the exercise can be regenerated`() {
        LastSession.remember(session())

        val reading = SessionReplayCodec.read(LastSession.block(), StoredSpecText) as ReplayReading.Readable

        assertEquals(ScoreRef.Generated(SEED, difficulty()), reading.replay.score)
    }

    /** A blank would read as "nothing went wrong"; a report has to distinguish that from "no run". */
    @Test
    fun `with nothing played the block says so rather than being empty`() {
        LastSession.forget()

        val block = LastSession.block()

        assertTrue(block, block.contains("nothing to replay"))
        assertTrue(block, SessionReplayCodec.read(block, StoredSpecText) is ReplayReading.Unreadable)
    }

    private fun session() = SessionReplay(
        score = ScoreRef.Generated(SEED, difficulty()),
        tempoBpm = TEMPO_BPM,
        time = TimeSignature.FourFour,
        legs = listOf(PauseLeg(0L, 1_000L)),
        inputLabel = "PLAY IT",
        polyphony = Polyphony.Mono,
        latency = InputLatency(LATENCY_MS, InputLatency.Provenance.Measured),
        played = listOf(PlayedNote(Midi(MIDDLE_C), atNanos = 2_000L, confidence = 0.91f)),
        claimed = listOf(ClaimedVerdict(noteIndex = 0, kind = "Correct", dtMillis = 3.5)),
    )

    private fun difficulty() = DifficultySpec(
        staves = listOf(Staff.Upper),
        clefs = mapOf(Staff.Upper to Clef.Treble),
        keys = setOf(KeySignature(1)),
        time = TimeSignature.FourFour,
        bars = 4,
        range = mapOf(Staff.Upper to Midi(MIDDLE_C)..Midi(79)),
        symbols = setOf(NoteSymbol.Half, NoteSymbol.Quarter),
        maxDots = 0,
        allowTuplets = false,
        allowedAlterations = setOf(Alter.Natural),
        maxLeapSemitones = 7,
        tempoBpm = TEMPO_BPM,
        bothHandsActive = false,
    )
}
