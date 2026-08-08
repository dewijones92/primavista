package com.dewijones92.primavista.ui.repertoire

import com.dewijones92.primavista.score.CorpusPiece
import com.dewijones92.primavista.score.Dropped
import com.dewijones92.primavista.score.Polyphony
import com.dewijones92.primavista.score.SkillTag

/**
 * One shipped piece, as far as this build could read it.
 *
 * [dropped] is carried in full rather than as a count. A piece that parses to *nearly* the right
 * thing teaches wrong notes, so what was approximated has to be inspectable — a number alone tells
 * Dewi something is missing and nothing about whether it matters.
 */
internal data class RepertoireRow(
    val piece: CorpusPiece,
    val bars: Int,
    val notes: Int,
    val tempoBpm: Int,
    val polyphony: Polyphony,
    val firstPolyphonicBar: Int?,
    val skills: List<SkillTag>,
    val dropped: List<Dropped>,
    val failure: String?,
)
