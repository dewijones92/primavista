package com.dewijones92.primavista.database

import com.dewijones92.primavista.score.Alter
import com.dewijones92.primavista.score.SkillTag

/** What a stored skill key turned out to be. See `.claude/CODE-NOTES.md`. */
public sealed interface SkillKeyReading {
    public data class Readable(val tag: SkillTag) : SkillKeyReading

    /** [reason] is written to the diagnostics log, so a lost strength is never silent. */
    public data class Unreadable(val key: String, val reason: String) : SkillKeyReading
}

/**
 * The stored form of a [SkillTag], and the only place one is produced or read.
 * See `.claude/CODE-NOTES.md` for why the shape is what it is.
 */
public object SkillTagKeys {
    public const val FIELD_SEPARATOR: String = "|"
    public const val SET_SEPARATOR: String = ";"

    /** Bumped whenever a key's field list changes. See `.claude/CODE-NOTES.md`. */
    public const val FORMAT_VERSION: Int = 2

    public const val CLEF_REGION: String = "clefRegion"
    public const val LEGER_LINES: String = "legerLines"
    public const val ACCIDENTAL: String = "accidental"
    public const val KEY_READING: String = "keyReading"
    public const val RHYTHM_FIGURE: String = "rhythmFigure"
    public const val LEAP: String = "leap"
    public const val HAND_INDEPENDENCE: String = "handIndependence"

    private const val V1_LEGER_LINE_PARTS = 3

    public fun encode(tag: SkillTag): String = when (tag) {
        is SkillTag.ClefRegion -> join(CLEF_REGION, tag.clef.name, tag.band.name)
        is SkillTag.LegerLines -> join(LEGER_LINES, tag.clef.name, tag.count.toString(), tag.above.toString())
        is SkillTag.Accidental -> join(ACCIDENTAL, tag.alter.semitones.toString())
        is SkillTag.KeyReading -> join(KEY_READING, tag.fifths.toString())
        is SkillTag.RhythmFigure -> join(
            RHYTHM_FIGURE,
            tag.symbol.name,
            tag.dots.toString(),
            tag.tupletNumerator.toString(),
        )
        is SkillTag.Leap -> join(LEAP, tag.semitones.toString())
        SkillTag.HandIndependence -> HAND_INDEPENDENCE
    }

    /**
     * Every key this build cannot read comes back with the reason it could not, never as an
     * absence — a strength that vanishes without a line in the log is docs/spec.md I5 degrading
     * invisibly.
     */
    public fun read(key: String): SkillKeyReading {
        val parts = key.split(FIELD_SEPARATOR)
        supersededFormat(parts)?.let { return SkillKeyReading.Unreadable(key, it) }
        val tag = runCatching { decodeOrThrow(parts) }
            .getOrElse { return SkillKeyReading.Unreadable(key, it.message ?: it.toString()) }
        val rewritten = encode(tag)
        return if (rewritten == key) {
            SkillKeyReading.Readable(tag)
        } else {
            SkillKeyReading.Unreadable(key, "this build writes that skill as '$rewritten'")
        }
    }

    /** Null for a key this build cannot read, so a caller can log the loss rather than crash. */
    public fun decode(key: String): SkillTag? = (read(key) as? SkillKeyReading.Readable)?.tag

    public fun encodeSet(tags: Set<SkillTag>): String =
        tags.map { encode(it) }.sorted().joinToString(SET_SEPARATOR)

    public fun readSet(encoded: String): List<SkillKeyReading> =
        if (encoded.isEmpty()) emptyList() else encoded.split(SET_SEPARATOR).map { read(it) }

    public fun decodeSet(encoded: String): Set<SkillTag> =
        readSet(encoded).filterIsInstance<SkillKeyReading.Readable>().mapTo(mutableSetOf()) { it.tag }

    private fun join(vararg parts: String): String = parts.joinToString(FIELD_SEPARATOR)

    /**
     * A v1 leger-lines key recorded a count with no side, and the side cannot be recovered from
     * it. See `.claude/CODE-NOTES.md` for why guessing is the one unacceptable answer.
     */
    private fun supersededFormat(parts: List<String>): String? =
        if (parts.first() == LEGER_LINES && parts.size == V1_LEGER_LINE_PARTS) {
            "leger-lines key is format v1, which did not record which side of the staff the note " +
                "sat; discarded rather than guessed (this build writes v$FORMAT_VERSION)"
        } else {
            null
        }

    private val fieldsPerKind = mapOf(
        CLEF_REGION to 2,
        LEGER_LINES to 3,
        ACCIDENTAL to 1,
        KEY_READING to 1,
        RHYTHM_FIGURE to 3,
        LEAP to 1,
        HAND_INDEPENDENCE to 0,
    )

    private fun decodeOrThrow(parts: List<String>): SkillTag {
        val kind = parts.first()
        val args = parts.drop(1)
        val fields = requireNotNull(fieldsPerKind[kind]) { "unrecognised skill kind '$kind'" }
        require(args.size == fields) { "skill kind '$kind' takes $fields fields, got ${args.size}" }
        return when (kind) {
            CLEF_REGION -> SkillTag.ClefRegion(clefOf(args[0]), bandOf(args[1]))
            LEGER_LINES -> SkillTag.LegerLines(clefOf(args[0]), intOf(args[1]), boolOf(args[2]))
            ACCIDENTAL -> SkillTag.Accidental(Alter(intOf(args[0])))
            KEY_READING -> SkillTag.KeyReading(intOf(args[0]))
            RHYTHM_FIGURE -> SkillTag.RhythmFigure(symbolOf(args[0]), intOf(args[1]), intOf(args[2]))
            LEAP -> SkillTag.Leap(intOf(args[0]))
            HAND_INDEPENDENCE -> SkillTag.HandIndependence
            else -> error("skill kind '$kind' has a field count but no decoder")
        }
    }
}
