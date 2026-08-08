package com.dewijones92.primavista.ui.repertoire

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import com.dewijones92.primavista.di.AppContainer
import com.dewijones92.primavista.score.Polyphony

/**
 * What there is to read, with the facts that decide whether you can read it here: how polyphonic
 * it is, what it will make you read, and whether it parsed cleanly.
 *
 * Tapping a piece opens it; "Practise this" hands it to the Practise tab through [PracticeRequest].
 */
@Composable
public fun RepertoireRoute(container: AppContainer, modifier: Modifier = Modifier) {
    val rows by produceState<List<RepertoireRow>?>(null, container) {
        value = ParsedCorpus.of(container.musicXmlParser, container.diag).map { rowFor(container, it) }
    }
    RepertoireScreen(
        rows = rows,
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

private fun rowFor(container: AppContainer, parse: PieceParse): RepertoireRow {
    val score = parse.score
        ?: return RepertoireRow(
            piece = parse.piece,
            bars = 0,
            notes = 0,
            tempoBpm = 0,
            polyphony = Polyphony.Mono,
            firstPolyphonicBar = null,
            skills = emptyList(),
            dropped = emptyList(),
            failure = parse.failure ?: "the piece could not be read",
        )
    return RepertoireRow(
        piece = parse.piece,
        bars = score.measures.size,
        notes = score.attackedNotes.size,
        tempoBpm = score.defaultTempoBpm,
        polyphony = score.polyphony,
        firstPolyphonicBar = score.firstPolyphonicMeasure(),
        skills = container.scoreSkills.skillsOf(score).toList(),
        dropped = parse.dropped,
        failure = null,
    )
}
