package com.dewijones92.primavista.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dewijones92.primavista.practice.NoteJudgement
import com.dewijones92.primavista.practice.Verdict
import com.dewijones92.primavista.score.Midi
import com.dewijones92.primavista.score.ScoreOrigin
import com.dewijones92.primavista.score.Ticks
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionStoreTest {
    private lateinit var database: PrimaVistaDatabase
    private lateinit var store: SessionStore

    @Before
    fun setUp() {
        database = openTestDatabase()
        store = RoomSessionStore(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun everyVerdictKindSurvivesTheRoundTripThroughSqlite() = runBlocking {
        val judgements = listOf(
            NoteJudgement.OfNote(0, Verdict.Correct(dtMillis = -12.5), confidence = 0.91f),
            NoteJudgement.OfNote(1, Verdict.WrongPitch(Midi(66), Midi(65), dtMillis = 38.0), confidence = 0.74f),
            NoteJudgement.OfNote(2, Verdict.Early(dtMillis = -140.0), confidence = 1f),
            NoteJudgement.OfNote(3, Verdict.Late(dtMillis = 210.25), confidence = 0.5f),
            NoteJudgement.OfNote(4, Verdict.Missed, confidence = 0f),
            NoteJudgement.Unexpected(Verdict.Extra(Midi(72), Ticks(10_080L)), confidence = 0.33f),
        )

        store.save(sampleSession(), judgements)

        assertEquals(judgements, store.judgements(SessionId("session-1")))
    }

    /** A trill puts several extras in one session, and none of them answers to a note index. */
    @Test
    fun severalExtrasInOneSessionAreAllKept() = runBlocking {
        val trill = (0..3).map {
            NoteJudgement.Unexpected(Verdict.Extra(Midi(71 + it % 2), Ticks(it * 2_520L)))
        }

        store.save(sampleSession(), trill)

        assertEquals(trill, store.judgements(SessionId("session-1")))
    }

    @Test
    fun aGeneratedSessionKeepsItsSeedAndSpecSoItCanBeReplayed() = runBlocking {
        val origin = ScoreOrigin.Generated(seed = 987_654_321L, spec = sampleSpec())
        store.save(sampleSession(origin = origin), emptyList())

        val read = store.recent().single()

        assertEquals(origin, read.origin)
        assertEquals(sampleSpec(), (read.origin as ScoreOrigin.Generated).spec)
        assertTrue(read.originDescriptor.contains("seed=987654321"))
    }

    @Test
    fun aParsedSessionKeepsItsSourceAndLicence() = runBlocking {
        val origin = ScoreOrigin.Parsed(sourceName = "bwv-anh-114.musicxml", licence = "Public Domain")
        store.save(sampleSession(origin = origin), emptyList())

        assertEquals(origin, store.recent().single().origin)
    }

    @Test
    fun savingTwiceReplacesTheSessionRatherThanDuplicatingIt() = runBlocking {
        val paused = sampleSession(finishedAtEpochMillis = null, correct = 4)
        store.save(paused, listOf(NoteJudgement.OfNote(0, Verdict.Correct(1.0))))
        assertEquals(listOf(paused.id), store.unfinished().map { it.id })

        val finished = sampleSession(finishedAtEpochMillis = 1_700_000_090_000L, correct = 11)
        store.save(
            finished,
            listOf(NoteJudgement.OfNote(0, Verdict.Correct(1.0)), NoteJudgement.OfNote(1, Verdict.Missed)),
        )

        assertEquals(1, database.sessions().count())
        assertEquals(2, database.noteVerdicts().count())
        assertEquals(11, store.recent().single().correct)
        assertTrue(store.unfinished().isEmpty())
    }

    @Test
    fun recentIsNewestFirstAndHonoursItsLimit() = runBlocking {
        listOf(1_000L, 3_000L, 2_000L).forEachIndexed { index, startedAt ->
            store.save(
                sampleSession(id = "s$index").copy(startedAtEpochMillis = startedAt),
                emptyList(),
            )
        }

        assertEquals(listOf("s1", "s2"), store.recent(limit = 2).map { it.id.value })
    }

    @Test
    fun anUnreadableStoredSpecLeavesTheSessionReadableAndSaysWhatWasStored() = runBlocking {
        val row = sampleSession().toEntity().copy(originSpec = "v=1;staves=Nonsense")
        database.sessions().upsert(row)

        val read = store.recent().single()

        assertNull(read.origin)
        assertTrue(read.originDescriptor.contains("Nonsense"))
        assertFalse(read.originDescriptor.contains("spec=-"))
    }

    /** A parsed origin with no source name cannot be reopened, so it is a loss and must read as one. */
    @Test
    fun aParsedRowWithoutASourceNameReadsAsAnUnknownOriginRatherThanAnEmptyOne() = runBlocking {
        val row = sampleSession().toEntity().copy(
            originKind = OriginKinds.PARSED,
            originSourceName = null,
            originLicence = "Public Domain",
            originSeed = null,
            originSpec = null,
        )
        database.sessions().upsert(row)

        val read = store.recent().single()

        assertNull(read.origin)
        assertTrue(read.originDescriptor.contains("kind=parsed"))
        assertEquals(9, read.correct)
    }

    @Test
    fun deletingASessionRemovesItsVerdicts() = runBlocking {
        store.save(sampleSession(), listOf(NoteJudgement.OfNote(0, Verdict.Missed)))

        store.delete(SessionId("session-1"))

        assertEquals(0, database.sessions().count())
        assertEquals(0, database.noteVerdicts().count())
    }
}
