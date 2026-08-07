package com.dewijones92.primavista.score

/**
 * What a generated exercise should contain. Every field is a dial the scheduler can turn to
 * target a weak skill (docs/spec.md I5).
 */
public data class DifficultySpec(
    val staves: List<Staff>,
    val clefs: Map<Staff, Clef>,
    val key: KeySignature,
    val time: TimeSignature,
    val bars: Int,
    /** Inclusive pitch bounds per staff — the generator must not wander outside them. */
    val range: Map<Staff, ClosedRange<Midi>>,
    val symbols: Set<NoteSymbol>,
    val maxDots: Int,
    val allowTuplets: Boolean,
    val allowedAlterations: Set<Alter>,
    /** Largest melodic leap in semitones. Small values give stepwise, readable lines. */
    val maxLeapSemitones: Int,
    val tempoBpm: Int,
    /** When false, the lower staff rests throughout — hands-separate practice. */
    val bothHandsActive: Boolean,
) {
    init {
        require(bars > 0) { "an exercise needs at least one bar" }
        require(symbols.isNotEmpty()) { "an exercise needs at least one note value" }
        require(staves.isNotEmpty()) { "an exercise needs at least one staff" }
    }
}

/**
 * Generates an exercise from a seed and a spec.
 *
 * **Deterministic, and that is a requirement rather than a nicety**: the same seed and spec
 * must always produce the identical [Score], because a diagnostics report records only those
 * two things and a future session has to be able to reconstruct exactly what Dewi was looking
 * at (docs/todos/diagnostics-report.md). Nothing in an implementation may consult the wall
 * clock or an unseeded random source.
 */
public interface ExerciseGenerator {
    public fun generate(seed: Long, spec: DifficultySpec): Score

    /**
     * Builds a spec aimed at [target], starting from [base]. How the scheduler turns "bass-clef
     * leger lines are weak" into material that drills them.
     */
    public fun specTargeting(target: SkillTag, base: DifficultySpec): DifficultySpec
}
