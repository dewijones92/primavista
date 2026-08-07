package com.dewijones92.primavista.score

/**
 * A clef, defined by where it puts a reference pitch. Adding one is adding an entry here and
 * nowhere else — the layout engine reads [referenceDiatonicIndex] rather than branching per
 * clef, which is what stops treble and bass becoming two renderers.
 *
 * [referenceDiatonicIndex] is the [Pitch.diatonicIndex] of the note sitting on the bottom
 * staff line.
 */
public enum class Clef(public val glyphName: String, public val referenceDiatonicIndex: Int) {
    Treble("gClef", bottomLine(Letter.E, octave = 4)),
    Bass("fClef", bottomLine(Letter.G, octave = 2)),
    Alto("cClef", bottomLine(Letter.F, octave = 3)),
}

private fun bottomLine(letter: Letter, octave: Int): Int =
    octave * Pitch.LETTERS_PER_OCTAVE + letter.diatonicStep

/**
 * Which staff of the system a note belongs to. Named for the hand rather than the clef
 * because it survives a clef change mid-piece, which is common in real piano writing.
 */
public enum class Staff { Upper, Lower }

public data class KeySignature(val fifths: Int) {
    init {
        require(fifths in -MAX_FIFTHS..MAX_FIFTHS) { "key signature $fifths beyond seven accidentals" }
    }

    public val isSharpKey: Boolean get() = fifths > 0
    public val accidentalCount: Int get() = kotlin.math.abs(fifths)

    public companion object {
        public const val MAX_FIFTHS: Int = 7
        public val C: KeySignature = KeySignature(0)
    }
}

public data class TimeSignature(val beats: Int, val beatUnit: Int) {
    init {
        require(beats > 0) { "time signature needs at least one beat" }
        require(beatUnit in ALLOWED_BEAT_UNITS) { "beat unit $beatUnit is not a note value" }
    }

    public val measureTicks: Ticks
        get() = Ticks(MusicalTime.TICKS_PER_QUARTER * 4 * beats / beatUnit)

    public companion object {
        public val ALLOWED_BEAT_UNITS: Set<Int> = setOf(1, 2, 4, 8, 16)
        public val FourFour: TimeSignature = TimeSignature(4, 4)
    }
}

public sealed interface ScoreEvent {
    public val onset: Ticks
    public val duration: Duration
    public val staff: Staff
    public val voice: Int

    public val endsAt: Ticks get() = onset + duration.ticks
}

public data class Note(
    override val onset: Ticks,
    override val duration: Duration,
    override val staff: Staff,
    override val voice: Int,
    val pitch: Pitch,
    /** True when this note continues a tie and so must not be re-attacked or re-judged. */
    val tiedFromPrevious: Boolean = false,
    val tiedToNext: Boolean = false,
) : ScoreEvent

public data class Rest(
    override val onset: Ticks,
    override val duration: Duration,
    override val staff: Staff,
    override val voice: Int,
) : ScoreEvent

/**
 * One bar. [index] is 0-based — its position in [Score.measures]. [number] is the 1-based bar a
 * human reads, and is what [Dropped.measure] and [Score.firstPolyphonicMeasure] both mean;
 * [numberOf] is the module's only `+ 1`. See `.claude/CODE-NOTES.md`.
 */
public data class Measure(
    val index: Int,
    val start: Ticks,
    val time: TimeSignature,
    val key: KeySignature,
    val clefs: Map<Staff, Clef>,
) {
    public val number: Int get() = numberOf(index)

    public companion object {
        public fun numberOf(index: Int): Int = index + 1
    }
}

/**
 * How many notes can sound at once. A property of the music, checked against what an input
 * device can actually hear — see docs/spec.md I3.
 */
public enum class Polyphony { Mono, Poly }

@JvmInline
public value class ScoreId(public val value: String)

/**
 * Where a [Score] came from, and the reason this is a sealed type rather than a label: a
 * [Generated] score is fully reproducible from its seed and spec, so putting those two
 * fields in a diagnostics report turns "an exercise went wrong" into a replayable case.
 * That is the highest-value line in the report (docs/todos/diagnostics-report.md).
 */
public sealed interface ScoreOrigin {
    public data class Parsed(val sourceName: String, val licence: String) : ScoreOrigin

    public data class Generated(val seed: Long, val spec: DifficultySpec) : ScoreOrigin
}

/**
 * A piece of music to read. The repo's most important unification: a procedurally generated
 * exercise and a parsed Bach minuet are this same type, so the scrolling loop, the judge and
 * the layout engine cannot tell them apart. If they ever diverge, that is a design failure
 * (see CLAUDE.md, *The ladder problem*).
 */
public data class Score(
    val id: ScoreId,
    val title: String,
    val composer: String?,
    val origin: ScoreOrigin,
    val staves: List<Staff>,
    val measures: List<Measure>,
    val events: List<ScoreEvent>,
    val defaultTempoBpm: Int,
) {
    public val notes: List<Note> get() = events.filterIsInstance<Note>()

    /** Notes that are actually attacked — a tied continuation is not played again. */
    public val attackedNotes: List<Note> get() = notes.filterNot { it.tiedFromPrevious }

    public val endsAt: Ticks get() = events.maxOfOrNull { it.endsAt } ?: Ticks.ZERO

    public val isGrandStaff: Boolean get() = staves.size > 1

    /**
     * Whether more than one note is ever **sounding** at the same time.
     *
     * Overlap, not simultaneous onset, and the distinction is the whole of docs/spec.md I3. The
     * commonest piano texture on earth is a held left-hand note under a moving right hand, and its
     * onsets never coincide — so an onset-based test calls it monophonic, lets it onto a mic that
     * can only follow one line, and silently mis-scores it. That was found by the adversarial
     * review on 2026-08-07 and is exactly the failure the refusal exists to prevent.
     *
     * Lives on [Score] rather than in the judge so that the refusal gate, [ScoreSummary] and the
     * scheduler all ask the same question and cannot answer it differently.
     */
    public val polyphony: Polyphony
        get() = if (firstPolyphonicMeasure() == null) Polyphony.Mono else Polyphony.Poly

    /**
     * The bar where two notes first overlap, [Measure.number]-style, or null if none ever do. Tied
     * continuations count, because a tie extends a sound. See `.claude/CODE-NOTES.md`.
     */
    public fun firstPolyphonicMeasure(): Int? {
        val at = notes.sortedBy { it.onset.value }
            .zipWithNext()
            .firstOrNull { (earlier, later) -> later.onset < earlier.endsAt }
            ?.second?.onset
            ?: return null
        val measure = measures.lastOrNull { it.start <= at } ?: measures.firstOrNull()
        return measure?.number ?: Measure.numberOf(index = 0)
    }
}

/** A summary for choosing what to practise without loading every event of every piece. */
public data class ScoreSummary(
    val id: ScoreId,
    val title: String,
    val composer: String?,
    val polyphony: Polyphony,
    val skills: Set<SkillTag>,
    val bars: Int,
    val defaultTempoBpm: Int,
)
