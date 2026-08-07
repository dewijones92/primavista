package com.dewijones92.primavista.notation

/** Slides the system down so nothing sits above y = 0, and measures its real height. */
internal fun StaffSystem.fitVertically(metrics: GlyphMetrics, margin: Double): StaffSystem {
    val ys = mutableListOf<Double>()
    val allGlyphs = glyphs + notes.flatMap { listOfNotNull(it.notehead, it.accidental, it.flag) + it.dots }
    allGlyphs.forEach { glyph ->
        val box = metrics.boundingBox(glyph.glyph)
        ys += glyph.y.value - box.northEastY.value * glyph.scaleY
        ys += glyph.y.value - box.southWestY.value * glyph.scaleY
    }
    (lines + notes.flatMap { listOfNotNull(it.stem) + it.legerLines }).forEach { line ->
        val half = line.thickness.value / 2
        ys += listOf(line.y1.value - half, line.y1.value + half, line.y2.value - half, line.y2.value + half)
    }
    beams.forEach { beam ->
        val half = beam.thickness.value / 2
        ys += listOf(beam.startY.value - half, beam.startY.value + half, beam.endY.value - half, beam.endY.value + half)
    }
    curves.forEach { ys += listOf(it.startY.value, it.controlY.value, it.endY.value) }
    staffTopY.values.forEach { ys += listOf(it.value, it.value + STAFF_HEIGHT) }

    val lowest = ys.min()
    val highest = ys.max()
    return shiftedDown(margin - lowest).copy(height = (highest - lowest + 2 * margin).spaces)
}

private fun StaffSystem.shiftedDown(dy: Double): StaffSystem = copy(
    notes = notes.map { it.shiftedDown(dy) },
    glyphs = glyphs.map { it.shiftedDown(dy) },
    lines = lines.map { it.shiftedDown(dy) },
    beams = beams.map { it.shiftedDown(dy) },
    curves = curves.map { it.shiftedDown(dy) },
    staffTopY = staffTopY.mapValues { (_, y) -> (y.value + dy).spaces },
)

private fun LaidOutGlyph.shiftedDown(dy: Double) = copy(y = (y.value + dy).spaces)

private fun LaidOutLine.shiftedDown(dy: Double) =
    copy(y1 = (y1.value + dy).spaces, y2 = (y2.value + dy).spaces)

private fun LaidOutBeam.shiftedDown(dy: Double) =
    copy(startY = (startY.value + dy).spaces, endY = (endY.value + dy).spaces)

private fun LaidOutCurve.shiftedDown(dy: Double) = copy(
    startY = (startY.value + dy).spaces,
    controlY = (controlY.value + dy).spaces,
    endY = (endY.value + dy).spaces,
)

private fun LaidOutNote.shiftedDown(dy: Double) = copy(
    notehead = notehead.shiftedDown(dy),
    accidental = accidental?.shiftedDown(dy),
    dots = dots.map { it.shiftedDown(dy) },
    stem = stem?.shiftedDown(dy),
    legerLines = legerLines.map { it.shiftedDown(dy) },
    flag = flag?.shiftedDown(dy),
)
