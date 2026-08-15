package com.dewijones92.primavista.practice

import com.dewijones92.primavista.score.DifficultySpec
import com.dewijones92.primavista.score.Polyphony
import com.dewijones92.primavista.score.SkillTag
import kotlin.math.roundToInt

/** Notes of one skill below which a probe has shown something, but not enough to act on. */
private const val MIN_ATTEMPTS_FOR_CREDIT = 3

private const val PERCENT_SCALE = 100

/** Arbitrary but fixed, so consecutive probes do not read as neighbouring seeds. */
private const val PROBE_SEED_STRIDE = 1_000_003L

/** Stage numbers probed, in order. Accelerating: easy first, then bigger steps. */
private val RUNG_STAGE_NUMBERS = listOf(1, 2, 4, 7, 10)

/** What to put in front of the reader next, and where it sits on the path. */
public data class PlacementProbe(
    val stage: Stage,
    val seed: Long,
    val spec: DifficultySpec,
    val ordinal: Int,
)

/**
 * How one probe went, as the ordinary judge saw it.
 *
 * [JudgeOutcome] rather than a score, so a refusal stays a refusal: a probe the input could not
 * honestly be scored on is no evidence, not a bad result (docs/spec.md I3).
 */
public data class PlacementProbeResult(
    val probe: PlacementProbe,
    val outcome: JudgeOutcome,
) {
    /** Cleanliness, not accuracy: notes that were not written count against a placement. */
    public val wentWell: Boolean
        get() {
            val result = (outcome as? JudgeOutcome.Judged)?.result ?: return false
            return result.notesExpected > 0 && result.cleanliness >= SkillOutcome.CLEAN_ACCURACY
        }
}

public sealed interface PlacementStep {
    public data class Probe(val probe: PlacementProbe) : PlacementStep

    public data class Complete(val placement: Placement) : PlacementStep
}

/**
 * What a placement leaves behind: initial skill states, and a line a report can be read from.
 *
 * Deliberately **not** a stage. Where the reader stands is `Curriculum.currentStage(states)` and
 * only ever that, so the path and the placement cannot tell two stories about the same person.
 */
public data class Placement(
    val states: List<SkillState>,
    val probesTaken: Int,
    val summary: String,
)

public data class PlacementRequest(
    val seed: Long,
    val input: Polyphony,
    val nowEpochMillis: Long,
)

/**
 * The short adaptive read that meets a reader where they are: start easy, climb while it goes
 * well, stop when it stops.
 *
 * **Measured, not declared.** It asks the same [PerformanceJudge] the app scores every session
 * with, and seeds from the same [SkillOutcome]s — asking "are you a beginner?" would collect an
 * answer about confidence rather than about reading (docs/journey.md).
 *
 * **Pure and deterministic**, so a placement replays exactly from a report: the same request and
 * the same probe results always give the same probes and the same seeding.
 *
 * **Conservative on thin evidence**, because placing someone too high strands them. See
 * .claude/CODE-NOTES.md for the four levers and why they are these ones.
 */
public interface PlacementRead {
    public fun next(request: PlacementRequest, history: List<PlacementProbeResult>): PlacementStep

    /** Declining the read is free and starts you at the bottom, which is a seeding of nothing. */
    public fun skipped(request: PlacementRequest): Placement
}

