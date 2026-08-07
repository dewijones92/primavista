package com.dewijones92.primavista.render

import com.dewijones92.primavista.notation.GlyphMetrics
import com.dewijones92.primavista.notation.LaidOutGlyph
import com.dewijones92.primavista.notation.LaidOutLine
import com.dewijones92.primavista.notation.StaffSystem
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.geom.GeneralPath
import java.awt.geom.QuadCurve2D
import java.awt.image.BufferedImage
import java.io.File

/**
 * Renders a laid-out [StaffSystem] to a PNG on the JVM, with no device and no Compose.
 *
 * A second renderer looks like a DRY violation and is the opposite: all the *geometry* still comes
 * from `:core:notation`, and this only converts it to Java2D calls. Having two independent output
 * adapters over one layout engine is what proves the engine is renderer-agnostic — and it buys two
 * things the Compose path cannot. Engraving can be eyeballed in a second instead of after an
 * emulator build, and a golden PNG can be diffed in a unit test, so a layout regression is caught
 * by CI rather than by noticing the staff looks odd.
 */
public class StaffPngRenderer(
    private val metrics: GlyphMetrics,
    private val fontFile: File,
    private val staffSpacePx: Float = 14f,
    private val theme: Theme = Theme.Manuscript,
) {
    public enum class Theme(
        public val paper: Color,
        public val ink: Color,
        public val staffLine: Color,
        public val playhead: Color,
    ) {
        Manuscript(Color(0xFF, 0xFD, 0xF8), Color(0x14, 0x12, 0x1A), Color(0x5A, 0x56, 0x48), Color(0x8A, 0x5A, 0x00)),
        Ink(Color(0x1A, 0x17, 0x24), Color(0xF4, 0xF1, 0xFA), Color(0x8A, 0x82, 0xA0), Color(0xE8, 0xA1, 0x3C)),
    }

    private val bravura: Font by lazy {
        // SMuFL defines a music font's em as four staff spaces, so this is the one conversion
        // between the engraving unit and a point size — the same constant the Compose path uses.
        Font.createFont(Font.TRUETYPE_FONT, fontFile).deriveFont(staffSpacePx * SPACES_PER_EM)
    }

    public fun render(system: StaffSystem, playheadX: Double? = null, marginSpaces: Double = 2.0): BufferedImage {
        val margin = (marginSpaces * staffSpacePx).toInt()
        val width = (system.width.value * staffSpacePx).toInt() + margin * 2
        val height = (system.height.value * staffSpacePx).toInt() + margin * 2
        val image = BufferedImage(width.coerceAtLeast(1), height.coerceAtLeast(1), BufferedImage.TYPE_INT_RGB)

        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = theme.paper
        g.fillRect(0, 0, width, height)
        g.translate(margin, margin)
        g.font = bravura

        system.lines.forEach { line ->
            g.color = if (line.y1 == line.y2) theme.staffLine else theme.ink
            drawLine(g, line)
        }

        g.color = theme.ink
        system.beams.forEach { beam ->
            val half = beam.thickness.value * staffSpacePx / 2
            val path = GeneralPath().apply {
                moveTo(beam.startX.value * staffSpacePx, beam.startY.value * staffSpacePx - half)
                lineTo(beam.endX.value * staffSpacePx, beam.endY.value * staffSpacePx - half)
                lineTo(beam.endX.value * staffSpacePx, beam.endY.value * staffSpacePx + half)
                lineTo(beam.startX.value * staffSpacePx, beam.startY.value * staffSpacePx + half)
                closePath()
            }
            g.fill(path)
        }

        system.curves.forEach { curve ->
            g.stroke = BasicStroke(
                (curve.thickness.value * staffSpacePx).toFloat(),
                BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND,
            )
            g.draw(
                QuadCurve2D.Double(
                    curve.startX.value * staffSpacePx,
                    curve.startY.value * staffSpacePx,
                    curve.controlX.value * staffSpacePx,
                    curve.controlY.value * staffSpacePx,
                    curve.endX.value * staffSpacePx,
                    curve.endY.value * staffSpacePx,
                ),
            )
        }

        system.glyphs.forEach { drawGlyph(g, it) }
        system.notes.forEach { note ->
            note.legerLines.forEach {
                g.color = theme.ink
                drawLine(g, it)
            }
            note.stem?.let {
                g.color = theme.ink
                drawLine(g, it)
            }
            note.accidental?.let { drawGlyph(g, it) }
            drawGlyph(g, note.notehead)
            note.flag?.let { drawGlyph(g, it) }
            note.dots.forEach { drawGlyph(g, it) }
        }

        playheadX?.let { x ->
            g.color = theme.playhead
            g.stroke = BasicStroke(PlayheadWidthSpaces * staffSpacePx)
            g.drawLine(
                (x * staffSpacePx).toInt(),
                -margin / 2,
                (x * staffSpacePx).toInt(),
                (system.height.value * staffSpacePx).toInt() + margin / 2,
            )
        }

        g.dispose()
        return image
    }

    public fun renderToFile(system: StaffSystem, target: File, playheadX: Double? = null) {
        target.parentFile?.mkdirs()
        javax.imageio.ImageIO.write(render(system, playheadX), "png", target)
    }

    private fun drawLine(g: java.awt.Graphics2D, line: LaidOutLine) {
        g.stroke = BasicStroke(
            (line.thickness.value * staffSpacePx).toFloat(),
            BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_MITER,
        )
        g.draw(
            java.awt.geom.Line2D.Double(
                line.x1.value * staffSpacePx,
                line.y1.value * staffSpacePx,
                line.x2.value * staffSpacePx,
                line.y2.value * staffSpacePx,
            ),
        )
    }

    private fun drawGlyph(g: java.awt.Graphics2D, glyph: LaidOutGlyph) {
        g.color = theme.ink
        // scaleY is 1.0 for everything except the grand-staff brace, whose height is a property of
        // the system it spans rather than of the typeface. Ignoring it draws the brace at its
        // natural four spaces next to a sixteen-space system, which looks like a mistake.
        g.font = if (glyph.scaleY == 1.0) {
            bravura
        } else {
            bravura.deriveFont(java.awt.geom.AffineTransform.getScaleInstance(1.0, glyph.scaleY))
        }
        // Java2D draws text from the baseline, and Bravura registers every glyph's origin on the
        // staff line it belongs to — which IS the baseline. So unlike the Compose path, no vertical
        // correction is needed here.
        g.drawString(
            String(Character.toChars(metrics.codepoint(glyph.glyph))),
            (glyph.x.value * staffSpacePx).toFloat(),
            (glyph.y.value * staffSpacePx).toFloat(),
        )
        g.font = bravura
    }

    private companion object {
        const val SPACES_PER_EM = 4f
        const val PlayheadWidthSpaces = 0.22f
    }
}
