package com.dewijones92.primavista.ui.repertoire

import com.dewijones92.primavista.practice.StageId
import com.dewijones92.primavista.score.CorpusPiece
import com.dewijones92.primavista.score.Dropped
import com.dewijones92.primavista.score.Polyphony
import com.dewijones92.primavista.score.Score
import com.dewijones92.primavista.score.SkillTag

/**
 * One shipped piece, as far as this build could read it.
 *
 * [material] is split from [dropped] rather than counted with it, because the two mean opposite
 * things. A slur that did not survive changes nothing you read — the app draws from the parsed
 * score, so the page and the expectation still agree. A dropped triplet leaves a silence where
 * three notes should be. Only the second is worth alarming anyone about, and lumping them together
 * put "487 unsupported markings dropped" on every real song in the corpus.
 *
 * [opensAs] is how many bars "Practise this" will actually give you. A 197-bar song is not a unit
 * of practice, and saying so on the card is more honest than opening it and letting Dewi find out.
 */
internal data class RepertoireRow(
    val piece: CorpusPiece,
    /** Null when it did not parse: a dead piece is still a row, it just cannot be practised. */
    val score: Score?,
    val bars: Int,
    val notes: Int,
    val tempoBpm: Int,
    val polyphony: Polyphony,
    val firstPolyphonicBar: Int?,
    val skills: List<SkillTag>,
    val dropped: List<Dropped>,
    val material: List<Dropped>,
    val rung: StageId?,
    val opensAs: Int?,
    val failure: String?,
) {
    /** Cosmetic losses: worth stating, not worth a warning. */
    val decoration: Int get() = dropped.size - material.size

    val isWholePiece: Boolean get() = opensAs == null || opensAs == bars
}
