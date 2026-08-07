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
    /**
     * Vertical scale, 1.0 for everything except the grand-staff brace.
     *
     * The brace is the one glyph whose height is not a property of the typeface but of the system it
     * spans — Bravura draws it about four spaces tall, and a grand staff is sixteen. Without this the
     * renderer has no way to stretch it and the brace sits next to the lower staff looking like a
     * mistake.
     */
    val scaleY: Double = 1.0,
)

/** Staff lines, stems, leger lines and barlines. One primitive: the renderer draws thick lines. */
public data class LaidOutLine(
    val x1: StaffSpaces,
    val y1: StaffSpaces,
    val x2: StaffSpaces,
    val y2: StaffSpaces,
    val thickness: StaffSpaces,
)

/**
 * A beam is a sloped quadrilateral, not a line, because its ends are cut vertically.
 *
 * [startY] and [endY] are the beam's **centre** line, so a renderer extends half [thickness] either
 * side and a beamed stem ends on it. Stated because "the edge" is an equally natural reading, and
 * the two differ by half a beam — enough to leave a visible gap between stem and beam.
 */
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
 *
 * **Nullable, and that is the point.** A tie continuation is drawn but never attacked, so it
 * borrows the index of the attack it continues; when a source names a tie whose attack is not in
 * the score — a truncated excerpt, a tie into a repeat — there is no such index, and the previous
 * contract's non-null `Int` forced an answer of 0. That silently pointed the second bar of a piece
 * at the first note of it, so a verdict coloured an unrelated notehead: a wrong answer offered
 * where none exists, which docs/spec.md I2 forbids. A consumer must treat null as "this notehead
 * carries no verdict" rather than defaulting it.
 */
public data class LaidOutNote(
    val attackIndex: Int?,
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

/**
 * Where one measure begins, and how much room it got.
 *
 * Both spans are carried rather than derived from the next anchor, for two reasons that turned out
 * to be blockers. Interpolating inside the **last** measure has no following anchor to measure
 * against; and a measure that begins with a clef or key change is **wider than its duration
 * implies**, because that furniture has to be drawn before the first note. A single global
 * ticks-to-x scale cannot express either, so a mid-piece clef change ended up applied to the notes
 * but never drawn — the staff showing a different pitch from the one being judged, which is a
 * breach of spec I2 rather than a cosmetic omission.
 *
 * So x is **piecewise**: linear within a measure, with each measure's own span. [noteAreaX] is where
 * the notes actually start, after any furniture.
 */
public data class MeasureAnchor(
    val measureIndex: Int,
    val start: Ticks,
    val durationTicks: Ticks,
    val x: StaffSpaces,
    val noteAreaX: StaffSpaces,
    val width: StaffSpaces,
)

private const val DEFAULT_QUARTER_NOTE_WIDTH = 3.2
private const val DEFAULT_STAFF_SEPARATION = 12.0
private const val DEFAULT_LEADING_PADDING = 1.0
private const val DEFAULT_TRAILING_PADDING = 2.0
private const val DEFAULT_MINIMUM_NOTE_SPACING = 1.4
private const val DEFAULT_STEM_LENGTH = 3.5
private const val DEFAULT_BEAM_NOTE_LIMIT = 4

/**
 * The knobs that are ours rather than the font's. Everything not here comes from
 * [GlyphMetrics.engraving], because the typeface designer already decided it.
 */
public data class LayoutStyle(
    /** Horizontal space one quarter note occupies. The single dial controlling density. */
    val quarterNoteWidth: StaffSpaces = StaffSpaces(DEFAULT_QUARTER_NOTE_WIDTH),
    /** Vertical gap between the two staves of a grand staff, top line to top line. */
    val staffSeparation: StaffSpaces = StaffSpaces(DEFAULT_STAFF_SEPARATION),
    val leadingPadding: StaffSpaces = StaffSpaces(DEFAULT_LEADING_PADDING),
    val trailingPadding: StaffSpaces = StaffSpaces(DEFAULT_TRAILING_PADDING),
    val minimumNoteSpacing: StaffSpaces = StaffSpaces(DEFAULT_MINIMUM_NOTE_SPACING),
    /** Stems are conventionally 3.5 spaces, lengthened to reach the middle line where needed. */
    val standardStemLength: StaffSpaces = StaffSpaces(DEFAULT_STEM_LENGTH),
    val beamNoteCountLimit: Int = DEFAULT_BEAM_NOTE_LIMIT,
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
