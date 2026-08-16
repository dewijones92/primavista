package com.dewijones92.primavista.di

import com.dewijones92.primavista.common.NoOpDiag
import com.dewijones92.primavista.practice.Curriculum
import com.dewijones92.primavista.score.Corpus
import com.dewijones92.primavista.score.DomMusicXmlParser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading the shipped corpus, and specifically what happens when that read is **interrupted**.
 *
 * The Repertoire tab starts the read from a `LaunchedEffect`, so leaving the tab cancels it — an
 * ordinary thing to do, since the read takes seconds on a phone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShippedRepertoireTest {

    private fun shipped() = ShippedRepertoire(DomMusicXmlParser(), NoOpDiag, Curriculum.Standard)

    @Test
    fun `a completed read yields every shipped piece exactly once`() = runTest {
        val repertoire = shipped()

        val loaded = repertoire.load()

        assertEquals(Corpus.pieces.size, loaded.size)
        assertEquals(loaded.map { it.piece.id }.distinct().size, loaded.size)
    }

    /**
     * Switching away from the Repertoire tab mid-read and back must not show a piece twice. The
     * rows are keyed by piece id in a `LazyColumn`, and a duplicate key is a crash rather than a
     * cosmetic repeat — and `expected - arrived` would go negative, which is a second crash.
     */
    @Test
    fun `a cancelled read does not leave its pieces behind for the next one`() = runTest {
        val repertoire = shipped()
        val first: Job = launch { repertoire.load() }
        while (repertoire.parsed.value.isEmpty()) yield()
        first.cancel()

        val loaded = repertoire.load()

        assertEquals(Corpus.pieces.size, repertoire.parsed.value.size)
        assertEquals("a piece arrived twice", loaded.size, repertoire.parsed.value.distinctBy { it.piece.id }.size)
        assertTrue("more arrived than exist", repertoire.parsed.value.size <= repertoire.expected)
    }

    @Test
    fun `loading twice does not read the corpus twice`() = runTest {
        val repertoire = shipped()

        val first = repertoire.load()
        val second = repertoire.load()

        assertTrue("the second load re-read the corpus", first === second)
        assertEquals(Corpus.pieces.size, repertoire.parsed.value.size)
    }
}
