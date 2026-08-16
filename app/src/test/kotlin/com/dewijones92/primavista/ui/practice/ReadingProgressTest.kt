package com.dewijones92.primavista.ui.practice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val CORPUS = 44

/**
 * What a wait says while the library is being read.
 *
 * The first thing anyone ever does in this app is tap *Read this*, and on a cold start the corpus
 * takes seconds to parse — during which the card said one grey sentence and showed no movement at
 * all. A screen that cannot show progress is indistinguishable from one that has hung.
 */
class ReadingProgressTest {

    @Test
    fun `a wait in progress says how far it has got`() {
        val text = waitingText(loading = true, reading = ReadingProgress(read = 12, expected = CORPUS))

        assertEquals("Reading your songs… 12 of $CORPUS", text)
    }

    @Test
    fun `once everything is read the wait is the scheduler thinking rather than the disk`() {
        val text = waitingText(loading = true, reading = ReadingProgress(read = CORPUS, expected = CORPUS))

        assertEquals("Choosing something to read…", text)
    }

    /** Not loading is not the same as loading nothing, and must not read as progress. */
    @Test
    fun `nothing loaded is said plainly rather than as a stalled count`() {
        assertEquals("Nothing loaded", waitingText(loading = false, reading = ReadingProgress(0, CORPUS)))
    }

    @Test
    fun `a library of nothing is settled rather than dividing by zero`() {
        val empty = ReadingProgress(read = 0, expected = 0)

        assertTrue(empty.settled)
        assertEquals(1f, empty.fraction, 0f)
    }

    @Test
    fun `the fraction tracks the count and never leaves nought to one`() {
        assertEquals(0f, ReadingProgress(0, CORPUS).fraction, 0f)
        assertEquals(0.5f, ReadingProgress(CORPUS / 2, CORPUS).fraction, 0.02f)
        assertEquals(1f, ReadingProgress(CORPUS, CORPUS).fraction, 0f)
    }

    /** More arriving than expected is a miscount, not a reason to overrun the bar. */
    @Test
    fun `more read than expected is still a full bar and settled`() {
        val over = ReadingProgress(read = CORPUS + 5, expected = CORPUS)

        assertEquals(1f, over.fraction, 0f)
        assertTrue(over.settled)
    }

    @Test
    fun `a part-read library is not settled`() {
        assertFalse(ReadingProgress(read = 1, expected = CORPUS).settled)
    }

    /** The default a preview or a test gets must never show a bar stuck at nought. */
    @Test
    fun `the settled default shows no progress bar`() {
        assertTrue(ReadingProgress.Settled.settled)
    }
}
