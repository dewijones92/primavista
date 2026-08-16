package com.dewijones92.primavista.score

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MIN_IMPORTED_BARS = 8
private val PASSAGE_LENGTHS = listOf(4, 8, 16)
private val corpusDirectories = listOf("/corpus", "/corpus/lieder")

class CorpusTest {

    private val parser = DomMusicXmlParser()
    private val skills = DerivedScoreSkills()

    @Test
    fun `every shipped piece parses, losing nothing a reader would notice`() {
        for (piece in Corpus.pieces) {
            val result = Corpus.parse(piece, parser)
            assertTrue("${piece.title}: $result", result is MusicXmlResult.Parsed)
            val parsed = result as MusicXmlResult.Parsed
            assertEquals("${piece.title} lost music", emptyList<Dropped>(), parsed.material)
        }
    }

    @Test
    fun `the hand-authored openings parse with nothing dropped at all`() {
        for (piece in handAuthored) {
            val parsed = Corpus.parse(piece, parser) as MusicXmlResult.Parsed
            assertTrue("${piece.title} dropped ${parsed.dropped}", parsed.isClean)
        }
    }

    @Test
    fun `both manifests are read, and the imported repertoire is the larger half`() {
        assertEquals(3, handAuthored.size)
        assertTrue("imported ${imported.size}", imported.size >= handAuthored.size)
        assertTrue(imported.all { it.part == PartChoice.Keyboard })
        assertTrue(handAuthored.all { it.part == PartChoice.First })
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
    fun `every bar of every hand-authored opening adds up exactly`() {
        for (piece in handAuthored) {
            val score = scoreOf(piece)
            assertEquals("${piece.title} bars", 4, score.measures.size)
            assertBarsAddUp(piece.title, score)
        }
    }

    @Test
    fun `every imported piece is long enough to be worth reading, and offers a passage`() {
        for (piece in imported) {
            val score = scoreOf(piece)
            assertTrue("${piece.title} has ${score.measures.size} bars", score.measures.size >= MIN_IMPORTED_BARS)
            assertTrue("${piece.title} has no four-bar passage", score.passages(bars = 4).isNotEmpty())
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

    /**
     * The guard the short-bar leak escaped. Every passage of every shipped piece must contain only
     * events that really fall inside its bars — measured against the **next bar's start**, which is
     * where a bar actually ends, rather than against what its time signature nominally holds.
     *
     * Corpus-wide because that is where it was found: 126 leaked events across 41 songs, and none
     * of them in a hand-written fixture. Real engraving has short bars; invented fixtures do not.
     */
    @Test
    fun `no passage of any shipped piece reaches past its own last barline`() {
        val leaks = Corpus.pieces.flatMap { piece ->
            val score = scoreOf(piece)
            PASSAGE_LENGTHS.flatMap { bars -> leaksIn(piece.id.value, score, bars) }
        }

        assertEquals(emptyList<String>(), leaks)
    }

    /**
     * Sixteen of the shipped songs open on a pickup the engraving prints as bar 0. Counting from
     * document position labelled every bar of those pieces one ahead of the page — and the bar
     * number is what Dewi would use to find a passage in the score in front of him.
     */
    @Test
    fun `the shipped pieces keep the bar numbers their engravings print`() {
        val opening = Corpus.pieces.associate { it.id.value to scoreOf(it).measures.first().number }
        val pickups = opening.filterValues { it == 0 }

        // A lower bound rather than a count: which pieces ship is a curated selection that changes
        // whenever the import is re-run, but "at least one opens on a pickup" is what stops this
        // test passing by finding nothing to check.
        assertTrue("no shipped piece opens on a pickup, so this proves nothing", pickups.isNotEmpty())
        assertEquals(
            "a piece opens on a bar number that is neither a pickup nor bar one",
            emptyList<String>(),
            opening.filterValues { it != 0 && it != 1 }.keys.toList(),
        )
    }

    @Test
    fun `a passage of a pickup piece is titled from the printed bar`() {
        val piece = Corpus.pieces.first { scoreOf(it).measures.first().number == 0 }

        val passage = scoreOf(piece).excerpt(fromIndex = 0, bars = 4)

        assertTrue(passage.title, passage.title.endsWith("(bars 0–3)"))
        assertEquals(0, PassageId.read(passage.id)?.fromIndex)
    }

    /**
     * The other direction from "every row has a file", which parsing already proves: a file that
     * ships with **no row naming it** is dead weight in the APK that nothing can ever read, and an
     * import that half-wrote its manifest would leave exactly that.
     */
    @Test
    fun `no score ships that the manifests do not name`() {
        val named = Corpus.pieces.map { it.resourcePath.substringAfterLast('/') }.toSet()
        val shipped = corpusDirectories.flatMap { directory ->
            val url = requireNotNull(Corpus::class.java.getResource(directory)) { "no $directory in the build" }
            java.io.File(url.toURI()).listFiles().orEmpty().filter { it.isFile }.map { it.name }
        }

        assertTrue("no scores found at all, so this proves nothing", shipped.size >= Corpus.pieces.size)
        assertEquals(emptyList<String>(), shipped.filterNot { it.endsWith(".tsv") || it in named })
    }

    @Test
    fun `every piece records where it came from and under what licence`() {
        for (piece in Corpus.pieces) {
            assertTrue(piece.title, piece.source.isNotBlank())
            assertTrue(piece.title, piece.licence.contains("public domain") || piece.licence.contains("CC0"))
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
            // Not `1..size`: sixteen shipped songs open on a pickup printed as bar 0, so the bar
            // a refusal names has to be one of the score's own printed numbers, not a position.
            val bar = score.firstPolyphonicMeasure()
            assertTrue("${piece.title} bar $bar", bar != null && bar in score.measures.map { it.number })
        }
    }

    private val handAuthored get() = Corpus.pieces.filter { it.part == PartChoice.First }
    private val imported get() = Corpus.pieces.filter { it.part == PartChoice.Keyboard }

    /** Every window of [bars] whose event count disagrees with the bars it claims to cover. */
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
