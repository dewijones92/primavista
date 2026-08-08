package com.dewijones92.primavista.ui.repertoire

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import com.dewijones92.primavista.di.AppContainer
import com.dewijones92.primavista.score.Corpus
import com.dewijones92.primavista.score.CorpusPiece
import com.dewijones92.primavista.score.MusicXmlResult
import com.dewijones92.primavista.score.Polyphony
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What there is to read, with the facts that decide whether you can read it here: how polyphonic
 * it is, what it will make you read, and whether it parsed cleanly.
 *
 * Tapping a piece opens it; "Practise this" hands it to the Practise tab through [PracticeRequest].
 */
@Composable
public fun RepertoireRoute(container: AppContainer, modifier: Modifier = Modifier) {
    val rows by produceState<List<RepertoireRow>?>(null, container) {
        value = withContext(Dispatchers.Default) { Corpus.pieces.map { rowFor(container, it) } }
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

private fun rowFor(container: AppContainer, piece: CorpusPiece): RepertoireRow =
    when (val parsed = Corpus.parse(piece, container.musicXmlParser)) {
        is MusicXmlResult.Parsed -> RepertoireRow(
            piece = piece,
            bars = parsed.score.measures.size,
            notes = parsed.score.attackedNotes.size,
            tempoBpm = parsed.score.defaultTempoBpm,
            polyphony = parsed.score.polyphony,
            firstPolyphonicBar = parsed.score.firstPolyphonicMeasure(),
            skills = container.scoreSkills.skillsOf(parsed.score).toList(),
            dropped = parsed.dropped,
            failure = null,
        )
        is MusicXmlResult.Failed -> RepertoireRow(
            piece = piece,
            bars = 0,
            notes = 0,
            tempoBpm = 0,
            polyphony = Polyphony.Mono,
            firstPolyphonicBar = null,
            skills = emptyList(),
            dropped = emptyList(),
            failure = parsed.reason,
        )
    }
