package com.dewijones92.primavista.score

/**
 * A piece shipped with the app. [source] and [licence] are recorded per piece rather than as a
 * blanket claim, because "public domain" is a fact about a particular work and its engraving.
 */
public data class CorpusPiece(
    val id: ScoreId,
    val title: String,
    val composer: String,
    val source: String,
    val licence: String,
    val resourcePath: String,
)

/**
 * The hand-authored starter repertoire. Small on purpose — the generator covers the volume, and
 * every piece here is one Dewi can actually recognise, which is what makes progress legible.
 */
public object Corpus {
    private const val ENGRAVING_LICENCE =
        "Engraving hand-authored for this repository and dedicated to the public domain (CC0)."

    public val pieces: List<CorpusPiece> = listOf(
        CorpusPiece(
            id = ScoreId("corpus-minuet-in-g"),
            title = "Minuet in G (BWV Anh. 114) — opening",
            composer = "Christian Petzold",
            source = "Notebook for Anna Magdalena Bach (1725); composer died 1733, so the work is public domain.",
            licence = ENGRAVING_LICENCE,
            resourcePath = "/corpus/minuet-in-g.musicxml",
        ),
        CorpusPiece(
            id = ScoreId("corpus-ode-to-joy"),
            title = "Ode to Joy (Symphony No. 9) — theme",
            composer = "Ludwig van Beethoven",
            source = "Symphony No. 9, finale (1824); composer died 1827, so the work is public domain.",
            licence = ENGRAVING_LICENCE,
            resourcePath = "/corpus/ode-to-joy.musicxml",
        ),
        CorpusPiece(
            id = ScoreId("corpus-ah-vous-dirai-je-maman"),
            title = "Ah, vous dirai-je, Maman — theme",
            composer = "Traditional (theme of Mozart's K. 265)",
            source = "French folk melody first printed in 1761; anonymous and long out of copyright.",
            licence = ENGRAVING_LICENCE,
            resourcePath = "/corpus/ah-vous-dirai-je-maman.musicxml",
        ),
    )

    public fun read(piece: CorpusPiece): String {
        val stream = requireNotNull(Corpus::class.java.getResourceAsStream(piece.resourcePath)) {
            "corpus resource ${piece.resourcePath} is missing from the build"
        }
        return stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    /** The parsed [Score] is keyed by [CorpusPiece.id], not its title. See `.claude/CODE-NOTES.md`. */
    public fun parse(piece: CorpusPiece, parser: MusicXmlParser): MusicXmlResult =
        parser.parse(read(piece), piece.id.value, piece.licence)
}
