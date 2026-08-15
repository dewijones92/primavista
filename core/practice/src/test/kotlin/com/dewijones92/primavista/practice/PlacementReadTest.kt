package com.dewijones92.primavista.practice

import com.dewijones92.primavista.score.Clef
import com.dewijones92.primavista.score.PitchBand
import com.dewijones92.primavista.score.Polyphony
import com.dewijones92.primavista.score.SkillTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val READ_NOW = 1_700_000_000_000L
private const val PROBE_NOTES = 12

private val trebleMid = SkillTag.ClefRegion(Clef.Treble, PitchBand.MiddleStaff)
private val bassMid = SkillTag.ClefRegion(Clef.Bass, PitchBand.MiddleStaff)

class PlacementReadTest {
    private val read = AdaptivePlacementRead()
    private val curriculum = Curriculum.Standard
    private val request = PlacementRequest(seed = 17L, input = Polyphony.Poly, nowEpochMillis = READ_NOW)

    @Test
    fun `it starts at the bottom, because nobody is insulted by one easy line`() {
        val first = read.next(request, emptyList())

        val probe = (first as PlacementStep.Probe).probe
        assertEquals(StageId(1), probe.stage.id)
        assertEquals(0, probe.ordinal)
        assertEquals(curriculum.stages.first().spec, probe.spec)
    }

    @Test
    fun `it climbs while the reading holds, in accelerating steps, and then stops`() {
        val visited = mutableListOf<Int>()
        var history = emptyList<PlacementProbeResult>()
        while (true) {
            val step = read.next(request, history)
            if (step is PlacementStep.Complete) break
            val probe = (step as PlacementStep.Probe).probe
            visited += probe.stage.id.number
            history = history + clean(probe)
        }

        assertEquals(listOf(1, 2, 4, 7, 10), visited)
        assertEquals("two minutes, not twenty", 5, history.size)
    }

    @Test
    fun `it stops the moment the reading stops holding`() {
        val first = probe(read.next(request, emptyList()))
        val second = probe(read.next(request, listOf(clean(first))))

        val after = read.next(request, listOf(clean(first), short(second)))

        assertEquals(StageId(2), second.stage.id)
        assertTrue("chose $after", after is PlacementStep.Complete)
        assertEquals(2, (after as PlacementStep.Complete).placement.probesTaken)
    }

    @Test
    fun `a refusal is no evidence at all, and it ends the read rather than counting against you`() {
        val first = probe(read.next(request, emptyList()))
        val refused = PlacementProbeResult(
            first,
            JudgeOutcome.Refused(RefusalReason.PolyphonicScoreOnMonoInput(firstPolyphonicBar = 3, inputLabel = "mic")),
        )

        val step = read.next(request, listOf(refused))

        val placement = (step as PlacementStep.Complete).placement
        assertEquals(emptyList<SkillState>(), placement.states)
        assertTrue(placement.summary, "refused" in placement.summary)
    }

    @Test
    fun `the same request and the same playing give the same probes and the same seeding`() {
        val first = probe(read.next(request, emptyList()))
        val history = listOf(clean(first))

        val again = probe(read.next(request, history))
        val andAgain = probe(read.next(request, history))
        val placement = complete(read.next(request, history + short(again)))
        val replayed = complete(read.next(request, history + short(andAgain)))

        assertEquals(again, andAgain)
        assertEquals(placement, replayed)
        assertNotEquals(
            "a different placement seed must not replay the same exercises",
            again.seed,
            probe(read.next(request.copy(seed = 18L), history)).seed,
        )
    }

    @Test
    fun `a skill read cleanly is seeded solid, due at once, and owed no ladder rung`() {
        val first = probe(read.next(request, emptyList()))

        val readCleanly = listOf(SkillOutcome(trebleMid, attempts = 8, cleanAttempts = 8))

        val placement = complete(read.next(request, listOf(short(first, readCleanly))))

        val seeded = placement.states.single { it.tag == trebleMid }
        assertEquals(SkillState.SOLID_STRENGTH, seeded.strength, 1e-9)
        assertTrue(seeded.isSolid)
        assertEquals("the scheduler must be free to disagree immediately", READ_NOW, seeded.dueAtEpochMillis)
        assertEquals("a placement is not a rung on the spacing ladder", 0, seeded.repetition)
        assertEquals(1, seeded.attempts)
        assertEquals(0, seeded.lapses)
    }

    @Test
    fun `a skill half read is seeded below solid, so it is not claimed as passed`() {
        val first = probe(read.next(request, emptyList()))
        val half = listOf(SkillOutcome(trebleMid, attempts = 8, cleanAttempts = 4))

        val placement = complete(read.next(request, listOf(short(first, half))))

        val seeded = placement.states.single { it.tag == trebleMid }
        assertEquals(0.4, seeded.strength, 1e-9)
        assertFalse(seeded.isSolid)
        assertEquals(1, seeded.lapses)
        assertEquals(StageId(1), curriculum.currentStage(placement.states).id)
    }

