package com.dewijones92.primavista.database

import com.dewijones92.primavista.score.Alter
import com.dewijones92.primavista.score.DifficultySpec
import com.dewijones92.primavista.score.KeySignature
import com.dewijones92.primavista.score.Staff
import com.dewijones92.primavista.score.TimeSignature

/**
 * The stored form of a [DifficultySpec]. With the seed, this is what makes a generated session
 * replayable from a report — see `.claude/CODE-NOTES.md`.
 */
public object DifficultyCodec {
    public const val VERSION: Int = 1

    private const val FIELD_SEPARATOR = ";"
    private const val NAME_SEPARATOR = "="
    private const val LIST_SEPARATOR = ","
    private const val PAIR_SEPARATOR = ">"
    private const val NAMED_PARTS = 2

    private const val VERSION_FIELD = "v"
    private const val STAVES = "staves"
    private const val CLEFS = "clefs"
    private const val FIFTHS = "fifths"
    private const val BEATS = "beats"
    private const val BEAT_UNIT = "beatUnit"
    private const val BARS = "bars"
    private const val RANGE = "range"
    private const val SYMBOLS = "symbols"
    private const val MAX_DOTS = "maxDots"
    private const val TUPLETS = "tuplets"
    private const val ALTERS = "alters"
    private const val MAX_LEAP = "maxLeap"
    private const val TEMPO = "tempo"
    private const val BOTH_HANDS = "bothHands"

    public fun encode(spec: DifficultySpec): String = listOf(
        VERSION_FIELD to VERSION.toString(),
        STAVES to spec.staves.joinToString(LIST_SEPARATOR) { it.name },
        CLEFS to encodePerStaff(spec.clefs) { it.name },
        FIFTHS to spec.key.fifths.toString(),
        BEATS to spec.time.beats.toString(),
        BEAT_UNIT to spec.time.beatUnit.toString(),
        BARS to spec.bars.toString(),
        RANGE to encodePerStaff(spec.range) { encodeMidiRange(it) },
        SYMBOLS to spec.symbols.sorted().joinToString(LIST_SEPARATOR) { it.name },
        MAX_DOTS to spec.maxDots.toString(),
        TUPLETS to spec.allowTuplets.toString(),
        ALTERS to spec.allowedAlterations.map { it.semitones }.sorted().joinToString(LIST_SEPARATOR),
        MAX_LEAP to spec.maxLeapSemitones.toString(),
        TEMPO to spec.tempoBpm.toString(),
        BOTH_HANDS to spec.bothHandsActive.toString(),
    ).joinToString(FIELD_SEPARATOR) { "${it.first}$NAME_SEPARATOR${it.second}" }

    /** Null when this build cannot rebuild the spec, so the row is kept and the loss is logged. */
    public fun decode(encoded: String): DifficultySpec? = runCatching { decodeOrThrow(encoded) }.getOrNull()

    private fun decodeOrThrow(encoded: String): DifficultySpec {
        val fields = encoded.split(FIELD_SEPARATOR)
            .map { it.split(NAME_SEPARATOR, limit = NAMED_PARTS) }
            .filter { it.size == NAMED_PARTS }
            .associate { it.first() to it.last() }
        require(fields.int(VERSION_FIELD) == VERSION) {
            "stored spec is version ${fields.int(VERSION_FIELD)}, this build reads $VERSION"
        }
        val staves = fields.items(STAVES) { staffOf(it) }
        val clefs = fields.perStaff(CLEFS) { clefOf(it) }
        val range = fields.perStaff(RANGE) { midiRangeOf(it) }
        require(staves.distinct().size == staves.size) { "stored spec repeats a staff: $staves" }
        require(staves.all { it in clefs && it in range }) {
            "stored spec has no clef or range for every staff of $staves"
        }
        return DifficultySpec(
            staves = staves,
            clefs = clefs,
            key = KeySignature(fields.int(FIFTHS)),
            time = TimeSignature(fields.int(BEATS), fields.int(BEAT_UNIT)),
            bars = fields.int(BARS),
            range = range,
            symbols = fields.items(SYMBOLS) { symbolOf(it) }.toSet(),
            maxDots = fields.int(MAX_DOTS),
            allowTuplets = fields.bool(TUPLETS),
            allowedAlterations = fields.items(ALTERS) { Alter(intOf(it)) }.toSet(),
            maxLeapSemitones = fields.int(MAX_LEAP),
            tempoBpm = fields.int(TEMPO),
            bothHandsActive = fields.bool(BOTH_HANDS),
        )
    }

    private fun <V> encodePerStaff(values: Map<Staff, V>, encodeValue: (V) -> String): String =
        values.toSortedMap().entries.joinToString(LIST_SEPARATOR) {
            "${it.key.name}$PAIR_SEPARATOR${encodeValue(it.value)}"
        }

    private fun Map<String, String>.text(field: String): String =
        requireNotNull(this[field]) { "stored spec has no '$field'" }

    private fun Map<String, String>.int(field: String): Int = intOf(text(field))

    private fun Map<String, String>.bool(field: String): Boolean = text(field).toBooleanStrict()

    private fun <T> Map<String, String>.items(field: String, item: (String) -> T): List<T> {
        val raw = text(field)
        return if (raw.isEmpty()) emptyList() else raw.split(LIST_SEPARATOR).map(item)
    }

    private fun <V> Map<String, String>.perStaff(field: String, value: (String) -> V): Map<Staff, V> =
        items(field) { entry ->
            val parts = entry.split(PAIR_SEPARATOR, limit = NAMED_PARTS)
            require(parts.size == NAMED_PARTS) { "'$entry' is not a staff-keyed value" }
            staffOf(parts.first()) to value(parts.last())
        }.toMap()
}
