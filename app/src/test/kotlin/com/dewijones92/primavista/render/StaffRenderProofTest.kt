package com.dewijones92.primavista.render

import com.dewijones92.primavista.notation.BravuraGlyphMetrics
import com.dewijones92.primavista.notation.ClassicalStaffLayout
import com.dewijones92.primavista.notation.GlyphMetricsSource
import com.dewijones92.primavista.score.Corpus
import com.dewijones92.primavista.score.DomMusicXmlParser
import com.dewijones92.primavista.score.MusicXmlResult
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Renders the real corpus through the real layout engine to PNGs under
 * `app/build/render-proof/`, then asserts the images are not blank.
 *
 * Not a golden-image test — those are for locking geometry down once it is right, and this exists to
 * answer a cruder question first: does the engraving actually *look* like music? A layout is a list
 * of numbers, and numbers can pass every unit test while producing a staff nobody would call
 * readable. Running it on the JVM means that check takes seconds and needs no device.
 */
class StaffRenderProofTest {

    private val assets = File("src/main/assets/smufl")
    private val fontFile = File("src/main/res/font/bravura.otf")

    private val metricsSource = object : GlyphMetricsSource {
        override fun bravuraMetadataJson() = File(assets, "bravura_metadata.json").readText()
        override fun glyphNamesJson() = File(assets, "glyphnames.json").readText()
    }

    @Test
    fun `every corpus piece renders to a legible staff`() {
        val metrics = BravuraGlyphMetrics.from(metricsSource)
        val layout = ClassicalStaffLayout()
        val parser = DomMusicXmlParser()
        val outputDir = File("build/render-proof")

        var rendered = 0
        Corpus.pieces.forEach { piece ->
            val parsed = Corpus.parse(piece, parser)
            assertTrue(
                "${piece.title} did not parse: $parsed",
                parsed is MusicXmlResult.Parsed,
            )
            val score = (parsed as MusicXmlResult.Parsed).score
            val system = layout.layout(score, metrics)

            StaffPngRenderer.Theme.entries.forEach { theme ->
                val renderer = StaffPngRenderer(metrics, fontFile, staffSpacePx = 14f, theme = theme)
                val image = renderer.render(system, playheadX = system.width.value / 3)
                val target = File(outputDir, "${piece.id.value}-${theme.name.lowercase()}.png")
                target.parentFile?.mkdirs()
                javax.imageio.ImageIO.write(image, "png", target)
                rendered++

                // A blank canvas would satisfy every geometric assertion in :core:notation while
                // showing nothing, so count the pixels that are not the paper colour. This is the
                // one thing the layout tests structurally cannot check.
                val inkPixels = (0 until image.width step 2).sumOf { x ->
                    (0 until image.height step 2).count { y -> image.getRGB(x, y) != theme.paper.rgb }
                }
                assertTrue(
                    "${piece.title} (${theme.name}) rendered $inkPixels ink pixels — effectively blank",
                    inkPixels > MIN_INK_PIXELS,
                )
            }
        }
        assertTrue("nothing was rendered", rendered > 0)
    }

    private companion object {
        const val MIN_INK_PIXELS = 500
    }
}
