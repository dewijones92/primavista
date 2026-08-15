package com.dewijones92.primavista.ui.progress

import com.dewijones92.primavista.practice.SkillState
import com.dewijones92.primavista.ui.mascot.MascotMood
import kotlin.math.roundToInt

/**
 * The shape the Progress screen puts on a bare list of [SkillState]s.
 *
 * Pure, and separately tested, because every number on that screen has to be derivable from stored
 * evidence — a "trend" the store cannot support would be the app flattering him, which is the one
 * thing docs/spec.md says it must never do.
 */
internal enum class SkillBucket(val title: String, val blurb: String) {
    Due("Due now", "What the scheduler picks from next."),
    Building("Building", "Read at least once, not yet reliable."),
    Mastered("Solid", "Reliable, and not due for review."),
}

/** One finished session, reduced to what a trend strip can honestly draw. */
public data class SessionPoint(
    val atEpochMillis: Long,
    val accuracy: Double,
    val title: String,
)

/**
 * The "Solid" bucket is [SkillState.isSolid] **and not due**, and the second half is about spacing
 * rather than about reading — a skill can be read reliably and still be worth revisiting today.
 * Solidity itself is the curriculum's word, so it is asked for rather than restated here: this file
 * used to carry its own 0.8, which meant a stage could pass on a skill this screen would not colour
 * as solid. See `.claude/CODE-NOTES.md`.
 */
internal fun bucketOf(state: SkillState, nowEpochMillis: Long): SkillBucket = when {
    state.isDue(nowEpochMillis) -> SkillBucket.Due
    state.isSolid -> SkillBucket.Mastered
    else -> SkillBucket.Building
}

/** Due first, then weakest first — the order the scheduler itself would work through them. */
internal fun ordered(states: List<SkillState>, nowEpochMillis: Long): List<SkillState> =
    states.sortedWith(compareByDescending<SkillState> { it.isDue(nowEpochMillis) }.thenBy { it.strength })

internal fun readingStrength(states: List<SkillState>): Double =
    if (states.isEmpty()) 0.0 else states.sumOf { it.strength } / states.size

internal fun relativeDue(state: SkillState, nowEpochMillis: Long): String {
    val remaining = state.dueAtEpochMillis - nowEpochMillis
    return when {
        remaining <= 0 -> "due now"
        remaining < MILLIS_PER_HOUR -> "due in ${atLeastOne(remaining, MILLIS_PER_MINUTE)}m"
        remaining < MILLIS_PER_DAY -> "due in ${atLeastOne(remaining, MILLIS_PER_HOUR)}h"
        else -> "due in ${atLeastOne(remaining, MILLIS_PER_DAY)}d"
    }
}

/**
 * The lifetime record, in the words the scheduler uses. Lapses are named rather than folded into
 * the strength, because "solid but it has caught you out twice" is a different thing to practise
 * than "solid, never missed".
 */
internal fun attemptsText(state: SkillState): String = buildString {
    append(state.attempts)
    append(if (state.attempts == 1) " session" else " sessions")
    if (state.lapses > 0) append(" · ${state.lapses} lapse${if (state.lapses == 1) "" else "s"}")
    if (state.repetition > 0) append(" · rung ${state.repetition}")
}

internal fun percent(value: Double): Int = (value * PERCENT_SCALE).roundToInt()

/** What Trill's face says at the top of the screen, and the sentence that has to justify it. */
internal data class ProgressGreeting(val mood: MascotMood, val line: String)

/**
 * Pure, so the one rule that matters is a unit test rather than something to be eyeballed: a face
 * is only pleased where the stored evidence earns it. [sessions] must be oldest-first, which is the
 * order the route reads them in. See `.claude/CODE-NOTES.md`.
 */
internal fun greetingFor(states: List<SkillState>, sessions: List<SessionPoint>): ProgressGreeting {
    val best = personalBest(sessions)
    return when {
        states.isEmpty() -> ProgressGreeting(MascotMood.Sleepy, NOTHING_TRACKED)
        best != null -> ProgressGreeting(MascotMood.Impressed, best)
        states.all { it.isSolid } -> ProgressGreeting(MascotMood.Delighted, allSolidLine(states.size))
        else -> ProgressGreeting(MascotMood.Idle, trackedLine(states.size))
    }
}

/**
 * The line for a best-yet session, or null when there is not one to claim. Both guards are honesty
 * rather than taste: a "best" out of two sessions is arithmetic on nothing, and two runs that print
 * the same percentage have not beaten each other on the screen Dewi is looking at.
 */
private fun personalBest(sessions: List<SessionPoint>): String? {
    if (sessions.size < MIN_BEST_SESSIONS) return null
    val latest = percent(sessions.last().accuracy)
    val previous = percent(sessions.dropLast(1).maxOf { it.accuracy })
    if (latest <= previous) return null
    return "Your best stored session yet — $latest% of its written notes, past $previous%."
}

internal fun trackedLine(count: Int): String =
    "$count reading skill${plural(count)} tracked, every one of them from notes this app put in " +
        "front of you."

private fun allSolidLine(count: Int): String =
    if (count == 1) "The one skill tracked is reading solid." else "All $count tracked skills are reading solid."

private fun plural(count: Int): String = if (count == 1) "" else "s"

/**
 * Whether the recent run is going the right way, from the sessions themselves. Null when there is
 * not enough to compare — two points are a line, one point is not a direction.
 */
internal fun trendOf(points: List<SessionPoint>): Double? {
    if (points.size < MIN_TREND_POINTS) return null
    val half = points.size / 2
    val older = points.take(half).map { it.accuracy }.average()
    val newer = points.drop(points.size - half).map { it.accuracy }.average()
    return newer - older
}

/** Says which halves were compared, so it cannot be read as a claim about the run as a whole. */
internal fun trendText(delta: Double?): String = when {
    delta == null -> "Not enough finished sessions to call a direction yet."
    delta >= TREND_NOTICEABLE ->
        "Improving — the newer half of these bars beat the older half by ${percent(delta)} points."
    delta <= -TREND_NOTICEABLE ->
        "Slipping — the newer half of these bars is ${percent(-delta)} points behind the older half."
    else -> "Holding steady across the bars shown."
}

private fun atLeastOne(remaining: Long, unit: Long): Long = maxOf(1L, remaining / unit)

private const val NOTHING_TRACKED = "Nothing read yet"
private const val PERCENT_SCALE = 100
private const val MIN_TREND_POINTS = 4
private const val MIN_BEST_SESSIONS = 4
private const val TREND_NOTICEABLE = 0.05
private const val MILLIS_PER_MINUTE = 60_000L
private const val MILLIS_PER_HOUR = 60 * MILLIS_PER_MINUTE
private const val MILLIS_PER_DAY = 24 * MILLIS_PER_HOUR
