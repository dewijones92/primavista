package com.dewijones92.primavista.notation

/**
 * A length in **staff spaces** — the distance between two staff lines, and the unit all classical
 * engraving is specified in. Bravura's own metrics are published in these units, so working in them
 * means the layout engine never sees a pixel and the whole staff scales by choosing one number.
 */
@JvmInline
public value class StaffSpaces(public val value: Double) : Comparable<StaffSpaces> {
    public operator fun plus(other: StaffSpaces): StaffSpaces = StaffSpaces(value + other.value)
    public operator fun minus(other: StaffSpaces): StaffSpaces = StaffSpaces(value - other.value)
    public operator fun times(factor: Double): StaffSpaces = StaffSpaces(value * factor)
    override fun compareTo(other: StaffSpaces): Int = value.compareTo(other.value)

    public companion object {
        public val ZERO: StaffSpaces = StaffSpaces(0.0)
    }
}

/**
 * The SMuFL glyphs this app draws, by canonical name.
 *
 * **No codepoints here on purpose.** They live in the shipped `assets/smufl/glyphnames.json`,
 * trimmed from the W3C SMuFL spec by `tools/smufl/trim-metadata.py` from the one list in
 * `tools/smufl/glyphs.txt`. A unit test asserts this enum's [glyphName]s are exactly that file's
 * keys, so a glyph cannot be used in code without being shipped, or shipped without being used,
 * and a codepoint cannot be mistyped into a box character.
 */
public enum class SmuflGlyph(public val glyphName: String) {
    GClef("gClef"),
    FClef("fClef"),
    CClef("cClef"),

    NoteheadDoubleWhole("noteheadDoubleWhole"),
    NoteheadWhole("noteheadWhole"),
    NoteheadHalf("noteheadHalf"),
    NoteheadBlack("noteheadBlack"),

    RestDoubleWhole("restDoubleWhole"),
    RestWhole("restWhole"),
    RestHalf("restHalf"),
    RestQuarter("restQuarter"),
    Rest8th("rest8th"),
    Rest16th("rest16th"),
    Rest32nd("rest32nd"),

    Flag8thUp("flag8thUp"),
    Flag8thDown("flag8thDown"),
    Flag16thUp("flag16thUp"),
    Flag16thDown("flag16thDown"),
    Flag32ndUp("flag32ndUp"),
    Flag32ndDown("flag32ndDown"),

    AccidentalDoubleFlat("accidentalDoubleFlat"),
    AccidentalFlat("accidentalFlat"),
    AccidentalNatural("accidentalNatural"),
    AccidentalSharp("accidentalSharp"),
    AccidentalDoubleSharp("accidentalDoubleSharp"),

    AugmentationDot("augmentationDot"),

    BarlineSingle("barlineSingle"),
    BarlineDouble("barlineDouble"),
    BarlineFinal("barlineFinal"),

    Brace("brace"),

    TimeSig0("timeSig0"),
    TimeSig1("timeSig1"),
    TimeSig2("timeSig2"),
    TimeSig3("timeSig3"),
    TimeSig4("timeSig4"),
    TimeSig5("timeSig5"),
    TimeSig6("timeSig6"),
    TimeSig7("timeSig7"),
    TimeSig8("timeSig8"),
    TimeSig9("timeSig9"),
    TimeSigCommon("timeSigCommon"),
    TimeSigCutCommon("timeSigCutCommon"),
    ;

    public companion object {
        public fun timeSigDigit(digit: Int): SmuflGlyph {
            require(digit in 0..9) { "$digit is not a digit" }
            return entries.first { it.glyphName == "timeSig$digit" }
        }
    }
}

/**
 * Supplies the two metadata files. A port because `:core:notation` is pure JVM and the files live
 * in Android assets — the app reads them, this module parses them.
 */
public interface GlyphMetricsSource {
    /** Bravura's own metrics, untrimmed: it must stay authoritative as the font version moves. */
    public fun bravuraMetadataJson(): String

    /** The trimmed SMuFL name→codepoint map. */
    public fun glyphNamesJson(): String
}

public data class GlyphBox(
    val southWestX: StaffSpaces,
    val southWestY: StaffSpaces,
    val northEastX: StaffSpaces,
    val northEastY: StaffSpaces,
) {
    public val width: StaffSpaces get() = northEastX - southWestX
    public val height: StaffSpaces get() = northEastY - southWestY
}

/**
 * Engraving constants from the font, not from us. Stem thickness, beam thickness and the rest are
 * design decisions the typeface's designer already made; overriding them by hand is how a staff
 * ends up looking almost right.
 */
public data class EngravingDefaults(
    val staffLineThickness: StaffSpaces,
    val stemThickness: StaffSpaces,
    val beamThickness: StaffSpaces,
    val beamSpacing: StaffSpaces,
    val legerLineThickness: StaffSpaces,
    val legerLineExtension: StaffSpaces,
    val thinBarlineThickness: StaffSpaces,
    val thickBarlineThickness: StaffSpaces,
    val barlineSeparation: StaffSpaces,
)

/**
 * Parsed font metrics. Built once from a [GlyphMetricsSource] and reused; the metadata is 1.2MB, so
 * parsing it per layout would be visible.
 */
public interface GlyphMetrics {
    public val engraving: EngravingDefaults

    public fun codepoint(glyph: SmuflGlyph): Int

    public fun advanceWidth(glyph: SmuflGlyph): StaffSpaces

    public fun boundingBox(glyph: SmuflGlyph): GlyphBox

    /**
     * A named attachment point, e.g. `stemUpSE` on a notehead — where a stem must actually meet it.
     * Placing stems by eye instead of by anchor is the difference between engraving and drawing.
     */
    public fun anchor(glyph: SmuflGlyph, name: String): Pair<StaffSpaces, StaffSpaces>?
}
