package com.dewijones92.primavista.practice

import com.dewijones92.primavista.score.Letter
import com.dewijones92.primavista.score.MusicalTime
import com.dewijones92.primavista.score.Ticks
import com.dewijones92.primavista.score.TimeSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ONE_SECOND_NANOS = 1_000_000_000L
private const val ONE_MINUTE_NANOS = 60_000_000_000L

class TempoConductorTest {
    @Test
    fun `position advances with the clock at a known tempo`() {
        val clock = FakeClock()
        val conductor = TempoConductor(clock, tempoBpm = TEST_TEMPO_BPM)

        conductor.start()

        assertEquals(TransportState.Running, conductor.state)
        assertEquals(Ticks.ZERO, conductor.position())

        clock.advance(ONE_SECOND_NANOS)
        assertEquals(beat(1), conductor.position())

        clock.advance(ONE_SECOND_NANOS / 2)
        assertEquals(halfBeat(3), conductor.position())
    }

    @Test
    fun `a minute of wall time is exactly the tempo in beats`() {
        listOf(40, 60, 72, 120, 208).forEach { bpm ->
            val clock = FakeClock()
            val conductor = TempoConductor(clock, tempoBpm = bpm)
            conductor.start()

            clock.advance(ONE_MINUTE_NANOS)

            assertEquals("at $bpm bpm", MusicalTime.quarters(bpm), conductor.position())
        }
    }

    @Test
    fun `pause freezes the position however long the clock runs`() {
        val clock = FakeClock()
        val conductor = TempoConductor(clock, tempoBpm = TEST_TEMPO_BPM)
        conductor.start()
        clock.advance(ONE_SECOND_NANOS)

        conductor.pause()
        clock.advance(ONE_SECOND_NANOS * 5)

        assertEquals(TransportState.Paused, conductor.state)
        assertEquals(beat(1), conductor.position())
    }

    @Test
    fun `resume does not jump the position forward by the paused time`() {
        val clock = FakeClock()
        val conductor = TempoConductor(clock, tempoBpm = TEST_TEMPO_BPM)
        conductor.start()
        clock.advance(ONE_SECOND_NANOS)
        conductor.pause()
        clock.advance(ONE_SECOND_NANOS * 5)

        conductor.resume()

        assertEquals(TransportState.Running, conductor.state)
        assertEquals(beat(1), conductor.position())

        clock.advance(ONE_SECOND_NANOS)
        assertEquals(beat(2), conductor.position())
    }

    @Test
    fun `a pause moves the wall time of a later musical position, so nothing may cache it`() {
        val clock = FakeClock()
        val conductor = TempoConductor(clock, tempoBpm = TEST_TEMPO_BPM)
        conductor.start()
        val beforePause = conductor.nanosFor(beat(4))

        conductor.pause()
        clock.advance(ONE_SECOND_NANOS * 5)
        conductor.resume()

        assertEquals(beforePause + ONE_SECOND_NANOS * 5, conductor.nanosFor(beat(4)))
    }

    @Test
    fun `the count-in counts down and the position crosses zero`() {
        val clock = FakeClock()
        val conductor = TempoConductor(clock, tempoBpm = TEST_TEMPO_BPM, countInBeats = 4)

        conductor.start()

        assertEquals(TransportState.CountingIn, conductor.state)
        assertEquals(Ticks(-beat(4).value), conductor.position())
        assertEquals(4, conductor.countInBeatsRemaining())

        clock.advance(ONE_SECOND_NANOS)
        assertEquals(3, conductor.countInBeatsRemaining())
        assertEquals(TransportState.CountingIn, conductor.state)

        clock.advance(ONE_SECOND_NANOS * 3)
        assertEquals(Ticks.ZERO, conductor.position())
        assertEquals(0, conductor.countInBeatsRemaining())
        assertEquals(TransportState.Running, conductor.state)
    }

    @Test
    fun `a count-in starting mid-piece still lands on the requested position`() {
        val clock = FakeClock()
        val conductor = TempoConductor(clock, tempoBpm = TEST_TEMPO_BPM, countInBeats = 2)

        conductor.start(beat(8))

        assertEquals(beat(6), conductor.position())
        assertEquals(2, conductor.countInBeatsRemaining())
        assertEquals(TransportState.CountingIn, conductor.state)

        clock.advance(ONE_SECOND_NANOS)
        assertEquals(1, conductor.countInBeatsRemaining())

        clock.advance(ONE_SECOND_NANOS)
        assertEquals(beat(8), conductor.position())
        assertEquals(0, conductor.countInBeatsRemaining())
        assertEquals(TransportState.Running, conductor.state)
    }

    @Test
    fun `nanosFor and ticksAt are exact inverses`() {
        val positions = listOf(0L, 1L, 315L, 10_080L, 15_120L, 40_320L, 123_457L, -40_320L, 4_032_000L)
        listOf(40, 60, 72, 120, 208).forEach { bpm ->
            listOf(2, 4, 8).forEach { beatUnit ->
                val conductor = TempoConductor(
                    FakeClock(),
                    tempoBpm = bpm,
                    time = TimeSignature(4, beatUnit),
                ).also { it.start() }
                positions.forEach { value ->
                    val ticks = Ticks(value)
                    assertEquals(
                        "$bpm bpm, beat unit $beatUnit, $value ticks",
                        ticks,
                        conductor.ticksAt(conductor.nanosFor(ticks)),
                    )
                }
            }
        }
    }

