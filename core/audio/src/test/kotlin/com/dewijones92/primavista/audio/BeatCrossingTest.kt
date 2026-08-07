package com.dewijones92.primavista.audio

import com.dewijones92.primavista.score.MusicalTime
import com.dewijones92.primavista.score.Ticks
import com.dewijones92.primavista.score.TimeSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BeatCrossingTest {

    @Test
    fun beatLengthFollowsTheBeatUnitNotTheQuarter() {
        val cases = listOf(
            TimeSignature(4, 4) to MusicalTime.TICKS_PER_QUARTER,
            TimeSignature(6, 8) to MusicalTime.TICKS_PER_QUARTER / 2,
            TimeSignature(2, 2) to MusicalTime.TICKS_PER_QUARTER * 2,
            TimeSignature(3, 16) to MusicalTime.TICKS_PER_QUARTER / 4,
        )
        for ((time, expected) in cases) {
            assertEquals("${time.beats}/${time.beatUnit}", expected, BeatCrossing(time).beatTicks)
        }
    }

    @Test
    fun firesTheDownbeatOnTheFirstSampleAfterAStart() {
        val beat = BeatCrossing(TimeSignature.FourFour).crossed(Ticks.ZERO)

        assertNotNull(beat)
        assertTrue(beat!!.isAccent)
        assertEquals(0, beat.indexInBar)
    }

    @Test
    fun staysSilentWhenTheFirstSampleLandsWellInsideABeat() {
        val crossing = BeatCrossing(TimeSignature.FourFour)
        val halfWayIntoBeat = Ticks(MusicalTime.TICKS_PER_QUARTER / 2)

        assertNull(crossing.crossed(halfWayIntoBeat))
        assertNotNull(crossing.crossed(Ticks(MusicalTime.TICKS_PER_QUARTER)))
    }

    @Test
    fun firesOncePerBeatNoMatterHowOftenItIsSampled() {
        val crossing = BeatCrossing(TimeSignature.FourFour)
        val quarter = MusicalTime.TICKS_PER_QUARTER
        crossing.crossed(Ticks.ZERO)

        val fired = mutableListOf<Beat>()
        var position = 0L
        while (position <= quarter * BEATS_TO_WALK) {
            crossing.crossed(Ticks(position))?.let { fired += it }
            position += SAMPLE_STRIDE_TICKS
        }

        assertEquals(BEATS_TO_WALK, fired.size)
        assertEquals(listOf(1, 2, 3, 0, 1, 2, 3, 0), fired.map { it.indexInBar })
        assertEquals(2, fired.count { it.isAccent })
    }

    /**
     * Major finding 4. With a one-beat pickup the first bar line is a quarter in, and a grid
     * derived from tick zero accents the pickup note instead — the click lands on the wrong beat
     * for the whole piece.
     */
    @Test
    fun accentsTheRealBarLineOfAPickupBarRatherThanTickZero() {
        val quarter = MusicalTime.TICKS_PER_QUARTER
        val crossing = BeatCrossing(TimeSignature.FourFour, barStart = Ticks(quarter))

        val pickup = crossing.crossed(Ticks.ZERO)
        val barLine = crossing.crossed(Ticks(quarter))

        assertNotNull("the pickup beat still clicks", pickup)
        assertTrue("the pickup note is beat four, not the downbeat", !pickup!!.isAccent)
        assertEquals("a one-beat pickup is the last beat of the implied bar", 3, pickup.indexInBar)
        assertTrue("the accent belongs on the bar line", barLine!!.isAccent)
    }

    @Test
    fun keepsAccentingEveryBarLineAfterAPickup() {
        val quarter = MusicalTime.TICKS_PER_QUARTER
        val crossing = BeatCrossing(TimeSignature.FourFour, barStart = Ticks(quarter))

        val accents = mutableListOf<Long>()
        var position = 0L
        while (position <= quarter * BEATS_TO_WALK) {
            crossing.crossed(Ticks(position))?.let { if (it.isAccent) accents += position }
            position += SAMPLE_STRIDE_TICKS
        }

        assertEquals(listOf(quarter, quarter * 5), accents)
    }

    /** A metre change re-configures with the new bar's start, and beat one lands on it. */
    @Test
    fun accentsTheFirstBeatOfANewMetreAtTheBarItActuallyStartsOn() {
        val quarter = MusicalTime.TICKS_PER_QUARTER
        val changeAt = quarter * BEATS_TO_WALK + quarter / 2
        val crossing = BeatCrossing(TimeSignature(3, 4), barStart = Ticks(changeAt))

        val downbeat = crossing.crossed(Ticks(changeAt))
        val second = crossing.crossed(Ticks(changeAt + quarter))
        val nextBar = crossing.crossed(Ticks(changeAt + quarter * 3))

        assertTrue("$downbeat", downbeat!!.isAccent)
        assertEquals(1, second!!.indexInBar)
        assertTrue("three beats later is the next bar in 3/4: $nextBar", nextBar!!.isAccent)
    }

    @Test
    fun accentsTheFirstBeatOfEachBarDuringANegativeCountIn() {
        val crossing = BeatCrossing(TimeSignature.FourFour)
        val quarter = MusicalTime.TICKS_PER_QUARTER
        val countInStart = Ticks(-quarter * COUNT_IN_BEATS)

        val first = crossing.crossed(countInStart)
        assertNotNull(first)
        assertTrue("a count-in downbeat must accent", first!!.isAccent)

        val second = crossing.crossed(Ticks(-quarter * COUNT_IN_BEATS + quarter))
        assertEquals(1, second!!.indexInBar)
    }

    @Test
    fun isSilentOnABackwardsJumpBecauseASeekIsNotABeat() {
        val crossing = BeatCrossing(TimeSignature.FourFour)
        val quarter = MusicalTime.TICKS_PER_QUARTER
        crossing.crossed(Ticks(quarter * 4))

        assertNull("rewinding must not click", crossing.crossed(Ticks(quarter)))
        assertNotNull("the next real crossing still clicks", crossing.crossed(Ticks(quarter * 2)))
    }

    @Test
    fun resetForgetsThePreviousBeatSoAStartClicksAgain() {
        val crossing = BeatCrossing(TimeSignature.FourFour)
        crossing.crossed(Ticks.ZERO)
        crossing.reset()

        assertNotNull(crossing.crossed(Ticks.ZERO))
    }

    private companion object {
        const val BEATS_TO_WALK = 8
        const val COUNT_IN_BEATS = 4

        /** Divides a quarter exactly, so the walk lands on the last beat boundary. */
        const val SAMPLE_STRIDE_TICKS = 140L
    }
}
