package com.dewijones92.primavista.practice

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private val LONDON: ZoneId = ZoneId.of("Europe/London")
private val SYDNEY: ZoneId = ZoneId.of("Australia/Sydney")
private val TUESDAY: LocalDate = LocalDate.of(2026, 8, 11)

class StreakTest {
    @Test
    fun `nobody who has never finished a session is shown a streak`() {
        assertEquals(Streak.None, Streak.of(emptyList(), LONDON, at(TUESDAY, LocalTime.NOON)))
    }

    @Test
    fun `one session today is a run of one`() {
        val streak = Streak.of(listOf(at(TUESDAY, LocalTime.of(19, 30))), LONDON, at(TUESDAY, LocalTime.of(20, 0)))

        assertEquals(Streak(currentDays = 1, bestDays = 1, daysPractised = 1), streak)
    }

    @Test
    fun `it counts days practised, not sessions finished`() {
        val threeInOneEvening = listOf(
            at(TUESDAY, LocalTime.of(18, 0)),
            at(TUESDAY, LocalTime.of(18, 20)),
            at(TUESDAY, LocalTime.of(21, 5)),
        )

        val streak = Streak.of(threeInOneEvening, LONDON, at(TUESDAY, LocalTime.of(22, 0)))

        assertEquals(1, streak.daysPractised)
        assertEquals(1, streak.currentDays)
    }

    @Test
    fun `consecutive days build the run`() {
        val days = (0L..3L).map { at(TUESDAY.plusDays(it), LocalTime.NOON) }

        val streak = Streak.of(days, LONDON, at(TUESDAY.plusDays(3), LocalTime.of(23, 0)))

        assertEquals(Streak(currentDays = 4, bestDays = 4, daysPractised = 4), streak)
    }

    @Test
    fun `yesterday is still a live run, because the day is not over until it is over`() {
        val days = listOf(at(TUESDAY, LocalTime.NOON), at(TUESDAY.plusDays(1), LocalTime.of(22, 0)))

        val streak = Streak.of(days, LONDON, at(TUESDAY.plusDays(2), LocalTime.of(7, 30)))

        assertEquals(2, streak.currentDays)
    }

    @Test
    fun `a gap simply leaves no run in progress, and the best is still the best`() {
        val days = listOf(
            at(TUESDAY, LocalTime.NOON),
            at(TUESDAY.plusDays(1), LocalTime.NOON),
            at(TUESDAY.plusDays(2), LocalTime.NOON),
            at(TUESDAY.plusDays(9), LocalTime.NOON),
        )

        val streak = Streak.of(days, LONDON, at(TUESDAY.plusDays(20), LocalTime.NOON))

        assertEquals("no shame, no penalty - simply nothing in progress", 0, streak.currentDays)
        assertEquals(3, streak.bestDays)
        assertEquals(4, streak.daysPractised)
    }

    @Test
    fun `the best run is the longest anywhere in the history, not the latest`() {
        val days = listOf(0L, 1L, 2L, 3L, 7L, 8L).map { at(TUESDAY.plusDays(it), LocalTime.NOON) }

        val streak = Streak.of(days, LONDON, at(TUESDAY.plusDays(8), LocalTime.of(23, 30)))

        assertEquals(2, streak.currentDays)
        assertEquals(4, streak.bestDays)
    }

    @Test
    fun `which day a session belonged to is the caller's timezone, never the machine's`() {
        val eitherSideOfMidnight = listOf(
            at(TUESDAY, LocalTime.of(23, 30), LONDON),
            at(TUESDAY.plusDays(1), LocalTime.of(1, 0), LONDON),
        )
        val now = at(TUESDAY.plusDays(1), LocalTime.NOON, LONDON)

        val inLondon = Streak.of(eitherSideOfMidnight, LONDON, now)
        val inSydney = Streak.of(eitherSideOfMidnight, SYDNEY, now)

        assertEquals("two days in London", 2, inLondon.daysPractised)
        assertEquals("one Wednesday morning in Sydney", 1, inSydney.daysPractised)
        assertEquals(2, inLondon.currentDays)
        assertEquals(1, inSydney.currentDays)
    }

    private fun at(date: LocalDate, time: LocalTime, zone: ZoneId = LONDON): Long =
        date.atTime(time).atZone(zone).toInstant().toEpochMilli()
}
