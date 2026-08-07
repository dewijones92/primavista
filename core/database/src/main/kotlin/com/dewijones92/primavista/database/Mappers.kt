package com.dewijones92.primavista.database

import com.dewijones92.primavista.practice.InputLatency
import com.dewijones92.primavista.practice.NoteJudgement
import com.dewijones92.primavista.practice.SkillState
import com.dewijones92.primavista.practice.Verdict
import com.dewijones92.primavista.score.Midi
import com.dewijones92.primavista.score.ScoreId
import com.dewijones92.primavista.score.ScoreOrigin
import com.dewijones92.primavista.score.ScoreSummary
import com.dewijones92.primavista.score.Ticks

public object OriginKinds {
    public const val PARSED: String = "parsed"
    public const val GENERATED: String = "generated"
    public const val UNKNOWN: String = "unknown"
}

public object VerdictKinds {
    public const val CORRECT: String = "correct"
    public const val WRONG_PITCH: String = "wrongPitch"
    public const val EARLY: String = "early"
    public const val LATE: String = "late"
    public const val MISSED: String = "missed"
    public const val EXTRA: String = "extra"
}

internal fun StoredSession.toEntity(): SessionEntity {
    val parsed = origin as? ScoreOrigin.Parsed
    val generated = origin as? ScoreOrigin.Generated
    return SessionEntity(
        id = id.value,
        scoreId = scoreId.value,
        scoreTitle = scoreTitle,
        originKind = when (origin) {
            is ScoreOrigin.Parsed -> OriginKinds.PARSED
            is ScoreOrigin.Generated -> OriginKinds.GENERATED
            null -> OriginKinds.UNKNOWN
        },
        originSourceName = parsed?.sourceName,
        originLicence = parsed?.licence,
        originSeed = generated?.seed,
        originSpec = generated?.let { DifficultyCodec.encode(it.spec) },
        inputLabel = inputLabel,
        polyphony = polyphony,
        tempoBpm = tempoBpm,
        latencyMillis = latency.millis,
        latencyProvenance = latency.provenance,
        startedAtEpochMillis = startedAtEpochMillis,
        finishedAtEpochMillis = finishedAtEpochMillis,
        notesExpected = notesExpected,
        correct = correct,
    )
}

internal fun SessionEntity.toStored(): StoredSession = StoredSession(
    id = SessionId(id),
    scoreId = ScoreId(scoreId),
    scoreTitle = scoreTitle,
    origin = decodeOrigin(),
    inputLabel = inputLabel,
    polyphony = polyphony,
    tempoBpm = tempoBpm,
    latency = InputLatency(latencyMillis, latencyProvenance),
    startedAtEpochMillis = startedAtEpochMillis,
    finishedAtEpochMillis = finishedAtEpochMillis,
    notesExpected = notesExpected,
    correct = correct,
    originDescriptor = describeOrigin(),
)

private fun SessionEntity.decodeOrigin(): ScoreOrigin? = when (originKind) {
    OriginKinds.PARSED -> originSourceName?.let { ScoreOrigin.Parsed(it, originLicence.orEmpty()) }
    OriginKinds.GENERATED -> originSeed?.let { seed ->
        originSpec?.let { DifficultyCodec.decode(it) }?.let { ScoreOrigin.Generated(seed, it) }
    }
    else -> null
}

internal fun SessionEntity.describeOrigin(): String =
    "kind=$originKind source=${originSourceName ?: "-"} licence=${originLicence ?: "-"} " +
        "seed=${originSeed ?: "-"} spec=${originSpec ?: "-"}"

internal fun NoteJudgement.toEntity(sessionId: SessionId): NoteVerdictEntity = when (val settled = verdict) {
    is Verdict.Correct -> row(sessionId, VerdictKinds.CORRECT, dtMillis = settled.dtMillis)
    is Verdict.WrongPitch -> row(
        sessionId,
        VerdictKinds.WRONG_PITCH,
        expected = settled.expected,
        heard = settled.heard,
        dtMillis = settled.dtMillis,
    )
    is Verdict.Early -> row(sessionId, VerdictKinds.EARLY, dtMillis = settled.dtMillis)
    is Verdict.Late -> row(sessionId, VerdictKinds.LATE, dtMillis = settled.dtMillis)
    Verdict.Missed -> row(sessionId, VerdictKinds.MISSED)
    is Verdict.Extra -> row(sessionId, VerdictKinds.EXTRA, heard = settled.heard, atTicks = settled.atTicks)
}

/** Left as a reading rather than a null so the caller can log why a row was dropped. */
internal fun NoteVerdictEntity.read(): VerdictRowReading =
    runCatching { VerdictRowReading.Readable(judgementOrThrow()) }
        .getOrElse { VerdictRowReading.Unreadable(id, it.message ?: it.toString()) }

