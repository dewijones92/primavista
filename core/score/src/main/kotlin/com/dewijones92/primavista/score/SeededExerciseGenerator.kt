package com.dewijones92.primavista.score

import com.dewijones92.primavista.common.Diag
import com.dewijones92.primavista.common.NoOpDiag
import kotlin.random.Random

private const val DIAG_TAG = "exercise"

/**
 * Deterministic exercise generation: everything random comes from [Random] seeded with the
 * caller's seed, in a fixed order (staff by staff, bar by bar), so a diagnostics report
 * carrying only a seed and a spec reconstructs exactly what Dewi was reading.
 */
public class SeededExerciseGenerator(private val diag: Diag = NoOpDiag) : ExerciseGenerator {

    override fun generate(seed: Long, spec: DifficultySpec): Score {
        val choices = rhythmChoices(spec)
        require(choices.isNotEmpty()) {
            "no writable note value survives symbols=${spec.symbols} maxDots=${spec.maxDots}"
        }
        val bar = BarFill(spec.time.measureTicks.value, choices)
        require(bar.isPossible) {
            "note values ${spec.symbols} cannot fill a ${spec.time.beats}/${spec.time.beatUnit} bar exactly"
        }
        // The key is chosen from the seed rather than drawn from `random`, so a single-key spec
        // produces byte-identical music to before a level could write in more than one.
        val writing = Writing(spec, bar, Random(seed), spec.keyFor(seed))
        val key = writing.key
        val events = spec.staves.flatMapIndexed { position, staff -> staffEvents(writing, staff, position) }
        val score = Score(
            id = ScoreId(identifierOf(seed, spec, key)),
            title = "Generated exercise",
            composer = null,
            origin = ScoreOrigin.Generated(seed, spec),
            staves = spec.staves,
            measures = measuresOf(spec, key),
            events = events.sortedWith(scoreEventOrder),
            defaultTempoBpm = spec.tempoBpm,
        )
        diag.event(
            DIAG_TAG,
            "generated ${describe(seed, spec)} notes=${score.notes.size} poly=${score.polyphony} " +
                "polyFromBar=${score.firstPolyphonicMeasure() ?: "none"}",
        )
        return score
    }

    override fun specTargeting(target: SkillTag, base: DifficultySpec): DifficultySpec {
        val targeted = when (target) {
            is SkillTag.ClefRegion -> base.withClefRegion(target.clef, target.band)
            is SkillTag.LegerLines -> base.withLegerLines(target.clef, target.count, target.above)
            is SkillTag.Accidental -> base.withAccidental(target.alter)
            is SkillTag.KeyReading -> base.copy(
                keys = setOf(KeySignature(target.fifths)),
                maxKeyAccidentals = maxOf(base.maxKeyAccidentals, kotlin.math.abs(target.fifths)),
            )
            is SkillTag.RhythmFigure -> base.withRhythmFigure(target)
            is SkillTag.Leap -> base.withLeap(target.semitones)
            SkillTag.HandIndependence -> base.withBothHands()
        }
        diag.event(DIAG_TAG, "targeting $target range=${targeted.range} symbols=${targeted.symbols}")
        return targeted
    }

    /** The one line a report needs to reconstruct an exercise exactly (docs/spec.md I7). */
    private fun describe(seed: Long, spec: DifficultySpec): String =
        "seed=$seed bars=${spec.bars} time=${spec.time.beats}/${spec.time.beatUnit} " +
            "keys=${spec.keys.map { it.fifths }} wroteIn=${spec.keyFor(seed).fifths}fifths " +
            "staves=${spec.staves} clefs=${spec.clefs} range=${spec.range} symbols=${spec.symbols} " +
            "maxDots=${spec.maxDots} tuplets=${spec.allowTuplets} alterations=${spec.allowedAlterations} " +
            "maxLeap=${spec.maxLeapSemitones}semitones tempo=${spec.tempoBpm}bpm bothHands=${spec.bothHandsActive}"

    /** One exercise being written: everything fixed for its whole length, gathered once. */
    private class Writing(
        val spec: DifficultySpec,
        val bar: BarFill,
        val random: Random,
        val key: KeySignature,
    )

    private fun staffEvents(writing: Writing, staff: Staff, position: Int): List<ScoreEvent> {
        val spec = writing.spec
        val key = writing.key
        val voice = position + 1
        val measureTicks = spec.time.measureTicks
        if (position > 0 && !spec.bothHandsActive) {
            return (0 until spec.bars).flatMap { index ->
                laidOut(writing.bar.fillWithLongest(), measureTicks * index) { onset, duration ->
                    Rest(onset, duration, staff, voice)
                }
            }
        }
        val range = requireNotNull(spec.range[staff]) { "spec has no pitch range for $staff" }
        val walker = MelodyWalker(
            ladder = scaleLadder(key, range),
            extras = extraAlterations(spec, key),
            maxLeapSemitones = spec.maxLeapSemitones,
            range = range,
            keyPitchClasses = keyPitchClasses(key),
            random = writing.random,
        )
        return (0 until spec.bars).flatMap { index ->
            laidOut(writing.bar.fillRandomly(writing.random), measureTicks * index) { onset, duration ->
                Note(onset, duration, staff, voice, walker.next())
            }
        }
    }

    private fun <T : ScoreEvent> laidOut(
        durations: List<Duration>,
        start: Ticks,
        build: (Ticks, Duration) -> T,
    ): List<T> {
        var onset = start
        return durations.map { duration ->
            val event = build(onset, duration)
            onset += duration.ticks
            event
        }
    }

    private fun measuresOf(spec: DifficultySpec, key: KeySignature): List<Measure> =
        (0 until spec.bars).map { index ->
            Measure(index, spec.time.measureTicks * index, spec.time, key, spec.clefs)
        }

    private fun extraAlterations(spec: DifficultySpec, key: KeySignature): List<Alter> =
        spec.allowedAlterations
            .filter { it != Alter.Natural || key.fifths != 0 }
            .sortedBy { it.semitones }

    private fun identifierOf(seed: Long, spec: DifficultySpec, key: KeySignature): String =
        "generated-$seed-${spec.bars}bars-${spec.time.beats}-${spec.time.beatUnit}-key${key.fifths}"
}
