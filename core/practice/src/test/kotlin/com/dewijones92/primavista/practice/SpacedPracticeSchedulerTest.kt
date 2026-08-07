package com.dewijones92.primavista.practice

import com.dewijones92.primavista.score.Alter
import com.dewijones92.primavista.score.Clef
import com.dewijones92.primavista.score.PitchBand
import com.dewijones92.primavista.score.Polyphony
import com.dewijones92.primavista.score.ScoreId
import com.dewijones92.primavista.score.SeededExerciseGenerator
import com.dewijones92.primavista.score.SkillTag
import com.dewijones92.primavista.score.Staff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val HOUR_MILLIS = 60L * 60L * 1000L
private const val DAY_MILLIS = 24L * HOUR_MILLIS
private const val NOW = 1_700_000_000_000L

private val bassLedger = SkillTag.LegerLines(Clef.Bass, count = 2, above = false)
private val bassMiddle = SkillTag.ClefRegion(Clef.Bass, PitchBand.MiddleStaff)

class SpacedPracticeSchedulerTest {
    private val generator = SeededExerciseGenerator()
    private val scheduler = SpacedPracticeScheduler(generator::specTargeting)

    @Test
    fun `a clean attempt raises strength and pushes the due date out`() {
        val before = state(trebleMiddle, strength = 0.2, dueAtEpochMillis = NOW, attempts = 0)

        val after = scheduler
            .update(listOf(before), listOf(SkillOutcome(trebleMiddle, attempts = 10, cleanAttempts = 10)), NOW)
            .single()

        assertEquals(0.52, after.strength, 1e-9)
        assertEquals(NOW + 4 * HOUR_MILLIS, after.dueAtEpochMillis)
        assertEquals(1, after.attempts)
        assertEquals(0, after.lapses)
        assertEquals(1, after.repetition)
        assertFalse(after.isDue(NOW))
    }

    @Test
    fun `a lapse drops strength hard and brings the due date to soon`() {
        val before = state(trebleMiddle, strength = 0.8, dueAtEpochMillis = NOW, attempts = 3, lapses = 0)

        val after = scheduler
            .update(listOf(before), listOf(SkillOutcome(trebleMiddle, attempts = 10, cleanAttempts = 3)), NOW)
            .single()

        assertEquals(0.28, after.strength, 1e-9)
        assertEquals(NOW + 10 * 60 * 1000L, after.dueAtEpochMillis)
        assertEquals(1, after.lapses)
        assertTrue(after.isDue(NOW + 11 * 60 * 1000L))
    }

    @Test
    fun `a lapse puts the skill back on the bottom rung, not one rung down`() {
        val mastered = state(
            trebleMiddle,
            strength = 0.95,
            dueAtEpochMillis = NOW,
            attempts = 9,
            lapses = 0,
            repetition = 6,
        )
        val failed = listOf(SkillOutcome(trebleMiddle, attempts = 8, cleanAttempts = 2))
        val recovered = listOf(SkillOutcome(trebleMiddle, attempts = 8, cleanAttempts = 8))

        val afterLapse = scheduler.update(listOf(mastered), failed, NOW).single()
        val afterRecovery = scheduler.update(listOf(afterLapse), recovered, NOW).single()

        assertEquals(0, afterLapse.repetition)
        assertEquals(NOW + 10 * 60 * 1000L, afterLapse.dueAtEpochMillis)
        assertEquals(
            "one good session must not hide it for days",
            NOW + 4 * HOUR_MILLIS,
            afterRecovery.dueAtEpochMillis
        )
        assertEquals(1, afterRecovery.repetition)
        assertEquals("attempts and lapses stay lifetime totals", 11, afterRecovery.attempts)
        assertEquals(1, afterRecovery.lapses)
    }

    @Test
    fun `each clean attempt roughly doubles the interval`() {
        var states = listOf(state(trebleMiddle, strength = 0.0, dueAtEpochMillis = NOW, attempts = 0))
        val clean = listOf(SkillOutcome(trebleMiddle, attempts = 4, cleanAttempts = 4))
        val intervals = (1..4).map {
            states = scheduler.update(states, clean, NOW)
            states.single().dueAtEpochMillis - NOW
        }

        assertEquals(
            listOf(4 * HOUR_MILLIS, 8 * HOUR_MILLIS, 16 * HOUR_MILLIS, 32 * HOUR_MILLIS),
            intervals,
        )
        assertTrue("strength kept climbing", states.single().strength > 0.7)
    }

