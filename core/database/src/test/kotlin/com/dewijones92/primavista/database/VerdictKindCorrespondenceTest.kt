package com.dewijones92.primavista.database

import com.dewijones92.primavista.practice.Verdict
import com.dewijones92.primavista.score.Midi
import com.dewijones92.primavista.score.Ticks
import org.junit.Assert.assertEquals
import org.junit.Test

private const val A_DT = 4.0

/**
 * A verdict is spelled twice on purpose, and this is what stops the two drifting apart.
 *
 * `VerdictKinds` is **on disk** — every session row already written uses `"wrongPitch"`, so those
 * strings cannot change without a data migration that would buy nothing. `Verdict.kind` is the
 * word a shared diagnostics report prints, and reads as `WrongPitch`. Both are literals rather
 * than reflection, because the release build minifies (docs/spec.md I7).
 *
 * Since neither can simply defer to the other, the guard is a **one-to-one correspondence**: every
 * verdict has exactly one stored kind and exactly one reported kind, and adding a verdict without
 * both fails here.
 */
class VerdictKindCorrespondenceTest {

    private val everyVerdict = listOf(
        Verdict.Correct(A_DT),
        Verdict.WrongPitch(Midi(60), Midi(61), A_DT),
        Verdict.Early(-A_DT),
        Verdict.Late(A_DT),
        Verdict.Missed,
        Verdict.Extra(Midi(60), Ticks.ZERO),
    )

    private val stored = listOf(
        VerdictKinds.CORRECT,
        VerdictKinds.WRONG_PITCH,
        VerdictKinds.EARLY,
        VerdictKinds.LATE,
        VerdictKinds.MISSED,
        VerdictKinds.EXTRA,
    )

    @Test
    fun `the reported name and the stored name differ only in their first letter`() {
        assertEquals(stored, everyVerdict.map { it.kind.replaceFirstChar { first -> first.lowercase() } })
    }

    /** On-disk values are frozen by rows already written; this is what says so out loud. */
    @Test
    fun `the stored spellings are exactly what is already on disk`() {
        assertEquals(
            listOf("correct", "wrongPitch", "early", "late", "missed", "extra"),
            stored,
        )
    }

    @Test
    fun `no two verdicts share a stored kind`() {
        assertEquals(stored.size, stored.distinct().size)
    }
}
