package com.dewijones92.primavista.practice

import com.dewijones92.primavista.score.Midi
import com.dewijones92.primavista.score.Ticks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val A_DT = 12.5

/**
 * The names a diagnostics report writes down.
 *
 * These are literals on the type rather than `this::class.simpleName` because the release build
 * minifies: a reflective name reaches Dewi's phone as `a`, and docs/spec.md I7 — a report can
 * settle what happened — would hold in debug and nowhere else. The repo has paid for this lesson
 * before, in Totum, where R8 renamed everything a crash report printed.
 */
class VerdictNamesTest {

    private val everyVerdict = listOf(
        Verdict.Correct(A_DT),
        Verdict.WrongPitch(Midi(60), Midi(61), A_DT),
        Verdict.Early(-A_DT),
        Verdict.Late(A_DT),
        Verdict.Missed,
        Verdict.Extra(Midi(60), Ticks.ZERO),
    )

    @Test
    fun `every verdict names itself, and names itself the same way twice`() {
        assertEquals(
            listOf("Correct", "WrongPitch", "Early", "Late", "Missed", "Extra"),
            everyVerdict.map { it.kind },
        )
    }

    /**
     * The names have to be distinct, because a replay compares them: two verdicts sharing a name
     * would make a re-judged session agree with a report it should have contradicted.
     */
    @Test
    fun `no two verdicts share a name`() {
        assertEquals(everyVerdict.size, everyVerdict.map { it.kind }.distinct().size)
    }

    /**
     * The tell that someone went back to reflection. R8 renames to short lower-case identifiers, so
     * a name that is not the exact word a human would write is the failure this guards.
     */
    @Test
    fun `no verdict name looks like something a minifier produced`() {
        val suspicious = everyVerdict.map { it.kind }.filter { it.length < 2 || it.first().isLowerCase() }

        assertEquals(emptyList<String>(), suspicious)
    }

    @Test
    fun `what a report claims uses the verdict's own name`() {
        val claimed = ClaimedVerdict.of(NoteJudgement.OfNote(0, Verdict.WrongPitch(Midi(60), Midi(61), A_DT), 1f))

        assertEquals("WrongPitch", claimed.kind)
        assertTrue(claimed.dtMillis == A_DT)
    }
}
