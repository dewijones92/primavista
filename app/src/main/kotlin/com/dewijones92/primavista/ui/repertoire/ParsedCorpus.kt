package com.dewijones92.primavista.ui.repertoire

import com.dewijones92.primavista.common.Diag
import com.dewijones92.primavista.score.Corpus
import com.dewijones92.primavista.score.CorpusPiece
import com.dewijones92.primavista.score.Dropped
import com.dewijones92.primavista.score.MusicXmlParser
import com.dewijones92.primavista.score.MusicXmlResult
import com.dewijones92.primavista.score.Score
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val TAG = "corpus"

/** One shipped piece and whatever this build could make of it. */
internal data class PieceParse(val piece: CorpusPiece, val result: MusicXmlResult) {
    val score: Score? get() = (result as? MusicXmlResult.Parsed)?.score

    val dropped: List<Dropped> get() = (result as? MusicXmlResult.Parsed)?.dropped.orEmpty()

    val failure: String? get() = (result as? MusicXmlResult.Failed)?.reason
}

/**
 * The corpus, parsed once for the whole app rather than once per screen that wants it.
 * See `.claude/CODE-NOTES.md`.
 */
internal object ParsedCorpus {

    private val lock = Mutex()
    private var parsedBy: MusicXmlParser? = null
    private var parsed: List<PieceParse>? = null

    suspend fun of(parser: MusicXmlParser, diag: Diag): List<PieceParse> = lock.withLock {
        val cached = parsed?.takeIf { parsedBy === parser }
        if (cached != null) {
            diag.counted(TAG, "servedFromCache")
            return@withLock cached
        }
        withContext(Dispatchers.Default) { parseAll(parser, diag) }
            .also {
                parsed = it
                parsedBy = parser
            }
    }

    private fun parseAll(parser: MusicXmlParser, diag: Diag): List<PieceParse> {
        val all = Corpus.pieces.map { piece -> PieceParse(piece, read(piece, parser)) }
        all.forEach { report(it, diag) }
        diag.event(
            TAG,
            "parsed pieces=${all.size} readable=${all.count { it.score != null }} " +
                "failed=${all.count { it.failure != null }} " +
                "withDropped=${all.count { it.dropped.isNotEmpty() }}",
        )
        return all
    }

    /** A missing resource is a failed piece, not a dead tab. */
    private fun read(piece: CorpusPiece, parser: MusicXmlParser): MusicXmlResult =
        runCatching { Corpus.parse(piece, parser) }
            .getOrElse { MusicXmlResult.Failed(it.message ?: it.toString()) }

    private fun report(parse: PieceParse, diag: Diag) {
        val title = parse.piece.title
        parse.failure?.let { diag.event(TAG, "'$title' failed to parse: $it") }
        if (parse.dropped.isNotEmpty()) {
            diag.event(
                TAG,
                "'$title' parsed with ${parse.dropped.size} dropped: " +
                    parse.dropped.joinToString("; ") { it.toString() },
            )
        }
    }
}
