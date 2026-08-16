package com.dewijones92.primavista.score

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private const val MEGABYTE = 1024 * 1024

/**
 * A `.mxl` picked off the phone is not one this repo wrote.
 *
 * Every shipped file came from `tools/repertoire`, so reading each entry whole was safe. Since the
 * file picker exists, the same code path takes an arbitrary archive — and a small one can inflate
 * to gigabytes, which on a phone is an out-of-memory kill rather than an error. These hold the line
 * that it is refused, **with a reason**, before any of it is held in memory.
 */
class MxlContainerTest {

    private val parser = DomMusicXmlParser()

    @Test
    fun `an ordinary shipped file still reads`() {
        val piece = Corpus.pieces.first { it.resourcePath.endsWith(".mxl") }

        val reading = MxlContainer.read(Corpus.read(piece))

        assertTrue("$reading", reading is MxlReading.Read)
        assertTrue((reading as MxlReading.Read).entries.isNotEmpty())
    }

    /** Every shipped file must sit well inside the caps, or the caps are the wrong ones. */
    @Test
    fun `every shipped mxl is far below the limits`() {
        val compressed = Corpus.pieces.filter { it.resourcePath.endsWith(".mxl") }

        assertTrue("no compressed pieces ship, so this proves nothing", compressed.isNotEmpty())
        for (piece in compressed) {
            val entries = (MxlContainer.read(Corpus.read(piece)) as MxlReading.Read).entries
            assertTrue(piece.title, entries.size <= MxlContainer.MAX_ENTRIES)
            assertTrue(piece.title, entries.values.sumOf { it.size.toLong() } < MxlContainer.MAX_INFLATED_BYTES / 2)
        }
    }

    /**
     * The real thing: a small archive whose single entry inflates far past the budget. Zeroes
     * compress at roughly a thousand to one, so this is a few kilobytes on disk.
     */
    @Test
    fun `a zip bomb is refused by size, and says what the limit was`() {
        val bomb = zipOf("score.xml" to ByteArray(MxlContainer.MAX_INFLATED_BYTES.toInt() + MEGABYTE))

        val reading = MxlContainer.read(bomb)

        assertTrue("$reading", reading is MxlReading.Refused)
        assertTrue((reading as MxlReading.Refused).reason, reading.reason.contains("32MB"))
    }

    @Test
    fun `a bomb hidden in an entry that would be thrown away is still refused`() {
        val bomb = zipOf("art/cover.png" to ByteArray(MxlContainer.MAX_INFLATED_BYTES.toInt() + MEGABYTE))

        val reading = MxlContainer.read(bomb)

        assertTrue("skipping an entry still inflates it: $reading", reading is MxlReading.Refused)
    }

    @Test
    fun `an archive of thousands of files is refused by count`() {
        val many = zipOf(*(0..MxlContainer.MAX_ENTRIES + 1).map { "f$it.dat" to ByteArray(1) }.toTypedArray())

        val reading = MxlContainer.read(many)

        assertTrue("$reading", reading is MxlReading.Refused)
        assertTrue((reading as MxlReading.Refused).reason, reading.reason.contains("${MxlContainer.MAX_ENTRIES}"))
    }

    /** Images and fonts are common in a MuseScore export and none of them can be a score. */
    @Test
    fun `entries that cannot be music are not held in memory`() {
        val mixed = zipOf(
            "META-INF/container.xml" to "<container/>".toByteArray(),
            "score.xml" to "<score-partwise/>".toByteArray(),
            "Pictures/cover.png" to ByteArray(MEGABYTE),
            "fonts/Bravura.otf" to ByteArray(MEGABYTE),
        )

        val entries = (MxlContainer.read(mixed) as MxlReading.Read).entries

        assertEquals(setOf("META-INF/container.xml", "score.xml"), entries.keys)
    }

    @Test
    fun `something that is not a zip at all is refused with a reason`() {
        val reading = MxlContainer.read("this is a shopping list".toByteArray())

        assertTrue("$reading", reading is MxlReading.Read || reading is MxlReading.Refused)
        val entries = (reading as? MxlReading.Read)?.entries
        assertTrue("a non-zip must not yield entries", entries.isNullOrEmpty())
    }

    /** And the refusal has to reach the caller as a parse failure, not as a crash. */
    @Test
    fun `the parser turns a refused container into a stated failure`() {
        val bomb = zipOf("score.xml" to ByteArray(MxlContainer.MAX_INFLATED_BYTES.toInt() + MEGABYTE))

        val result = parser.parseCompressed(bomb, "picked", "unknown")

        assertTrue("$result", result is MusicXmlResult.Failed)
        assertTrue((result as MusicXmlResult.Failed).reason, result.reason.contains("32MB"))
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray =
        ByteArrayOutputStream().also { sink ->
            ZipOutputStream(sink).use { zip ->
                entries.forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }.toByteArray()
}
