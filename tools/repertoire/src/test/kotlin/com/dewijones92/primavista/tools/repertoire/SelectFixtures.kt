package com.dewijones92.primavista.tools.repertoire

import com.dewijones92.primavista.practice.StageId
import com.dewijones92.primavista.score.Alter
import com.dewijones92.primavista.score.Clef
import com.dewijones92.primavista.score.DifficultySpec
import com.dewijones92.primavista.score.KeySignature
import com.dewijones92.primavista.score.Midi
import com.dewijones92.primavista.score.NoteSymbol
import com.dewijones92.primavista.score.Score
import com.dewijones92.primavista.score.SeededExerciseGenerator
import com.dewijones92.primavista.score.Staff
import com.dewijones92.primavista.score.TimeSignature
import kotlin.io.path.Path

/** One shape of accepted piece, shared so selection and writing are tested against the same thing. */
internal object SelectFixtures {
    fun accepted(composer: String, id: String, stage: Int?, passages: Int): Screening.Accepted {
        val score = anyScore()
        val placed = List(passages) { Passage(score, emptySet(), stage?.let { StageId(it) }) }
        return Screening.Accepted(
            source = SourcePiece(
                path = Path(id),
                id = id,
                title = "Piece $id",
                composer = composer,
                source = "test",
                licence = "CC0",
                bytes = ByteArray(0),
            ),
            score = score,
            skills = emptySet(),
            stage = null,
            passages = placed,
            cosmetic = emptyList(),
        )
    }

    private fun anyScore(): Score = SeededExerciseGenerator().generate(
        seed = 1L,
        spec = DifficultySpec(
            staves = listOf(Staff.Upper),
            clefs = mapOf(Staff.Upper to Clef.Treble),
            keys = setOf(KeySignature.C),
            time = TimeSignature.FourFour,
            bars = 4,
            range = mapOf(Staff.Upper to Midi(60)..Midi(72)),
            symbols = setOf(NoteSymbol.Quarter),
            maxDots = 0,
            allowTuplets = false,
            allowedAlterations = setOf(Alter.Natural),
            maxLeapSemitones = 4,
            tempoBpm = 60,
            bothHandsActive = false,
        ),
    )
}
