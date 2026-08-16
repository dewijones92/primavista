package com.dewijones92.primavista.ui.repertoire

import com.dewijones92.primavista.score.CorpusPiece
import com.dewijones92.primavista.score.PartChoice
import com.dewijones92.primavista.score.Polyphony
import com.dewijones92.primavista.score.ScoreId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sentence under "Repertoire".
 *
 * It read `Corpus.pieces.size` and the words "all public domain" — a second copy of the count, and
 * a claim that stops being true the moment a file off the phone is on the shelf, since a picked
 * piece's rights are precisely what the app says it does not know.
 */
class ShelfSummaryTest {

    @Test
    fun `only shipped pieces are all public domain`() {
        val summary = shelfSummary(rows(shipped = 44, kept = 0))

        assertTrue(summary, summary.startsWith("44 pieces"))
        assertTrue(summary, summary.contains("all public domain"))
    }

    /** The claim that had gone wrong: one kept piece and the shelf is no longer all public domain. */
    @Test
    fun `one file of his own stops the shelf claiming to be all public domain`() {
        val summary = shelfSummary(rows(shipped = 44, kept = 1))

        assertTrue(summary, summary.startsWith("45 pieces"))
        assertFalse(summary, summary.contains("all public domain"))
        assertTrue(summary, summary.contains("44 public domain"))
        assertTrue(summary, summary.contains("1 your own"))
    }

    @Test
    fun `a shelf of nothing but his own says so`() {
        val summary = shelfSummary(rows(shipped = 0, kept = 3))

        assertTrue(summary, summary.contains("all your own"))
    }

    /** The count comes from the shelf, so a piece that failed to read is still counted. */
    @Test
    fun `a piece that would not read is still a piece on the shelf`() {
        val dead = rows(shipped = 1, kept = 0).map { it.copy(score = null, failure = "unreadable") }

        assertTrue(shelfSummary(dead), shelfSummary(dead).startsWith("1 pieces"))
    }

    private fun rows(shipped: Int, kept: Int): List<RepertoireRow> =
        (0 until shipped).map { row("shipped-$it", kept = false) } +
            (0 until kept).map { row("kept-$it", kept = true) }

    private fun row(id: String, kept: Boolean) = RepertoireRow(
        piece = CorpusPiece(ScoreId(id), id, "", "", "", "", PartChoice.Keyboard),
        kept = kept,
        score = null,
        bars = 1,
        notes = 1,
        tempoBpm = 60,
        polyphony = Polyphony.Mono,
        firstPolyphonicBar = null,
        skills = emptyList(),
        dropped = emptyList(),
        material = emptyList(),
        rung = null,
        opensAs = null,
        failure = null,
    )
}
