package com.dewijones92.primavista.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dewijones92.primavista.score.Clef
import com.dewijones92.primavista.score.NoteSymbol
import com.dewijones92.primavista.score.PitchBand
import com.dewijones92.primavista.score.Polyphony
import com.dewijones92.primavista.score.ScoreId
import com.dewijones92.primavista.score.ScoreSummary
import com.dewijones92.primavista.score.SkillTag
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepertoireStoreTest {
    private lateinit var database: PrimaVistaDatabase
    private lateinit var store: RepertoireStore

    @Before
    fun setUp() {
        database = openTestDatabase()
        store = RoomRepertoireStore(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun entry(id: String, title: String): RepertoireEntry = RepertoireEntry(
        summary = ScoreSummary(
            id = ScoreId(id),
            title = title,
            composer = "J. S. Bach",
            polyphony = Polyphony.Poly,
            skills = setOf(
                SkillTag.ClefRegion(Clef.Treble, PitchBand.MiddleStaff),
                SkillTag.LegerLines(Clef.Bass, 1, above = false),
                SkillTag.RhythmFigure(NoteSymbol.Eighth, dots = 0, tupletNumerator = 1),
                SkillTag.HandIndependence,
            ),
            bars = 32,
            defaultTempoBpm = 84,
        ),
        licence = "Public Domain",
        source = "imslp",
        addedAtEpochMillis = 1_700_000_000_000L,
    )

    @Test
    fun anEntryRoundTripsIncludingItsSkillSet() = runBlocking {
        val entry = entry("bwv-anh-114", "Minuet in G")
        store.upsert(entry)

        assertEquals(entry, store.all().single())
    }

    @Test
    fun summariesAreExactlyWhatTheSchedulerNeeds() = runBlocking {
        store.upsert(entry("b", "Second"))
        store.upsert(entry("a", "First"))

        assertEquals(listOf("First", "Second"), store.summaries().map { it.title })
    }

    @Test
    fun upsertingTheSameScoreReplacesIt() = runBlocking {
        store.upsert(entry("bwv-anh-114", "Minuet in G"))
        store.upsert(entry("bwv-anh-114", "Menuet in G major"))

        assertEquals(listOf("Menuet in G major"), store.all().map { it.summary.title })
    }

    @Test
    fun forgettingAScoreRemovesIt() = runBlocking {
        store.upsert(entry("bwv-anh-114", "Minuet in G"))

        store.forget(ScoreId("bwv-anh-114"))

        assertTrue(store.all().isEmpty())
    }
}
