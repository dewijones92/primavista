package com.dewijones92.primavista.ui.results

import com.dewijones92.primavista.practice.NoteJudgement
import com.dewijones92.primavista.practice.SessionResult
import com.dewijones92.primavista.practice.Verdict
import com.dewijones92.primavista.score.Midi
import com.dewijones92.primavista.score.Ticks

/** One hand-built session, shared by the tone tests and the mascot tests so both judge the same run. */
internal fun resultOf(correct: Int, expected: Int, extras: Int = 0): SessionResult {
    val judged = List(correct) { NoteJudgement.OfNote(it, Verdict.Correct(0.0)) } +
        List(expected - correct) { NoteJudgement.OfNote(correct + it, Verdict.Missed) } +
        List(extras) { NoteJudgement.Unexpected(Verdict.Extra(Midi(60), Ticks(it.toLong()))) }
    return SessionResult(judgements = judged, skillOutcomes = emptyList(), notesExpected = expected)
}