    @Test
    fun `the scroll offset and the judging window read the same now`() {
        val clock = FakeClock()
        val conductor = TempoConductor(clock, tempoBpm = 72, countInBeats = 2)
        conductor.start()

        repeat(20) {
            clock.advance(ms(97.0))
            assertEquals(conductor.position(), conductor.ticksAt(clock.nowNanos()))
        }
        conductor.pause()
        clock.advance(ONE_SECOND_NANOS)
        conductor.resume()
        repeat(20) {
            clock.advance(ms(97.0))
            assertEquals(conductor.position(), conductor.ticksAt(clock.nowNanos()))
        }
    }

    @Test
    fun `changing tempo hands the position over without a jump`() {
        val clock = FakeClock()
        val slow = TempoConductor(clock, tempoBpm = 60)
        slow.start()
        clock.advance(ONE_SECOND_NANOS * 2)
        val handover = slow.position()

        val fast = TempoConductor(clock, tempoBpm = 120)
        fast.start(handover)

        assertEquals(handover, fast.position())
        clock.advance(ONE_SECOND_NANOS)
        assertEquals(handover + beat(2), fast.position())
    }

    @Test
    fun `stop finishes the transport and freezes where it got to`() {
        val clock = FakeClock()
        val conductor = TempoConductor(clock, tempoBpm = TEST_TEMPO_BPM)
        conductor.start()
        clock.advance(ONE_SECOND_NANOS * 3)

        conductor.stop()
        clock.advance(ONE_SECOND_NANOS * 3)

        assertEquals(TransportState.Finished, conductor.state)
        assertEquals(beat(3), conductor.position())
    }

    @Test
    fun `an idle conductor is idle and sits at zero`() {
        val conductor = TempoConductor(FakeClock(), tempoBpm = TEST_TEMPO_BPM, countInBeats = 4)

        assertEquals(TransportState.Idle, conductor.state)
        assertEquals(Ticks.ZERO, conductor.position())
        assertEquals(0, conductor.countInBeatsRemaining())

        conductor.pause()
        conductor.resume()
        conductor.stop()
        assertEquals(TransportState.Idle, conductor.state)
    }

    @Test
    fun `the clock cannot be wound backwards and neither can the tempo be nonsense`() {
        val clock = FakeClock(startNanos = 500L)
        clock.advanceMillis(1.5)
        assertEquals(1_500_500L, clock.nowNanos())

        assertTrue(runCatching { clock.advance(-1L) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(
            runCatching { TempoConductor(clock, tempoBpm = 0) }.exceptionOrNull() is IllegalArgumentException,
        )
        assertTrue(
            runCatching {
                TempoConductor(clock, tempoBpm = 60, countInBeats = -1)
            }.exceptionOrNull() is IllegalArgumentException,
        )
    }

    @Test
    fun `a snapshot keeps the wall time of everything already played, across a pause`() {
        val clock = FakeClock()
        val conductor = TempoConductor(clock, tempoBpm = TEST_TEMPO_BPM)
        conductor.start()
        val playedAt = conductor.nanosFor(beat(1))
        val stillToCome = conductor.nanosFor(beat(2))
        clock.advance(ONE_SECOND_NANOS + ms(120.0))

        conductor.pause()
        clock.advance(ONE_SECOND_NANOS * 17)
        conductor.resume()

        val snapshot = conductor.timingSnapshot()
        assertEquals("a note already played keeps the moment it happened", playedAt, snapshot.nanosFor(beat(1)))
        assertEquals(
            "a position still to come genuinely happens 17s later",
            stillToCome + ONE_SECOND_NANOS * 17,
            snapshot.nanosFor(beat(2)),
        )
    }

    @Test
    fun `a perfect performance across a pause re-judges identically from its own snapshot`() {
        val clock = FakeClock()
        val conductor = TempoConductor(clock, tempoBpm = TEST_TEMPO_BPM)
        val letters = listOf(Letter.C, Letter.D, Letter.E, Letter.F)
        val score = scoreOf(letters.mapIndexed { index, letter -> tone(beat(index), letter, 4) })
        val played = mutableListOf<PlayedNote>()
        fun play(index: Int) {
            clock.advance(conductor.nanosFor(beat(index)) - clock.nowNanos())
            played += PlayedNote(midiOf(letters[index], 4), clock.nowNanos())
        }
        conductor.start()

        play(0)
        play(1)
        clock.advance(ms(120.0))
        conductor.pause()
        clock.advance(ONE_SECOND_NANOS * 17)
        conductor.resume()
        play(2)
        play(3)
        conductor.stop()

        val snapshot = conductor.timingSnapshot()
        val result = judged(WindowedJudge().judgeAll(score, polySource, snapshot, played))

        assertEquals(letters.size, result.correct)
        assertEquals(0, result.extras)
        assertTrue("judgements were $result", result.judgements.all { it.verdict == Verdict.Correct(0.0) })

        conductor.start(beat(0))
        clock.advance(ONE_SECOND_NANOS * 30)
        val again = judged(WindowedJudge().judgeAll(score, polySource, snapshot, played))
        assertEquals("the snapshot moved when the transport did", result.judgements, again.judgements)
    }
}