internal sealed interface VerdictRowReading {
    data class Readable(val judgement: NoteJudgement) : VerdictRowReading

    data class Unreadable(val rowId: Long, val reason: String) : VerdictRowReading
}

private fun NoteJudgement.row(
    sessionId: SessionId,
    kind: String,
    expected: Midi? = null,
    heard: Midi? = null,
    dtMillis: Double? = null,
    atTicks: Ticks? = null,
): NoteVerdictEntity = NoteVerdictEntity(
    sessionId = sessionId.value,
    noteIndex = (this as? NoteJudgement.OfNote)?.noteIndex,
    kind = kind,
    expectedMidi = expected?.number,
    heardMidi = heard?.number,
    dtMillis = dtMillis,
    atTicks = atTicks?.value,
    confidence = confidence,
)

private fun NoteVerdictEntity.judgementOrThrow(): NoteJudgement = if (kind == VerdictKinds.EXTRA) {
    require(noteIndex == null) { "an extra answering to note $noteIndex is a sentinel from format v1" }
    NoteJudgement.Unexpected(
        Verdict.Extra(midiOf(heardMidi, "heardMidi"), Ticks(requireNotNull(atTicks) { "an extra without atTicks" })),
        confidence,
    )
} else {
    NoteJudgement.OfNote(
        requireNotNull(noteIndex) { "verdict '$kind' answers to no note, which only an extra may do" },
        verdictOrThrow(),
        confidence,
    )
}

private fun NoteVerdictEntity.verdictOrThrow(): Verdict = when (kind) {
    VerdictKinds.CORRECT -> Verdict.Correct(dt())
    VerdictKinds.WRONG_PITCH -> Verdict.WrongPitch(
        expected = midiOf(expectedMidi, "expectedMidi"),
        heard = midiOf(heardMidi, "heardMidi"),
        dtMillis = dt(),
    )
    VerdictKinds.EARLY -> Verdict.Early(dt())
    VerdictKinds.LATE -> Verdict.Late(dt())
    VerdictKinds.MISSED -> Verdict.Missed
    else -> error("unrecognised verdict kind '$kind'")
}

private fun NoteVerdictEntity.dt(): Double = requireNotNull(dtMillis) { "verdict '$kind' without dtMillis" }

private fun midiOf(value: Int?, field: String): Midi = Midi(requireNotNull(value) { "verdict without $field" })

internal fun SkillState.toEntity(): SkillStateEntity = SkillStateEntity(
    skillKey = SkillTagKeys.encode(tag),
    strength = strength,
    dueAtEpochMillis = dueAtEpochMillis,
    attempts = attempts,
    lapses = lapses,
)

internal sealed interface SkillRowReading {
    data class Readable(val state: SkillState) : SkillRowReading

    data class Unreadable(val key: String, val reason: String) : SkillRowReading
}

internal fun SkillStateEntity.read(): SkillRowReading = when (val key = SkillTagKeys.read(skillKey)) {
    is SkillKeyReading.Unreadable -> SkillRowReading.Unreadable(skillKey, key.reason)
    is SkillKeyReading.Readable -> runCatching {
        SkillRowReading.Readable(SkillState(key.tag, strength, dueAtEpochMillis, attempts, lapses))
    }.getOrElse { SkillRowReading.Unreadable(skillKey, it.message ?: it.toString()) }
}

internal fun RepertoireEntry.toEntity(): RepertoireEntity = RepertoireEntity(
    scoreId = summary.id.value,
    title = summary.title,
    composer = summary.composer,
    licence = licence,
    source = source,
    polyphony = summary.polyphony,
    skillKeys = SkillTagKeys.encodeSet(summary.skills),
    bars = summary.bars,
    defaultTempoBpm = summary.defaultTempoBpm,
    addedAtEpochMillis = addedAtEpochMillis,
)

internal fun RepertoireEntity.toEntry(): RepertoireEntry = RepertoireEntry(
    summary = ScoreSummary(
        id = ScoreId(scoreId),
        title = title,
        composer = composer,
        polyphony = polyphony,
        skills = SkillTagKeys.decodeSet(skillKeys),
        bars = bars,
        defaultTempoBpm = defaultTempoBpm,
    ),
    licence = licence,
    source = source,
    addedAtEpochMillis = addedAtEpochMillis,
)

internal fun PracticeSettings.toEntity(): SettingsEntity = SettingsEntity(
    tempoBpm = tempoBpm,
    metronomeOn = metronomeOn,
    listenFirstOn = listenFirstOn,
    inputLabel = inputLabel,
)

internal fun SettingsEntity.toSettings(): PracticeSettings = PracticeSettings(
    tempoBpm = tempoBpm,
    metronomeOn = metronomeOn,
    listenFirstOn = listenFirstOn,
    inputLabel = inputLabel,
)

internal fun AudioRouteLatencyEntity.toLatency(): InputLatency = InputLatency(millis, provenance)
