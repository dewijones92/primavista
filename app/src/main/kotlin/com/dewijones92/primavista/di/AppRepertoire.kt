package com.dewijones92.primavista.di

import com.dewijones92.primavista.common.Diag
import com.dewijones92.primavista.practice.Curriculum
import com.dewijones92.primavista.practice.Repertoire
import com.dewijones92.primavista.practice.Stage
import com.dewijones92.primavista.practice.StageId
import com.dewijones92.primavista.score.Corpus
import com.dewijones92.primavista.score.CorpusPiece
import com.dewijones92.primavista.score.Dropped
import com.dewijones92.primavista.score.MusicXmlParser
import com.dewijones92.primavista.score.MusicXmlResult
import com.dewijones92.primavista.score.Score
import com.dewijones92.primavista.score.ScoreLibrary
import com.dewijones92.primavista.score.material
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val TAG = "repertoire"

/**
 * One piece, how it read, and what it offers to read.
 *
 * [passages] is on the parse rather than looked up later so that a piece is *finished* the moment
 * its own coroutine ends — which is what lets the Repertoire tab show a card as soon as it exists
 * instead of after the whole corpus has been read.
 */
public data class PieceParse(
    val piece: CorpusPiece,
    val library: String = Corpus.label,
    val result: MusicXmlResult,
    val passages: List<Score> = emptyList(),
) {
    public val score: Score? get() = (result as? MusicXmlResult.Parsed)?.score

    public val dropped: List<Dropped> get() = (result as? MusicXmlResult.Parsed)?.dropped.orEmpty()

    /** What was lost that a reader would notice, as opposed to what was only decoration. */
    public val material: List<Dropped> get() = (result as? MusicXmlResult.Parsed)?.material.orEmpty()

    public val failure: String? get() = (result as? MusicXmlResult.Failed)?.reason
}

/**
 * What there is to read: every library's pieces parsed, and windowed into the passages the path can
 * place.
 *
 * **One owner, because it is expensive.** Until this existed the Repertoire tab and the scheduler
 * each parsed and windowed the corpus independently — the same work twice. Measured on the api35
 * emulator, reading the twenty-eight shipped pieces held the process in back-to-back GC for 24
 * seconds sequentially and 12 in parallel; the DOM, not the windowing, is where that goes.
 *
 * So the work is done once, in parallel, and **published as it lands** through [parsed], so a
 * screen can show what has arrived rather than waiting on the last file. See
 * `.claude/CODE-NOTES.md` for the measurements and what is still owed.
 */
public class AppRepertoire(
    private val parser: MusicXmlParser,
    private val diag: Diag,
    curriculum: Curriculum,
    private val libraries: List<ScoreLibrary> = listOf(Corpus),
) {
    private val repertoire = Repertoire(curriculum)
    private val lock = Mutex()
    private var complete: List<PieceParse>? = null
    private val arriving = MutableStateFlow<List<PieceParse>>(emptyList())

    /** Pieces as they finish reading, in completion order. Empty until [load] is called. */
    public val parsed: StateFlow<List<PieceParse>> = arriving.asStateFlow()

    /** How many there are to read, so a screen can size its waiting state honestly. */
    public val expected: Int get() = libraries.sumOf { it.pieces().size }

    /** Re-reads every library, so a score kept since the last read is offered. */
    public suspend fun reload(): List<PieceParse> = lock.withLock {
        complete = null
        withContext(Dispatchers.Default) { read() }.also { complete = it }
    }

    /** Every piece and how it read, failures included — a dead piece is still a row. */
    public suspend fun load(): List<PieceParse> = lock.withLock {
        complete ?: withContext(Dispatchers.Default) { read() }.also { complete = it }
    }

    /** What the scheduler may choose from: readable passages, not whole songs. See [Repertoire]. */
    public suspend fun passages(): List<Score> = load().flatMap { it.passages }

    /** The pieces that read at all, as written. */
    public suspend fun pieces(): List<Score> = load().mapNotNull { it.score }

    public fun rungFor(score: Score): StageId? = repertoire.rungFor(score)

    /** The most of [score] that [stage] admits, or the easiest thing it has. */
    public fun passageFor(score: Score, stage: Stage): Score? = repertoire.passageFor(score, stage)

    private suspend fun read(): List<PieceParse> = coroutineScope {
        // Emptied first because a read can be **cancelled**: the Repertoire tab starts it from a
        // LaunchedEffect, so leaving the tab kills it mid-way and leaves whatever had already
        // landed. Appending to that on the next attempt showed 66 pieces where 44 exist — and the
        // rows are keyed by piece id in a LazyColumn, where a duplicate key is a crash rather than
        // a repeat, with `expected - arrived` going negative right behind it.
        arriving.value = emptyList()
        val parses = libraries
            .flatMap { library -> library.pieces().map { library to it } }
            .map { (library, piece) -> async { readOne(library, piece).also(::landed) } }
            .awaitAll()
        diag.event(
            TAG,
            "read ${parses.size} pieces, ${parses.count { it.failure != null }} failed; " +
                "offers ${parses.sumOf { it.passages.size }} passages [windows=${Repertoire.DEFAULT_WINDOWS} " +
                "byRung=${parses.flatMap { it.passages }.groupingBy { rungFor(it)?.number }.eachCount()}]",
        )
        parses
    }

    private fun readOne(library: ScoreLibrary, piece: CorpusPiece): PieceParse {
        val result = runCatching {
            library.bytesOf(piece)
                ?.let { Corpus.parse(piece, parser, it) }
                ?: MusicXmlResult.Failed("its file is missing from the ${library.label} library")
        }.getOrElse { MusicXmlResult.Failed(it.message ?: it.toString()) }
        val parse = PieceParse(piece, library.label, result)
        report(parse)
        return parse.copy(passages = parse.score?.let { repertoire.offers(it) }.orEmpty())
    }

    private fun landed(parse: PieceParse) {
        arriving.update { it + parse }
    }

    private fun report(parse: PieceParse) {
        val title = parse.piece.title
        parse.failure?.let { diag.event(TAG, "'$title' failed to parse: $it") }
        if (parse.material.isNotEmpty()) {
            diag.event(TAG, "'$title' lost music: " + parse.material.joinToString("; ") { it.toString() })
        }
    }
}
