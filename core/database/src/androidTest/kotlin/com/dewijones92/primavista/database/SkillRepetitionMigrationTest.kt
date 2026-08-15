package com.dewijones92.primavista.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dewijones92.primavista.practice.SkillOutcome
import com.dewijones92.primavista.practice.SkillState
import com.dewijones92.primavista.score.Clef
import com.dewijones92.primavista.score.SkillTag
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The SM-2 rung on the real file: it used to reset to 0 on every reload, which hid a just-failed
 * skill for about ten days (docs/spec.md I5). See `.claude/CODE-NOTES.md`.
 */
@RunWith(AndroidJUnit4::class)
class SkillRepetitionMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val ledgerLines = SkillTag.LegerLines(Clef.Bass, 2, above = false)
    private val keepStates = SkillUpdateRule { states, _, _ -> states }

    @Before
    fun setUp() {
        context.deleteDatabase(PrimaVistaDatabase.FILE_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(PrimaVistaDatabase.FILE_NAME)
    }

    /** A v1 file is what is actually on Dewi's phone, so the upgrade is run against one. */
    @Test
    fun aVersionOneSkillRowKeepsItsStrengthAndStartsOnTheBottomRung() = runBlocking {
        createDatabaseAtVersion(context, PrimaVistaDatabase.FILE_NAME, version = 1) {
            execSQL(
                "INSERT INTO skill_states (skillKey, strength, dueAtEpochMillis, attempts, lapses) " +
                    "VALUES (?, ?, ?, ?, ?)",
                arrayOf<Any>(SkillTagKeys.encode(ledgerLines), 0.8, 1_700_000_000_000L, 40, 3),
            )
        }

        val reloaded = opened().use { RoomSkillStore(it, keepStates).states().single() }

        assertEquals(ledgerLines, reloaded.tag)
        assertEquals(0.8, reloaded.strength, 1e-9)
        assertEquals(1_700_000_000_000L, reloaded.dueAtEpochMillis)
        assertEquals(40, reloaded.attempts)
        assertEquals(3, reloaded.lapses)
        assertEquals(0, reloaded.repetition)
    }

    @Test
    fun theStoredRungSurvivesTheDatabaseBeingClosedAndReopened() = runBlocking {
        val mature = SkillState(
            ledgerLines,
            strength = 0.9,
            dueAtEpochMillis = 2_000L,
            attempts = 6,
            lapses = 1,
            repetition = 4
        )
        opened().use {
            RoomSkillStore(it, SkillUpdateRule { _, _, _ -> listOf(mature) })
                .record(listOf(SkillOutcome(ledgerLines, attempts = 1, cleanAttempts = 1)), nowEpochMillis = 0L)
        }

        val reloaded = opened().use { RoomSkillStore(it, keepStates).states().single() }

        assertEquals(mature, reloaded)
    }

    private fun opened(): PrimaVistaDatabase = openRealDatabase(context)
}
