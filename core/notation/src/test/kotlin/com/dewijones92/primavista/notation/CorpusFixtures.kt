package com.dewijones92.primavista.notation

import com.dewijones92.primavista.score.Corpus
import com.dewijones92.primavista.score.DomMusicXmlParser
import com.dewijones92.primavista.score.Measure
import com.dewijones92.primavista.score.MusicXmlResult
import com.dewijones92.primavista.score.Note
import com.dewijones92.primavista.score.Score

/** A shipped piece, parsed the way the app parses it: real material, not a fixture. */
internal fun corpusScore(id: String): Score {
    val piece = Corpus.pieces.first { it.id.value == id }
    return when (val result = Corpus.parse(piece, DomMusicXmlParser())) {
        is MusicXmlResult.Parsed -> result.score
        is MusicXmlResult.Failed -> error("corpus piece $id did not parse: ${result.reason}")
    }
}

internal fun Score.withMeasure(index: Int, change: (Measure) -> Measure): Score =
    copy(measures = measures.mapIndexed { at, measure -> if (at == index) change(measure) else measure })

internal fun Score.plusNotes(added: List<Note>): Score = copy(events = events + added)
