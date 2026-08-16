package com.dewijones92.primavista.score

/**
 * Where a library keeps its music. See .claude/CODE-NOTES.md.
 */
public interface ScoreLibrary {
    /** Short, stable, and in every log line this library produces. */
    public val label: String

    public fun pieces(): List<CorpusPiece>

    /** The file's bytes, or null with a reason logged — a piece whose file is gone is not a crash. */
    public fun bytesOf(piece: CorpusPiece): ByteArray?
}

/**
 * The tab-separated line that describes one piece, read and written in one place.
 *
 * The format was already the app's way of saying "a piece": the import tool writes it, `Corpus`
 * reads it. Both knew the column order separately, which is a duplicated decision of exactly the
 * kind CLAUDE.md records having shipped twice — so the order lives here now and both sides ask.
 */
public object ScoreManifest {

    public const val HEADER: String = "#id\ttitle\tcomposer\tsource\tlicence\tlocator\tpart"

    public fun read(text: String, from: String): List<CorpusPiece> = text.lineSequence()
        .filter { it.isNotBlank() && it.first() != COMMENT }
        .map { pieceFrom(it, from) }
        .toList()

    /** The whole file, header included, so a writer cannot produce one this reader will not take. */
    public fun write(pieces: List<CorpusPiece>): String =
        (listOf(HEADER) + pieces.map(::rowOf)).joinToString("\n", postfix = "\n")

    public fun rowOf(piece: CorpusPiece): String = listOf(
        piece.id.value,
        piece.title,
        piece.composer,
        piece.source,
        piece.licence,
        piece.locator,
        nameOf(piece.part),
    ).joinToString(SEPARATOR.toString()) { field ->
        require(!field.contains(SEPARATOR) && !field.contains('\n')) {
            "a manifest field would break the row: '$field'"
        }
        field
    }

    private fun pieceFrom(line: String, from: String): CorpusPiece {
        val fields = line.split(SEPARATOR)
        require(fields.size == FIELDS) { "$from: expected $FIELDS tab-separated fields, found ${fields.size}" }
        return CorpusPiece(
            id = ScoreId(fields[ID_FIELD]),
            title = fields[TITLE_FIELD],
            composer = fields[COMPOSER_FIELD],
            source = fields[SOURCE_FIELD],
            licence = fields[LICENCE_FIELD],
            locator = fields[LOCATOR_FIELD],
            part = partFrom(fields[PART_FIELD], from),
        )
    }

    private fun partFrom(field: String, from: String): PartChoice = when {
        field == FIRST -> PartChoice.First
        field == KEYBOARD -> PartChoice.Keyboard
        field.startsWith(ID_PREFIX) -> PartChoice.ById(field.removePrefix(ID_PREFIX))
        else -> error("$from: '$field' is not a part choice")
    }

    private fun nameOf(part: PartChoice): String = when (part) {
        PartChoice.First -> FIRST
        PartChoice.Keyboard -> KEYBOARD
        is PartChoice.ById -> "$ID_PREFIX${part.id}"
    }

    private const val COMMENT = '#'
    private const val SEPARATOR = '\t'
    private const val FIELDS = 7
    private const val ID_PREFIX = "id:"
    private const val FIRST = "first"
    private const val KEYBOARD = "keyboard"

    private const val ID_FIELD = 0
    private const val TITLE_FIELD = 1
    private const val COMPOSER_FIELD = 2
    private const val SOURCE_FIELD = 3
    private const val LICENCE_FIELD = 4
    private const val LOCATOR_FIELD = 5
    private const val PART_FIELD = 6
}
