package com.dewijones92.primavista.score

/**
 * Parses a MusicXML document into a [Score].
 *
 * Two hard constraints, both from paid-for lessons recorded in CLAUDE.md:
 *
 * 1. **Hardened DOM.** This runs on Android, whose XML parser rejects `DocumentBuilder`
 *    bean-property toggles the desktop JVM accepts. Totum shipped an RSS bug that passed
 *    every JVM test and only failed on a device for exactly this reason. Set no optional
 *    features; guard the ones you must.
 * 2. **Be loud about what was dropped.** A file that parses to *nearly* the right thing is
 *    more dangerous than one that fails outright, because it teaches wrong notes. Anything
 *    unsupported goes in [MusicXmlResult.Parsed.dropped] and is surfaced, not swallowed.
 */
public interface MusicXmlParser {
    /** Parses an uncompressed `.musicxml` / `.xml` document. */
    public fun parse(
        xml: String,
        sourceName: String,
        licence: String,
        part: PartChoice = PartChoice.First,
    ): MusicXmlResult

    /** Parses a compressed `.mxl` container, resolving its root file from META-INF/container.xml. */
    public fun parseCompressed(
        bytes: ByteArray,
        sourceName: String,
        licence: String,
        part: PartChoice = PartChoice.First,
    ): MusicXmlResult
}

/**
 * Which `<part>` of a multi-part score to read. This app reads one keyboard part at a time, so a
 * score written for more than one performer has to be told which performer it is.
 *
 * It exists because real repertoire is not solo piano. A song is a voice part on one staff plus a
 * piano part on two, and reading "the first part" gets you the vocal line — a single melody, when
 * the thing worth sight-reading is the accompaniment underneath it.
 */
public sealed interface PartChoice {
    /** The first part in the document. Right for a solo piece, wrong for anything with a singer. */
    public data object First : PartChoice

    public data class ById(val id: String) : PartChoice

    /**
     * The first part written on two or more staves.
     *
     * Two staves **is** the definition of keyboard writing — a brace, a right hand and a left hand —
     * so this needs no instrument-name matching and survives a score that calls the part "Pianoforte",
     * "Klavier" or nothing at all. Names in this corpus are inconsistent and often not in English;
     * the staff count is a fact about the notation.
     */
    public data object Keyboard : PartChoice
}

public sealed interface MusicXmlResult {
    public data class Parsed(val score: Score, val dropped: List<Dropped>) : MusicXmlResult {
        public val isClean: Boolean get() = dropped.isEmpty()
    }

    public data class Failed(val reason: String) : MusicXmlResult
}

/**
 * Something in the source this app does not read. [measure] is 1-based to match what a human
 * sees on the page, because the point of this record is that Dewi can go and look.
 */
public data class Dropped(
    val element: String,
    val measure: Int?,
    val detail: String,
) {
    override fun toString(): String =
        "dropped <$element>" + (measure?.let { " at bar $it" } ?: "") + ": $detail"
}

/**
 * The first four bytes of every zip, and so of every `.mxl`.
 *
 * A file picked from the system picker arrives as bytes with a display name that may be anything at
 * all — `score.xml` for a zipped one, no extension, a name in a script this app cannot case-fold.
 * The content says what it is and the name is a hint, so this dispatches on the content.
 */
private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

/** Whether these bytes are a compressed MusicXML container rather than a plain document. */
public fun isCompressedMusicXml(bytes: ByteArray): Boolean =
    bytes.size >= ZIP_MAGIC.size && ZIP_MAGIC.indices.all { bytes[it] == ZIP_MAGIC[it] }

/**
 * Parses MusicXML bytes of either kind, telling them apart by content rather than by file name.
 *
 * The one entry point for "here are some bytes, read them": the shipped corpus and a file Dewi
 * picked take the same road, so a piece he brings is judged, graded and windowed by exactly the
 * code that handles a piece that shipped.
 */
public fun MusicXmlParser.parseAny(
    bytes: ByteArray,
    sourceName: String,
    licence: String,
    part: PartChoice = PartChoice.First,
): MusicXmlResult = if (isCompressedMusicXml(bytes)) {
    parseCompressed(bytes, sourceName, licence, part)
} else {
    parse(bytes.toString(Charsets.UTF_8), sourceName, licence, part)
}
