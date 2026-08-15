package com.dewijones92.primavista.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dewijones92.primavista.practice.NoteJudgement
import com.dewijones92.primavista.practice.Streak
import com.dewijones92.primavista.practice.Verdict
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Which sessions count as a day practised. The fold itself is `Streak` in `:core:practice`; what
 * is on trial here is the evidence this module hands it.
 */
@RunWith(AndroidJUnit4::class)
class PracticeStreakStoreTest {
    private lateinit var database: PrimaVistaDatabase
    private lateinit var store: RoomSessionStore
    private val london: ZoneId = ZoneId.of("Europe/London")
    private val played = NoteJudgement.OfNote(0, Verdict.Correct(dtMillis = 4.0))

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
    fun noSessionsIsNoStreakRatherThanARefusal() = runBlocking {
        assertEquals(Streak.None, store.streak(london, at(2026, 8, 15)).readOrFail())
    }

    @Test
    fun daysWithANotePlayedCountAndRunTogether() = runBlocking {
        save("s0", at(2026, 8, 13), listOf(played))
        save("s1", at(2026, 8, 14), listOf(played))
        save("s2", at(2026, 8, 15), listOf(played))

        val streak = store.streak(london, at(2026, 8, 15, hour = 21)).readOrFail()

        assertEquals(3, streak.currentDays)
        assertEquals(3, streak.daysPractised)
    }

    /** Opening the app and playing nothing is showing up, and showing up is not practice. */
    @Test
    fun aSessionWithNoVerdictsAtAllIsNotADayPractised() = runBlocking {
        save("s0", at(2026, 8, 15), emptyList())

        assertEquals(Streak.None, store.streak(london, at(2026, 8, 15, hour = 21)).readOrFail())
    }

    /** A piece that scrolled past untouched is judged entirely `Missed`, and is not practice. */
    @Test
    fun aSessionWhereEveryNoteWasMissedIsNotADayPractised() = runBlocking {
        save(
            "s0",
            at(2026, 8, 15),
            listOf(NoteJudgement.OfNote(0, Verdict.Missed), NoteJudgement.OfNote(1, Verdict.Missed)),
        )

        assertEquals(Streak.None, store.streak(london, at(2026, 8, 15, hour = 21)).readOrFail())
    }

    @Test
    fun aWrongNoteIsStillPractice() = runBlocking {
        save("s0", at(2026, 8, 15), listOf(NoteJudgement.OfNote(0, Verdict.Missed), played))

        assertEquals(1, store.streak(london, at(2026, 8, 15, hour = 21)).readOrFail().daysPractised)
    }

    /** A session is written at pause too, so practice that was never finished still counts. */
    @Test
    fun practiceThatWasPausedAndNeverFinishedStillCounts() = runBlocking {
        store.save(
            sampleSession(id = "s0").copy(
                startedAtEpochMillis = at(2026, 8, 15),
                finishedAtEpochMillis = null,
            ),
            listOf(played),
        )

        assertEquals(1, store.streak(london, at(2026, 8, 15, hour = 21)).readOrFail().currentDays)
    }

    @Test
    fun twoSessionsOnOneDayAreOneDay() = runBlocking {
        save("s0", at(2026, 8, 15, hour = 8), listOf(played))
        save("s1", at(2026, 8, 15, hour = 20), listOf(played))

        assertEquals(1, store.streak(london, at(2026, 8, 15, hour = 21)).readOrFail().daysPractised)
    }

    /**
     * The streak query reads timestamps only, so the `@TypeConverter` failure that refuses
     * `recent()` cannot take the streak with it. See `.claude/CODE-NOTES.md`.
     */
    @Test
    fun anEnumNameThisBuildCannotReadRefusesTheHistoryButNotTheStreak() = runBlocking {
        save("s0", at(2026, 8, 15), listOf(played))
        database.openHelper.writableDatabase.execSQL("UPDATE sessions SET latencyProvenance = 'Unmeasured'")

        assertTrue(store.recent() is StoredReading.Unreadable)
        assertEquals(1, store.streak(london, at(2026, 8, 15, hour = 21)).readOrFail().currentDays)
    }

    private suspend fun save(id: String, startedAt: Long, judgements: List<NoteJudgement>) =
        store.save(
            sampleSession(id = id).copy(startedAtEpochMillis = startedAt, finishedAtEpochMillis = startedAt + 60_000L),
            judgements,
        )

    private fun at(year: Int, month: Int, day: Int, hour: Int = 19): Long =
        LocalDateTime.of(year, month, day, hour, 0).atZone(london).toInstant().toEpochMilli()
}
