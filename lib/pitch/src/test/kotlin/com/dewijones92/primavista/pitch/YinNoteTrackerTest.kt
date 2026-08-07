package com.dewijones92.primavista.pitch

import com.dewijones92.primavista.common.Diag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class YinNoteTrackerTest {

    private val sampleRate = Signals.SAMPLE_RATE

    /**
     * The test that proves onset detection is load-bearing rather than decorative: four
     * repetitions of one pitch are four notes, and the same signal read by pitch alone is one.
     */
    @Test
    fun `four separate notes at the same pitch are four tracked notes`() {
        val take = repeatedNotes(count = 4)

        val notes = YinNoteTracker(sampleRate).push(take.signal, take.signal.size)

        assertEquals("got ${notes.map { it.atFrame }}, onsets ${take.onsetFrames}", 4, notes.size)
        notes.forEachIndexed { index, note ->
            val error = abs(centsBetween(Hertz(A4), note.hertz))
            assertTrue(
                "note ${index + 1} read as ${note.hertz.value} Hz ($error cents off)",
                error <= TRACKED_TOLERANCE_CENTS
            )
            assertTrue(
                "note ${index + 1} at ${note.atFrame}, onset ${take.onsetFrames[index]}",
                abs(note.atFrame - take.onsetFrames[index]) <= ONSET_TOLERANCE_FRAMES,
            )
        }
    }

    /**
     * The same property at the level and attack a microphone actually delivers rather than the
     * loud, 3 ms attack a synthesiser does. A quiet gradual attack is the case where an onset is
     * easiest to lose, and losing it turns four repetitions back into one held note. The tolerance
     * is two blocks rather than one because a 50 ms ramp has no instant to be right about: the rise
     * is only visible once it clears the silence floor, which is a block after the ramp began.
     */
    @Test
    fun `four quiet notes with gradual attacks are four tracked notes`() {
        val take = repeatedNotes(count = 4, amplitude = 0.003, attackFrames = Signals.frames(0.05))

        val notes = YinNoteTracker(sampleRate).push(take.signal, take.signal.size)

        assertEquals("got ${notes.map { it.atFrame }}, onsets ${take.onsetFrames}", 4, notes.size)
        notes.forEachIndexed { index, note ->
            assertTrue(
                "note ${index + 1} at ${note.atFrame}, onset ${take.onsetFrames[index]}",
                abs(note.atFrame - take.onsetFrames[index]) <= GRADUAL_ATTACK_TOLERANCE_FRAMES,
            )
        }
    }

    @Test
    fun `without onsets the same four notes collapse into one, which is why onsets exist`() {
        val take = repeatedNotes(count = 4)

        val notes = YinNoteTracker(sampleRate, onsetDetector = DeafOnsetDetector).push(take.signal, take.signal.size)

        assertEquals(1, notes.size)
    }

    @Test
    fun `one held note is one tracked note`() {
        val signal = Signals.concat(Signals.silence(LEAD_FRAMES), Signals.note(A4, Signals.frames(0.8)))

        val notes = YinNoteTracker(sampleRate).push(signal, signal.size)

        assertEquals("got ${notes.map { it.atFrame }}", 1, notes.size)
        assertTrue(abs(notes.single().atFrame - LEAD_FRAMES) <= ONSET_TOLERANCE_FRAMES)
    }

    @Test
    fun `vibrato is one note, not several`() {
        val signal = Signals.concat(
            Signals.silence(LEAD_FRAMES),
            Signals.shaped(Signals.vibrato(A4, VIBRATO_DEPTH_CENTS, VIBRATO_RATE_HERTZ, Signals.frames(0.8))),
        )

        val notes = YinNoteTracker(sampleRate).push(signal, signal.size)

        assertEquals("got ${notes.map { it.hertz.value }}", 1, notes.size)
        val error = abs(centsBetween(Hertz(A4), notes.single().hertz))
        assertTrue(
            "read as ${notes.single().hertz.value} Hz, $error cents from the centre",
            error <= VIBRATO_DEPTH_CENTS
        )
    }

    /**
     * docs/spec.md I2: the note began at the onset and we merely learned its pitch later, so
     * atFrame is the onset and the lateness is reported rather than absorbed.
     */
    @Test
    fun `atFrame is the onset and detectionDelayFrames is the gap to the confirmation`() {
        val signal = Signals.concat(Signals.silence(LEAD_FRAMES), Signals.note(A4, Signals.frames(0.5)))

        val note = YinNoteTracker(sampleRate).push(signal, signal.size).single()

        assertTrue(
            "atFrame ${note.atFrame} is not the onset $LEAD_FRAMES",
            abs(note.atFrame - LEAD_FRAMES) <= ONSET_TOLERANCE_FRAMES
        )
        assertTrue(
            "a pitch cannot be known instantly: delay ${note.detectionDelayFrames}",
            note.detectionDelayFrames > 0
        )
        assertTrue(
            "confirmation ${note.atFrame + note.detectionDelayFrames} is not inside the note",
            note.atFrame + note.detectionDelayFrames < signal.size,
        )
    }

    /**
     * The honesty property, and physics: a window holds fewer cycles of a low note, so a low
     * note's pitch is knowable later. Asserted across several onset offsets so the property is
     * shown rather than a single lucky alignment.
     */
    @Test
    fun `a low note takes longer to identify than a high one`() {
        for (leadSeconds in LEAD_OFFSETS_SECONDS) {
            val low = trackOneNote(LOW_E, leadSeconds)
            val high = trackOneNote(G6, leadSeconds)

            assertTrue(
                "lead=${leadSeconds}s: low E delay ${low.detectionDelayFrames} should exceed " +
                    "G6 delay ${high.detectionDelayFrames}",
                low.detectionDelayFrames > high.detectionDelayFrames,
            )
        }
    }

    @Test
    fun `a slurred pitch change with no attack is still a new note`() {
        val signal = legato()

        val notes = YinNoteTracker(sampleRate).push(signal, signal.size)

        assertEquals("got ${notes.map { it.hertz.value }}", 2, notes.size)
        assertTrue(abs(centsBetween(Hertz(A4), notes[0].hertz)) <= TRACKED_TOLERANCE_CENTS)
        assertTrue(abs(centsBetween(Hertz(C5), notes[1].hertz)) <= TRACKED_TOLERANCE_CENTS)
    }

    @Test
    fun `the pitch-change rule alone finds both notes of a slur`() {
        val signal = legato()

        val notes = YinNoteTracker(sampleRate, onsetDetector = DeafOnsetDetector).push(signal, signal.size)

        assertEquals("got ${notes.map { it.hertz.value }}", 2, notes.size)
        assertTrue(abs(centsBetween(Hertz(C5), notes[1].hertz)) <= TRACKED_TOLERANCE_CENTS)
    }

    /**
     * docs/spec.md I2: the same instant must not be reported differently because the player slurred
     * rather than tongued. Swept, so it is a property rather than one lucky alignment. The bar and
     * the measured residuals are in `.claude/CODE-NOTES.md`.
     */
    @Test
    fun `a legato pitch change and a struck note of the same onset are timed alike`() {
        for (changeSeconds in CHANGE_OFFSETS_SECONDS) {
            val change = Signals.frames(changeSeconds)

            val slurred = YinNoteTracker(sampleRate).let { it.push(slurInto(change), slurInto(change).size) }
            val struck = YinNoteTracker(sampleRate).let { it.push(strikeInto(change), strikeInto(change).size) }

            assertEquals("slur at $change: ${slurred.map { it.hertz.value }}", 2, slurred.size)
            assertEquals("strike at $change: ${struck.map { it.hertz.value }}", 2, struck.size)
            val slurAt = slurred[1].atFrame
            val strikeAt = struck[1].atFrame
            assertTrue(
                "the same C5 at frame $change was slurred at $slurAt but struck at $strikeAt " +
                    "(${abs(slurAt - strikeAt)} frames apart, bar: $PATH_AGREEMENT_FRAMES)",
                abs(slurAt - strikeAt) <= PATH_AGREEMENT_FRAMES,
            )
            assertTrue(
                "slurred C5 at $slurAt is not within $PATH_AGREEMENT_FRAMES of the truth $change",
                abs(slurAt - change) <= PATH_AGREEMENT_FRAMES,
            )
            assertTrue(
                "struck C5 at $strikeAt is not within $PATH_AGREEMENT_FRAMES of the truth $change",
                abs(strikeAt - change) <= PATH_AGREEMENT_FRAMES,
            )
        }
    }

    /**
     * The fixtures above separate notes with digital silence, which a microphone never produces.
     * Here each attack has to be found against a decaying tail rather than against nothing.
     */
    @Test
    fun `repeated strikes over a noise floor with decaying tails are still separate notes`() {
        for (take in decayingTakes()) {
            val notes = YinNoteTracker(sampleRate).push(take.signal, take.signal.size)

            assertEquals(
                "${take.label}: got ${notes.map { it.atFrame }}, onsets ${take.onsetFrames}",
                take.onsetFrames.size,
                notes.size,
            )
            notes.forEachIndexed { index, note ->
                assertTrue(
                    "${take.label}: note ${index + 1} read as ${note.hertz.value} Hz",
                    abs(centsBetween(Hertz(A4), note.hertz)) <= TRACKED_TOLERANCE_CENTS,
                )
                assertTrue(
                    "${take.label}: note ${index + 1} at ${note.atFrame}, onset ${take.onsetFrames[index]}",
                    abs(note.atFrame - take.onsetFrames[index]) <= ONSET_TOLERANCE_FRAMES,
                )
            }
        }
    }

    /**
     * The hardest repetition: same pitch, no gap, so the energy step is the only evidence. Paired
     * with the ratio the detector cannot see, so it cannot pass by becoming trigger-happy.
     */
    @Test
    fun `a re-attack that never falls silent is a second note, below its ratio is not`() {
        val audible = reattack(AUDIBLE_REATTACK)
        val gentle = reattack(GENTLE_REATTACK)

        val audibleNotes = YinNoteTracker(sampleRate).push(audible, audible.size)
        val gentleNotes = YinNoteTracker(sampleRate).push(gentle, gentle.size)

        assertEquals("got ${audibleNotes.map { it.atFrame }}", 2, audibleNotes.size)
        assertTrue(
            "the re-attack was placed at ${audibleNotes[1].atFrame}, not $REATTACK_FRAME",
            abs(audibleNotes[1].atFrame - REATTACK_FRAME) <= ONSET_TOLERANCE_FRAMES,
        )
        assertEquals(
            "a ${GENTLE_REATTACK}x re-attack is under the detector's limit and must stay one note",
            1,
            gentleNotes.size,
        )
    }

    /**
     * Two paths with different corrections means a report has to say which one produced a note and
     * what it was corrected from, or a timing complaint from Dewi's phone cannot be re-derived.
     */
    @Test
    fun `the diagnostic names the emission path and the frame before correction`() {
        val diag = RecordingDiag()
        val signal = slurInto(Signals.frames(0.4))

        YinNoteTracker(sampleRate, diag = diag).push(signal, signal.size)

        val emitted = diag.events.filter { it.contains(" note hz=") }
        assertEquals(diag.events.toString(), 2, emitted.size)
        assertTrue(emitted.last(), emitted.last().contains("src=pitchChange"))
        assertTrue(
            "without rawFrame a report cannot re-derive the correction: $emitted",
            emitted.all { it.contains("rawFrame=") },
        )
    }

    @Test
    fun `silence and noise produce no notes`() {
        val silence = Signals.silence(Signals.frames(0.5))
        assertEquals(emptyList<TrackedNote>(), YinNoteTracker(sampleRate).push(silence, silence.size))

        val noise = Signals.concat(Signals.silence(LEAD_FRAMES), Signals.whiteNoise(Signals.frames(0.5)))
        assertEquals(emptyList<TrackedNote>(), YinNoteTracker(sampleRate).push(noise, noise.size))
    }

    @Test
    fun `a percussive click is an onset but not a note`() {
        val signal = Signals.concat(
            Signals.silence(LEAD_FRAMES),
            Signals.whiteNoise(CLICK_FRAMES, amplitude = 0.6),
            Signals.silence(Signals.frames(0.4)),
        )

        assertTrue(EnergyOnsetDetector(sampleRate).push(signal, signal.size).isNotEmpty())
        assertEquals(emptyList<TrackedNote>(), YinNoteTracker(sampleRate).push(signal, signal.size))
    }

    @Test
    fun `estimates below the confidence floor never become a note`() {
        val estimates = scriptedEstimates(confidence = 0.2f)

        val notes = trackerOver(estimates).push(FloatArray(SCRIPT_FRAMES), SCRIPT_FRAMES)

        assertEquals(emptyList<TrackedNote>(), notes)
    }

    @Test
    fun `the same estimates above the confidence floor do become a note`() {
        val estimates = scriptedEstimates(confidence = 0.9f)

        val notes = trackerOver(estimates).push(FloatArray(SCRIPT_FRAMES), SCRIPT_FRAMES)

        assertEquals(1, notes.size)
        assertEquals(FAKE_ONSET_FRAME, notes.single().atFrame)
        assertEquals((estimates.last().atFrame - FAKE_ONSET_FRAME).toInt(), notes.single().detectionDelayFrames)
    }

    /** An onset that never got a pitch must not be glued onto whatever is played next. */
    @Test
    fun `a stale onset is abandoned rather than credited to a much later note`() {
        val late = Signals.frames(1.0)
        val estimates = List(3) {
            DetectedPitch(Hertz(A4), 0.9f, late + it * HOP_FRAMES.toLong())
        }

        val frames = late + Signals.frames(0.2)
        val notes = trackerOver(estimates).push(FloatArray(frames), frames)

        assertEquals(1, notes.size)
        assertEquals(
            "the note must start where its pitch began, not at the stale onset",
            late.toLong() - SCRIPT_WINDOW_FRAMES / 2,
            notes.single().atFrame
        )
    }

    @Test
    fun `a second onset supersedes an unconfirmed first one`() {
        val signal = Signals.concat(
            Signals.silence(LEAD_FRAMES),
            Signals.whiteNoise(CLICK_FRAMES, amplitude = 0.6),
            Signals.silence(Signals.frames(0.06)),
            Signals.whiteNoise(CLICK_FRAMES, amplitude = 0.6),
            Signals.silence(Signals.frames(0.06)),
            Signals.note(A4, Signals.frames(0.4)),
        )

        val notes = YinNoteTracker(sampleRate).push(signal, signal.size)

        assertEquals("two clicks then a note is one note: ${notes.map { it.atFrame }}", 1, notes.size)
        assertTrue(abs(centsBetween(Hertz(A4), notes.single().hertz)) <= TRACKED_TOLERANCE_CENTS)
        val afterBothClicks = (LEAD_FRAMES + CLICK_FRAMES + Signals.frames(0.06) + CLICK_FRAMES).toLong()
        assertTrue(
            "the note was credited to an earlier click at ${notes.single().atFrame}, not its own onset",
            notes.single().atFrame >= afterBothClicks,
        )
    }

    @Test
    fun `ragged chunk sizes give the same notes as one push`() {
        val take = repeatedNotes(count = 3)
        val whole = YinNoteTracker(sampleRate).push(take.signal, take.signal.size)

        val chunked = Signals.pushInChunks(take.signal, RAGGED_CHUNKS, YinNoteTracker(sampleRate)::push)

        assertEquals(whole.map { it.atFrame }, chunked.map { it.atFrame })
        assertEquals(whole.size, chunked.size)
    }

    @Test
    fun `reset clears the sounding pitch and both detectors`() {
        val tracker = YinNoteTracker(sampleRate)
        val take = repeatedNotes(count = 2)
        val first = tracker.push(take.signal, take.signal.size)

        tracker.reset()
        val second = tracker.push(take.signal, take.signal.size)

        assertEquals(first, second)
        assertEquals(2, first.size)
    }

    @Test
    fun `an incoherent configuration is refused at construction`() {
        assertThrows(IllegalArgumentException::class.java) { YinNoteTracker(0) }
        assertThrows(IllegalArgumentException::class.java) { YinNoteTracker(sampleRate, stableEstimates = 0) }
        assertThrows(IllegalArgumentException::class.java) { YinNoteTracker(sampleRate, stabilityCents = 0.0) }
        assertThrows(IllegalArgumentException::class.java) { YinNoteTracker(sampleRate, pitchChangeCents = 10.0) }
        assertThrows(IllegalArgumentException::class.java) { YinNoteTracker(sampleRate, confidenceFloor = 2f) }
        assertThrows(IllegalArgumentException::class.java) { YinNoteTracker(sampleRate, onsetGraceFrames = -1) }
        assertThrows(IllegalArgumentException::class.java) { YinNoteTracker(sampleRate, confirmWithinFrames = 0) }
    }

    private fun trackOneNote(hertz: Double, leadSeconds: Double): TrackedNote {
        val signal = Signals.concat(
            Signals.silence(Signals.frames(leadSeconds)),
            Signals.note(hertz, Signals.frames(0.5)),
            Signals.silence(Signals.frames(0.05)),
        )
        val notes = YinNoteTracker(sampleRate).push(signal, signal.size)
        assertEquals("expected exactly one note at $hertz Hz, lead ${leadSeconds}s", 1, notes.size)
        return notes.single()
    }

    /** A4 gliding into C5 with no energy edge: only the pitch-change rule can find the second note. */
    private fun slurInto(changeFrame: Int): FloatArray = Signals.shaped(
        Signals.concat(Signals.sine(A4, changeFrame), Signals.sine(C5, Signals.frames(0.5))),
    )

    /** The same C5 at the same frame, but tongued, so the onset path finds it instead. */
    private fun strikeInto(changeFrame: Int): FloatArray = Signals.concat(
        Signals.shaped(Signals.sine(A4, changeFrame)),
        Signals.struck(C5, Signals.frames(0.5), amplitude = STRUCK_AMPLITUDE),
    )

    private class DecayingTake(val signal: FloatArray, val onsetFrames: List<Long>, val label: String)

    private fun decayingTakes(): List<DecayingTake> = listOf(
        strikesOverNoiseFloor(0.35, Signals.frames(0.12), "slow decay, 0.35 s apart"),
        strikesOverNoiseFloor(0.25, Signals.frames(0.05), "fast decay, 0.25 s apart"),
    )

    private fun strikesOverNoiseFloor(
        periodSeconds: Double,
        halfLifeFrames: Int,
        label: String,
    ): DecayingTake {
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
        return DecayingTake(signal, onsets, label)
    }

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

    private fun legato(): FloatArray = Signals.shaped(
        Signals.concat(
            Signals.silence(LEAD_FRAMES),
            Signals.sine(A4, Signals.frames(0.3)),
            Signals.sine(C5, Signals.frames(0.3)),
        ),
    )

    private fun scriptedEstimates(confidence: Float): List<DetectedPitch> =
        List(3) { DetectedPitch(Hertz(A4), confidence, FIRST_ESTIMATE_FRAME + it * HOP_FRAMES.toLong()) }

    private fun trackerOver(estimates: List<DetectedPitch>) = YinNoteTracker(
        sampleRate = sampleRate,
        pitchDetector = ScriptedPitchDetector(sampleRate, estimates),
        onsetDetector = FixedOnsetDetector(listOf(FAKE_ONSET_FRAME)),
    )

    private class Take(val signal: FloatArray, val onsetFrames: List<Long>)

    private fun repeatedNotes(
        count: Int,
        amplitude: Double = 0.3,
        attackFrames: Int = Signals.ATTACK_FRAMES,
    ): Take {
        val noteFrames = Signals.frames(0.2)
        val gapFrames = Signals.frames(0.1)
        val parts = mutableListOf(Signals.silence(LEAD_FRAMES))
        val onsets = mutableListOf<Long>()
        var at = LEAD_FRAMES.toLong()
        repeat(count) {
            onsets += at
            parts += Signals.note(A4, noteFrames, amplitude = amplitude, attackFrames = attackFrames)
            parts += Signals.silence(gapFrames)
            at += noteFrames + gapFrames
        }
        return Take(Signals.concat(*parts.toTypedArray()), onsets)
    }

    /** Emits pre-scripted estimates when the stream reaches their frame; no DSP involved. */
    private class ScriptedPitchDetector(
        override val sampleRate: Int,
        private val script: List<DetectedPitch>,
        override val windowFrames: Int = SCRIPT_WINDOW_FRAMES,
        override val hopFrames: Int = HOP_FRAMES,
    ) : PitchDetector {
        private var consumed = 0L

        override fun push(pcm: FloatArray, frames: Int): List<DetectedPitch> {
            val from = consumed
            consumed += frames
            return script.filter { it.atFrame >= from && it.atFrame < consumed }
        }

        override fun reset() {
            consumed = 0L
        }
    }

    /** Exact frames, so `hopFrames = 1`: nothing to centre, and the arithmetic stays readable. */
    private class FixedOnsetDetector(private val frames: List<Long>) : OnsetDetector {
        override val sampleRate: Int = Signals.SAMPLE_RATE
        override val hopFrames: Int = 1
        private var consumed = 0L

        override fun push(pcm: FloatArray, frames: Int): List<NoteOnset> {
            val from = consumed
            consumed += frames
            return this.frames.filter { it >= from && it < consumed }.map { NoteOnset(it, 1f) }
        }

        override fun reset() {
            consumed = 0L
        }
    }

    private class RecordingDiag : Diag {
        val events = mutableListOf<String>()
        override fun event(tag: String, message: String) {
            events += "$tag $message"
        }

        override fun counted(tag: String, key: String, increment: Int): Unit = Unit
        override fun state(tag: String, snapshot: () -> String): Unit = Unit
        override fun report(header: Map<String, String>): String = events.joinToString("\n")
    }

    private object DeafOnsetDetector : OnsetDetector {
        override val sampleRate: Int = Signals.SAMPLE_RATE
        override val hopFrames: Int = 1
        override fun push(pcm: FloatArray, frames: Int): List<NoteOnset> = emptyList()
        override fun reset(): Unit = Unit
    }

    private companion object {
        const val A4 = 440.0
        const val C5 = 523.25
        const val LOW_E = 82.41
        const val G6 = 1567.98
        const val HOP_FRAMES = 512
        const val SCRIPT_WINDOW_FRAMES = 2048
        const val TRACKED_TOLERANCE_CENTS = 10.0
        const val VIBRATO_DEPTH_CENTS = 30.0
        const val VIBRATO_RATE_HERTZ = 5.0
        const val CLICK_FRAMES = 300
        const val FAKE_ONSET_FRAME = 1000L
        const val FIRST_ESTIMATE_FRAME = 2000L
        const val STRIKE_COUNT = 4
        const val STRUCK_AMPLITUDE = 0.5
        const val SUSTAIN_AMPLITUDE = 0.08
        const val GENTLE_REATTACK = 1.4
        const val AUDIBLE_REATTACK = 2.0

        /** One pitch-analysis hop: the coarser of the two paths' quanta, so the floor on agreement. */
        val PATH_AGREEMENT_FRAMES = YinPitchDetector.DEFAULT_HOP_FRAMES.toLong()

        val ONSET_TOLERANCE_FRAMES = EnergyOnsetDetector.DEFAULT_BLOCK_FRAMES.toLong()
        val GRADUAL_ATTACK_TOLERANCE_FRAMES = 2 * ONSET_TOLERANCE_FRAMES
        val LEAD_FRAMES = Signals.frames(0.05)
        val REATTACK_FRAME = Signals.frames(0.5)
        val SCRIPT_FRAMES = Signals.frames(0.2)
        val RAGGED_CHUNKS = intArrayOf(5, 1024, 33, 2731, 128)
        val LEAD_OFFSETS_SECONDS = doubleArrayOf(0.030, 0.041, 0.050, 0.063, 0.077, 0.090)
        val CHANGE_OFFSETS_SECONDS = doubleArrayOf(0.300, 0.337, 0.375, 0.400, 0.451, 0.500)
    }
}
