package com.dewijones92.primavista.tools.repertoire

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeBytes

/**
 * What an import leaves on disk.
 *
 * An import is a **statement of what ships**, not an addition to it: a piece dropped from the
 * selection has to leave, or its file stays in the resources, ships in the APK, and is named by no
 * manifest — dead weight nothing can read.
 */
class ImportWriterTest {

    @Test
    fun `a manifest row is written for every piece, and the resource beside it`() {
        val out = Files.createTempDirectory("import")

        ImportWriter(out).write(listOf(piece("a"), piece("b")))

        val manifest = out.resolve("lieder/manifest.tsv").readText().trim().lines()
        assertEquals(3, manifest.size)
        assertTrue(manifest.first().startsWith("#"))
        assertEquals(setOf("a", "b"), manifest.drop(1).map { it.split("\t").first() }.toSet())
        assertEquals(setOf("a.mxl", "b.mxl", "manifest.tsv"), scoresIn(out))
    }

    /** The one that matters: a second import must not leave the first one's pieces behind. */
    @Test
    fun `a piece dropped from the selection is removed, not left behind`() {
        val out = Files.createTempDirectory("import")
        ImportWriter(out).write(listOf(piece("a"), piece("b")))

        ImportWriter(out).write(listOf(piece("b")))

        assertEquals(setOf("b.mxl", "manifest.tsv"), scoresIn(out))
    }

    @Test
    fun `a file that was never ours is left alone`() {
        val out = Files.createTempDirectory("import")
        out.resolve("lieder").createDirectories()
        out.resolve("lieder/notes.txt").writeBytes("mine".toByteArray())

        ImportWriter(out).write(listOf(piece("a")))

        assertTrue("notes.txt" in scoresIn(out))
    }

    /** A field carrying a tab would silently shift every column after it. */
    @Test
    fun `a field that would break the row is refused before anything is written`() {
        val out = Files.createTempDirectory("import")
        val broken = piece("a").let { it.copy(source = it.source.copy(title = "one\ttwo")) }

        val failure = runCatching { ImportWriter(out).write(listOf(broken)) }.exceptionOrNull()

        assertTrue("$failure", failure is IllegalArgumentException)
        assertTrue("nothing should have been written: ${scoresIn(out)}", scoresIn(out).none { it.endsWith(".mxl") })
    }

    /** Empty when the directory was never made, which is itself a valid answer for a refused write. */
    private fun scoresIn(out: Path): Set<String> {
        val scores = out.resolve("lieder")
        return if (scores.exists()) scores.listDirectoryEntries().map { it.name }.toSet() else emptySet()
    }

    private fun piece(id: String): Screening.Accepted = SelectFixtures.accepted(
        composer = "Composer",
        id = id,
        stage = 6,
        passages = 1,
    )
}
