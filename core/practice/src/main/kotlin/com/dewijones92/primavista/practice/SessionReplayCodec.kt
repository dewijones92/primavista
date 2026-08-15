package com.dewijones92.primavista.practice

import com.dewijones92.primavista.score.Midi
import com.dewijones92.primavista.score.Polyphony
import com.dewijones92.primavista.score.ScoreId
import com.dewijones92.primavista.score.TimeSignature

/**
 * A [SessionReplay] as lines of text, because that is what gets shared.
 *
 * A report leaves the phone through a share sheet as a string (spec I6 — there is no network), so
 * the replay has to survive being pasted into a message and read back a week later. Hence lines
 * rather than a binary blob, and hence a **stated refusal** rather than a null when it cannot be
 * read: "I cannot rebuild this session" is a finding, and a silent absence is not.
 *
 * The difficulty spec of a generated exercise is deliberately **not** re-encoded here. It already
 * has a codec in `:core:database` used to make sessions replayable from the practice history, and
 * two encodings of one thing is the duplication this repo has been bitten by twice — so the spec
 * arrives through [SpecText] and the app hands in the encoder it already owns.
 */
private const val GENERATED = "generated"
private const val SHIPPED = "shipped"
private const val PASSAGE = "passage"
private const val CLAIM_PARTS = 3
private const val PASSAGE_PARTS = 3
private const val LIST = ","

public object SessionReplayCodec {
    public const val BEGIN: String = "--- replay begin ---"
    public const val END: String = "--- replay end ---"

    private const val VERSION_FIELD = "v"
    private const val VERSION = 1
    private const val FIELD = "="
    private const val NAMED_PARTS = 2

    private const val SCORE = "score"
    private const val TEMPO = "tempo"
    private const val TIME = "time"
    private const val LEGS = "legs"
    private const val INPUT = "input"
    private const val POLY = "poly"
    private const val LATENCY = "latency"
    private const val PLAYED = "played"
    private const val CLAIMED = "claimed"

    public fun encode(replay: SessionReplay, spec: SpecText): String = buildString {
        appendLine(BEGIN)
        appendLine(field(VERSION_FIELD, VERSION.toString()))
        appendLine(field(SCORE, encodeScore(replay.score, spec)))
        appendLine(field(TEMPO, replay.tempoBpm.toString()))
        appendLine(field(TIME, "${replay.time.beats}/${replay.time.beatUnit}"))
        appendLine(field(LEGS, replay.legs.joinToString(LIST) { "${it.fromTicks}@${it.originNanos}" }))
        appendLine(field(INPUT, replay.inputLabel))
        appendLine(field(POLY, replay.polyphony.name))
        appendLine(field(LATENCY, "${replay.latency.millis}/${replay.latency.provenance.name}"))
        replay.played.forEach { appendLine(field(PLAYED, "${it.midi.number}@${it.atNanos}~${it.confidence}")) }
        replay.claimed.forEach { appendLine(field(CLAIMED, "${it.noteIndex}:${it.kind}:${it.dtMillis ?: ""}")) }
        append(END)
    }

    /** Reads back what [encode] wrote, finding the block inside a whole report if need be. */
    public fun read(report: String, spec: SpecText): ReplayReading =
        runCatching { ReplayReading.Readable(decodeOrThrow(blockIn(report), spec)) }
            .getOrElse { ReplayReading.Unreadable(it.message ?: it.toString()) }

