package com.dewijones92.primavista.score

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val MIN_IMPORTED_BARS = 8
private val PASSAGE_LENGTHS = listOf(4, 8, 16)

/**
 * The shipped repertoire, parsed **on a device**.
 *
 * CLAUDE.md names the precedent exactly: Totum shipped an RSS bug that passed every JVM test and
 * only failed on a phone, because Android's XML parser rejects `DocumentBuilder` bean-property
 * toggles the desktop JVM accepts. This repo parses XML too, and until now every one of the 44
 * shipped pieces had only ever been read by a JVM.
 *
 * The claims here deliberately mirror `CorpusTest`'s, and the JVM run is the baseline: the same
 * assertions on both platforms mean a parser that behaves differently on Android shows up as a
 * device-only failure rather than as nothing at all.
 */
@RunWith(AndroidJUnit4::class)
class CorpusOnDeviceTest {

    private val parser = DomMusicXmlParser()
    private val skills = DerivedScoreSkills()

    @Test
    fun everyShippedPieceParsesOnADeviceLosingNothingAReaderWouldNotice() {
        val failures = Corpus.pieces.mapNotNull { piece ->
            when (val result = Corpus.parse(piece, parser)) {
                is MusicXmlResult.Failed -> "${piece.title}: ${result.reason}"
                is MusicXmlResult.Parsed ->
                    result.material.takeIf { it.isNotEmpty() }?.let { "${piece.title} lost music: $it" }
            }
        }

        assertTrue("no pieces ship, so this proves nothing", Corpus.pieces.isNotEmpty())
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun everyShippedPieceIsGrandStaffWithBothHandsWritten() {
        for (piece in Corpus.pieces) {
            val score = scoreOf(piece)
            assertTrue(piece.title, score.isGrandStaff)
            assertTrue(piece.title, score.notes.any { it.staff == Staff.Upper })
            assertTrue(piece.title, score.notes.any { it.staff == Staff.Lower })
            assertTrue(piece.title, skills.skillsOf(score).contains(SkillTag.HandIndependence))
        }
    }

    /** The bar labels a device shows have to be the printed ones too, not this build's positions. */
    @Test
    fun theShippedPiecesKeepTheBarNumbersTheirEngravingsPrint() {
        val opening = Corpus.pieces.associate { it.id.value to scoreOf(it).measures.first().number }

        assertTrue("no piece opens on a pickup, so this proves nothing", opening.values.any { it == 0 })
        assertEquals(emptyList<String>(), opening.filterValues { it != 0 && it != 1 }.keys.toList())
    }

    /** The passage arithmetic runs on the device too, so it is checked there. */
    @Test
    fun noPassageOfAnyShippedPieceReachesPastItsOwnLastBarline() {
        val leaks = Corpus.pieces.flatMap { piece ->
            val score = scoreOf(piece)
            PASSAGE_LENGTHS.flatMap { bars -> leaksIn(piece.id.value, score, bars) }
        }

        assertEquals(emptyList<String>(), leaks)
    }

    @Test
    fun everyImportedPieceIsLongEnoughToBeWorthReading() {
        val imported = Corpus.pieces.filter { it.part == PartChoice.Keyboard }

        assertTrue("nothing was imported, so this proves nothing", imported.isNotEmpty())
        for (piece in imported) {
            val score = scoreOf(piece)
            assertTrue("${piece.title} has ${score.measures.size} bars", score.measures.size >= MIN_IMPORTED_BARS)
            assertTrue("${piece.title} has no four-bar passage", score.passages(bars = 4).isNotEmpty())
        }
    }

    private fun leaksIn(id: String, score: Score, bars: Int): List<String> =
        (score.measures.indices step bars)
            .filter { it + bars <= score.measures.size }
            .mapNotNull { from ->
                val endsAt = score.measures.getOrNull(from + bars)?.start
                    ?: score.measures.last().let { it.start + it.time.measureTicks }
                val expected = score.events.count { it.onset >= score.measures[from].start && it.onset < endsAt }
                val got = score.excerpt(from, bars).events.size
                "$id bars ${from + 1}..${from + bars}: $got events, expected $expected".takeIf { got != expected }
            }

    private fun scoreOf(piece: CorpusPiece): Score =
        (Corpus.parse(piece, parser) as MusicXmlResult.Parsed).score
}
