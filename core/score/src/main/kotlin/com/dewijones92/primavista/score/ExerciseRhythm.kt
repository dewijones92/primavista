package com.dewijones92.primavista.score

import kotlin.random.Random

private const val UNDOTTED_WEIGHT = 6
private const val DOTTED_WEIGHT = 2
private const val TUPLET_WEIGHT = 1
private const val TUPLET_IN_THE_TIME_OF = 2
private const val NOTES_PER_TUPLET = 3

/**
 * One rhythmic move the generator can make: a written value, and how many of it are emitted
 * together. A tuplet is one move of three notes rather than three independent ones, so a
 * generated triplet is a real triplet group instead of three notes that happen to add up.
 */
internal class RhythmChoice(val duration: Duration, val repeats: Int, val weight: Int) {
    val totalTicks: Long get() = duration.ticks.value * repeats
    val figure: SkillTag.RhythmFigure get() = duration.figure
}

internal fun rhythmChoices(spec: DifficultySpec): List<RhythmChoice> {
    val dotLimit = spec.maxDots.coerceIn(0, Duration.MAX_DOTS)
    val symbols = NoteSymbol.entries.filter { it in spec.symbols }
    val plain = symbols.flatMap { symbol ->
        (0..dotLimit).mapNotNull { dots ->
            writtenValue(symbol, dots)?.let {
                RhythmChoice(it, 1, if (dots == 0) UNDOTTED_WEIGHT else DOTTED_WEIGHT)
            }
        }
    }
    if (!spec.allowTuplets) return plain
    val tuplets = symbols.flatMap { symbol ->
        (0..dotLimit).mapNotNull { dots ->
            writtenValue(symbol, dots, NOTES_PER_TUPLET, TUPLET_IN_THE_TIME_OF)?.let {
                RhythmChoice(it, NOTES_PER_TUPLET, TUPLET_WEIGHT)
            }
        }
    }
    return plain + tuplets
}

private fun writtenValue(symbol: NoteSymbol, dots: Int, numerator: Int = 1, denominator: Int = 1): Duration? {
    val duration = runCatching { Duration(symbol, dots, numerator, denominator) }.getOrNull() ?: return null
    return duration.takeIf { it.ticksOrNull != null }
}

/**
 * Which lengths can still end a bar exactly, worked out once per bar shape.
 *
 * The whole point: a bar that does not add up is a bug the rest of the app inherits, so the
 * generator is only ever offered choices from which an exact finish is still reachable.
 */
internal class BarFill(measureTicks: Long, private val choices: List<RhythmChoice>) {
    private val unit: Long = choices.fold(measureTicks) { carried, choice -> gcd(carried, choice.totalTicks) }
    private val slots: Int = if (unit > 0) (measureTicks / unit).toInt() else 0
    private val steps: List<Int> = choices.map { if (unit > 0) (it.totalTicks / unit).toInt() else 0 }
    private val reachable: BooleanArray = BooleanArray(slots + 1).also { table ->
        table[0] = true
        for (slot in 1..slots) {
            table[slot] = steps.any { it in 1..slot && table[slot - it] }
        }
    }

    val isPossible: Boolean get() = choices.isNotEmpty() && unit > 0 && reachable[slots]

    /** Whether a bar exists that *contains* [choice] — see `.claude/CODE-NOTES.md`. */
    fun canPlace(choice: RhythmChoice): Boolean {
        if (!isPossible || choice.totalTicks % unit != 0L) return false
        val step = (choice.totalTicks / unit).toInt()
        return step in 1..slots && reachable[slots - step]
    }

    fun fillRandomly(random: Random): List<Duration> = fill { weightedPick(it, random) }

    /** Rest bars want the plainest possible reading, so they take the longest value that fits. */
    fun fillWithLongest(): List<Duration> = fill { viable -> viable.maxBy { steps[it] } }

    private fun fill(select: (List<Int>) -> Int): List<Duration> {
        require(isPossible) { "no combination of the chosen note values fills this bar exactly" }
        val filled = mutableListOf<Duration>()
        var remaining = slots
        while (remaining > 0) {
            val viable = choices.indices.filter { steps[it] in 1..remaining && reachable[remaining - steps[it]] }
            val chosen = select(viable)
            repeat(choices[chosen].repeats) { filled += choices[chosen].duration }
            remaining -= steps[chosen]
        }
        return filled
    }

    private fun weightedPick(viable: List<Int>, random: Random): Int {
        var ticket = random.nextInt(viable.sumOf { choices[it].weight })
        for (index in viable) {
            ticket -= choices[index].weight
            if (ticket < 0) return index
        }
        return viable.last()
    }
}

private tailrec fun gcd(left: Long, right: Long): Long = if (right == 0L) left else gcd(right, left % right)
