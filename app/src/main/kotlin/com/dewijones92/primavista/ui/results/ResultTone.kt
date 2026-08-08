package com.dewijones92.primavista.ui.results

import com.dewijones92.primavista.practice.SessionResult

/**
 * How a finished session is allowed to present itself.
 *
 * Only [Excellent] celebrates, and it is judged on [SessionResult.cleanliness] rather than
 * [SessionResult.accuracy], because accuracy alone gives full marks to a run that played every
 * written note *and* twenty that were not written. A flourish over a poor performance would be the
 * app flattering Dewi, and this app's only job is telling him the truth about his playing — so the
 * decision lives here, in a pure function with its own tests, rather than inside a composable.
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

/** Plain about what happened. No encouragement that the numbers do not support. */
internal fun supportOf(result: SessionResult, tone: ResultTone): String = when (tone) {
    ResultTone.Nothing -> "No notes were judged, so there is nothing to tell you."
    ResultTone.Rough ->
        "Most of it did not land. Reading is a skill and the list below is where it went wrong."
    ResultTone.Mixed -> "Some of it landed. The skills below are what cost you the rest."
    ResultTone.Good -> "Most of it landed, in pitch and in time."
    ResultTone.Excellent ->
        "${result.correct} of ${result.notesExpected} notes, right pitch and on the beat."
}

/**
 * Shown only when there were extras, because a run that scores 100% on the written notes while
 * playing others is not a clean one and the single percentage cannot say so.
 */
internal fun extrasNote(result: SessionResult): String? =
    if (result.extras == 0) {
        null
    } else {
        "${result.extras} note${if (result.extras == 1) "" else "s"} played that were not written — " +
            "counting those, this run is ${(result.cleanliness * PERCENT_SCALE).toInt()}%."
    }

private const val EXCELLENT_AT = 0.95
private const val GOOD_AT = 0.85
private const val MIXED_AT = 0.6
private const val PERCENT_SCALE = 100
