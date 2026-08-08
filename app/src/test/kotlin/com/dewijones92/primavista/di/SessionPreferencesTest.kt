package com.dewijones92.primavista.di

import com.dewijones92.primavista.database.PracticeSettings
import com.dewijones92.primavista.practice.KeyboardTapSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The stored tempo is a ceiling. These are the three cases that separate a ceiling from an
 * override, and getting them wrong would speed a piece up on Dewi's behalf.
 */
class SessionPreferencesTest {

    @Test
    fun `a piece written faster than the ceiling is slowed to it`() {
        assertEquals(72, sessionTempoBpm(writtenBpm = 120, ceilingBpm = 72))
    }

    @Test
    fun `a piece written slower than the ceiling keeps its own tempo`() {
        assertEquals(50, sessionTempoBpm(writtenBpm = 50, ceilingBpm = 72))
    }

    @Test
    fun `the ceiling never speeds anything up`() {
        (40..200).forEach { ceiling ->
            (40..200).forEach { written ->
                assertTrue(
                    "written=$written ceiling=$ceiling was sped up",
                    sessionTempoBpm(written, ceiling) <= written,
                )
            }
        }
    }

    @Test
    fun `an unchosen input opens on the keyboard`() {
        val opening = openingInput(PracticeSettings(inputLabel = null), micGranted = true)

        assertEquals(InputMode.Tap, opening.mode)
        assertFalse(opening.revoked)
    }

    @Test
    fun `the stored mic input opens on the mic when the permission is still granted`() {
        val opening = openingInput(PracticeSettings(inputLabel = "mic"), micGranted = true)

        assertEquals(InputMode.Mic, opening.mode)
        assertFalse(opening.revoked)
    }

    @Test
    fun `a revoked permission opens on the keyboard and says the preference was not honoured`() {
        val opening = openingInput(PracticeSettings(inputLabel = "mic"), micGranted = false)

        assertEquals(InputMode.Tap, opening.mode)
        assertTrue("a session opening on TAP against the stored MIC must say so", opening.revoked)
    }

    @Test
    fun `a label nothing recognises is not silently taken for one that is`() {
        assertNull(InputMode.of("midi"))
        assertNull(InputMode.of(null))
    }

    /** The stored label is an `AnswerSource.label`, so the two must agree — see `.claude/CODE-NOTES.md`. */
    @Test
    fun `the tap mode's label is the label the tap source actually reports`() {
        assertEquals(KeyboardTapSource().label, InputMode.Tap.label)
    }

    @Test
    fun `every mode round-trips through the label it is stored as`() {
        InputMode.entries.forEach { mode -> assertEquals(mode, InputMode.of(mode.label)) }
    }
}
