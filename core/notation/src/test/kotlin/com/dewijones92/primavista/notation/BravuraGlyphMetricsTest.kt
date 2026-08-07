package com.dewijones92.primavista.notation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BravuraGlyphMetricsTest {
    private val metrics = RealBravura.metrics

    @Test
    fun `engraving defaults are the font's own, not ours`() {
        val engraving = metrics.engraving
        assertEquals(0.13, engraving.staffLineThickness.value, EPSILON)
        assertEquals(0.12, engraving.stemThickness.value, EPSILON)
        assertEquals(0.5, engraving.beamThickness.value, EPSILON)
        assertEquals(0.25, engraving.beamSpacing.value, EPSILON)
        assertEquals(0.16, engraving.legerLineThickness.value, EPSILON)
        assertEquals(0.4, engraving.legerLineExtension.value, EPSILON)
        assertEquals(0.16, engraving.thinBarlineThickness.value, EPSILON)
        assertEquals(0.5, engraving.thickBarlineThickness.value, EPSILON)
        assertEquals(0.4, engraving.barlineSeparation.value, EPSILON)
    }

    @Test
    fun `every glyph the app draws has a codepoint, an advance and a box`() {
        SmuflGlyph.entries.forEach { glyph ->
            assertTrue("${glyph.glyphName} codepoint", metrics.codepoint(glyph) > 0)
            assertTrue("${glyph.glyphName} advance", metrics.advanceWidth(glyph).value > 0.0)
            assertTrue("${glyph.glyphName} width", metrics.boundingBox(glyph).width.value > 0.0)
            assertTrue("${glyph.glyphName} height", metrics.boundingBox(glyph).height.value > 0.0)
        }
    }

    @Test
    fun `the shipped glyph list is exactly the glyphs the code draws`() {
        val shipped = Json.parseToJsonElement(RealBravura.glyphNamesJson).jsonObject.keys
        val drawn = SmuflGlyph.entries.map { it.glyphName }.toSet()
        assertEquals("shipped but never drawn", emptySet<String>(), shipped - drawn)
        assertEquals("drawn but never shipped", emptySet<String>(), drawn - shipped)
    }

    @Test
    fun `codepoints are parsed out of the U+ form`() {
        assertEquals(0xE050, metrics.codepoint(SmuflGlyph.GClef))
        assertEquals(0xE062, metrics.codepoint(SmuflGlyph.FClef))
        assertEquals(0xE0A4, metrics.codepoint(SmuflGlyph.NoteheadBlack))
        assertEquals(0xE000, metrics.codepoint(SmuflGlyph.Brace))
    }

    @Test
    fun `notehead advance and bounding box match the metadata`() {
        assertEquals(1.18, metrics.advanceWidth(SmuflGlyph.NoteheadBlack).value, EPSILON)
        val box = metrics.boundingBox(SmuflGlyph.NoteheadBlack)
        assertEquals(0.0, box.southWestX.value, EPSILON)
        assertEquals(-0.5, box.southWestY.value, EPSILON)
        assertEquals(1.18, box.northEastX.value, EPSILON)
        assertEquals(0.5, box.northEastY.value, EPSILON)
    }

    @Test
    fun `stem anchors come from the font and unknown anchors are absent`() {
        val up = metrics.anchor(SmuflGlyph.NoteheadBlack, "stemUpSE")
        assertEquals(1.18, up?.first?.value ?: 0.0, EPSILON)
        assertEquals(0.168, up?.second?.value ?: 0.0, EPSILON)
        val down = metrics.anchor(SmuflGlyph.NoteheadBlack, "stemDownNW")
        assertEquals(0.0, down?.first?.value ?: 1.0, EPSILON)
        assertEquals(-0.168, down?.second?.value ?: 0.0, EPSILON)
        assertNull(metrics.anchor(SmuflGlyph.NoteheadBlack, "stemSidewaysNNE"))
        assertNull(metrics.anchor(SmuflGlyph.BarlineSingle, "stemUpSE"))
    }

    @Test
    fun `the 1_2MB metadata is read once, not once per lookup`() {
        var reads = 0
        val counting = object : GlyphMetricsSource {
            override fun bravuraMetadataJson(): String {
                reads++
                return RealBravura.metadataJson
            }

            override fun glyphNamesJson(): String = RealBravura.glyphNamesJson
        }
        val parsed = BravuraGlyphMetrics.from(counting)
        repeat(100) {
            SmuflGlyph.entries.forEach { glyph ->
                parsed.advanceWidth(glyph)
                parsed.boundingBox(glyph)
                parsed.anchor(glyph, "stemUpSE")
            }
        }
        assertEquals(1, reads)
    }

    @Test
    fun `a glyph missing from the metadata is named in the failure`() {
        val source = sourceOf(RealBravura.metadataJson, """{"gClef":{"codepoint":"U+E050"}}""")
        try {
            BravuraGlyphMetrics.from(source)
            fail("expected a missing-glyph failure")
        } catch (expected: IllegalStateException) {
            val message = expected.message ?: ""
            assertTrue(message, message.contains("codepoint"))
            assertTrue(message, message.contains("noteheadBlack"))
        }
    }

    @Test
    fun `an engraving default that is not a number is named in the failure`() {
        val doctored = RealBravura.metadataJson.replace(
            """"stemThickness": 0.12""",
            """"stemThickness": ["Academico", "serif"]""",
        )
        assertTrue("metadata shape changed", doctored != RealBravura.metadataJson)
        try {
            BravuraGlyphMetrics.from(sourceOf(doctored, RealBravura.glyphNamesJson))
            fail("expected a non-numeric engraving default to be rejected")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message ?: "", (expected.message ?: "").contains("stemThickness"))
        }
    }

    @Test
    fun `sections we do not model do not break the parse`() {
        val extended = RealBravura.metadataJson.replaceFirst("{", """{"somethingNew": {"nested": [1, 2, 3]},""")
        val parsed = BravuraGlyphMetrics.from(sourceOf(extended, RealBravura.glyphNamesJson))
        assertEquals(0.12, parsed.engraving.stemThickness.value, EPSILON)
    }
}
