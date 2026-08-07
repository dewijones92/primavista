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
    public fun parse(xml: String, sourceName: String, licence: String): MusicXmlResult

    /** Parses a compressed `.mxl` container, resolving its root file from META-INF/container.xml. */
    public fun parseCompressed(bytes: ByteArray, sourceName: String, licence: String): MusicXmlResult
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
