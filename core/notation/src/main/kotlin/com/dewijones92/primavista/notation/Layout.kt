package com.dewijones92.primavista.notation

import com.dewijones92.primavista.score.Score
import com.dewijones92.primavista.score.Staff
import com.dewijones92.primavista.score.Ticks

/**
 * Coordinates are in [StaffSpaces], x increasing rightwards and **y increasing downwards** to match
 * every drawing surface it will be handed to. Stated because the musical instinct is the opposite —
 * higher pitch, lower y — and silently disagreeing about that would put every note upside down.
 */
public data class LaidOutGlyph(
    val glyph: SmuflGlyph,
    val x: StaffSpaces,
    val y: StaffSpaces,
)

/** Staff lines, stems, leger lines and barlines. One primitive: the renderer draws thick lines. */
public data class LaidOutLine(
    val x1: StaffSpaces,
    val y1: StaffSpaces,
    val x2: StaffSpaces,
    val y2: StaffSpaces,
    val thickness: StaffSpaces,
)

/** A beam is a sloped quadrilateral, not a line, because its ends are cut vertically. */
public data class LaidOutBeam(
    val startX: StaffSpaces,
    val startY: StaffSpaces,
    val endX: StaffSpaces,
    val endY: StaffSpaces,
    val thickness: StaffSpaces,
)

public data class LaidOutCurve(
    val startX: StaffSpaces,
    val startY: StaffSpaces,
    val controlX: StaffSpaces,
    val controlY: StaffSpaces,
    val endX: StaffSpaces,
    val endY: StaffSpaces,
    val thickness: StaffSpaces,
)

/**
 * A laid-out note, carrying [attackIndex] back into [Score.attackedNotes].
 *
 * That index is the join between the three things the app does with a note — draw it, judge it,
 * and colour it once judged. Without it the UI would have to re-find the note it just drew in
 * order to show a verdict on it, and the two searches would eventually disagree.
 */
public data class LaidOutNote(
    val attackIndex: Int,
    val onset: Ticks,
    val staff: Staff,
    val notehead: LaidOutGlyph,
    val accidental: LaidOutGlyph?,
    val dots: List<LaidOutGlyph>,
    val stem: LaidOutLine?,
    val flag: LaidOutGlyph?,
    val legerLines: List<LaidOutLine>,
)

/**
 * Everything needed to draw one continuous system of music, plus the two mappings that make it
 * playable rather than merely visible.
 */
public data class StaffSystem(
    val notes: List<LaidOutNote>,
    val glyphs: List<LaidOutGlyph>,
    val lines: List<LaidOutLine>,
    val beams: List<LaidOutBeam>,
    val curves: List<LaidOutCurve>,
    val width: StaffSpaces,
    val height: StaffSpaces,
    /** Y of each staff's top line, so the renderer can place overlays without re-deriving geometry. */
    val staffTopY: Map<Staff, StaffSpaces>,
    /** Ticks→x, sampled at every measure start. The scroll and the playhead interpolate within it. */
    val measureAnchors: List<MeasureAnchor>,
)

public data class MeasureAnchor(val measureIndex: Int, val start: Ticks, val x: StaffSpaces)

/**
 * The knobs that are ours rather than the font's. Everything not here comes from
 * [GlyphMetrics.engraving], because the typeface designer already decided it.
 */
public data class LayoutStyle(
    /** Horizontal space one quarter note occupies. The single dial controlling density. */
    val quarterNoteWidth: StaffSpaces = StaffSpaces(3.2),
    /** Vertical gap between the two staves of a grand staff, top line to top line. */
    val staffSeparation: StaffSpaces = StaffSpaces(12.0),
    val leadingPadding: StaffSpaces = StaffSpaces(1.0),
    val trailingPadding: StaffSpaces = StaffSpaces(2.0),
    val minimumNoteSpacing: StaffSpaces = StaffSpaces(1.4),
    /** Stems are conventionally 3.5 spaces, lengthened to reach the middle line where needed. */
    val standardStemLength: StaffSpaces = StaffSpaces(3.5),
    val beamNoteCountLimit: Int = 4,
)

/**
 * Turns a [Score] into geometry. Pure, and pure is what makes it testable: a layout is a list of
 * numbers, so a staff's engraving can be asserted exactly rather than looked at.
 *
 * **One engine for every clef.** Treble, bass and the grand staff differ only by
 * `Clef.referenceDiatonicIndex` and which staff a note belongs to. A `when (clef)` anywhere in an
 * implementation is the pillar-split failure CLAUDE.md's first law forbids.
 */
public interface StaffLayout {
    public fun layout(score: Score, metrics: GlyphMetrics, style: LayoutStyle = LayoutStyle()): StaffSystem

    /**
     * Where the playhead belongs for a musical position, interpolating between measure anchors.
     * The one conversion from musical time to horizontal position, so the drawn playhead and the
     * judged note cannot drift apart (docs/spec.md I1).
     */
    public fun xOf(system: StaffSystem, position: Ticks, style: LayoutStyle = LayoutStyle()): StaffSpaces
}
