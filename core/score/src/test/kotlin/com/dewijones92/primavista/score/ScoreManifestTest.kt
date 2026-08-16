package com.dewijones92.primavista.score

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The manifest format, now that both the import tool and the app read and write it.
 *
 * It used to be two copies of one decision: `Corpus` knew the column order for reading and
 * `ImportWriter` knew it again for writing. That is the shape CLAUDE.md records having shipped
 * twice, so the round trip below is the property that keeps them honest.
 */
class ScoreManifestTest {

    private val piece = CorpusPiece(
        id = ScoreId("schubert-D118"),
        title = "Gretchen am Spinnrade",
        composer = "Schubert, Franz",
        source = "OpenScore Lieder Corpus",
        licence = "CC0 1.0",
        locator = "/corpus/lieder/schubert-D118.mxl",
        part = PartChoice.Keyboard,
    )

    @Test
    fun `a piece survives being written and read back`() {
        val read = ScoreManifest.read(ScoreManifest.write(listOf(piece)), "a test")

        assertEquals(listOf(piece), read)
    }

    @Test
    fun `every part choice survives the round trip`() {
        val parts = listOf(PartChoice.First, PartChoice.Keyboard, PartChoice.ById("P2"))
        val pieces = parts.mapIndexed { index, part -> piece.copy(id = ScoreId("p$index"), part = part) }

        assertEquals(parts, ScoreManifest.read(ScoreManifest.write(pieces), "a test").map { it.part })
    }

    /** The header the writer emits must be a comment the reader skips, or every file gains a row. */
    @Test
    fun `the header this writes is skipped by this reader`() {
        val written = ScoreManifest.write(listOf(piece))

        assertTrue(written.startsWith(ScoreManifest.HEADER))
        assertEquals(1, ScoreManifest.read(written, "a test").size)
    }

    @Test
    fun `an empty manifest is a shelf with nothing on it`() {
        assertEquals(emptyList<CorpusPiece>(), ScoreManifest.read(ScoreManifest.write(emptyList()), "a test"))
    }

    /** A tab inside a field would silently shift every column after it, so it is refused loudly. */
    @Test
    fun `a field containing a tab is refused rather than written`() {
        val broken = piece.copy(title = "Gretchen\tam Spinnrade")

        val failure = assertThrows(IllegalArgumentException::class.java) { ScoreManifest.write(listOf(broken)) }

        assertTrue("${failure.message}", failure.message.orEmpty().contains("break the row"))
    }

    @Test
    fun `a row with the wrong number of fields names the file it came from`() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            ScoreManifest.read("one\ttwo\tthree", "the kept manifest")
        }

        assertTrue("${failure.message}", failure.message.orEmpty().contains("the kept manifest"))
    }

    /** The shipped manifests are the real test of the reader, so they are read here too. */
    @Test
    fun `every shipped piece round-trips through the writer`() {
        val shipped = Corpus.pieces

        assertTrue("no pieces ship, so this proves nothing", shipped.isNotEmpty())
        assertEquals(shipped, ScoreManifest.read(ScoreManifest.write(shipped), "the shipped manifests"))
    }
}
