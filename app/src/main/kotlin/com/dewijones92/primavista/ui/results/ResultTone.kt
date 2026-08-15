package com.dewijones92.primavista.ui.results

import com.dewijones92.primavista.practice.SessionResult
import com.dewijones92.primavista.ui.mascot.MascotMood
import com.dewijones92.primavista.ui.progress.percent

/**
 * How a finished session is allowed to present itself.
 *
 * Only [Excellent] celebrates, and every figure on the sheet — headline, tint, meter and the big
 * number itself — is read from [SessionResult.cleanliness], because accuracy alone gives full marks
 * to a run that played every written note *and* twenty that were not written. A flourish over a poor
 * performance would be the app flattering Dewi, and this app's only job is telling him the truth
 * about his playing — so the decision lives here, in pure functions with their own tests, rather
 * than inside a composable.
 */
internal enum class ResultTone(val headline: String, val celebrates: Boolean) {
    Nothing("Nothing to judge", false),
    Rough("Rough one", false),
    Mixed("Getting there", false),
    Good("Solid", false),
    Excellent("Clean run", true),
}

internal fun toneOf(result: SessionResult): ResultTone = when {
    result.notesExpected == 0 -> ResultTone.Nothing
    result.cleanliness >= EXCELLENT_AT -> ResultTone.Excellent
    result.cleanliness >= GOOD_AT -> ResultTone.Good
    result.cleanliness >= MIXED_AT -> ResultTone.Mixed
    else -> ResultTone.Rough
}

/**
 * Trill's face for a finished session. Derived *from* [ResultTone] rather than from the numbers a
 * second time, so she cannot disagree with the headline beside her — and only the two tones that
 * describe a good run reach a pleased face. See `.claude/CODE-NOTES.md`.
 */
internal fun moodFor(tone: ResultTone): MascotMood = when (tone) {
    ResultTone.Nothing -> MascotMood.Curious
    ResultTone.Rough -> MascotMood.Wincing
    ResultTone.Mixed -> MascotMood.Idle
    ResultTone.Good -> MascotMood.Delighted
    ResultTone.Excellent -> MascotMood.Impressed
}

/** The arithmetic the headline percentage came from, spelled out. See `.claude/CODE-NOTES.md`. */
internal fun headlineBasis(result: SessionResult): String = when {
    result.notesExpected == 0 -> "There was nothing written to play"
    result.extras == 0 -> "${result.correct} of ${result.notesExpected} written notes"
    else ->
        "${result.correct} right, out of ${result.notesExpected} written " +
            "+ ${result.extras} unwritten"
}

/** Plain about what happened. No encouragement that the numbers do not support. */
internal fun supportOf(result: SessionResult, tone: ResultTone): String = when (tone) {
    ResultTone.Nothing -> "No notes were judged, so there is nothing to tell you."
    ResultTone.Rough ->
        "Most of it did not land. Reading is a skill and the list below is where it went wrong."
    ResultTone.Mixed -> "Some of it landed. The skills below are what cost you the rest."
    ResultTone.Good -> "Most of it landed, in pitch and in time."
    ResultTone.Excellent -> "${result.correct} notes right, in pitch and in time."
}

/**
 * Shown only when there were extras, because the headline counts them and the number alone cannot
 * say so — nor say what the written notes on their own came to.
 */
internal fun extrasNote(result: SessionResult): String? {
    if (result.extras == 0) return null
    val counted = "${result.extras} note${if (result.extras == 1) "" else "s"} played that " +
        "${if (result.extras == 1) "was" else "were"} not written"
    val alone = percent(result.accuracy)
    return if (alone == percent(result.cleanliness)) {
        "$counted, counted against you above."
    } else {
        "$counted, counted against the ${percent(result.cleanliness)}% above — " +
            "the written notes alone came to $alone%."
    }
}

private const val EXCELLENT_AT = 0.95

/** The one place "good" and "middling" are decided for a session and for the skills inside it. */
internal const val GOOD_AT: Double = 0.85
internal const val MIXED_AT: Double = 0.6
