package com.dewijones92.primavista.score

/**
 * A piece shipped with the app. [source] and [licence] are recorded per piece rather than as a
 * blanket claim, because "public domain" is a fact about a particular work and its engraving.
 */
/** [locator] is read by the [ScoreLibrary] that owns the piece, and means nothing outside it. */
public data class CorpusPiece(
    val id: ScoreId,
    val title: String,
    val composer: String,
    val source: String,
    val licence: String,
    val locator: String,
    val part: PartChoice,
)

/**
 * The repertoire shipped with the app: a few hand-authored openings everyone knows, plus real
 * nineteenth-century songs imported from the OpenScore Lieder Corpus by `tools/repertoire`.
 *
 * Read from manifests rather than written out in Kotlin, so an import is a data change: the tool
 * rewrites its own manifest and touches no code. Difficulty is deliberately **not** in the file —
 * the app derives it from the score with the same grader the import used, and a stored second copy
 * of a derivation is the duplication this repo has already been bitten by twice (CLAUDE.md).
 */
public object Corpus : ScoreLibrary {
    private const val HAND_AUTHORED = "/corpus/manifest.tsv"
    private const val IMPORTED = "/corpus/lieder/manifest.tsv"

    override val label: String = "shipped"

    public val pieces: List<CorpusPiece> by lazy { listOf(HAND_AUTHORED, IMPORTED).flatMap(::manifestAt) }

    override fun pieces(): List<CorpusPiece> = pieces

    override fun bytesOf(piece: CorpusPiece): ByteArray = read(piece)

    public fun read(piece: CorpusPiece): ByteArray {
        val stream = requireNotNull(Corpus::class.java.getResourceAsStream(piece.locator)) {
            "corpus resource ${piece.locator} is missing from the build"
        }
        return stream.use { it.readBytes() }
    }

    /**
     * The parsed [Score] is keyed by [CorpusPiece.id], not its title (see `.claude/CODE-NOTES.md`),
     * and takes the manifest's title and composer over the file's. Real engravings are inconsistent
     * about both — a song's `<work-title>` is often the collection it sits in, or absent — whereas
     * the manifest names one song.
     */
    public fun parse(piece: CorpusPiece, parser: MusicXmlParser): MusicXmlResult =
        parse(piece, parser, read(piece))

    /** Parsing given the bytes, so a library that is not the classpath reaches the same score. */
    public fun parse(piece: CorpusPiece, parser: MusicXmlParser, bytes: ByteArray): MusicXmlResult {
        val result = parser.parseAny(bytes, piece.id.value, piece.licence, piece.part)
        return when (result) {
            is MusicXmlResult.Failed -> result
            is MusicXmlResult.Parsed ->
                result.copy(score = result.score.copy(title = piece.title, composer = piece.composer))
        }
    }

    private fun manifestAt(path: String): List<CorpusPiece> {
        val stream = requireNotNull(Corpus::class.java.getResourceAsStream(path)) {
            "corpus manifest $path is missing from the build"
        }
        return ScoreManifest.read(stream.use { it.readBytes() }.toString(Charsets.UTF_8), path)
    }
}
