package com.dewijones92.primavista.di

import com.dewijones92.primavista.common.Diag
import com.dewijones92.primavista.score.CorpusPiece
import com.dewijones92.primavista.score.ScoreId
import com.dewijones92.primavista.score.ScoreLibrary
import com.dewijones92.primavista.score.ScoreManifest
import java.io.File
import java.security.MessageDigest

private const val TAG = "repertoire.kept"
private const val MANIFEST = "manifest.tsv"
private const val SUFFIX = ".musicxml"
private const val COMMENT = '#'
private const val HEX = 255

/**
 * Scores Dewi opened from his phone, kept so they are still there tomorrow.
 *
 * A second [ScoreLibrary] rather than a second kind of music. See .claude/CODE-NOTES.md.
 */
public class KeptScores(private val directory: File, private val diag: Diag) : ScoreLibrary {

    override val label: String = LABEL

    /**
     * One bad row loses one piece, not the shelf. A manifest that fails to parse whole would take
     * every kept score down with it, which is a worse answer than "this one line is unreadable".
     */
    override fun pieces(): List<CorpusPiece> {
        val manifest = File(directory, MANIFEST).takeIf { it.isFile } ?: return emptyList()
        val text = runCatching { manifest.readText() }.getOrElse {
            diag.event(TAG, "the kept manifest could not be read, so no kept score is offered: ${it.message}")
            return emptyList()
        }
        return text.lineSequence()
            .filter { it.isNotBlank() && it.first() != COMMENT }
            .mapNotNull { line -> rowOrNull(line) }
            .toList()
    }

    override fun bytesOf(piece: CorpusPiece): ByteArray? {
        val file = File(directory, piece.locator)
        if (!file.isFile) {
            diag.event(TAG, "'${piece.title}' is in the manifest but its file ${piece.locator} is gone")
            return null
        }
        return runCatching { file.readBytes() }.getOrElse {
            diag.event(TAG, "'${piece.title}' could not be read from ${piece.locator}: ${it.message}")
            null
        }
    }

    /**
     * The score file is written before the manifest row deliberately: the other order leaves a row
     * pointing at nothing if the second write fails, and an orphan file is the harmless failure.
     */
    public fun keep(piece: CorpusPiece, bytes: ByteArray): CorpusPiece {
        directory.mkdirs()
        val stored = piece.copy(locator = locatorFor(piece.id))
        File(directory, stored.locator).writeBytes(bytes)
        rewrite(pieces().filterNot { it.id == stored.id } + stored)
        diag.event(TAG, "kept '${stored.title}' id=${stored.id.value} as ${stored.locator} (${bytes.size} bytes)")
        return stored
    }

    public fun forget(id: ScoreId) {
        val going = pieces().firstOrNull { it.id == id }
        if (going == null) {
            diag.event(TAG, "nothing to forget for id=${id.value}")
            return
        }
        rewrite(pieces().filterNot { it.id == id })
        val removed = File(directory, going.locator).delete()
        diag.event(TAG, "forgot '${going.title}' id=${id.value} fileDeleted=$removed")
    }

    private fun rowOrNull(line: String): CorpusPiece? =
        runCatching { ScoreManifest.read(line, "the kept manifest").single() }.getOrElse {
            diag.event(TAG, "a kept row is unreadable and is skipped: ${it.message}")
            null
        }

    private fun rewrite(pieces: List<CorpusPiece>) {
        directory.mkdirs()
        File(directory, MANIFEST).writeText(ScoreManifest.write(pieces))
    }

    /**
     * Hashed rather than derived from the title: a picked file's name can be anything at all,
     * including characters no filesystem takes, and two files can share one.
     */
    private fun locatorFor(id: ScoreId): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(id.value.toByteArray())
        return digest.joinToString("") { "%02x".format(it.toInt() and HEX) } + SUFFIX
    }

    public companion object {
        /** One spelling, so a kept row cannot be told from a shipped one by a typo. */
        public const val LABEL: String = "kept"
    }
}
