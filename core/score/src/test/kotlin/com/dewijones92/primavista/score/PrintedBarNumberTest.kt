package com.dewijones92.primavista.score

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val PICKUP = 0

/**
 * Where a bar sits and what is printed above it are two facts, and on a piece with a pickup they
 * differ for its whole length.
 *
 * Sixteen of the forty-one shipped songs open `<measure number="0" implicit="yes">`. Counting from
 * document position labelled every one of their bars one ahead of the engraving, so a passage the
 * app called "bars 5–8" was printed as bars 4–7 — and the bar number is exactly what Dewi would use
 * to find the passage in the score in front of him.
 */
class PrintedBarNumberTest {

    private val parser = DomMusicXmlParser()

    @Test
    fun `a pickup bar keeps the number the engraving printed on it`() {
        val score = parsed(withPickup())

        assertEquals(listOf(PICKUP, 1, 2), score.measures.map { it.number })
        assertEquals(listOf(0, 1, 2), score.measures.map { it.index })
    }

    @Test
    fun `a score that numbers its bars normally is unchanged`() {
        val score = parsed(fromBarOne())

        assertEquals(listOf(1, 2), score.measures.map { it.number })
    }

    /** Nothing printed the number, so position is all there is — and that is still a sane answer. */
    @Test
    fun `music with no printed numbers falls back to its position`() {
        val generated = SeededExerciseGenerator().generate(1L, spec(bars = 3))

        assertEquals(listOf(1, 2, 3), generated.measures.map { it.number })
        assertNull(generated.measures.first().printedNumber)
    }

    @Test
    fun `a passage of a pickup piece is titled with the printed bars`() {
        val passage = parsed(withPickup()).excerpt(fromIndex = 0, bars = 2)

        assertTrue(passage.title, passage.title.endsWith("(bars 0–1)"))
    }

    /**
     * The id carries the index and the title carries the print, and they are genuinely different
     * numbers: on an ordinary score the first bar is index 0 and prints as 1, and on a pickup piece
     * it is index 0 and prints as 0. One id scheme cannot serve both, which is why there are two.
     */
    @Test
    fun `the id counts from zero while the title says what is printed`() {
        val ordinary = parsed(fromBarOne()).excerpt(fromIndex = 0, bars = 1)
        val pickup = parsed(withPickup()).excerpt(fromIndex = 0, bars = 1)

        assertEquals(0, PassageId.read(ordinary.id)?.fromIndex)
        assertTrue(ordinary.title, ordinary.title.endsWith("(bars 1–1)"))
        assertNotEquals("index and print differ on an ordinary score", 1, PassageId.read(ordinary.id)?.fromIndex)

        assertEquals(0, PassageId.read(pickup.id)?.fromIndex)
        assertTrue(pickup.title, pickup.title.endsWith("(bars 0–0)"))
    }

    @Test
    fun `a passage id round-trips back to the same window`() {
        val score = parsed(withPickup())
        val passage = score.excerpt(fromIndex = 1, bars = 2)

        val read = requireNotNull(PassageId.read(passage.id))
        val rebuilt = score.excerpt(read.fromIndex, read.bars)

        assertEquals(passage.id, rebuilt.id)
        assertEquals(passage.notes.map { it.pitch }, rebuilt.notes.map { it.pitch })
    }

    @Test
    fun `an ordinary score id is not mistaken for a passage`() {
        assertNull(PassageId.read(ScoreId("lieder-lc6670960")))
        assertNull(PassageId.read(ScoreId("corpus-minuet-in-g")))
    }

    /** A dropped element is reported at the bar a human would look at. */
    @Test
    fun `what was dropped is reported at the printed bar`() {
        val parsed = parser.parse(withPickupAndSomethingDropped(), "test", "n/a") as MusicXmlResult.Parsed

        assertEquals(listOf(PICKUP), parsed.dropped.mapNotNull { it.measure })
    }

    private fun parsed(xml: String): Score =
        (parser.parse(xml, "test", "n/a") as MusicXmlResult.Parsed).score

    private fun note(step: String, octave: Int, duration: Int, type: String) = """
        <note><pitch><step>$step</step><octave>$octave</octave></pitch>
        <duration>$duration</duration><voice>1</voice><type>$type</type></note>
    """.trimIndent()

    private fun withPickup() = """
        <score-partwise>
          <part-list><score-part id="P1"><part-name>Piano</part-name></score-part></part-list>
          <part id="P1">
            <measure number="0" implicit="yes">
              <attributes><divisions>1</divisions><time><beats>4</beats><beat-type>4</beat-type></time></attributes>
              ${note("C", 4, 1, "quarter")}
            </measure>
            <measure number="1">${note("D", 4, 4, "whole")}</measure>
            <measure number="2">${note("E", 4, 4, "whole")}</measure>
          </part>
        </score-partwise>
    """.trimIndent()

    private fun withPickupAndSomethingDropped() = """
        <score-partwise>
          <part-list><score-part id="P1"><part-name>Piano</part-name></score-part></part-list>
          <part id="P1">
            <measure number="0" implicit="yes">
              <attributes><divisions>1</divisions><transpose><chromatic>2</chromatic></transpose></attributes>
              ${note("C", 4, 4, "whole")}
            </measure>
          </part>
        </score-partwise>
    """.trimIndent()

    private fun fromBarOne() = """
        <score-partwise>
          <part-list><score-part id="P1"><part-name>Piano</part-name></score-part></part-list>
          <part id="P1">
            <measure number="1">
              <attributes><divisions>1</divisions></attributes>
              ${note("C", 4, 4, "whole")}
            </measure>
            <measure number="2">${note("D", 4, 4, "whole")}</measure>
          </part>
        </score-partwise>
    """.trimIndent()
}
