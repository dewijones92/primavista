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
public object Corpus {
    private const val HAND_AUTHORED = "/corpus/manifest.tsv"
    private const val IMPORTED = "/corpus/lieder/manifest.tsv"
    private const val COMPRESSED_SUFFIX = ".mxl"
    private const val COMMENT = '#'
    private const val FIELDS = 7
    private const val ID_PREFIX = "id:"

    // Column order of the manifests, which the import tool writes and this reads.
    private const val ID_FIELD = 0
    private const val TITLE_FIELD = 1
    private const val COMPOSER_FIELD = 2
    private const val SOURCE_FIELD = 3
    private const val LICENCE_FIELD = 4
    private const val RESOURCE_FIELD = 5
    private const val PART_FIELD = 6

    public val pieces: List<CorpusPiece> by lazy { listOf(HAND_AUTHORED, IMPORTED).flatMap(::manifestAt) }

    public fun read(piece: CorpusPiece): ByteArray {
        val stream = requireNotNull(Corpus::class.java.getResourceAsStream(piece.resourcePath)) {
            "corpus resource ${piece.resourcePath} is missing from the build"
        }
        return stream.use { it.readBytes() }
    }

    /**
     * The parsed [Score] is keyed by [CorpusPiece.id], not its title (see `.claude/CODE-NOTES.md`),
     * and takes the manifest's title and composer over the file's. Real engravings are inconsistent
     * about both — a song's `<work-title>` is often the collection it sits in, or absent — whereas
     * the manifest names one song.
     */
    public fun parse(piece: CorpusPiece, parser: MusicXmlParser): MusicXmlResult {
        val bytes = read(piece)
        val result = if (piece.resourcePath.endsWith(COMPRESSED_SUFFIX)) {
            parser.parseCompressed(bytes, piece.id.value, piece.licence, piece.part)
        } else {
            parser.parse(bytes.toString(Charsets.UTF_8), piece.id.value, piece.licence, piece.part)
        }
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
        return stream.use { it.readBytes() }
            .toString(Charsets.UTF_8)
            .lineSequence()
            .filter { it.isNotBlank() && it.first() != COMMENT }
            .map { pieceFrom(it, path) }
            .toList()
    }

    private fun pieceFrom(line: String, manifest: String): CorpusPiece {
        val fields = line.split('\t')
        require(fields.size == FIELDS) { "$manifest: expected $FIELDS tab-separated fields, found ${fields.size}" }
        return CorpusPiece(
            id = ScoreId(fields[ID_FIELD]),
            title = fields[TITLE_FIELD],
            composer = fields[COMPOSER_FIELD],
            source = fields[SOURCE_FIELD],
            licence = fields[LICENCE_FIELD],
            resourcePath = fields[RESOURCE_FIELD],
            part = partFrom(fields[PART_FIELD], manifest),
        )
    }

    private fun partFrom(field: String, manifest: String): PartChoice = when {
        field == "first" -> PartChoice.First
        field == "keyboard" -> PartChoice.Keyboard
        field.startsWith(ID_PREFIX) -> PartChoice.ById(field.removePrefix(ID_PREFIX))
        else -> error("$manifest: '$field' is not a part choice")
    }
}
