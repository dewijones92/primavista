package com.dewijones92.primavista.ui.progress

import com.dewijones92.primavista.practice.SkillState
import com.dewijones92.primavista.score.Clef
import com.dewijones92.primavista.score.PitchBand
import com.dewijones92.primavista.score.SkillTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressModelTest {

    @Test
    fun `a due skill is due whatever its strength`() {
        val strongButDue = state(strength = 0.95, dueAt = NOW - 1)

        assertEquals(SkillBucket.Due, bucketOf(strongButDue, NOW))
    }

    @Test
    fun `strength alone separates building from solid once nothing is due`() {
        assertEquals(SkillBucket.Building, bucketOf(state(0.4, NOW + HOUR), NOW))
        assertEquals(SkillBucket.Mastered, bucketOf(state(0.9, NOW + HOUR), NOW))
    }

    @Test
    fun `ordering puts what is due first, then the weakest`() {
        val solidDue = state(0.9, NOW - 1, tag = SkillTag.HandIndependence)
        val weakLater = state(0.1, NOW + HOUR)
        val strongLater = state(0.8, NOW + HOUR, tag = SkillTag.Leap(7))

        val order = ordered(listOf(strongLater, weakLater, solidDue), NOW)

        assertEquals(listOf(solidDue, weakLater, strongLater), order)
    }

    @Test
    fun `a due time in the past reads as due now rather than a negative countdown`() {
        assertEquals("due now", relativeDue(state(0.5, NOW - DAY), NOW))
    }

    @Test
    fun `the countdown never rounds down to zero of its own unit`() {
        assertEquals("due in 1m", relativeDue(state(0.5, NOW + 1_000), NOW))
        assertEquals("due in 1h", relativeDue(state(0.5, NOW + HOUR + 1), NOW))
        assertEquals("due in 3d", relativeDue(state(0.5, NOW + 3 * DAY), NOW))
    }

    @Test
    fun `a direction needs enough sessions to compare, and says so when it has not got them`() {
        assertNull(trendOf(listOf(point(0.2), point(0.9))))
        assertTrue(trendText(null).contains("Not enough"))
    }

    @Test
    fun `improving and slipping are read from the sessions themselves`() {
        val rising = listOf(point(0.2), point(0.3), point(0.8), point(0.9))
        val falling = rising.reversed()

        assertTrue(trendOf(rising)!! > 0)
        assertTrue(trendOf(falling)!! < 0)
        assertTrue(trendText(trendOf(rising)).startsWith("Improving"))
        assertTrue(trendText(trendOf(falling)).startsWith("Slipping"))
    }

    @Test
    fun `average strength of nothing is zero rather than a divide by zero`() {
        assertEquals(0.0, readingStrength(emptyList()), 0.0)
        assertEquals(0.5, readingStrength(listOf(state(0.4, NOW), state(0.6, NOW))), 1e-9)
    }

    @Test
    fun `the lifetime line counts sessions, and names lapses only when there were some`() {
        assertEquals("1 session", attemptsText(state(0.5, NOW, attempts = 1)))
        assertTrue(attemptsText(state(0.5, NOW, attempts = 4, lapses = 2)).contains("2 lapses"))
    }

    private fun state(
        strength: Double,
        dueAt: Long,
        attempts: Int = 3,
        lapses: Int = 0,
        tag: SkillTag = SkillTag.ClefRegion(Clef.Bass, PitchBand.BelowStaff),
    ) = SkillState(tag, strength, dueAt, attempts, lapses)

    private fun point(accuracy: Double) = SessionPoint(NOW, accuracy, "piece")

    private companion object {
        const val NOW = 1_754_000_000_000L
        const val HOUR = 3_600_000L
        const val DAY = 24 * HOUR
    }
}
