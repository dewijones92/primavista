package com.dewijones92.primavista.database

import com.dewijones92.primavista.practice.NoteJudgement
import com.dewijones92.primavista.practice.Verdict
import com.dewijones92.primavista.score.Midi
import com.dewijones92.primavista.score.Ticks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row ↔ judgement, without a device. A verdict that does not survive storage is a report that
 * cannot be re-judged (docs/spec.md I7).
 */
class NoteVerdictRowTest {
    private val session = SessionId("session-1")

    private val judgements: List<NoteJudgement> = listOf(
        NoteJudgement.OfNote(0, Verdict.Correct(dtMillis = -12.5), confidence = 0.91f),
        NoteJudgement.OfNote(1, Verdict.WrongPitch(Midi(66), Midi(65), dtMillis = 38.0), confidence = 0.74f),
        NoteJudgement.OfNote(2, Verdict.Early(dtMillis = -140.0), confidence = 1f),
        NoteJudgement.OfNote(3, Verdict.Late(dtMillis = 210.25), confidence = 0.5f),
        NoteJudgement.OfNote(4, Verdict.Missed, confidence = 0f),
        NoteJudgement.Unexpected(Verdict.Extra(Midi(72), Ticks(10_080L)), confidence = 0.33f),
    )

    @Test
    fun everyJudgementSurvivesTheRoundTrip() {
        judgements.forEach { judgement ->
            val reading = judgement.toEntity(session).read()

            assertTrue("$judgement was unreadable: $reading", reading is VerdictRowReading.Readable)
            assertEquals(judgement, (reading as VerdictRowReading.Readable).judgement)
        }
    }

    /** Ticks in a column named milliseconds is the "spell the unit" failure CLAUDE.md names. */
    @Test
    fun anExtraStoresItsPositionInTicksAndLeavesTheMillisecondColumnEmpty() {
        val row = NoteJudgement.Unexpected(Verdict.Extra(Midi(72), Ticks(30_240L))).toEntity(session)

        assertEquals(30_240L, row.atTicks)
        assertNull("an extra has no offset from an expected note", row.dtMillis)
    }

    /** The sealed judgement is what abolished the `-1` note index; the column must not restore it. */
    @Test
    fun anExtraAnswersToNoNoteIndexAtAll() {
        val row = NoteJudgement.Unexpected(Verdict.Extra(Midi(72), Ticks(0L))).toEntity(session)

        assertNull(row.noteIndex)
    }

    @Test
    fun aJudgementOfANoteKeepsItsIndex() {
        val row = NoteJudgement.OfNote(7, Verdict.Missed).toEntity(session)

        assertEquals(7, row.noteIndex)
    }

    /** A row written by the sentinel-era build must be refused, not read as note -1. */
    @Test
    fun anExtraCarryingASentinelNoteIndexIsRefusedWithAReason() {
        val row = NoteJudgement.Unexpected(Verdict.Extra(Midi(72), Ticks(0L))).toEntity(session).copy(noteIndex = -1)

        val reading = row.read()

        assertTrue(reading is VerdictRowReading.Unreadable)
        assertTrue((reading as VerdictRowReading.Unreadable).reason.contains("sentinel"))
    }

    @Test
    fun aNonExtraWithoutANoteIndexIsRefusedRatherThanInvented() {
        val row = NoteJudgement.OfNote(3, Verdict.Missed).toEntity(session).copy(noteIndex = null)

        assertTrue(row.read() is VerdictRowReading.Unreadable)
    }

    @Test
    fun confidenceSurvivesSoAReportCanTellAWrongNoteFromAnUnsureOne() {
        val row = NoteJudgement.OfNote(0, Verdict.WrongPitch(Midi(66), Midi(65), 0.0), confidence = 0.21f)
            .toEntity(session)

        assertEquals(0.21f, row.confidence, 1e-6f)
        assertEquals(0.21f, (row.read() as VerdictRowReading.Readable).judgement.confidence, 1e-6f)
    }

    @Test
    fun everyUnreadableRowSaysWhyAndNamesItself() {
        val row = NoteJudgement.OfNote(0, Verdict.Correct(1.0)).toEntity(session)
            .copy(id = 42L, kind = "fromTheFuture")

        val reading = row.read()

        assertTrue(reading is VerdictRowReading.Unreadable)
        assertEquals(42L, (reading as VerdictRowReading.Unreadable).rowId)
        assertTrue(reading.reason.contains("fromTheFuture"))
    }

    @Test
    fun aRowMissingTheFieldItsKindNeedsIsRefused() {
        listOf(
            NoteJudgement.OfNote(0, Verdict.Correct(1.0)).toEntity(session).copy(dtMillis = null),
            NoteJudgement.OfNote(0, Verdict.WrongPitch(Midi(66), Midi(65), 0.0)).toEntity(session)
                .copy(expectedMidi = null),
            NoteJudgement.Unexpected(Verdict.Extra(Midi(72), Ticks(0L))).toEntity(session).copy(atTicks = null),
        ).forEach { row ->
            assertTrue("row $row should be unreadable", row.read() is VerdictRowReading.Unreadable)
        }
    }
}
