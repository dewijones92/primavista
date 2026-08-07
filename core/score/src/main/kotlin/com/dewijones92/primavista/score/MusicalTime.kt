package com.dewijones92.primavista.score

/**
 * A musical position or length, in ticks.
 *
 * Integer ticks over a deliberately rich divisor rather than floating point or rationals.
 * [MusicalTime.TICKS_PER_QUARTER] is 10080 = 2^5 · 3^2 · 5 · 7, so a quarter note divides
 * exactly by 2, 3, 4, 5, 6, 7, 8, 9, 16 and 32 — every subdivision and tuplet real music
 * uses, including the awkward ones. Doubles would accumulate error across a piece and turn
 * "does this triplet land on the beat?" into a question about epsilon rather than about
 * music; rationals would be exact but drag arithmetic into every comparison. Integers are
 * exact here and free.
 */
@JvmInline
public value class Ticks(public val value: Long) : Comparable<Ticks> {
    public operator fun plus(other: Ticks): Ticks = Ticks(value + other.value)
    public operator fun minus(other: Ticks): Ticks = Ticks(value - other.value)
    public operator fun times(factor: Int): Ticks = Ticks(value * factor)
    override fun compareTo(other: Ticks): Int = value.compareTo(other.value)

    public companion object {
        public val ZERO: Ticks = Ticks(0)
    }
}

public object MusicalTime {
    public const val TICKS_PER_QUARTER: Long = 10080L

    public fun quarters(count: Int): Ticks = Ticks(TICKS_PER_QUARTER * count)

    public fun wholes(count: Int): Ticks = Ticks(TICKS_PER_QUARTER * 4 * count)
}

/**
 * The written shape of a note or rest, independent of how long it actually lasts.
 *
 * [undottedTicks] is stored as an exact integer rather than derived from a fraction, so no
 * duration in this app is ever the result of a floating-point multiply.
 */
public enum class NoteSymbol(public val undottedTicks: Long, public val flagCount: Int) {
    DoubleWhole(MusicalTime.TICKS_PER_QUARTER * 8, 0),
    Whole(MusicalTime.TICKS_PER_QUARTER * 4, 0),
    Half(MusicalTime.TICKS_PER_QUARTER * 2, 0),
    Quarter(MusicalTime.TICKS_PER_QUARTER, 0),
    Eighth(MusicalTime.TICKS_PER_QUARTER / 2, 1),
    Sixteenth(MusicalTime.TICKS_PER_QUARTER / 4, 2),
    ThirtySecond(MusicalTime.TICKS_PER_QUARTER / 8, 3),
    ;

    /** True when the symbol has a stem at all — breves and semibreves do not. */
    public val hasStem: Boolean get() = this != DoubleWhole && this != Whole

    public val isBeamable: Boolean get() = flagCount > 0
}

/**
 * A written duration: a symbol, augmentation dots, and an optional tuplet ratio.
 *
 * [ticks] is derived rather than stored so a duration cannot claim a symbol and a length
 * that disagree — the most likely way a rhythm bug enters a notation model. The arithmetic
 * is checked for exactness rather than assumed: 10080 ticks per quarter makes every real
 * combination divide cleanly, so a remainder means the caller has asked for something this
 * app cannot represent, and saying so beats silently truncating a note.
 */
public data class Duration(
    val symbol: NoteSymbol,
    val dots: Int = 0,
    val tupletNumerator: Int = 1,
    val tupletDenominator: Int = 1,
) {
    init {
        require(dots in 0..MAX_DOTS) { "$dots augmentation dots is beyond what this app reads" }
        require(tupletNumerator > 0 && tupletDenominator > 0) { "tuplet ratio must be positive" }
    }

    public val ticks: Ticks
        get() {
            // n dots multiply the base by (2^(n+1) - 1) / 2^n: 3/2 for one, 7/4 for two.
            val dotNumerator = (1L shl (dots + 1)) - 1
            val dotDenominator = 1L shl dots
            val numerator = symbol.undottedTicks * dotNumerator * tupletDenominator
            val denominator = dotDenominator * tupletNumerator
            require(numerator % denominator == 0L) {
                "$symbol with $dots dots at $tupletNumerator:$tupletDenominator is not " +
                    "representable in ${MusicalTime.TICKS_PER_QUARTER} ticks per quarter"
            }
            return Ticks(numerator / denominator)
        }

    public val isTuplet: Boolean get() = tupletNumerator != tupletDenominator

    public companion object {
        public const val MAX_DOTS: Int = 2
    }
}
