package com.dewijones92.primavista.practice

import com.dewijones92.primavista.score.Corpus
import com.dewijones92.primavista.score.DomMusicXmlParser
import com.dewijones92.primavista.score.MusicXmlResult
import com.dewijones92.primavista.score.Score
import com.dewijones92.primavista.score.admits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SHORT_PIECE_BARS = 4
private const val LONG_PIECE_BARS = 40

class RepertoireTest {

    private val repertoire = Repertoire()
    private val curriculum = Curriculum.Standard
    private val shipped: List<Score> by lazy {
        Corpus.pieces.mapNotNull { (Corpus.parse(it, DomMusicXmlParser()) as? MusicXmlResult.Parsed)?.score }
    }

    @Test
    fun `a piece a rung already admits is offered whole, keeping its name`() {
        val minuet = shipped.first { it.title.startsWith("Minuet in G") }
        val offered = repertoire.offers(minuet)
        assertEquals(listOf(minuet), offered)
        assertEquals(minuet.title, offered.single().title)
    }

    @Test
    fun `a song no rung admits whole is offered as the passages of it that are readable`() {
        val long = shipped.first { it.measures.size > LONG_PIECE_BARS && repertoire.rungFor(it) == null }
        val offered = repertoire.offers(long)
        assertTrue("${long.title} offers nothing", offered.isNotEmpty())
        assertTrue(offered.all { it.measures.size < long.measures.size })
        assertTrue(offered.all { it.id != long.id })
    }

    /** The whole point: nothing is offered that no rung of the path can read. */
    @Test
    fun `everything offered is admitted by some rung`() {
        val unplaceable = shipped.flatMap { repertoire.offers(it) }.filter { repertoire.rungFor(it) == null }
        assertEquals(emptyList<String>(), unplaceable.map { it.id.value })
    }

    @Test
    fun `every shipped piece offers something to read`() {
        val silent = shipped.filter { repertoire.offers(it).isEmpty() }
        assertEquals(emptyList<String>(), silent.map { it.title })
    }

    @Test
    fun `an offered passage keeps the piece's name and its provenance`() {
        val long = shipped.first { repertoire.rungFor(it) == null }
        val passage = repertoire.offers(long).first()
        assertTrue(passage.title, passage.title.startsWith(long.title))
        assertEquals(long.origin, passage.origin)
        assertEquals(long.composer, passage.composer)
    }

    @Test
    fun `opening at a rung gives the most of the piece that rung admits`() {
        val stage = curriculum.stages.last()
        val long = shipped.first { repertoire.rungFor(it) == null }
        val opened = repertoire.passageFor(long, stage)
        assertNotNull(long.title, opened)
        val admitted = repertoire.offers(long).filter { stage.spec.admits(it).isAdmitted }
        assertEquals(admitted.maxOf { it.measures.size }, opened?.measures?.size)
    }

    /** A beginner asking for a piece above them gets its easiest passage, not a refusal. */
    @Test
    fun `opening at the bottom rung still gives back something`() {
        val bottom = curriculum.stages.first()
        for (piece in shipped) {
            assertNotNull(piece.title, repertoire.passageFor(piece, bottom))
        }
    }

    @Test
    fun `offerings are ordered easiest first`() {
        val long = shipped.first { repertoire.rungFor(it) == null }
        val rungs = repertoire.offers(long).map { repertoire.rungFor(it)?.number ?: 0 }
        assertEquals(rungs.sorted(), rungs)
    }

    @Test
    fun `the whole shipped corpus offers material across more than one rung`() {
        val byRung = shipped.flatMap { repertoire.offers(it) }.groupingBy { repertoire.rungFor(it)?.number }.eachCount()
        assertTrue("only $byRung", byRung.keys.filterNotNull().size > 1)
        assertTrue("only ${byRung.values.sum()} passages", byRung.values.sum() > shipped.size)
    }

    @Test
    fun `a piece shorter than the smallest window is still offered whole or not at all`() {
        val short = shipped.filter { it.measures.size <= SHORT_PIECE_BARS }
        assertTrue("no short pieces shipped", short.isNotEmpty())
        short.forEach { assertEquals(listOf(it), repertoire.offers(it)) }
    }
}
