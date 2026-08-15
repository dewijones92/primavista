package com.dewijones92.primavista.tools.repertoire

import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.readBytes

/**
 * One candidate file, with the provenance that has to travel with it.
 *
 * [source] is a sentence about *this* work and *this* engraving rather than a blanket claim over a
 * corpus, because that is what "public domain" actually is (see `Corpus`).
 */
public data class SourcePiece(
    val path: Path,
    val id: String,
    val title: String,
    val composer: String,
    val source: String,
    val licence: String,
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean = other is SourcePiece && other.path == path
    override fun hashCode(): Int = path.hashCode()
    override fun toString(): String = "$id ($composer — $title)"
}

/**
 * Reads the OpenScore Lieder layout: `scores/<Composer>/<Collection>/<Song>/lc<id>.mxl`, where the
 * three directory names carry the metadata and the file name carries the corpus's own identifier.
 *
 * The directories are the metadata because the `.mxl` payloads are inconsistent about it — many
 * have no `<work-title>` at all, and the ones that do often hold a movement number rather than a
 * name. Names are underscore-escaped and `_` alone means "no collection".
 */
public class LiederCorpus(private val root: Path, private val commit: String) {

    public fun read(file: Path): SourcePiece {
        val song = file.parent
        val collection = song.parent
        val composer = collection.parent
        val composerName = humanise(composer.name)
        val collectionName = humanise(collection.name).takeUnless { it == NO_COLLECTION }
        val songName = humanise(song.name)
        val title = collectionName?.let { "$it: $songName" } ?: songName
        return SourcePiece(
            path = file,
            id = "lieder-${file.name.removeSuffix(SUFFIX)}",
            title = title,
            composer = composerName,
            source = "OpenScore Lieder Corpus, ${root.relativize(file)} at $commit",
            licence = LICENCE,
            bytes = file.readBytes(),
        )
    }

    /** `Holmès,_Augusta_Mary_Anne` is a directory name; `Augusta Mary Anne Holmès` is a composer. */
    private fun humanise(directory: String): String {
        val spaced = directory.replace('_', ' ').trim()
        val comma = spaced.indexOf(", ")
        return if (comma < 0) spaced else spaced.substring(comma + 2) + " " + spaced.substring(0, comma)
    }

    public companion object {
        public const val SUFFIX: String = ".mxl"
        private const val NO_COLLECTION = ""
        public const val LICENCE: String =
            "Engraving from the OpenScore Lieder Corpus, released under Creative Commons Zero (CC0 1.0). " +
                "Gotham & Jonas, 'The OpenScore Lieder Corpus' (2022)."
    }
}
