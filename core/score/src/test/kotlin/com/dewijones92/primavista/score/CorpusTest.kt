package com.dewijones92.primavista.score

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CorpusTest {

    private val parser = DomMusicXmlParser()
    private val skills = DerivedScoreSkills()

    @Test
    fun `every shipped piece parses with nothing dropped`() {
        assertEquals(3, Corpus.pieces.size)
        for (piece in Corpus.pieces) {
            val result = Corpus.parse(piece, parser)
            assertTrue("${piece.title}: $result", result is MusicXmlResult.Parsed)
            val parsed = result as MusicXmlResult.Parsed
            assertTrue("${piece.title} dropped ${parsed.dropped}", parsed.isClean)
        }
    }

    @Test
    fun `every shipped piece is grand staff with both hands written`() {
        for (piece in Corpus.pieces) {
            val score = scoreOf(piece)
            assertTrue(piece.title, score.isGrandStaff)
            assertEquals(listOf(Staff.Upper, Staff.Lower), score.staves)
            assertTrue(piece.title, score.notes.any { it.staff == Staff.Upper })
            assertTrue(piece.title, score.notes.any { it.staff == Staff.Lower })
            assertTrue(piece.title, skills.skillsOf(score).contains(SkillTag.HandIndependence))
        }
    }

    @Test
    fun `every bar of every shipped piece adds up exactly`() {
        for (piece in Corpus.pieces) {
            val score = scoreOf(piece)
            assertEquals("${piece.title} bars", 4, score.measures.size)
            assertBarsAddUp(piece.title, score)
        }
    }

    @Test
    fun `the minuet keeps its key signature and its written F sharp`() {
        val score = scoreOf(Corpus.pieces.first { it.id == ScoreId("corpus-minuet-in-g") })
        assertEquals(KeySignature(1), score.measures.first().key)
        assertEquals(112, score.defaultTempoBpm)
        assertTrue(score.notes.any { it.pitch == Pitch(Letter.F, Alter.Sharp, 5) })
        assertTrue(
            "an F sharp in G major is the key, not a reading accidental",
            skills.skillsOf(score).none { it is SkillTag.Accidental },
        )
    }

    @Test
    fun `the tie in the Beethoven theme is read as one attack held over the barline`() {
        val score = scoreOf(Corpus.pieces.first { it.id == ScoreId("corpus-ode-to-joy") })
        val tiedFrom = score.notes.filter { it.tiedFromPrevious }
        val tiedTo = score.notes.filter { it.tiedToNext }
        assertEquals(1, tiedFrom.size)
        assertEquals(1, tiedTo.size)
        assertEquals(tiedTo.single().pitch, tiedFrom.single().pitch)
        assertEquals(score.notes.size - 1, score.attackedNotes.size)
    }

    @Test
    fun `the chord in the folk theme shares one onset`() {
        val score = scoreOf(Corpus.pieces.first { it.id == ScoreId("corpus-ah-vous-dirai-je-maman") })
        val lastBar = score.measures.last().start
        val chord = score.notes.filter { it.staff == Staff.Upper && it.onset > lastBar }
            .groupBy { it.onset }
            .maxBy { it.value.size }
        assertEquals(2, chord.value.size)
    }

    @Test
    fun `every piece records where it came from and under what licence`() {
        for (piece in Corpus.pieces) {
            assertTrue(piece.title, piece.source.isNotBlank())
            assertTrue(piece.title, piece.licence.contains("public domain"))
            assertTrue(piece.title, piece.composer.isNotBlank())
        }
        assertEquals(Corpus.pieces.size, Corpus.pieces.map { it.id }.toSet().size)
        assertEquals(Corpus.pieces.size, Corpus.pieces.map { it.resourcePath }.toSet().size)
    }

    @Test
    fun `a parsed piece keeps its id and its licence, so a report can name what was played`() {
        for (piece in Corpus.pieces) {
            val score = scoreOf(piece)
            assertEquals(piece.id, score.id)
            assertEquals(ScoreOrigin.Parsed(piece.id.value, piece.licence), score.origin)
            assertEquals(piece.title, score.title)
        }
    }

    @Test
    fun `every shipped piece is polyphonic, at a bar that exists`() {
        for (piece in Corpus.pieces) {
            val score = scoreOf(piece)
            assertEquals(piece.title, Polyphony.Poly, score.polyphony)
            val bar = score.firstPolyphonicMeasure()
            assertTrue("${piece.title} bar $bar", bar != null && bar in 1..score.measures.size)
        }
    }

    private fun scoreOf(piece: CorpusPiece): Score =
        (Corpus.parse(piece, parser) as MusicXmlResult.Parsed).score

    private fun assertBarsAddUp(label: String, score: Score) {
        for (measure in score.measures) {
            val barEnd = measure.start + measure.time.measureTicks
            for (staff in score.staves) {
                val inBar = score.events.filter { it.staff == staff && it.onset >= measure.start && it.onset < barEnd }
                if (inBar.isEmpty()) continue
                val sum = inBar.groupBy { it.onset }.values.sumOf { atOnset -> atOnset.first().duration.ticks.value }
                assertEquals("$label bar ${measure.number} $staff", measure.time.measureTicks.value, sum)
            }
        }
    }
}
