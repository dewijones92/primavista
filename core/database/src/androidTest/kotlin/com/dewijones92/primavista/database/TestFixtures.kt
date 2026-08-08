package com.dewijones92.primavista.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dewijones92.primavista.practice.InputLatency
import com.dewijones92.primavista.score.Alter
import com.dewijones92.primavista.score.Clef
import com.dewijones92.primavista.score.DifficultySpec
import com.dewijones92.primavista.score.KeySignature
import com.dewijones92.primavista.score.Midi
import com.dewijones92.primavista.score.NoteSymbol
import com.dewijones92.primavista.score.Polyphony
import com.dewijones92.primavista.score.ScoreId
import com.dewijones92.primavista.score.ScoreOrigin
import com.dewijones92.primavista.score.Staff
import com.dewijones92.primavista.score.TimeSignature

internal fun openTestDatabase(): PrimaVistaDatabase =
    Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        PrimaVistaDatabase::class.java,
    )
        .addCallback(PrimaVistaDatabase.ForeignKeysOn)
        .build()

/** A refusal in a test that expected data is a failure with its reason, never a silent empty list. */
internal fun <T> StoredReading<T>.readOrFail(): T = when (this) {
    is StoredReading.Readable -> value
    is StoredReading.Unreadable -> throw AssertionError("the read of $what was refused: $reason")
}

/** Reads the pragma back, because a cascade test passes silently when enforcement is off. */
internal fun PrimaVistaDatabase.foreignKeysOn(): Boolean =
    query("PRAGMA foreign_keys", emptyArray()).use { cursor ->
        cursor.moveToFirst() && cursor.getInt(0) == 1
    }

internal fun sampleSpec(): DifficultySpec = DifficultySpec(
    staves = listOf(Staff.Upper, Staff.Lower),
    clefs = mapOf(Staff.Upper to Clef.Treble, Staff.Lower to Clef.Bass),
    key = KeySignature(-3),
    time = TimeSignature(3, 4),
    bars = 8,
    range = mapOf(
        Staff.Upper to Midi(60)..Midi(84),
        Staff.Lower to Midi(36)..Midi(60),
    ),
    symbols = setOf(NoteSymbol.Quarter, NoteSymbol.Eighth, NoteSymbol.Half),
    maxDots = 1,
    allowTuplets = true,
    allowedAlterations = setOf(Alter.Flat, Alter.Natural, Alter.Sharp),
    maxLeapSemitones = 7,
    tempoBpm = 76,
    bothHandsActive = true,
)

internal fun sampleSession(
    id: String = "session-1",
    origin: ScoreOrigin = ScoreOrigin.Generated(seed = 4_242L, spec = sampleSpec()),
    finishedAtEpochMillis: Long? = 1_700_000_060_000L,
    notesExpected: Int = 12,
    correct: Int = 9,
): StoredSession = StoredSession(
    id = SessionId(id),
    scoreId = ScoreId("score-1"),
    scoreTitle = "Study in A minor",
    origin = origin,
    inputLabel = "tap",
    polyphony = Polyphony.Poly,
    tempoBpm = 76,
    latency = InputLatency(61.5, InputLatency.Provenance.Measured),
    startedAtEpochMillis = 1_700_000_000_000L,
    finishedAtEpochMillis = finishedAtEpochMillis,
    notesExpected = notesExpected,
    correct = correct,
)
