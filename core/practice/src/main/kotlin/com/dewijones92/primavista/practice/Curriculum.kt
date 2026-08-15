package com.dewijones92.primavista.practice

import com.dewijones92.primavista.score.DifficultySpec
import com.dewijones92.primavista.score.SkillTag

/** Where a stage sits on the path, 1-based, as a beginner would count it. */
@JvmInline
public value class StageId(public val number: Int) {
    init {
        require(number >= FIRST) { "stage $number is below the first rung" }
    }

    override fun toString(): String = "stage $number"

    public companion object {
        public const val FIRST: Int = 1
    }
}

/**
 * One rung of the path: a coherent set of reading skills, in a sensible teaching order.
 *
 * A stage decides **which skills are in play**. It never picks material — the scheduler still
 * chooses which of those to drill and the generator still writes the notes, and a stage that
 * chose for itself would be a second scheduler (docs/journey.md).
 *
 * [skills] is what this stage *adds*, not everything it uses, which is what makes "the first
 * stage that is not solid" a meaningful answer. [spec] is cumulative in the other direction:
 * it carries every dial the earlier stages turned, so stage-seven material still contains
 * stage-two quarter notes.
 */
public data class Stage(
    val id: StageId,
    val title: String,
    val blurb: String,
    val skills: Set<SkillTag>,
    val spec: DifficultySpec,
) {
    init {
        require(skills.isNotEmpty()) { "$id claims no skills, so it would be passed without reading a note" }
    }

    /** What this stage hands the scheduler. */
    public val focus: PracticeFocus get() = PracticeFocus(skills, spec)
}

/**
 * The path: the ordered stages, and the one rule for whether you are past one.
 *
 * The rule is deliberately about reading and nothing else — **a stage is passed when its skills
 * are solid**, never when enough sessions have happened, because sessions completed measures
 * showing up and this app measures reading (docs/journey.md).
 */
public interface Curriculum {
    public val stages: List<Stage>

    public fun stage(id: StageId): Stage?

    public fun isPassed(stage: Stage, states: List<SkillState>): Boolean

    /**
     * The **first** stage that is not passed, so a gap left behind in an earlier stage pulls you
     * back to it rather than being skipped. Everything solid returns the last stage, which is
     * open-ended on purpose.
     */
    public fun currentStage(states: List<SkillState>): Stage

    /** Every skill claimed up to and including [id] — what material at that rung may draw on. */
    public fun skillsThrough(id: StageId): Set<SkillTag>

    public companion object {
        /** The ten rungs of docs/journey.md, built once. */
        public val Standard: Curriculum by lazy { StagedCurriculum(standardStages()) }
    }
}

public class StagedCurriculum(override val stages: List<Stage>) : Curriculum {
    init {
        require(stages.isNotEmpty()) { "a curriculum with no stages has no path" }
        require(stages.map { it.id.number } == stages.indices.map { it + StageId.FIRST }) {
            "stages must be numbered 1..n in order, not ${stages.map { it.id.number }}"
        }
    }

    private val byId: Map<Int, Stage> = stages.associateBy { it.id.number }

    override fun stage(id: StageId): Stage? = byId[id.number]

    override fun isPassed(stage: Stage, states: List<SkillState>): Boolean = isPassed(stage, solidTags(states))

    override fun currentStage(states: List<SkillState>): Stage {
        val solid = solidTags(states)
        return stages.firstOrNull { !isPassed(it, solid) } ?: stages.last()
    }

    override fun skillsThrough(id: StageId): Set<SkillTag> =
        stages.filter { it.id.number <= id.number }.flatMapTo(mutableSetOf()) { it.skills }

    private fun isPassed(stage: Stage, solid: Set<SkillTag>): Boolean = stage.skills.all { it in solid }

    private fun solidTags(states: List<SkillState>): Set<SkillTag> =
        states.filter { it.isSolid }.mapTo(mutableSetOf()) { it.tag }
}