    @Test
    fun `an outcome with no attempts is not evidence of anything`() {
        val before = state(trebleMiddle, strength = 0.6, dueAtEpochMillis = NOW, attempts = 2)

        val after = scheduler.update(listOf(before), listOf(SkillOutcome(trebleMiddle, 0, 0)), NOW)

        assertEquals(listOf(before), after)
    }

    @Test
    fun `a skill never seen before is added by its first outcome`() {
        val after = scheduler.update(
            emptyList(),
            listOf(SkillOutcome(bassLedger, attempts = 6, cleanAttempts = 1)),
            NOW,
        )

        assertEquals(bassLedger, after.single().tag)
        assertEquals(0.0, after.single().strength, 1e-9)
        assertEquals(1, after.single().lapses)
        assertTrue(after.single().isDue(NOW + HOUR_MILLIS))
    }

    @Test
    fun `weakest puts due skills first, then ascending strength`() {
        val states = listOf(
            state(quarterRhythm, strength = 0.9, dueAtEpochMillis = NOW),
            state(bassLedger, strength = 0.0, dueAtEpochMillis = NOW + DAY_MILLIS),
            state(trebleMiddle, strength = 0.1, dueAtEpochMillis = NOW),
        )

        val order = scheduler.weakest(states, NOW).map { it.tag }

        assertEquals(listOf(trebleMiddle, quarterRhythm, bassLedger), order)
        assertEquals(listOf(trebleMiddle), scheduler.weakest(states, NOW, limit = 1).map { it.tag })
        assertEquals(emptyList<SkillState>(), scheduler.weakest(states, NOW, limit = 0))
    }

    @Test
    fun `a skill that keeps failing is what next targets`() {
        var states = listOf(
            state(trebleMiddle, strength = 0.9, dueAtEpochMillis = NOW + DAY_MILLIS, attempts = 5),
            state(bassLedger, strength = 0.9, dueAtEpochMillis = NOW + DAY_MILLIS, attempts = 5),
        )
        repeat(3) {
            states = scheduler.update(states, listOf(SkillOutcome(bassLedger, attempts = 8, cleanAttempts = 1)), NOW)
        }
        val available = listOf(
            summaryOf("etude-a", setOf(trebleMiddle, quarterRhythm)),
            summaryOf("etude-b", setOf(bassLedger, quarterRhythm)),
        )

        val choice = scheduler.next(available, states, Polyphony.Poly, NOW + HOUR_MILLIS, seed = 7L)

        assertTrue("chose $choice", bassLedger in choice.targeting())
        assertEquals(PracticeChoice.Piece::class, choice::class)
        assertEquals("etude-b", (choice as PracticeChoice.Piece).id.value)
    }

    @Test
    fun `mastered and nothing due moves on to a piece rather than drilling`() {
        val states = listOf(
            state(trebleMiddle, strength = 1.0, dueAtEpochMillis = NOW + DAY_MILLIS, attempts = 9),
            state(quarterRhythm, strength = 1.0, dueAtEpochMillis = NOW + 2 * DAY_MILLIS, attempts = 9),
        )
        val available = listOf(summaryOf("minuet", setOf(trebleMiddle, quarterRhythm)))

        val choice = scheduler.next(available, states, Polyphony.Poly, NOW, seed = 1L)

        assertEquals(
            PracticeChoice.Piece(ScoreId("minuet"), TEST_TEMPO_BPM, setOf(trebleMiddle, quarterRhythm)),
            choice,
        )
    }

    @Test
    fun `nothing suitable falls back to a generated exercise targeting the weakest skill`() {
        val states = listOf(state(bassMiddle, strength = 0.05, dueAtEpochMillis = NOW, attempts = 4, lapses = 3))
        val available = listOf(summaryOf("bach", setOf(bassMiddle, SkillTag.HandIndependence), Polyphony.Poly))

        val choice = scheduler.next(available, states, Polyphony.Poly, NOW, seed = 42L)

        assertTrue("chose $choice", choice is PracticeChoice.Generated)
        val generated = choice as PracticeChoice.Generated
        assertEquals(42L, generated.seed)
        assertEquals(setOf(bassMiddle), generated.targeting)
        assertEquals(Clef.Bass, generated.spec.clefs.getValue(Staff.Upper))
    }

    @Test
    fun `the spec a generated choice carries is the generator's own, not a second opinion`() {
        val targets = listOf(
            bassLedger,
            bassMiddle,
            SkillTag.Accidental(Alter.Sharp),
            SkillTag.KeyReading(3),
            SkillTag.Leap(11),
        )

        targets.forEach { target ->
            val states = listOf(state(target, strength = 0.0, dueAtEpochMillis = NOW))

            val choice = scheduler.next(emptyList(), states, Polyphony.Poly, NOW, seed = 4L)

            assertEquals(
                "$target",
                generator.specTargeting(target, SpacedPracticeScheduler.DefaultBase),
                (choice as PracticeChoice.Generated).spec,
            )
        }
    }

