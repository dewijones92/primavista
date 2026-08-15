package com.dewijones92.primavista.score

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Part selection on song-shaped scores: a voice on one staff, a piano on two. Reading "the first
 * part" of one of those gets the singer, and the thing worth sight-reading is underneath it.
 */
class PartChoiceTest {

    private val parser = DomMusicXmlParser()

    @Test
    fun `the first part of a song is the singer, which is the whole reason Keyboard exists`() {
        val parsed = parsed(song(), PartChoice.First)
        assertEquals(listOf(Staff.Upper), parsed.score.staves)
        assertEquals(listOf(69), parsed.score.notes.map { it.pitch.midi.number })
    }

    @Test
    fun `Keyboard reads the part written on two staves`() {
        val parsed = parsed(song(), PartChoice.Keyboard)
        assertEquals(listOf(Staff.Upper, Staff.Lower), parsed.score.staves)
        assertTrue(parsed.score.isGrandStaff)
        assertEquals(listOf(60, 48), parsed.score.notes.map { it.pitch.midi.number })
    }

    @Test
    fun `the parts that were not read are named, not silently discarded`() {
        val parsed = parsed(song(), PartChoice.Keyboard)
        val note = parsed.dropped.single { it.element == "part" }
        assertEquals("read P2; not read: P1", note.detail)
    }

    @Test
    fun `a single-part score reports nothing dropped, because nothing was`() {
        val parsed = parsed(solo(), PartChoice.Keyboard)
        assertTrue("expected a clean parse, got ${parsed.dropped}", parsed.isClean)
    }

    @Test
    fun `ById picks the part out by its id`() {
        val parsed = parsed(song(), PartChoice.ById("P1"))
        assertEquals(listOf(69), parsed.score.notes.map { it.pitch.midi.number })
    }

    @Test
    fun `an id that is not there fails with the ids that are`() {
        val failed = failed(song(), PartChoice.ById("P9"))
        assertEquals("no part with id 'P9'; found P1, P2", failed.reason)
    }

    @Test
    fun `a score with no keyboard part is refused, and says how many staves it did find`() {
        val failed = failed(voiceOnly(), PartChoice.Keyboard)
        assertTrue(
            "expected the staff counts in '${failed.reason}'",
            failed.reason.startsWith("no part is written on 2 or more staves") &&
                failed.reason.endsWith("found P1 on 1"),
        )
    }

    @Test
    fun `staves declared after the first bar still count`() {
        val parsed = parsed(stavesDeclaredLate(), PartChoice.Keyboard)
        assertEquals(listOf(Staff.Upper, Staff.Lower), parsed.score.staves)
    }

    @Test
    fun `the choice survives the mxl container`() {
        val result = parser.parseCompressed(mxlOf(song()), "test", "CC0", PartChoice.Keyboard)
        val parsed = result as MusicXmlResult.Parsed
        assertTrue(parsed.score.isGrandStaff)
    }

    private fun parsed(xml: String, part: PartChoice): MusicXmlResult.Parsed {
        val result = parser.parse(xml, "test", "CC0", part)
        assertTrue("expected a parse, got $result", result is MusicXmlResult.Parsed)
        return result as MusicXmlResult.Parsed
    }

    private fun failed(xml: String, part: PartChoice): MusicXmlResult.Failed {
        val result = parser.parse(xml, "test", "CC0", part)
        assertTrue("expected a refusal, got $result", result is MusicXmlResult.Failed)
        return result as MusicXmlResult.Failed
    }

    private fun note(step: String, octave: Int, staff: Int = 1, voice: Int = 1) = """
        <note>
          <pitch><step>$step</step><octave>$octave</octave></pitch>
          <duration>4</duration><voice>$voice</voice><type>whole</type><staff>$staff</staff>
        </note>
    """.trimIndent()

    private fun song() = """
        <score-partwise>
          <part-list>
            <score-part id="P1"><part-name>Singstimme</part-name></score-part>
            <score-part id="P2"><part-name>Klavier</part-name></score-part>
          </part-list>
          <part id="P1"><measure number="1">
            <attributes><divisions>1</divisions><clef><sign>G</sign><line>2</line></clef></attributes>
            ${note("A", 4)}
          </measure></part>
          <part id="P2"><measure number="1">
            <attributes>
              <divisions>1</divisions>
              <staves>2</staves>
              <clef number="1"><sign>G</sign><line>2</line></clef>
              <clef number="2"><sign>F</sign><line>4</line></clef>
            </attributes>
            ${note("C", 4)}
            <backup><duration>4</duration></backup>
            ${note("C", 3, staff = 2, voice = 2)}
          </measure></part>
        </score-partwise>
    """.trimIndent()

    private fun solo() = """
        <score-partwise>
          <part-list><score-part id="P1"><part-name>Piano</part-name></score-part></part-list>
          <part id="P1"><measure number="1">
            <attributes><divisions>1</divisions><staves>2</staves></attributes>
            ${note("C", 4)}
          </measure></part>
        </score-partwise>
    """.trimIndent()

    private fun voiceOnly() = """
        <score-partwise>
          <part-list><score-part id="P1"><part-name>Voice</part-name></score-part></part-list>
          <part id="P1"><measure number="1">
            <attributes><divisions>1</divisions></attributes>
            ${note("A", 4)}
          </measure></part>
        </score-partwise>
    """.trimIndent()

    private fun stavesDeclaredLate() = """
        <score-partwise>
          <part-list><score-part id="P1"><part-name>Piano</part-name></score-part></part-list>
          <part id="P1">
            <measure number="1">
              <attributes><divisions>1</divisions></attributes>
              ${note("C", 4)}
            </measure>
            <measure number="2">
              <attributes><staves>2</staves><clef number="2"><sign>F</sign><line>4</line></clef></attributes>
              ${note("C", 3, staff = 2, voice = 2)}
            </measure>
          </part>
        </score-partwise>
    """.trimIndent()

    private fun mxlOf(xml: String): ByteArray {
        val container = """
            <container><rootfiles><rootfile full-path="score.xml"/></rootfiles></container>
        """.trimIndent()
        return ByteArrayOutputStream().also { sink ->
            ZipOutputStream(sink).use { zip ->
                zip.putNextEntry(ZipEntry("META-INF/container.xml"))
                zip.write(container.toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("score.xml"))
                zip.write(xml.toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()
    }
}