public class AdaptivePlacementRead(
    private val curriculum: Curriculum = Curriculum.Standard,
) : PlacementRead {
    private val rungs: List<Stage> = RUNG_STAGE_NUMBERS.mapNotNull { curriculum.stage(StageId(it)) }

    override fun next(request: PlacementRequest, history: List<PlacementProbeResult>): PlacementStep {
        val climbing = history.all { it.wentWell }
        return if (climbing && history.size < rungs.size) {
            PlacementStep.Probe(probeAt(request, history.size))
        } else {
            PlacementStep.Complete(placementOf(request, history))
        }
    }

    override fun skipped(request: PlacementRequest): Placement =
        Placement(emptyList(), probesTaken = 0, summary = "placement skipped seed=${request.seed} seeded=0 skills")

    private fun probeAt(request: PlacementRequest, ordinal: Int): PlacementProbe {
        val stage = rungs[ordinal]
        return PlacementProbe(
            stage = stage,
            seed = request.seed + ordinal * PROBE_SEED_STRIDE,
            spec = stage.spec.playableBy(request.input),
            ordinal = ordinal,
        )
    }

    private fun placementOf(request: PlacementRequest, history: List<PlacementProbeResult>): Placement {
        val tallies = tallied(history)
        val states = tallies
            .filterValues { it.total.attempts >= MIN_ATTEMPTS_FOR_CREDIT }
            .map { (tag, tally) ->
                SkillState(
                    tag = tag,
                    strength = strengthOf(tally),
                    dueAtEpochMillis = request.nowEpochMillis,
                    attempts = tally.probes,
                    lapses = tally.lapses,
                    repetition = 0,
                )
            }
            .sortedBy { it.tag.toString() }
        return Placement(states, history.size, summaryOf(request, history, tallies, states))
    }

    private fun tallied(history: List<PlacementProbeResult>): Map<SkillTag, Tally> {
        val tallies = LinkedHashMap<SkillTag, Tally>()
        history.forEach { probe ->
            val result = (probe.outcome as? JudgeOutcome.Judged)?.result ?: return@forEach
            result.skillOutcomes.filter { it.attempts > 0 }.forEach { outcome ->
                tallies[outcome.tag] = tallies[outcome.tag]?.plus(outcome) ?: Tally.of(outcome)
            }
        }
        return tallies
    }

    private fun strengthOf(tally: Tally): Double =
        if (tally.total.isClean) {
            SkillState.SOLID_STRENGTH
        } else {
            tally.total.accuracy * SkillState.SOLID_STRENGTH
        }

    private fun summaryOf(
        request: PlacementRequest,
        history: List<PlacementProbeResult>,
        tallies: Map<SkillTag, Tally>,
        states: List<SkillState>,
    ): String {
        val thin = tallies.count { it.value.total.attempts < MIN_ATTEMPTS_FOR_CREDIT }
        return "placement seed=${request.seed} input=${request.input} probes=${history.size} " +
            "rungs=[${history.joinToString(transform = ::describe)}] " +
            "seeded=${states.size}skills solid=${states.count { it.isSolid }} " +
            "thin=$thin(under ${MIN_ATTEMPTS_FOR_CREDIT}notes, uncredited)"
    }

    private fun describe(result: PlacementProbeResult): String {
        val stage = result.probe.stage.id.number
        return when (val outcome = result.outcome) {
            is JudgeOutcome.Refused -> "$stage:refused(${outcome.reason})"
            is JudgeOutcome.Judged ->
                "$stage:${if (result.wentWell) "clean" else "short"} " +
                    "${percent(outcome.result.cleanliness)}%of${outcome.result.notesExpected}notes"
        }
    }
}

/** One skill's evidence across the whole read, carried as a [SkillOutcome] so accuracy has one definition. */
private data class Tally(val total: SkillOutcome, val probes: Int, val lapses: Int) {
    operator fun plus(outcome: SkillOutcome): Tally = Tally(
        total = total.copy(
            attempts = total.attempts + outcome.attempts,
            cleanAttempts = total.cleanAttempts + outcome.cleanAttempts,
        ),
        probes = probes + 1,
        lapses = lapses + if (outcome.isClean) 0 else 1,
    )

    companion object {
        fun of(outcome: SkillOutcome): Tally = Tally(outcome, probes = 1, lapses = if (outcome.isClean) 0 else 1)
    }
}

private fun percent(value: Double): Int = (value * PERCENT_SCALE).roundToInt()
