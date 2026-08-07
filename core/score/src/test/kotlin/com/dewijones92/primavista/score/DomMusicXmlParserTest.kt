package com.dewijones92.primavista.score

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DomMusicXmlParserTest {

    private val parser = DomMusicXmlParser()

    @Test
    fun `backup separates the hands instead of stacking them`() {
        val parsed = parsed(twoStaffBar())
        val upper = parsed.score.notes.filter { it.staff == Staff.Upper }
        val lower = parsed.score.notes.filter { it.staff == Staff.Lower }
        assertEquals(4, upper.size)
        assertEquals(2, lower.size)
        assertEquals(listOf(0L, 10080L, 20160L, 30240L), upper.map { it.onset.value })
        assertEquals(listOf(0L, 20160L), lower.map { it.onset.value })
        assertEquals(listOf(Staff.Upper, Staff.Lower), parsed.score.staves)
        assertTrue(parsed.score.isGrandStaff)
        assertEquals(TimeSignature.FourFour.measureTicks, parsed.score.endsAt)
    }

    @Test
    fun `forward moves the cursor on without inventing a rest`() {
        val parsed = parsed(
            measures(
                """
                <attributes><divisions>1</divisions></attributes>
                ${note("C", 4, 1, "quarter")}
                <forward><duration>2</duration></forward>
                ${note("E", 4, 1, "quarter")}
                """,
            ),
        )
        assertEquals(listOf(0L, 30240L), parsed.score.notes.map { it.onset.value })
        assertTrue(parsed.isClean)
    }

    @Test
    fun `a chord shares the onset of the note it hangs off`() {
        val parsed = parsed(
            measures(
                """
                <attributes><divisions>1</divisions></attributes>
                ${note("C", 4, 4, "whole")}
                <note><chord/><pitch><step>E</step><octave>4</octave></pitch>
                <duration>4</duration><voice>1</voice><type>whole</type></note>
                """,
            ),
        )
        assertEquals(listOf(0L, 0L), parsed.score.notes.map { it.onset.value })
        assertEquals(TimeSignature.FourFour.measureTicks, parsed.score.endsAt)
    }

    @Test
    fun `ties are read from both tie and tied elements`() {
        val parsed = parsed(
            measures(
                """
                <attributes><divisions>1</divisions></attributes>
                <note><pitch><step>C</step><octave>4</octave></pitch><duration>2</duration>
                <tie type="start"/><voice>1</voice><type>half</type>
                <notations><tied type="start"/></notations></note>
                <note><pitch><step>C</step><octave>4</octave></pitch><duration>2</duration>
                <tie type="stop"/><voice>1</voice><type>half</type>
                <notations><tied type="stop"/></notations></note>
                """,
            ),
        )
        assertEquals(listOf(false, true), parsed.score.notes.map { it.tiedFromPrevious })
        assertEquals(listOf(true, false), parsed.score.notes.map { it.tiedToNext })
        assertEquals(1, parsed.score.attackedNotes.size)
        assertTrue(parsed.isClean)
    }

    @Test
    fun `everything unsupported is named in dropped, with its bar`() {
        val parsed = parsed(
            measures(
                """
                <attributes>
                  <divisions>1</divisions><staves>3</staves>
                  <transpose><chromatic>-2</chromatic></transpose>
                </attributes>
                <direction><direction-type><dynamics><f/></dynamics></direction-type></direction>
                <note><grace/><pitch><step>D</step><octave>4</octave></pitch><voice>1</voice><type>eighth</type></note>
                <note><pitch><step>C</step><octave>4</octave></pitch><duration>4</duration><voice>1</voice>
                  <type>whole</type>
                  <lyric><text>la</text></lyric>
                  <notations><slur type="start"/><articulations><staccato/></articulations>
                    <ornaments><trill-mark/></ornaments></notations>
                </note>
                <barline><repeat direction="backward"/></barline>
                """,
            ),
        )
        val elements = parsed.dropped.map { it.element }
        val expectedDrops =
            listOf("staves", "transpose", "direction", "grace", "lyric", "slur", "articulations", "ornaments", "repeat")
        for (expected in expectedDrops) {
            assertTrue("expected a dropped <$expected> in $elements", elements.contains(expected))
        }
        assertTrue(parsed.dropped.all { it.measure == 1 })
        assertTrue(parsed.dropped.first().toString().contains("at bar 1"))
        assertEquals(1, parsed.score.notes.size)
    }

    @Test
    fun `the tempo of a direction is read even though the direction itself is dropped`() {
        val parsed = parsed(
            measures(
                """
                <attributes><divisions>1</divisions></attributes>
                <direction placement="above">
                  <direction-type><metronome><beat-unit>quarter</beat-unit>
                  <per-minute>63</per-minute></metronome></direction-type>
                  <sound tempo="63"/>
                </direction>
                ${note("C", 4, 4, "whole")}
                <direction><direction-type><words>rit.</words></direction-type><sound tempo="40"/></direction>
                """,
            ),
        )
        assertEquals(63, parsed.score.defaultTempoBpm)
        assertTrue(parsed.dropped.any { it.element == "direction" })
    }

    @Test
    fun `a second part is dropped rather than merged into the first`() {
        val xml = """
                <score-partwise>
              <part-list><score-part id="P1"><part-name>One</part-name></score-part>
              <score-part id="P2"><part-name>Two</part-name></score-part></part-list>
              <part id="P1"><measure number="1">
                <attributes><divisions>1</divisions></attributes>
                ${note("C", 4, 4, "whole")}
              </measure></part>
              <part id="P2"><measure number="1">
                ${note("G", 5, 4, "whole")}
              </measure></part>
            </score-partwise>
        """.trimIndent()
        val parsed = parsed(xml)
        assertEquals(1, parsed.score.notes.size)
        val part = parsed.dropped.single { it.element == "part" }
        assertEquals(null, part.measure)
        assertTrue(part.detail.contains("first of 2"))
    }

    @Test
    fun `a dropped note still occupies the time the file gave it`() {
        val parsed = parsed(
            measures(
                """
                <attributes><divisions>16</divisions><staves>2</staves></attributes>
                <note><pitch><step>C</step><octave>5</octave></pitch><duration>1</duration>
                  <voice>1</voice><type>64th</type><staff>1</staff></note>
                <note><pitch><step>D</step><octave>5</octave></pitch><duration>3</duration>
                  <voice>1</voice><type>eighth</type><staff>3</staff></note>
                ${note("E", 5, 16, "quarter")}
                """,
            ),
        )
        assertEquals(listOf(630L + 1890L), parsed.score.notes.map { it.onset.value })
        assertEquals(MusicalTime.quarters(1), parsed.score.notes.single().duration.ticks)
        assertTrue(parsed.dropped.any { it.element == "type" })
        assertTrue(parsed.dropped.any { it.element == "staff" })
    }

    @Test
    fun `a duration that does not scale to a whole tick is dropped, not rounded`() {
        val parsed = parsed(
            measures(
                """
                <attributes><divisions>11</divisions></attributes>
                ${note("C", 4, 1, "eighth")}
                """,
            ),
        )
        assertEquals(0, parsed.score.notes.size)
        val dropped = parsed.dropped.single { it.element == "duration" }
        assertTrue(dropped.detail.contains("not a whole tick"))
    }

    @Test
    fun `a type that disagrees with its duration is reported but still read`() {
        val parsed = parsed(
            measures(
                """
                <attributes><divisions>2</divisions></attributes>
                ${note("C", 4, 4, "quarter")}
                """,
            ),
        )
        assertEquals(1, parsed.score.notes.size)
        assertEquals(NoteSymbol.Quarter, parsed.score.notes.single().duration.symbol)
        assertTrue(parsed.dropped.single().detail.contains("<duration> is 20160"))
    }

    @Test
    fun `a whole-measure rest with no written type is recovered from its length`() {
        val parsed = parsed(
            measures(
                """
                <attributes><divisions>2</divisions><time><beats>3</beats><beat-type>4</beat-type></time></attributes>
                <note><rest measure="yes"/><duration>6</duration><voice>1</voice></note>
                """,
            ),
        )
        val rest = parsed.score.events.single()
        assertEquals(Duration(NoteSymbol.Half, dots = 1), rest.duration)
        assertTrue(parsed.isClean)
    }

    @Test
    fun `tuplets keep their ratio`() {
        val parsed = parsed(
            measures(
                """
                <attributes><divisions>6</divisions></attributes>
                <note><pitch><step>C</step><octave>4</octave></pitch><duration>4</duration><voice>1</voice>
                  <type>quarter</type><time-modification><actual-notes>3</actual-notes>
                  <normal-notes>2</normal-notes></time-modification></note>
                """,
            ),
        )
        val note = parsed.score.notes.single()
        assertTrue(note.duration.isTuplet)
        assertEquals(6720L, note.duration.ticks.value)
        assertTrue(parsed.isClean)
    }

    @Test
    fun `clefs and keys are read per staff and per bar`() {
        val parsed = parsed(twoStaffBar())
        assertEquals(Clef.Treble, parsed.score.measures.first().clefs[Staff.Upper])
        assertEquals(Clef.Bass, parsed.score.measures.first().clefs[Staff.Lower])
        assertEquals(KeySignature(-2), parsed.score.measures.first().key)
        assertEquals(120, parsed.score.defaultTempoBpm)
    }

    @Test
    fun `an alto clef is read, and a clef with no line falls back to its usual one`() {
        val parsed = parsed(
            measures(
                """
                <attributes><divisions>1</divisions>
                  <clef number="1"><sign>C</sign></clef>
                </attributes>
                ${note("C", 4, 4, "whole")}
                """,
            ),
        )
        assertEquals(Clef.Alto, parsed.score.measures.single().clefs[Staff.Upper])
        assertTrue(parsed.isClean)
    }

    @Test
    fun `an unreadable clef leaves the previous one in place and says so`() {
        val parsed = parsed(
            measures(
                """
                <attributes><divisions>1</divisions>
                  <clef number="1"><sign>TAB</sign><line>5</line></clef>
                </attributes>
                ${note("C", 4, 4, "whole")}
                """,
            ),
        )
        assertEquals(Clef.Treble, parsed.score.measures.single().clefs[Staff.Upper])
        assertTrue(parsed.dropped.single { it.element == "clef" }.detail.contains("unsupported clef"))
    }

    @Test
    fun `malformed XML fails outright`() {
        val failed = parser.parse("<score-partwise><part>", "broken", "n/a")
        assertTrue(failed is MusicXmlResult.Failed)
        assertTrue((failed as MusicXmlResult.Failed).reason.startsWith("malformed XML"))
    }

    @Test
    fun `a timewise score is refused because it is not the subset we read`() {
        val failed = parser.parse(
            "<?xml version=\"1.0\"?><score-timewise><measure/></score-timewise>",
            "timewise",
            "n/a",
        )
        assertEquals(
            "expected <score-partwise>, found <score-timewise>",
            (failed as MusicXmlResult.Failed).reason,
        )
    }

    @Test
    fun `a partwise score with no part fails rather than returning an empty piece`() {
        val failed = parser.parse("<score-partwise><part-list/></score-partwise>", "empty", "n/a")
        assertEquals("no <part> in <score-partwise>", (failed as MusicXmlResult.Failed).reason)
    }

    @Test
    fun `a compressed mxl is read through its container`() {
        val bytes = mxlOf("scores/inner.musicxml", twoStaffBar())
        val parsed = parser.parseCompressed(bytes, "zipped", "n/a") as MusicXmlResult.Parsed
        assertEquals(6, parsed.score.notes.size)
        assertTrue(parsed.isClean)
    }

    @Test
    fun `an mxl with no usable container still finds the score`() {
        val bytes = ByteArrayOutputStream().also { sink ->
            ZipOutputStream(sink).use { zip ->
                zip.putNextEntry(ZipEntry("only.musicxml"))
                zip.write(twoStaffBar().toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()
        val parsed = parser.parseCompressed(bytes, "zipped", "n/a") as MusicXmlResult.Parsed
        assertEquals(6, parsed.score.notes.size)
    }

    @Test
    fun `an mxl that is not a zip fails with a reason`() {
        val failed = parser.parseCompressed(byteArrayOf(1, 2, 3), "bogus", "n/a")
        assertTrue(failed is MusicXmlResult.Failed)
    }

    @Test
    fun `a parse is reported well enough to be argued about later`() {
        val diag = RecordingDiag()
        val loud = DomMusicXmlParser(diag)
        loud.parse(twoStaffBar(), "reported", "n/a")
        val line = diag.events.single()
        for (field in listOf("bars=1", "notes=6", "staves=2", "tempo=120bpm", "endsAt=40320ticks", "dropped=0")) {
            assertTrue("expected $field in $line", line.contains(field))
        }
        loud.parse("not xml", "broken", "n/a")
        assertTrue(diag.events.last().contains("parse failed source=broken"))
    }

    @Test
    fun `what was dropped is counted rather than logged per occurrence`() {
        val diag = RecordingDiag()
        DomMusicXmlParser(diag).parse(
            measures(
                """
                <attributes><divisions>1</divisions></attributes>
                <direction><direction-type><dynamics><f/></dynamics></direction-type></direction>
                <direction><direction-type><dynamics><p/></dynamics></direction-type></direction>
                ${note("C", 4, 4, "whole")}
                """,
            ),
            "counted",
            "n/a",
        )
        assertEquals(2, diag.counts["dropped:direction"])
    }

    @Test
    fun `title, composer and licence come through`() {
        val parsed = parsed(twoStaffBar(), licence = "CC0")
        assertEquals("A test bar", parsed.score.title)
        assertEquals("A. Nonymous", parsed.score.composer)
        assertEquals(ScoreOrigin.Parsed("test", "CC0"), parsed.score.origin)
        assertNotNull(parsed.score.id)
    }

    private fun parsed(xml: String, licence: String = "n/a"): MusicXmlResult.Parsed {
        val result = parser.parse(xml, "test", licence)
        assertTrue("expected a parse, got $result", result is MusicXmlResult.Parsed)
        return result as MusicXmlResult.Parsed
    }

    private fun note(step: String, octave: Int, duration: Int, type: String, staff: Int = 1, voice: Int = 1) = """
        <note>
          <pitch><step>$step</step><octave>$octave</octave></pitch>
          <duration>$duration</duration><voice>$voice</voice><type>$type</type><staff>$staff</staff>
        </note>
    """.trimIndent()

    private fun measures(body: String) = """
        <score-partwise>
          <part-list><score-part id="P1"><part-name>Test</part-name></score-part></part-list>
          <part id="P1"><measure number="1">$body</measure></part>
        </score-partwise>
    """.trimIndent()

    private fun twoStaffBar() = """
        <score-partwise>
          <work><work-title>A test bar</work-title></work>
          <identification><creator type="composer">A. Nonymous</creator></identification>
          <part-list><score-part id="P1"><part-name>Piano</part-name></score-part></part-list>
          <part id="P1"><measure number="1">
            <attributes>
              <divisions>1</divisions>
              <key><fifths>-2</fifths></key>
              <time><beats>4</beats><beat-type>4</beat-type></time>
              <staves>2</staves>
              <clef number="1"><sign>G</sign><line>2</line></clef>
              <clef number="2"><sign>F</sign><line>4</line></clef>
            </attributes>
            <sound tempo="120"/>
            ${note("C", 5, 1, "quarter")}
            ${note("D", 5, 1, "quarter")}
            ${note("E", 5, 1, "quarter")}
            ${note("F", 5, 1, "quarter")}
            <backup><duration>4</duration></backup>
            ${note("C", 3, 2, "half", staff = 2, voice = 2)}
            ${note("G", 2, 2, "half", staff = 2, voice = 2)}
          </measure></part>
        </score-partwise>
    """.trimIndent()

    private fun mxlOf(path: String, xml: String): ByteArray {
        val container = """
                <container><rootfiles><rootfile full-path="$path"/></rootfiles></container>
        """.trimIndent()
        return ByteArrayOutputStream().also { sink ->
            ZipOutputStream(sink).use { zip ->
                zip.putNextEntry(ZipEntry("META-INF/container.xml"))
                zip.write(container.toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry(path))
                zip.write(xml.toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()
    }
}
