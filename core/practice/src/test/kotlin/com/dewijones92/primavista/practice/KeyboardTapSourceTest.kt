package com.dewijones92.primavista.practice

import com.dewijones92.primavista.score.Letter
import com.dewijones92.primavista.score.Polyphony
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KeyboardTapSourceTest {
    @Test
    fun `a tap declares itself as a polyphonic, zero-latency input`() {
        val source = KeyboardTapSource()

        assertEquals("tap", source.label)
        assertEquals(Polyphony.Poly, source.polyphony)
        assertEquals(InputLatency.None, source.latency)
        assertEquals(InputLatency.Provenance.NotApplicable, source.latency.provenance)
    }

    @Test
    fun `a tap carries the caller's own timestamp onto the flow, in order`() = runTest {
        val source = KeyboardTapSource()
        val received = mutableListOf<PlayedNote>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            source.notes().collect { received += it }
        }

        source.onKeyPressed(midiOf(Letter.C, 4), 1_234_567L)
        source.onKeyPressed(midiOf(Letter.E, 4), 2_345_678L)
        collector.cancel()

        assertEquals(
            listOf(
                PlayedNote(midiOf(Letter.C, 4), 1_234_567L),
                PlayedNote(midiOf(Letter.E, 4), 2_345_678L),
            ),
            received,
        )
        assertEquals(2L, source.notesEmitted)
        assertEquals(0L, source.notesDropped)
    }

    @Test
    fun `a tap made before anything is collecting is held, not thrown away`() = runTest {
        val source = KeyboardTapSource()
        source.onKeyPressed(midiOf(Letter.C, 4), 1_000L)
        source.onKeyPressed(midiOf(Letter.E, 4), 2_000L)

        assertEquals(2L, source.notesEmitted)
        assertEquals(0L, source.notesDropped)

        val received = mutableListOf<PlayedNote>()
        val collector = launch { source.notes().collect { received += it } }
        advanceUntilIdle()
        collector.cancel()

        assertEquals(
            "a tap counted as emitted but never delivered surfaces as a false Missed",
            listOf(
                PlayedNote(midiOf(Letter.C, 4), 1_000L),
                PlayedNote(midiOf(Letter.E, 4), 2_000L),
            ),
            received,
        )
    }

    @Test
    fun `a fast trill is buffered rather than dropped while the collector is descheduled`() = runTest {
        val source = KeyboardTapSource()
        val received = mutableListOf<PlayedNote>()
        val collector = launch { source.notes().collect { received += it } }
        runCurrent()

        val trill = 64
        repeat(trill) { index ->
            source.onKeyPressed(midiOf(Letter.B, 4), index * 20_000_000L)
        }
        assertEquals("nothing has been delivered yet", 0, received.size)

        advanceUntilIdle()
        collector.cancel()

        assertEquals(trill, received.size)
        assertEquals(0L, source.notesDropped)
        assertEquals(trill.toLong(), source.notesEmitted)
    }

    @Test
    fun `overflowing the buffer is counted, never silently swallowed`() = runTest {
        val source = KeyboardTapSource(bufferCapacity = 2)
        val collector = launch { source.notes().collect { } }
        runCurrent()

        repeat(5) { index -> source.onKeyPressed(midiOf(Letter.C, 4), index.toLong()) }
        collector.cancel()

        assertTrue("dropped ${source.notesDropped}", source.notesDropped > 0)
        assertEquals(5L, source.notesEmitted + source.notesDropped)
    }

    @Test
    fun `a buffer that could not hold a single tap is refused`() {
        val failure = runCatching { KeyboardTapSource(bufferCapacity = 0) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }
}