    @Test
    fun `thin evidence earns nothing, because two notes is not a reading`() {
        val first = probe(read.next(request, emptyList()))
        val thin = listOf(
            SkillOutcome(trebleMid, attempts = 2, cleanAttempts = 2),
            SkillOutcome(bassMid, attempts = 3, cleanAttempts = 3),
        )

        val placement = complete(read.next(request, listOf(short(first, thin))))

        assertEquals(listOf(bassMid), placement.states.map { it.tag })
        assertTrue(placement.summary, "thin=1" in placement.summary)
    }

    @Test
    fun `evidence from every probe is added up before it is judged`() {
        val first = probe(read.next(request, emptyList()))
        val second = probe(read.next(request, listOf(clean(first))))
        val strong = listOf(SkillOutcome(trebleMid, attempts = 10, cleanAttempts = 10))
        val weak = listOf(SkillOutcome(trebleMid, attempts = 10, cleanAttempts = 2))

        val placement = complete(read.next(request, listOf(clean(first, strong), short(second, weak))))

        val seeded = placement.states.single { it.tag == trebleMid }
        assertEquals("12 of 20 is not a clean reading", 0.6 * SkillState.SOLID_STRENGTH, seeded.strength, 1e-9)
        assertEquals("both probes tested it", 2, seeded.attempts)
        assertEquals(1, seeded.lapses)
    }

    @Test
    fun `where a placement leaves you is what the curriculum reads off its states`() {
        val stageOne = curriculum.stages.first()
        val first = probe(read.next(request, emptyList()))
        val readEverything = stageOne.skills.map { SkillOutcome(it, attempts = 6, cleanAttempts = 6) }

        val placement = complete(read.next(request, listOf(short(first, readEverything))))

        assertEquals(StageId(2), curriculum.currentStage(placement.states).id)
    }

    @Test
    fun `skipping the read is free and starts you at the first stage`() {
        val placement = read.skipped(request)

        assertEquals(emptyList<SkillState>(), placement.states)
        assertEquals(0, placement.probesTaken)
        assertEquals(StageId(1), curriculum.currentStage(placement.states).id)
        assertTrue(placement.summary, "skipped" in placement.summary)
    }

    @Test
    fun `a mono input is never probed with material its own judge would refuse`() {
        val mono = request.copy(input = Polyphony.Mono)
        var history = emptyList<PlacementProbeResult>()
        val probes = mutableListOf<PlacementProbe>()
        while (true) {
            val step = read.next(mono, history)
            if (step is PlacementStep.Complete) break
            probes += probe(step)
            history = history + clean(probes.last())
        }

        assertTrue(probes.map { it.stage.id.number }.toString(), probes.none { it.spec.bothHandsActive })
    }

    @Test
    fun `the summary says which rungs were tried and how each went`() {
        val first = probe(read.next(request, emptyList()))
        val second = probe(read.next(request, listOf(clean(first))))

        val placement = complete(read.next(request, listOf(clean(first), short(second))))

        assertTrue(placement.summary, "probes=2" in placement.summary)
        assertTrue(placement.summary, "1:clean" in placement.summary)
        assertTrue(placement.summary, "2:short" in placement.summary)
        assertTrue(placement.summary, "seed=17" in placement.summary)
    }

    @Test
    fun `a shorter path is probed only as far as it goes`() {
        val short = AdaptivePlacementRead(StagedCurriculum(curriculum.stages.take(3)))
        val visited = mutableListOf<Int>()
        var history = emptyList<PlacementProbeResult>()
        while (true) {
            val step = short.next(request, history)
            if (step is PlacementStep.Complete) break
            visited += probe(step).stage.id.number
            history = history + clean(probe(step))
        }

        assertEquals("rungs past the end of the path are not invented", listOf(1, 2), visited)
    }

    private fun probe(step: PlacementStep): PlacementProbe = (step as PlacementStep.Probe).probe

    private fun complete(step: PlacementStep): Placement = (step as PlacementStep.Complete).placement

    private fun clean(probe: PlacementProbe, outcomes: List<SkillOutcome> = emptyList()): PlacementProbeResult =
        PlacementProbeResult(probe, judged(correct = PROBE_NOTES, outcomes = outcomes))

    private fun short(probe: PlacementProbe, outcomes: List<SkillOutcome> = emptyList()): PlacementProbeResult =
        PlacementProbeResult(probe, judged(correct = PROBE_NOTES / 3, outcomes = outcomes))

    private fun judged(correct: Int, outcomes: List<SkillOutcome>): JudgeOutcome = JudgeOutcome.Judged(
        SessionResult(
            judgements = (0 until correct).map { ofNote(it, Verdict.Correct(dtMillis = 0.0)) },
            skillOutcomes = outcomes,
            notesExpected = PROBE_NOTES,
        ),
    )
}
