package com.dewijones92.primavista.score

/**
 * A passage of a [Score], as a [Score].
 *
 * This is what makes real repertoire usable at all. A whole song contains every reading skill
 * somewhere in it, so the curriculum can only ever grade it "harder than the last rung" — 596
 * songs graded identically, which is one rung with a lot of pages rather than a ladder. Its opening
 * eight bars, on the other hand, are a specific difficulty, and one piece can offer an easy passage
 * and a hard one. See `.claude/CODE-NOTES.md`.
 *
 * The result is the same type as its parent, deliberately: an excerpt of Schubert, a whole Bach
 * minuet and a generated drill are indistinguishable to the scrolling loop, the judge and the
 * layout engine (CLAUDE.md, *The ladder problem*).
 *
 * @param fromIndex 0-based index into [Score.measures], matching [Measure.index].
 */
public fun Score.excerpt(fromIndex: Int, bars: Int): Score = Passages(this).at(fromIndex, bars)

/**
 * Every window of [bars] bars, stepping by [step]. Overlapping windows are allowed and useful: the
 * hard bar of a piece is worth reading both as an opening and as a continuation.
 */
public fun Score.passages(bars: Int, step: Int = bars): List<Score> {
    require(step > 0) { "a step of $step never advances" }
    val cutter = Passages(this)
    return (measures.indices step step)
        .filter { it + bars <= measures.size }
        .map { cutter.at(it, bars) }
}

/**
 * Cutting windows out of one score, with the per-score work done once.
 *
 * It exists for a measured reason. Cutting each window by filtering the whole event list scans
 * every event per window, so a 232-bar song at three window sizes rescanned thousands of events a
 * hundred times over — enough allocation to hold the emulator in back-to-back GC for seven seconds,
 * freeing 30-50MB every 300ms, which is what a "slow screen" turned out to be. Sorting once and
 * binary-searching the window makes each cut cost the window rather than the piece.
 */
private class Passages(private val score: Score) {
    /** Stable, so an already-ordered score keeps its secondary order within an onset. */
    private val ordered: List<ScoreEvent> = score.events.sortedBy { it.onset.value }
    private val onsets: LongArray = LongArray(ordered.size) { ordered[it].onset.value }

    fun at(fromIndex: Int, bars: Int): Score {
        require(bars > 0) { "an excerpt of $bars bars is not a passage" }
        require(fromIndex in score.measures.indices) {
            "bar index $fromIndex is outside the ${score.measures.size} bars of ${score.id.value}"
        }
        val window = score.measures.subList(fromIndex, minOf(fromIndex + bars, score.measures.size))
        val from = window.first().start
        val until = endOf(fromIndex + bars, window.last())
        // An event that starts inside the window is kept whole, even if it rings past the last
        // barline: clipping it would produce a duration nobody could notate.
        val kept = ordered.subList(firstAtOrAfter(from.value), firstAtOrAfter(until.value))
        // The title says what is PRINTED, because that is what Dewi reads off the page; the id says
        // the INDICES, because that is what rebuilds the window. See PassageId.
        val firstBar = window.first().number
        val lastBar = window.last().number
        return score.copy(
            id = PassageId.of(score.id, fromIndex, window.size),
            title = "${score.title} (bars $firstBar$EN_DASH$lastBar)",
            measures = window.mapIndexed { index, bar -> bar.copy(index = index, start = bar.start - from) },
            events = kept.map { it.rebased(from, hasTieInto(kept, it)) },
        )
    }

    /**
     * Where the window really ends: the **start of the next bar**, not this bar's start plus what
     * its time signature says it should hold.
     *
     * Those differ whenever a measure is short, which real music does constantly — a pickup, or a
     * bar split across a system. Trusting the time signature overshot the barline and pulled the
     * following bar's notes into the passage: 126 leaked events across the shipped corpus, e.g.
     * `lieder-lc8873154` bars 13-16, whose true end is tick 433,440 against a computed 453,600.
     * Only the last bar of a piece has no successor to ask, and there the signature is all there is.
     */
    private fun endOf(afterIndex: Int, last: Measure): Ticks =
        score.measures.getOrNull(afterIndex)?.start ?: (last.start + last.time.measureTicks)

    private fun firstAtOrAfter(tick: Long): Int {
        val found = onsets.binarySearch(tick)
        if (found < 0) return -(found + 1)
        var index = found
        while (index > 0 && onsets[index - 1] == tick) index--
        return index
    }
}

/**
 * Whether the note this one is tied from survived the cut. If it did not, the tie has no partner
 * and the note is now attacked — leaving the flag set would hide it from the judge entirely
 * (see [Score.attackedNotes]). The scan is only reached by a note that claims a tie, which is rare.
 */
private fun hasTieInto(kept: List<ScoreEvent>, event: ScoreEvent): Boolean {
    if (event !is Note || !event.tiedFromPrevious) return false
    return kept.any { earlier ->
        earlier is Note &&
            earlier.tiedToNext &&
            earlier.endsAt == event.onset &&
            earlier.pitch == event.pitch &&
            earlier.staff == event.staff &&
            earlier.voice == event.voice
    }
}

private fun ScoreEvent.rebased(from: Ticks, tiedFromPrevious: Boolean): ScoreEvent = when (this) {
    is Note -> copy(onset = onset - from, tiedFromPrevious = tiedFromPrevious)
    is Rest -> copy(onset = onset - from)
}

private const val EN_DASH = "–"
