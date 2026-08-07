package com.dewijones92.primavista.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ToneMixerTest {

    @Test
    fun hasNoAnchorBeforeAnythingIsPlayed() {
        assertNull(ToneMixer(RATE).anchorFrame)
    }

    /**
     * Major finding 3. The anchor used to be the written-frame count taken outside the render
     * loop, so it named a buffer the voice had already missed — up to 10.7ms early at 512 frames,
     * and a loopback measured against it over-states input latency by exactly that.
     */
    @Test
    fun theAnchorIsTheBufferTheVoiceActuallyFirstSoundsIn() {
        val mixer = ToneMixer(RATE)
        val buffer = FloatArray(BLOCK)
        repeat(BUFFERS_BEFORE) { mixer.render(buffer) }

        mixer.add(listOf(voice()))
        val anchor = mixer.anchorFrame
        val firstSounding = firstSoundingFrame(mixer, buffer)

        assertEquals(BUFFERS_BEFORE.toLong() * BLOCK, anchor)
        assertNotNull("the voice never sounded at all", firstSounding)
        assertTrue(
            "anchor=$anchor first=$firstSounding: the anchor must not precede the sound",
            firstSounding!! >= anchor!!,
        )
        assertTrue(
            "anchor=$anchor first=$firstSounding: a whole render buffer out is the bug",
            firstSounding < anchor + BLOCK,
        )
    }

    @Test
    fun theBufferBeforeTheAnchorIsStillSilent() {
        val mixer = ToneMixer(RATE)
        val buffer = FloatArray(BLOCK)

        mixer.render(buffer)

        assertTrue("nothing was added yet", buffer.all { it == 0f })
    }

    @Test
    fun eachAddMovesTheAnchorToWhereThatOneStarts() {
        val mixer = ToneMixer(RATE)
        val buffer = FloatArray(BLOCK)

        val first = mixer.add(listOf(voice()))
        mixer.render(buffer)
        val second = mixer.add(listOf(voice()))

        assertEquals(0L, first)
        assertEquals(BLOCK.toLong(), second)
        assertEquals(second, mixer.anchorFrame)
    }

    @Test
    fun countsEveryFrameItRenderedSoTheAnchorStaysAbsolute() {
        val mixer = ToneMixer(RATE)
        val buffer = FloatArray(BLOCK)

        repeat(BUFFERS_BEFORE) { mixer.render(buffer) }

        assertEquals(BUFFERS_BEFORE.toLong() * BLOCK, mixer.framesRendered)
    }

    @Test
    fun sumsVoicesAndHoldsThemUnderTheCeiling() {
        val mixer = ToneMixer(RATE)
        val buffer = FloatArray(BLOCK)
        mixer.add(listOf(loudVoice(), loudVoice()))

        repeat(BUFFERS_PAST_ATTACK) { mixer.render(buffer) }

        assertTrue("nothing sounded", buffer.any { it != 0f })
        assertTrue(
            "a sample escaped the ceiling: ${buffer.maxOf { abs(it) }}",
            buffer.all { abs(it) <= ToneMixer.DEFAULT_CEILING },
        )
        assertTrue("two loud voices should clip", buffer.any { abs(it) == ToneMixer.DEFAULT_CEILING })
    }

    @Test
    fun dropsVoicesOnceTheyHaveFinishedRatherThanMixingSilenceForever() {
        val mixer = ToneMixer(RATE)
        val buffer = FloatArray(BLOCK)
        mixer.add(listOf(ToneVoice(RATE, A4_HERTZ, durationFrames = BLOCK / 2)))

        assertEquals(1, mixer.soundingCount)
        mixer.render(buffer)

        assertEquals(0, mixer.soundingCount)
    }

    @Test
    fun beginReleaseAllRampsEveryVoiceRatherThanCuttingIt() {
        val mixer = ToneMixer(RATE)
        val buffer = FloatArray(BLOCK)
        mixer.add(listOf(voice()))
        repeat(BUFFERS_PAST_ATTACK) { mixer.render(buffer) }
        val steady = buffer.maxOf { abs(it) }

        val ramped = mixer.beginReleaseAll()
        repeat(BUFFERS_INTO_RAMP) { mixer.render(buffer) }
        val fading = buffer.maxOf { abs(it) }

        assertEquals(1, ramped)
        assertTrue("steady=$steady fading=$fading: the ramp must be coming down", fading < steady)
        assertTrue("a cut would be instant silence, a ramp is not", fading > 0f)
    }

    @Test
    fun clearDropsTheAnchorSoAReleasedPlayerCannotReportAStaleOne() {
        val mixer = ToneMixer(RATE)
        mixer.add(listOf(voice()))

        mixer.clear()

        assertNull(mixer.anchorFrame)
        assertEquals(0, mixer.soundingCount)
    }

    @Test
    fun rejectsANonPositiveSampleRate() {
        assertThrows(IllegalArgumentException::class.java) { ToneMixer(0) }
    }

    private fun firstSoundingFrame(mixer: ToneMixer, buffer: FloatArray): Long? {
        repeat(BUFFERS_TO_SEARCH) {
            val base = mixer.framesRendered
            mixer.render(buffer)
            val index = buffer.indexOfFirst { it != 0f }
            if (index >= 0) return base + index
        }
        return null
    }

    private fun voice() = ToneVoice(RATE, A4_HERTZ, durationFrames = RATE)

    private fun loudVoice() = ToneVoice(RATE, A4_HERTZ, durationFrames = RATE, amplitude = 1.0)

    private companion object {
        const val RATE = 48_000
        const val BLOCK = 512
        const val BUFFERS_BEFORE = 3
        const val BUFFERS_TO_SEARCH = 4
        const val BUFFERS_PAST_ATTACK = 3
        const val BUFFERS_INTO_RAMP = 2
        const val A4_HERTZ = 440.0
    }
}
