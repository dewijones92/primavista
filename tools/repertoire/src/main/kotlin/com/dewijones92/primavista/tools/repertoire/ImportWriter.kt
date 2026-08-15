package com.dewijones92.primavista.tools.repertoire

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

private const val SCORES_DIR = "lieder"
private const val MANIFEST = "lieder/manifest.tsv"

/**
 * Writes the chosen pieces as resources plus one manifest row each.
 *
 * The `.mxl` is copied **byte for byte** rather than re-encoded: it is the CC0 artefact as
 * published, so provenance survives, and the file the app parses is the file that was screened.
 * Nothing about the difficulty is written down — the app derives that from the score with the same
 * grader used here, and a second copy of a derivation is the kind of duplication this repo has
 * already been bitten by twice (CLAUDE.md).
 */
public class ImportWriter(private val out: Path) {

    public fun write(pieces: List<Screening.Accepted>): List<Screening.Accepted> {
        out.resolve(SCORES_DIR).createDirectories()
        pieces.forEach { piece ->
            out.resolve("$SCORES_DIR/${piece.source.id}${LiederCorpus.SUFFIX}").writeBytes(piece.source.bytes)
        }
        val rows = pieces.map { row(it) }
        out.resolve(MANIFEST).writeText((listOf(HEADER) + rows).joinToString("\n", postfix = "\n"))
        return pieces
    }

    private fun row(piece: Screening.Accepted): String = listOf(
        piece.source.id,
        piece.source.title,
        piece.source.composer,
        piece.source.source,
        piece.source.licence,
        "/corpus/$SCORES_DIR/${piece.source.id}${LiederCorpus.SUFFIX}",
        "keyboard",
    ).joinToString("\t") { field ->
        require(!field.contains('\t') && !field.contains('\n')) { "manifest field would break the row: $field" }
        field
    }

    private companion object {
        const val HEADER = "#id\ttitle\tcomposer\tsource\tlicence\tresource\tpart"
    }
}
