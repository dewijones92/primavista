package com.dewijones92.primavista.ui.repertoire

import com.dewijones92.primavista.score.Corpus
import com.dewijones92.primavista.score.DomMusicXmlParser
import com.dewijones92.primavista.score.Staff
import com.dewijones92.primavista.score.isCompressedMusicXml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A file Dewi already has, read by exactly the road a shipped piece takes.
 *
 * The point of these is the *sameness*: the shipped corpus is the fixture, because bytes that the
 * app ships and bytes Dewi picks must reach the same score. What differs is only what is known
 * about the file, which is nothing — hence the licence and the name coming from elsewhere.
 */
class PickedScoreTest {

    private val parser = DomMusicXmlParser()

    @Test
    fun `a zipped file is told from a plain one by its content, not its name`() {
        val zipped = Corpus.pieces.first { it.locator.endsWith(".mxl") }
        val plain = Corpus.pieces.first { it.locator.endsWith(".musicxml") }

        assertTrue(isCompressedMusicXml(Corpus.read(zipped)))
        assertFalse(isCompressedMusicXml(Corpus.read(plain)))
    }

    @Test
    fun `an mxl picked with a misleading name still reads`() {
        val zipped = Corpus.pieces.first { it.locator.endsWith(".mxl") }

        val read = readPicked(Corpus.read(zipped), "whatever-he-called-it.txt", parser)

        assertTrue("$read", read is Picked.Readable)
        assertTrue((read as Picked.Readable).score.measures.isNotEmpty())
    }

    /** A song's first part is the singer; a picked file gets the same keyboard-first treatment. */
    @Test
    fun `a picked song opens on its keyboard part, not its voice`() {
        val song = Corpus.pieces.first { it.part == com.dewijones92.primavista.score.PartChoice.Keyboard }

        val read = readPicked(Corpus.read(song), "song.mxl", parser) as Picked.Readable

        assertEquals(listOf(Staff.Upper, Staff.Lower), read.score.staves)
    }

    /** A single-line file is not a failure — the keyboard choice falls back to the first part. */
    @Test
    fun `a file with no keyboard part falls back to its first part`() {
        val read = readPicked(voiceOnly().toByteArray(), "voice.musicxml", parser)

        assertTrue("$read", read is Picked.Readable)
        assertEquals(listOf(Staff.Upper), (read as Picked.Readable).score.staves)
    }

    @Test
    fun `something that is not MusicXML is refused with a reason naming the file`() {
        val read = readPicked("this is a shopping list".toByteArray(), "list.txt", parser)

        assertTrue("$read", read is Picked.Refused)
        assertTrue((read as Picked.Refused).reason, read.reason.contains("list.txt"))
    }

    @Test
    fun `an empty file is refused rather than read as an empty score`() {
        val read = readPicked(ByteArray(0), "nothing.mxl", parser)

        assertTrue("$read", read is Picked.Refused)
        assertTrue((read as Picked.Refused).reason, read.reason.contains("empty"))
    }

    /** Nothing is known about a picked file's rights, and saying so is more use than a guess. */
    @Test
    fun `a picked score records that its licence is unknown`() {
        val read = readPicked(untitled().toByteArray(), "from-my-teacher.musicxml", parser) as Picked.Readable
        val origin = read.score.origin as com.dewijones92.primavista.score.ScoreOrigin.Parsed

        assertTrue(origin.licence, origin.licence.contains("whatever Dewi's copy is"))
    }

    @Test
    fun `a file with no title of its own is named after the file`() {
        val read = readPicked(untitled().toByteArray(), "Mystery Piece.musicxml", parser) as Picked.Readable

        assertEquals("Mystery Piece", read.score.title)
    }

    @Test
    fun `music missing from the page is surfaced before it is read`() {
        val read = readPicked(onThreeStaves().toByteArray(), "organ.musicxml", parser) as Picked.Readable

        assertTrue(read.lost.toString(), read.lost.isNotEmpty())
    }

    private fun voiceOnly() = """
        <score-partwise>
          <part-list><score-part id="P1"><part-name>Voice</part-name></score-part></part-list>
          <part id="P1"><measure number="1">
            <attributes><divisions>1</divisions></attributes>
            <note><pitch><step>A</step><octave>4</octave></pitch>
            <duration>4</duration><voice>1</voice><type>whole</type></note>
          </measure></part>
        </score-partwise>
    """.trimIndent()

    private fun untitled() = voiceOnly()

    /** Organ writing: a third staff this app does not read, so notes on it are simply gone. */
    private fun onThreeStaves() = """
        <score-partwise>
          <part-list><score-part id="P1"><part-name>Organ</part-name></score-part></part-list>
          <part id="P1"><measure number="1">
            <attributes><divisions>1</divisions><staves>3</staves></attributes>
            <note><pitch><step>C</step><octave>5</octave></pitch><duration>4</duration>
            <voice>1</voice><type>whole</type><staff>1</staff></note>
            <backup><duration>4</duration></backup>
            <note><pitch><step>C</step><octave>3</octave></pitch><duration>4</duration>
            <voice>2</voice><type>whole</type><staff>2</staff></note>
            <backup><duration>4</duration></backup>
            <note><pitch><step>C</step><octave>2</octave></pitch><duration>4</duration>
            <voice>3</voice><type>whole</type><staff>3</staff></note>
          </measure></part>
        </score-partwise>
    """.trimIndent()
}
