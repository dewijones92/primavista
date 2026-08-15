package com.dewijones92.primavista.practice

import java.time.Instant
import java.time.ZoneId

/**
 * Days practised, and nothing else.
 *
 * There is deliberately no "you broke it", no penalty and no record of what was lost:
 * [currentDays] of 0 means there is no run in progress, which is a fact about the calendar and
 * not a verdict on the reader (docs/journey.md).
 */
public data class Streak(
    val currentDays: Int,
    val bestDays: Int,
    val daysPractised: Int,
) {
    public companion object {
        public val None: Streak = Streak(currentDays = 0, bestDays = 0, daysPractised = 0)

        /**
         * [practisedAtEpochMillis] is one timestamp per session that **counted as practice**, and
         * which sessions those were is the caller's to decide — opening the app is not practice,
         * and a piece that scrolled past untouched is not either.
         *
         * [zone] is supplied rather than read, because "which day was that?" is a question about
         * where the reader is and a test cannot control `ZoneId.systemDefault()`. Yesterday still
         * counts as a live run: the day is not over until it is over.
         */
        public fun of(
            practisedAtEpochMillis: List<Long>,
            zone: ZoneId,
            nowEpochMillis: Long,
        ): Streak {
            val days = practisedAtEpochMillis.map { dayOf(it, zone) }.distinct().sorted()
            if (days.isEmpty()) return None
            val stillLive = dayOf(nowEpochMillis, zone) - days.last() <= 1
            return Streak(
                currentDays = if (stillLive) runEndingAtLast(days) else 0,
                bestDays = longestRun(days),
                daysPractised = days.size,
            )
        }
    }
}

private fun dayOf(epochMillis: Long, zone: ZoneId): Long =
    Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().toEpochDay()

private fun runEndingAtLast(days: List<Long>): Int {
    var index = days.lastIndex
    while (index > 0 && days[index] - days[index - 1] == 1L) index--
    return days.size - index
}

private fun longestRun(days: List<Long>): Int {
    var best = 1
    var run = 1
    for (index in 1..days.lastIndex) {
        run = if (days[index] - days[index - 1] == 1L) run + 1 else 1
        best = maxOf(best, run)
    }
    return best
}
