package com.dewijones92.primavista.practice

import com.dewijones92.primavista.score.Score
import com.dewijones92.primavista.score.admits
import com.dewijones92.primavista.score.covers
import com.dewijones92.primavista.score.passages

/**
 * Turns the shipped pieces into things that can actually be read today.
 *
 * A real song is not a unit of practice. Graded whole, every one of the imported Lieder lands past
 * the last rung, because a song contains every reading skill somewhere in it — one rung with a lot
 * of pages rather than a ladder. Windowed, the same songs place across the path, and the window
 * length is itself part of the difficulty: of the corpus screened at import, 735 passages placed at
 * four bars, 140 at eight and 17 at sixteen.
 *
 * So this is the one place that decides what a piece *offers*, and every caller — the scheduler's
 * candidate list and the Repertoire tab's "practise this" — asks it rather than windowing for
 * itself. A short piece is offered whole, keeping its name; a long one is offered as the passages
 * of it that some rung admits.
 */
public class Repertoire(
    private val curriculum: Curriculum = Curriculum.Standard,
    private val windows: List<Int> = DEFAULT_WINDOWS,
) {
    /**
     * Everything [score] offers, hardest last. Empty means nothing in this piece is readable at any
     * rung the path teaches — which is a fact about the piece, not a failure to report as an error.
     */
    public fun offers(score: Score): List<Score> {
        if (rungFor(score) != null) return listOf(score)
        // Graded once and carried, not asked again inside the comparator: a sort calls its
        // comparator O(n log n) times, and each call would re-walk every note against every rung.
        return windows.asSequence()
            .flatMap { bars -> score.passages(bars, step = bars) }
            .mapNotNull { passage -> rungFor(passage)?.let { passage to it.number } }
            .sortedWith(compareBy({ it.second }, { it.first.measures.size }, { it.first.id.value }))
            .map { it.first }
            .toList()
    }

    /**
     * What to open when Dewi asks for this piece while standing at [stage]: the most he can hold
     * that this rung admits, or failing that the easiest thing the piece has. Null when the piece
     * offers nothing at all.
     */
    public fun passageFor(score: Score, stage: Stage): Score? {
        val offered = offers(score)
        return offered.filter { stage.spec.admits(it).isAdmitted }.maxByOrNull { it.measures.size }
            ?: offered.firstOrNull()
    }

    /** The lowest rung whose own spec covers this music, or null when it is past the last one. */
    public fun rungFor(score: Score): StageId? =
        curriculum.stages.firstOrNull { it.spec.covers(score) }?.id

    public companion object {
        /**
         * Four bars is a glance, sixteen is a page. Offering all three lets the same song serve a
         * reader who can hold one line and a reader who can hold a phrase.
         */
        public val DEFAULT_WINDOWS: List<Int> = listOf(4, 8, 16)
    }
}