    @Test
    fun `a mono input is never handed material its own judge would refuse`() {
        val states = listOf(
            state(SkillTag.HandIndependence, strength = 0.0, dueAtEpochMillis = NOW),
            state(trebleMiddle, strength = 0.2, dueAtEpochMillis = NOW),
        )
        val available = listOf(
            summaryOf("bach", setOf(SkillTag.HandIndependence, trebleMiddle), Polyphony.Poly),
        )

        val choice = scheduler.next(available, states, Polyphony.Mono, NOW, seed = 5L)

        assertTrue("chose $choice", choice is PracticeChoice.Generated)
        val generated = choice as PracticeChoice.Generated
        assertFalse("a mono input cannot play two hands", generated.spec.bothHandsActive)
        assertFalse(SkillTag.HandIndependence in generated.targeting)
        val exercise = generator.generate(generated.seed, generated.spec)
        assertNull(
            "the scheduler proposed what the judge refuses",
            WindowedJudge().accepts(exercise, FakeSource("mic", Polyphony.Mono)),
        )
    }

    @Test
    fun `a polyphonic piece is not offered to a mono input even when it fits the weak skill`() {
        val states = listOf(
            state(trebleMiddle, strength = 0.1, dueAtEpochMillis = NOW),
            state(quarterRhythm, strength = 0.9, dueAtEpochMillis = NOW + DAY_MILLIS),
            state(SkillTag.HandIndependence, strength = 0.9, dueAtEpochMillis = NOW + DAY_MILLIS),
        )
        val available = listOf(summaryOf("minuet", setOf(trebleMiddle, quarterRhythm), Polyphony.Poly))

        val forTap = scheduler.next(available, states, Polyphony.Poly, NOW, seed = 2L)
        val forMic = scheduler.next(available, states, Polyphony.Mono, NOW, seed = 2L)

        assertEquals(PracticeChoice.Piece::class, forTap::class)
        assertTrue("chose $forMic", forMic is PracticeChoice.Generated)
    }

    @Test
    fun `with no history at all it starts at the bottom of the ladder`() {
        val choice = scheduler.next(emptyList(), emptyList(), Polyphony.Poly, NOW, seed = 3L)

        assertTrue(choice is PracticeChoice.Generated)
        val generated = choice as PracticeChoice.Generated
        assertEquals(setOf(trebleMiddle), generated.targeting)
        assertEquals(SpacedPracticeScheduler.DefaultBase.bars, generated.spec.bars)
    }

    @Test
    fun `the same inputs and seed give the same choice`() {
        val states = listOf(
            state(bassLedger, strength = 0.1, dueAtEpochMillis = NOW),
            state(trebleMiddle, strength = 0.4, dueAtEpochMillis = NOW),
        )
        val available = listOf(
            summaryOf("etude-b", setOf(bassLedger, trebleMiddle)),
            summaryOf("etude-a", setOf(bassLedger, trebleMiddle)),
        )

        val first = scheduler.next(available, states, Polyphony.Poly, NOW, seed = 11L)
        val again = scheduler.next(available.reversed(), states.reversed(), Polyphony.Poly, NOW, seed = 11L)

        assertEquals(first, again)
    }

    @Test
    fun `a piece demanding too many brand-new skills at once is not offered yet`() {
        val newcomers = setOf(
            SkillTag.Accidental(Alter.Sharp),
            SkillTag.KeyReading(4),
            SkillTag.Leap(9),
            quarterRhythm,
        )
        val states = listOf(state(trebleMiddle, strength = 0.1, dueAtEpochMillis = NOW))
        val tooHard = summaryOf("hard", newcomers + trebleMiddle)

        assertTrue(scheduler.next(listOf(tooHard), states, Polyphony.Poly, NOW, seed = 1L) is PracticeChoice.Generated)

        val eased = states + newcomers.take(3).map { state(it, strength = 0.9, dueAtEpochMillis = NOW + DAY_MILLIS) }
        val choice = scheduler.next(listOf(tooHard), eased, Polyphony.Poly, NOW, seed = 1L)
        assertTrue("chose $choice", choice is PracticeChoice.Piece)
        assertEquals(setOf(trebleMiddle), (choice as PracticeChoice.Piece).targeting)
    }
}

private fun PracticeChoice.targeting(): Set<SkillTag> = when (this) {
    is PracticeChoice.Piece -> targeting
    is PracticeChoice.Generated -> targeting
}
