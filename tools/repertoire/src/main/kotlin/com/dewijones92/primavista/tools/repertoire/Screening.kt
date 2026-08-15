package com.dewijones92.primavista.tools.repertoire

import com.dewijones92.primavista.practice.Curriculum
import com.dewijones92.primavista.practice.StageId
import com.dewijones92.primavista.score.Admission
import com.dewijones92.primavista.score.DerivedScoreSkills
import com.dewijones92.primavista.score.DropKind
import com.dewijones92.primavista.score.Dropped
import com.dewijones92.primavista.score.MusicXmlParser
import com.dewijones92.primavista.score.MusicXmlResult
import com.dewijones92.primavista.score.PartChoice
import com.dewijones92.primavista.score.Score
import com.dewijones92.primavista.score.SkillTag
import com.dewijones92.primavista.score.admits
import com.dewijones92.primavista.score.kind
import com.dewijones92.primavista.score.material
import com.dewijones92.primavista.score.passages

/** One readable window of a piece, graded. [stage] is null when it is past the last rung. */
public data class Passage(
    val score: Score,
    val skills: Set<SkillTag>,
    val stage: StageId?,
    val topRungRefusals: List<String> = emptyList(),
) {
    public val stageNumber: Int? get() = stage?.number
}

public sealed interface Screening {
    public val source: SourcePiece

    public data class Accepted(
        override val source: SourcePiece,
        val score: Score,
        val skills: Set<SkillTag>,
        val stage: StageId?,
        val passages: List<Passage>,
        val cosmetic: List<Dropped>,
    ) : Screening {
        /** Null means "past the last rung": harder than anything the path currently teaches. */
        public val stageNumber: Int? get() = stage?.number

        /** The rung this piece first becomes readable at — its easiest passage. */
        public val easiestPassage: Passage? get() = passages.minByOrNull { it.stageNumber ?: Int.MAX_VALUE }
    }

    public data class Rejected(
        override val source: SourcePiece,
        val reason: String,
        val material: List<Dropped> = emptyList(),
    ) : Screening
}

/**
 * Reads one source file the way the app would, and decides whether it is safe to practise against.
 *
 * Deliberately built on the app's own [MusicXmlParser],
 * [DerivedScoreSkills] and [Curriculum] rather than a tool-side copy: a piece this accepts is one
 * the phone will read identically, which is the only claim worth making here.
 */
public class Screener(
    private val parser: MusicXmlParser,
    private val curriculum: Curriculum = Curriculum.Standard,
    private val skills: DerivedScoreSkills = DerivedScoreSkills(),
    private val minimumBars: Int = DEFAULT_MINIMUM_BARS,
    private val passageBars: List<Int> = DEFAULT_PASSAGE_BARS,
) {
    public fun screen(source: SourcePiece): Screening {
        val parsed = when (
            val result = parser.parseCompressed(
                source.bytes,
                source.id,
                source.licence,
                PartChoice.Keyboard
            )
        ) {
            is MusicXmlResult.Failed -> return Screening.Rejected(source, result.reason)
            is MusicXmlResult.Parsed -> result
        }
        val material = parsed.material
        if (material.isNotEmpty()) {
            return Screening.Rejected(source, materialReason(material), material)
        }
        val score = parsed.score
        if (!score.isGrandStaff) {
            return Screening.Rejected(
                source,
                "the keyboard part came through on one staff, so it is not grand-staff writing"
            )
        }
        if (score.measures.size < minimumBars) {
            return Screening.Rejected(source, "only ${score.measures.size} bars, which is too short to read at tempo")
        }
        val tags = skills.skillsOf(score)
        return Screening.Accepted(
            source = source,
            score = score,
            skills = tags,
            stage = stageOf(score),
            passages = passageBars.flatMap { bars ->
                score.passages(bars, step = bars).map { passage ->
                    Passage(passage, skills.skillsOf(passage), stageOf(passage), topRungRefusals(passage))
                }
            },
            cosmetic = parsed.dropped.filter { it.kind == DropKind.Cosmetic },
        )
    }

    /**
     * The lowest rung whose own [com.dewijones92.primavista.score.DifficultySpec] covers this
     * music. Not its skill set — see the note on `DifficultySpec.admits`.
     */
    private fun stageOf(score: Score): StageId? =
        curriculum.stages.firstOrNull { it.spec.admits(score).isAdmitted }?.id

    /** When nothing admits a passage, what the last rung objected to — the dial to look at. */
    private fun topRungRefusals(score: Score): List<String> =
        when (val verdict = curriculum.stages.last().spec.admits(score)) {
            is Admission.Admitted -> emptyList()
            is Admission.Refused -> verdict.reasons
        }

    private fun materialReason(material: List<Dropped>): String {
        val counts = material.groupingBy { it.element }.eachCount().entries.sortedByDescending { it.value }
        val summary = counts.take(MAX_ELEMENTS_IN_REASON).joinToString { "${it.key} x${it.value}" }
        val first = material.first()
        return "material loss ($summary); first at bar ${first.measure ?: 0}: ${first.detail}"
    }

    public companion object {
        public const val DEFAULT_MINIMUM_BARS: Int = 8

        /**
         * A piece is graded at several window lengths because length IS difficulty: the same songs
         * yielded 735 placeable passages at four bars, 140 at eight and 17 at sixteen. A reader
         * takes on as much of a page as they can hold, so the app should be able to offer either.
         */
        public val DEFAULT_PASSAGE_BARS: List<Int> = listOf(4, 8, 16)
        private const val MAX_ELEMENTS_IN_REASON = 3
    }
}
