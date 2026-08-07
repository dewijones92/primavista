package com.dewijones92.primavista.pitch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class EnergyOnsetDetectorTest {

    private val sampleRate = Signals.SAMPLE_RATE

    /**
     * The tolerance is one block either way: an onset is reported at the start of the block its
     * rise was seen in, which is early when the attack is sharp and late by up to a block when the
     * attack's first block sits under the silence floor.
     */
    @Test
    fun `four separate notes give four onsets within one block of the truth`() {
        val take = repeatedNotes(count = 4)

        val onsets = EnergyOnsetDetector(sampleRate).push(take.signal, take.signal.size)

        assertEquals("expected ${take.onsetFrames}, got ${onsets.map { it.atFrame }}", 4, onsets.size)
        onsets.forEachIndexed { index, onset ->
            val error = abs(onset.atFrame - take.onsetFrames[index])
            assertTrue(
                "onset ${index + 1} reported at ${onset.atFrame}, truth ${take.onsetFrames[index]} " +
                    "($error frames off, bar: $TOLERANCE_FRAMES)",
                error <= TOLERANCE_FRAMES,
            )
        }
    }

    @Test
    fun `one held note gives one onset`() {
        val signal = Signals.concat(Signals.silence(LEAD_FRAMES), Signals.note(A4, Signals.frames(0.8)))

        val onsets = EnergyOnsetDetector(sampleRate).push(signal, signal.size)

        assertEquals(1, onsets.size)
        assertTrue(abs(onsets.single().atFrame - LEAD_FRAMES) <= TOLERANCE_FRAMES)
    }

    @Test
    fun `silence gives no onsets`() {
        val detector = EnergyOnsetDetector(sampleRate)
        assertEquals(emptyList<NoteOnset>(), detector.push(Signals.silence(Signals.frames(1.0)), Signals.frames(1.0)))
    }

    @Test
    fun `steady noise gives no onsets`() {
        val noise = Signals.whiteNoise(Signals.frames(1.0))
        assertEquals(emptyList<NoteOnset>(), EnergyOnsetDetector(sampleRate).push(noise, noise.size))
    }

    /** Log energy, not energy, is what makes one threshold work for a quiet entry and a loud one. */
    @Test
    fun `a quiet attack is detected on the same threshold as a loud one`() {
        val loud = Signals.concat(Signals.silence(LEAD_FRAMES), Signals.note(A4, Signals.frames(0.2), amplitude = 0.9))
        val quiet = Signals.concat(
            Signals.silence(LEAD_FRAMES),
            Signals.note(A4, Signals.frames(0.2), amplitude = 0.01)
        )

        val loudOnsets = EnergyOnsetDetector(sampleRate).push(loud, loud.size)
        val quietOnsets = EnergyOnsetDetector(sampleRate).push(quiet, quiet.size)

        assertEquals(1, loudOnsets.size)
        assertEquals(1, quietOnsets.size)
        assertEquals(loudOnsets.single().atFrame, quietOnsets.single().atFrame)
    }

    /**
     * The first block of a quiet gradual attack is itself under the silence floor. That block must
     * not consume the rising edge that belongs to the audible blocks behind it, or the note
     * disappears with no diagnostic saying so.
     */
    @Test
    fun `a quiet gradual attack is still an onset`() {
        val signal = Signals.concat(
            Signals.silence(LEAD_FRAMES),
            Signals.note(A4, Signals.frames(0.3), amplitude = 0.003, attackFrames = Signals.frames(0.05)),
        )

        val onsets = EnergyOnsetDetector(sampleRate).push(signal, signal.size)

        assertEquals("a -50 dBFS note with a 50 ms attack was dropped entirely", 1, onsets.size)
        assertTrue(
            "onset ${onsets.single().atFrame} is not within a block of $LEAD_FRAMES",
            abs(onsets.single().atFrame - LEAD_FRAMES) <= TOLERANCE_FRAMES,
        )
    }

    /**
     * The silence gate must not latch the rising edge: a rejected block that consumed it takes the
     * audible note behind it with it. Block-aligned so exactly one inaudible block sits between the
     * silence and the note, which is the case the review found.
     */
    @Test
    fun `a note starting right after a rejected-as-silent block still fires an onset`() {
        val blockFrames = EnergyOnsetDetector.DEFAULT_BLOCK_FRAMES
        val lead = SILENT_BLOCKS * blockFrames
        val signal = Signals.concat(
            Signals.silence(lead),
            Signals.sine(A4, blockFrames, amplitude = INAUDIBLE_AMPLITUDE),
            Signals.note(A4, Signals.frames(0.3)),
        )

        val onsets = EnergyOnsetDetector(sampleRate).push(signal, signal.size)

        assertEquals(
            "the inaudible block consumed the rising edge and swallowed the note behind it",
            1,
            onsets.size,
        )
        assertEquals((lead + blockFrames).toLong(), onsets.single().atFrame)
    }

    @Test
    fun `a rise below the silence floor is not an onset`() {
        val inaudible = Signals.concat(
            Signals.silence(LEAD_FRAMES),
            Signals.note(A4, Signals.frames(0.2), amplitude = 1.0e-5),
        )

        val onsets = EnergyOnsetDetector(sampleRate).push(inaudible, inaudible.size)

        assertEquals(emptyList<NoteOnset>(), onsets)
    }

    @Test
    fun `two attacks inside the minimum inter-onset interval report once`() {
        val signal = twoAttacksThirtyMillisApart()

        val onsets = EnergyOnsetDetector(sampleRate).push(signal, signal.size)

        assertEquals("a 30 ms re-attack must not double-fire: ${onsets.map { it.atFrame }}", 1, onsets.size)
    }

    /** The pair with the interval relaxed, so the test above is known to suppress a real edge. */
    @Test
    fun `the same two attacks report twice once the interval allows it`() {
        val signal = twoAttacksThirtyMillisApart()

        val onsets = EnergyOnsetDetector(sampleRate, minInterOnsetFrames = Signals.frames(0.005))
            .push(signal, signal.size)

        assertEquals("${onsets.map { it.atFrame }}", 2, onsets.size)
        val secondAttack = LEAD_FRAMES + Signals.frames(0.02) + Signals.frames(0.01)
        assertTrue(
            "second onset ${onsets[1].atFrame} is not the second attack at $secondAttack",
            abs(onsets[1].atFrame - secondAttack) <= TOLERANCE_FRAMES,
        )
    }

    private fun twoAttacksThirtyMillisApart(): FloatArray = Signals.concat(
        Signals.silence(LEAD_FRAMES),
        Signals.note(A4, Signals.frames(0.02)),
        Signals.silence(Signals.frames(0.01)),
        Signals.note(A4, Signals.frames(0.2)),
    )

    /**
     * A microphone never delivers the 100 ms of digital silence the other repeated-note fixtures
     * use, so the envelope is never reset by an impossible zero. See `.claude/CODE-NOTES.md`.
     */
    @Test
    fun `repeated notes over a noise floor with decaying tails are still separate onsets`() {
        for (take in decayingTakes()) {
            val onsets = EnergyOnsetDetector(sampleRate).push(take.signal, take.signal.size)

            assertEquals(
                "${take.label}: expected ${take.onsetFrames}, got ${onsets.map { it.atFrame }}",
                take.onsetFrames.size,
                onsets.size,
            )
            onsets.forEachIndexed { index, onset ->
                val error = abs(onset.atFrame - take.onsetFrames[index])
                assertTrue(
                    "${take.label}: onset ${index + 1} at ${onset.atFrame}, truth " +
                        "${take.onsetFrames[index]} ($error frames off, bar: $TOLERANCE_FRAMES)",
                    error <= TOLERANCE_FRAMES,
                )
            }
        }
    }

    /**
     * The detector's real limit, stated as a number rather than implied: below roughly a doubling
     * of level there is nothing to see. Ratios per rise time are in `.claude/CODE-NOTES.md`.
     */
    @Test
    fun `a re-attack that never falls silent needs the level to roughly double`() {
        val gentle = reattack(ratio = GENTLE_REATTACK)
        val doubled = reattack(ratio = AUDIBLE_REATTACK)

        val gentleOnsets = EnergyOnsetDetector(sampleRate).push(gentle, gentle.size)
        val doubledOnsets = EnergyOnsetDetector(sampleRate).push(doubled, doubled.size)

        assertEquals(
            "a ${GENTLE_REATTACK}x re-attack over a sounding note is below what log-energy flux sees",
            emptyList<NoteOnset>(),
            gentleOnsets,
        )
        assertEquals("${doubledOnsets.map { it.atFrame }}", 1, doubledOnsets.size)
        assertTrue(
            "the ${AUDIBLE_REATTACK}x re-attack was located at ${doubledOnsets.single().atFrame}, " +
                "not $REATTACK_FRAME",
            abs(doubledOnsets.single().atFrame - REATTACK_FRAME) <= TOLERANCE_FRAMES,
        )
    }

    @Test
    fun `onset strength is positive and reported`() {
        val signal = Signals.concat(Signals.silence(LEAD_FRAMES), Signals.note(A4, Signals.frames(0.2)))

        val onset = EnergyOnsetDetector(sampleRate).push(signal, signal.size).single()

        assertTrue("strength ${onset.strength}", onset.strength > 0f)
    }

    @Test
    fun `ragged chunk sizes give exactly the same onsets as one push`() {
        val take = repeatedNotes(count = 3)
        val whole = EnergyOnsetDetector(sampleRate).push(take.signal, take.signal.size)

        val chunked = Signals.pushInChunks(take.signal, RAGGED_CHUNKS, EnergyOnsetDetector(sampleRate)::push)

        assertEquals(whole, chunked)
    }

    @Test
    fun `reset clears the envelope so the next take starts fresh`() {
        val detector = EnergyOnsetDetector(sampleRate)
        val take = repeatedNotes(count = 2)
        val first = detector.push(take.signal, take.signal.size)

        detector.reset()
        val second = detector.push(take.signal, take.signal.size)

        assertEquals(first, second)
        assertEquals(2, first.size)
    }

    @Test
    fun `an incoherent configuration is refused at construction`() {
        assertThrows(IllegalArgumentException::class.java) { EnergyOnsetDetector(0) }
        assertThrows(IllegalArgumentException::class.java) { EnergyOnsetDetector(sampleRate, blockFrames = 4) }
        assertThrows(IllegalArgumentException::class.java) { EnergyOnsetDetector(sampleRate, historyBlocks = 0) }
        assertThrows(IllegalArgumentException::class.java) { EnergyOnsetDetector(sampleRate, thresholdMargin = 0.0) }
        assertThrows(
            IllegalArgumentException::class.java
        ) { EnergyOnsetDetector(sampleRate, thresholdMultiplier = -1.0) }
        assertThrows(IllegalArgumentException::class.java) { EnergyOnsetDetector(sampleRate, minInterOnsetFrames = -1) }
        assertThrows(IllegalArgumentException::class.java) { EnergyOnsetDetector(sampleRate, smoothing = 0.0) }
        assertThrows(IllegalArgumentException::class.java) { EnergyOnsetDetector(sampleRate).push(FloatArray(4), 5) }
    }

    private class Take(val signal: FloatArray, val onsetFrames: List<Long>, val label: String = "")

    private fun decayingTakes(): List<Take> = listOf(
        strikesOverNoiseFloor(0.35, Signals.frames(0.12), "slow decay, 0.35 s apart"),
        strikesOverNoiseFloor(0.25, Signals.frames(0.05), "fast decay, 0.25 s apart"),
    )

    private fun strikesOverNoiseFloor(periodSeconds: Double, halfLifeFrames: Int, label: String): Take {
        val period = Signals.frames(periodSeconds)
        val total = LEAD_FRAMES + period * STRIKE_COUNT
        val signal = Signals.noiseFloor(total)
        val onsets = mutableListOf<Long>()
        repeat(STRIKE_COUNT) { index ->
            val at = LEAD_FRAMES + index * period
            onsets += at.toLong()
            Signals.mixInto(
                signal,
                Signals.struck(A4, total - at, amplitude = 0.35, decayHalfLifeFrames = halfLifeFrames),
                at,
            )
        }
        return Take(signal, onsets, label)
    }

    /** One note that never stops, whose level steps up by [ratio] partway through. */
    private fun reattack(ratio: Double): FloatArray {
        val secondFrames = Signals.frames(0.3)
        val signal = Signals.shaped(
            Signals.sine(A4, REATTACK_FRAME + secondFrames, amplitude = SUSTAIN_AMPLITUDE),
        )
        val extra = Signals.sine(A4, secondFrames, amplitude = SUSTAIN_AMPLITUDE * (ratio - 1.0))
        for (n in 0 until minOf(Signals.ATTACK_FRAMES, secondFrames)) {
            extra[n] = extra[n] * n / Signals.ATTACK_FRAMES
        }
        Signals.mixInto(signal, extra, REATTACK_FRAME)
        return signal
    }

    private fun repeatedNotes(count: Int, hertz: Double = A4): Take {
        val noteFrames = Signals.frames(0.2)
        val gapFrames = Signals.frames(0.1)
        val parts = mutableListOf(Signals.silence(LEAD_FRAMES))
        val onsets = mutableListOf<Long>()
        var at = LEAD_FRAMES.toLong()
        repeat(count) {
            onsets += at
            parts += Signals.note(hertz, noteFrames)
            parts += Signals.silence(gapFrames)
            at += noteFrames + gapFrames
        }
        return Take(Signals.concat(*parts.toTypedArray()), onsets)
    }

    private companion object {
        const val A4 = 440.0
        const val TOLERANCE_FRAMES = EnergyOnsetDetector.DEFAULT_BLOCK_FRAMES.toLong()

        /** Under `DEFAULT_SILENCE_RMS`, but a huge log jump up from digital zero. */
        const val INAUDIBLE_AMPLITUDE = 2.0e-5

        const val SILENT_BLOCKS = 10
        const val STRIKE_COUNT = 4
        const val SUSTAIN_AMPLITUDE = 0.08
        const val GENTLE_REATTACK = 1.4
        const val AUDIBLE_REATTACK = 2.0

        val LEAD_FRAMES = Signals.frames(0.05)
        val REATTACK_FRAME = Signals.frames(0.5)
        val RAGGED_CHUNKS = intArrayOf(3, 129, 1000, 17, 4096)
    }
}
