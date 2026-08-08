package com.dewijones92.primavista.ui.staff

import com.dewijones92.primavista.notation.BravuraGlyphMetrics
import com.dewijones92.primavista.notation.ClassicalStaffLayout
import com.dewijones92.primavista.notation.GlyphMetricsSource
import com.dewijones92.primavista.notation.SmuflGlyph
import com.dewijones92.primavista.score.Corpus
import com.dewijones92.primavista.score.DomMusicXmlParser
import com.dewijones92.primavista.score.MusicXmlResult
import com.dewijones92.primavista.score.Score
import com.dewijones92.primavista.score.Ticks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The pinned strip is a correctness feature, not decoration: without it the key signature leaves the
 * screen inside the first bar and Dewi is sight-reading in G major with no way to see that.
 *
 * So the assertions are about *what the strip contains*, not about how it looks — the clef and the
 * key have to still be there deep into the piece, and nothing may be pinned that would overlap the
 * music, which is what the width assertion holds.
 */
class PinnedFurnitureTest {

    private val assets = File("src/main/assets/smufl")

    private val metrics = BravuraGlyphMetrics.from(
        object : GlyphMetricsSource {
            override fun bravuraMetadataJson() = File(assets, "bravura_metadata.json").readText()
            override fun glyphNamesJson() = File(assets, "glyphnames.json").readText()
        },
    )

    private fun minuet(): Score {
        val piece = Corpus.pieces.first { it.title.contains("Minuet") }
        val parsed = Corpus.parse(piece, DomMusicXmlParser())
        assertTrue("$piece did not parse: $parsed", parsed is MusicXmlResult.Parsed)
        return (parsed as MusicXmlResult.Parsed).score
    }

    @Test
    fun `the clef key and time signature are still pinned in the last bar`() {
        val score = minuet()
        val system = ClassicalStaffLayout().layout(score, metrics)
        val group = PinnedFurniture.of(system).at(score.endsAt - Ticks(1))

        requireNotNull(group) { "nothing pinned at the end of the piece" }
        val glyphs = group.glyphs.map { it.glyph }.toSet()
        assertTrue("no treble clef pinned: $glyphs", SmuflGlyph.GClef in glyphs)
        assertTrue("no bass clef pinned: $glyphs", SmuflGlyph.FClef in glyphs)
        assertTrue("G major's sharp was not pinned: $glyphs", SmuflGlyph.AccidentalSharp in glyphs)
        assertTrue("no time signature pinned: $glyphs", glyphs.any { it.glyphName.startsWith("timeSig") })
    }

    @Test
    fun `the strip is exactly as wide as the furniture it holds`() {
        val system = ClassicalStaffLayout().layout(minuet(), metrics)
        val first = system.measureAnchors.first()
        val group = requireNotNull(PinnedFurniture.of(system).at(Ticks.ZERO))

        assertEquals(first.noteAreaX.value, group.width.value, 1e-9)
        assertTrue(
            "a pinned glyph sits outside the strip and would overdraw the music",
            group.glyphs.all { it.x < group.width },
        )
    }

    @Test
    fun `the reserved gutter covers every group, so it cannot change width mid-piece`() {
        val furniture = PinnedFurniture.of(ClassicalStaffLayout().layout(minuet(), metrics))
        val widest = generateSequence(0L) { it + QUARTER }
            .takeWhile { it < BARS_PROBED * QUARTER }
            .mapNotNull { furniture.at(Ticks(it))?.width?.value }
            .max()

        assertEquals(widest, furniture.gutter.value, 1e-9)
    }
}

private const val QUARTER = 10080L
private const val BARS_PROBED = 32
