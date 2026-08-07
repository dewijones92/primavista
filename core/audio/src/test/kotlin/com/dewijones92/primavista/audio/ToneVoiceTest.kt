package com.dewijones92.primavista.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ToneVoiceTest {

    @Test
    fun startsAtSilenceSoTheAttackDoesNotClick() {
        val rendered = render(voice(), FRAMES)

        assertEquals(0f, rendered[0], 0f)
        val afterAttack = rendered.copyOfRange(ATTACK_PROBE_FRAME, ATTACK_PROBE_FRAME + SHORT_BUFFER)
        assertTrue("the attack must actually rise", afterAttack.maxOf { abs(it) } > AUDIBLE_PEAK)
    }

    @Test
    fun endsAtSilenceSoTheReleaseDoesNotClick() {
        val rendered = render(voice(), FRAMES)
        val peak = rendered.maxOf { abs(it) }

        assertTrue("peak should be audible, was $peak", peak > AUDIBLE_PEAK)
        assertTrue(
            "last sample ${rendered[FRAMES - 1]} should be near zero against peak $peak",
            abs(rendered[FRAMES - 1]) < peak * TAIL_FRACTION,
        )
    }

    @Test
    fun neverExceedsFullScale() {
        val rendered = render(voice(), FRAMES)

        assertTrue("peak ${rendered.maxOf { abs(it) }} clipped", rendered.all { abs(it) <= 1f })
    }

    @Test
    fun finishesExactlyAtItsDurationAndThenAddsNothing() {
        val subject = voice()
        val buffer = FloatArray(FRAMES)
        subject.mixInto(buffer, FRAMES)

        assertTrue(subject.isFinished)
        assertEquals(0, subject.framesRemaining)

        val after = FloatArray(SHORT_BUFFER)
        subject.mixInto(after, SHORT_BUFFER)
        assertTrue("a finished voice must be silent", after.all { it == 0f })
    }

    @Test
    fun beginReleaseRampsRatherThanCuttingTheNote() {
        val subject = voice(durationFrames = LONG_FRAMES)
        subject.mixInto(FloatArray(SHORT_BUFFER), SHORT_BUFFER)
        subject.beginRelease()

        val remaining = subject.framesRemaining
        assertTrue("release should still render frames, got $remaining", remaining > 0)
        assertTrue("release should be short, got $remaining", remaining <= releaseFrames() + 1)
        assertFalse(subject.isFinished)
    }

    @Test
    fun mixesAdditivelySoAChordIsOneBuffer() {
        val buffer = FloatArray(SHORT_BUFFER)
        voice(frequencyHertz = A4).mixInto(buffer, SHORT_BUFFER)
        val single = buffer.copyOf()
        voice(frequencyHertz = E5).mixInto(buffer, SHORT_BUFFER)

        assertNotEquals(
            "a second voice must change the buffer",
            single.toList(),
            buffer.toList(),
        )
    }

    @Test
    fun mutesPartialsAboveNyquistSoNothingAliases() {
        val nearNyquist = ToneVoice(LOW_RATE, LOW_RATE / 3.0, FRAMES)
        val rendered = render(nearNyquist, FRAMES)

        assertTrue("the fundamental should still sound", rendered.maxOf { abs(it) } > 0f)
        assertTrue(rendered.all { abs(it) <= 1f })
    }

    @Test
    fun rejectsImpossibleVoices() {
        assertThrows(IllegalArgumentException::class.java) { ToneVoice(0, A4, FRAMES) }
        assertThrows(IllegalArgumentException::class.java) { ToneVoice(RATE, 0.0, FRAMES) }
        assertThrows(IllegalArgumentException::class.java) { ToneVoice(RATE, A4, 0) }
    }

    private fun voice(
        frequencyHertz: Double = A4,
        durationFrames: Int = FRAMES,
    ) = ToneVoice(RATE, frequencyHertz, durationFrames)

    private fun render(subject: ToneVoice, frames: Int): FloatArray {
        val buffer = FloatArray(frames)
        subject.mixInto(buffer, frames)
        return buffer
    }

    private fun releaseFrames() = (ToneVoice.RELEASE_SECONDS * RATE).toInt()

    private companion object {
        const val RATE = 48_000
        const val LOW_RATE = 8_000
        const val A4 = 440.0
        const val E5 = 659.255
        const val FRAMES = 12_000
        const val LONG_FRAMES = 48_000
        const val SHORT_BUFFER = 256
        const val ATTACK_PROBE_FRAME = 400
        const val AUDIBLE_PEAK = 0.05f
        const val TAIL_FRACTION = 0.05f
    }
}