    private fun blockIn(report: String): List<String> {
        val begin = report.indexOf(BEGIN)
        require(begin >= 0) { "this report carries no replay block" }
        val end = report.indexOf(END, begin)
        require(end >= 0) { "the replay block is not closed, so the report was truncated" }
        return report.substring(begin + BEGIN.length, end).lines().map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun decodeOrThrow(lines: List<String>, spec: SpecText): SessionReplay {
        val fields = lines.map { line ->
            val parts = line.split(FIELD, limit = NAMED_PARTS)
            require(parts.size == NAMED_PARTS) { "'$line' is not a name${FIELD}value field" }
            parts.first() to parts.last()
        }
        val single = fields.filterNot { it.first == PLAYED || it.first == CLAIMED }.toMap()
        require(single[VERSION_FIELD]?.toIntOrNull() == VERSION) {
            "replay is version ${single[VERSION_FIELD]}, this build reads $VERSION"
        }
        val (beats, beatUnit) = single.text(TIME).split("/").map { it.toInt() }
        return SessionReplay(
            score = decodeScore(single.text(SCORE), spec),
            tempoBpm = single.text(TEMPO).toInt(),
            time = TimeSignature(beats, beatUnit),
            legs = decodeLegs(single.text(LEGS)),
            inputLabel = single.text(INPUT),
            polyphony = Polyphony.valueOf(single.text(POLY)),
            latency = decodeLatency(single.text(LATENCY)),
            played = fields.filter { it.first == PLAYED }.map { decodePlayed(it.second) },
            claimed = fields.filter { it.first == CLAIMED }.map { decodeClaimed(it.second) },
        )
    }

    private fun field(name: String, value: String): String {
        require(!value.contains('\n')) { "a replay field cannot span lines: $name" }
        return "$name$FIELD$value"
    }

    private fun Map<String, String>.text(field: String): String =
        requireNotNull(this[field]) { "the replay block has no '$field'" }
}

// Per-field reading, at file scope rather than on the object: the object owns the *format* — the
// markers, the field names and the version — and these only know how one value is spelt.
private fun encodeScore(ref: ScoreRef, spec: SpecText): String = when (ref) {
    is ScoreRef.Generated -> "$GENERATED ${ref.seed} ${spec.encode(ref.spec)}"
    is ScoreRef.Shipped -> "$SHIPPED ${ref.piece.value}"
    is ScoreRef.Passage -> "$PASSAGE ${ref.fromBar} ${ref.bars} ${ref.piece.value}"
}

private fun decodeScore(encoded: String, spec: SpecText): ScoreRef {
    val kind = encoded.substringBefore(' ')
    val rest = encoded.substringAfter(' ')
    return when (kind) {
        GENERATED -> ScoreRef.Generated(
            seed = rest.substringBefore(' ').toLong(),
            spec = requireNotNull(spec.decode(rest.substringAfter(' '))) { "the stored spec is unreadable" },
        )
        SHIPPED -> ScoreRef.Shipped(ScoreId(rest))
        PASSAGE -> ScoreRef.Passage(
            piece = ScoreId(rest.split(' ', limit = PASSAGE_PARTS).last()),
            fromBar = rest.substringBefore(' ').toInt(),
            bars = rest.split(' ')[1].toInt(),
        )
        else -> error("'$kind' is not a kind of score this build knows")
    }
}

private fun decodeLegs(encoded: String): List<PauseLeg> =
    if (encoded.isEmpty()) {
        emptyList()
    } else {
        encoded.split(LIST).map { PauseLeg(it.substringBefore('@').toLong(), it.substringAfter('@').toLong()) }
    }

private fun decodePlayed(encoded: String): PlayedNote = PlayedNote(
    midi = Midi(encoded.substringBefore('@').toInt()),
    atNanos = encoded.substringAfter('@').substringBefore('~').toLong(),
    confidence = encoded.substringAfter('~').toFloat(),
)

private fun decodeLatency(encoded: String): InputLatency = InputLatency(
    millis = encoded.substringBefore('/').toDouble(),
    provenance = InputLatency.Provenance.valueOf(encoded.substringAfter('/')),
)

private fun decodeClaimed(encoded: String): ClaimedVerdict {
    val parts = encoded.split(':')
    require(parts.size == CLAIM_PARTS) { "'$encoded' is not index:kind:dt" }
    return ClaimedVerdict(parts[0].toInt(), parts[1], parts[2].toDoubleOrNull())
}

/**
 * The difficulty-spec encoding, supplied rather than reimplemented.
 *
 * `:core:database`'s `DifficultyCodec` already does this and is already the thing that makes a
 * stored session replayable. Passing it in keeps one encoding of a spec in the app and keeps this
 * module free of a dependency on the database.
 */
public interface SpecText {
    public fun encode(spec: com.dewijones92.primavista.score.DifficultySpec): String

    /** Null when this build cannot rebuild the spec, which the caller turns into a stated reason. */
    public fun decode(encoded: String): com.dewijones92.primavista.score.DifficultySpec?
}

/** What reading a replay out of a report turned out to be. Never a bare null. */
public sealed interface ReplayReading {
    public data class Readable(val replay: SessionReplay) : ReplayReading

    public data class Unreadable(val reason: String) : ReplayReading
}
