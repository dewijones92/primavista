package com.dewijones92.primavista.score

/**
 * What a generated exercise should contain. Every field is a dial the scheduler can turn to
 * target a weak skill (docs/spec.md I5).
 */
public data class DifficultySpec(
    val staves: List<Staff>,
    val clefs: Map<Staff, Clef>,
    /**
     * The keys this level writes in. A **set**, because a rung called *Keys* that only ever wrote
     * in G major would teach one key signature and claim to teach key reading — and
     * `CurriculumTest` holds the line that a stage may only claim skills its own material tests.
     * Which one an exercise gets is chosen from its seed, so a piece stays reproducible.
     */
    val keys: Set<KeySignature>,
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
    /**
     * How large a key signature a reader at this level copes with, which is **not** the same
     * question as [key].
     *
     * [key] says what to *write* in — a generator has to pick one. This says what can be *read*,
     * and real music is in every key. Conflating them capped the whole ten-stage path at one sharp,
     * so a reader could finish it having never seen a B flat, and left 44,335 passages of the
     * shipped corpus refused on a difficulty the path claims to teach at stage six.
     *
     * Defaults to [key]'s own size, which is exactly the behaviour before the split.
     */
    val maxKeyAccidentals: Int = keys.maxOf { it.accidentalCount },
) {
    /**
     * What a reader at this level copes with, which is never less than what it writes.
     *
     * Derived rather than required, because `copy(key = …)` does not re-evaluate defaults: a
     * precondition here would turn every existing `copy` that changes the key into a crash, which
     * is exactly what happened when it was one.
     */
    /**
     * One key standing for the level, where a single one is needed — the staff geometry that turns
     * a staff step into a pitch, and the search for a key that can write a given accidental. The
     * plainest is chosen because it is stable: adding a harder key to a level must not move where
     * its notes sit on the staff.
     */
    public val plainestKey: KeySignature get() = keys.minBy { it.accidentalCount }

    public val readableKeyAccidentals: Int get() = maxOf(maxKeyAccidentals, keys.maxOf { it.accidentalCount })

    init {
        require(bars > 0) { "an exercise needs at least one bar" }
        require(symbols.isNotEmpty()) { "an exercise needs at least one note value" }
        require(staves.isNotEmpty()) { "an exercise needs at least one staff" }
        require(keys.isNotEmpty()) { "an exercise has to be written in some key" }
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
/**
 * Which of a spec's [DifficultySpec.keys] a seed writes in.
 *
 * Derived from the seed alone rather than by drawing from the generator's own random stream, so a
 * single-key spec produces byte-identical music to before this existed — only a level that gained
 * keys writes anything new.
 */
public fun DifficultySpec.keyFor(seed: Long): KeySignature {
    val ordered = keys.sortedBy { it.fifths }
    return ordered[((seed % ordered.size) + ordered.size).toInt() % ordered.size]
}

public interface ExerciseGenerator {
    public fun generate(seed: Long, spec: DifficultySpec): Score

    /**
     * Builds a spec aimed at [target], starting from [base]. How the scheduler turns "bass-clef
     * leger lines are weak" into material that drills them.
     */
    public fun specTargeting(target: SkillTag, base: DifficultySpec): DifficultySpec
}
