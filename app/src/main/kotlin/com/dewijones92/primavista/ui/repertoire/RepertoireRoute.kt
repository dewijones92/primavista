package com.dewijones92.primavista.ui.repertoire

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.dewijones92.primavista.di.AppContainer
import com.dewijones92.primavista.di.PieceParse
import com.dewijones92.primavista.di.ShippedRepertoire
import com.dewijones92.primavista.score.Polyphony

private const val UNREACHABLE = Int.MAX_VALUE

/**
 * What there is to read, with the facts that decide whether you can read it here: which rung it
 * becomes readable at, how much of it opens, how polyphonic it is, and whether anything was lost.
 *
 * Rows appear **as each piece finishes reading** rather than when the whole corpus has. Parsing a
 * real song costs real time (see [ShippedRepertoire]), and a screen that shows twenty-eight bones
 * for ten seconds and then everything at once reads as broken, where one that fills reads as busy.
 *
 * Ordered easiest first, so what Dewi can read today is at the top rather than buried under a
 * Schubert song he cannot.
 *
 * Tapping a piece opens it; "Practise this" hands it to the Practise tab through [PracticeRequest].
 */
@Composable
public fun RepertoireRoute(container: AppContainer, modifier: Modifier = Modifier) {
    val shipped = container.shippedRepertoire
    val arrived by shipped.parsed.collectAsState()
    LaunchedEffect(shipped) { shipped.load() }
    RepertoireScreen(
        rows = arrived.map { rowFor(container, shipped, it) }
            .sortedWith(compareBy({ it.rung?.number ?: UNREACHABLE }, { it.piece.title })),
        stillReading = shipped.expected - arrived.size,
        onPractise = { piece ->
            container.diag.event(
                "repertoire",
                "practise requested id=${piece.id.value} title='${piece.title}'",
            )
            PracticeRequest.request(piece)
        },
        modifier = modifier,
    )
}

private fun rowFor(container: AppContainer, shipped: ShippedRepertoire, parse: PieceParse): RepertoireRow {
    val score = parse.score ?: return unreadable(parse)
    val easiest = parse.passages.firstOrNull()
    return RepertoireRow(
        piece = parse.piece,
        bars = score.measures.size,
        notes = score.attackedNotes.size,
        tempoBpm = score.defaultTempoBpm,
        polyphony = score.polyphony,
        firstPolyphonicBar = score.firstPolyphonicMeasure(),
        skills = container.scoreSkills.skillsOf(score).toList(),
        dropped = parse.dropped,
        material = parse.material,
        rung = easiest?.let { shipped.rungFor(it) },
        opensAs = easiest?.measures?.size,
        failure = null,
    )
}

private fun unreadable(parse: PieceParse) = RepertoireRow(
    piece = parse.piece,
    bars = 0,
    notes = 0,
    tempoBpm = 0,
    polyphony = Polyphony.Mono,
    firstPolyphonicBar = null,
    skills = emptyList(),
    dropped = emptyList(),
    material = emptyList(),
    rung = null,
    opensAs = null,
    failure = parse.failure ?: "the piece could not be read",
)
