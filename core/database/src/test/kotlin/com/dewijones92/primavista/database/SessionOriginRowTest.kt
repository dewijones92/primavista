package com.dewijones92.primavista.database

import com.dewijones92.primavista.practice.InputLatency
import com.dewijones92.primavista.score.Polyphony
import com.dewijones92.primavista.score.ScoreOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A session outlives the build that wrote it, so the row has to say what it held even when this
 * build cannot rebuild the origin from it (docs/spec.md I4, I7).
 */
class SessionOriginRowTest {
    private val generated = SessionEntity(
        id = "session-1",
        scoreId = "score-1",
        scoreTitle = "Study in A minor",
        originKind = OriginKinds.GENERATED,
        originSourceName = null,
        originLicence = null,
        originSeed = 4_242L,
        originSpec = DifficultyCodec.encode(sampleSpec()),
        inputLabel = "tap",
        polyphony = Polyphony.Mono,
        tempoBpm = 76,
        latencyMillis = 61.5,
        latencyProvenance = InputLatency.Provenance.Measured,
        startedAtEpochMillis = 1_700_000_000_000L,
        finishedAtEpochMillis = 1_700_000_060_000L,
        notesExpected = 12,
        correct = 9,
    )

    @Test
    fun aGeneratedOriginComesBackWithTheSeedAndSpecThatReplayIt() {
        val stored = generated.toStored()

        assertEquals(ScoreOrigin.Generated(4_242L, sampleSpec()), stored.origin)
        assertTrue(stored.originDescriptor.contains("seed=4242"))
    }

    @Test
    fun aParsedOriginComesBackWithItsSourceAndLicence() {
        val row = generated.copy(
            originKind = OriginKinds.PARSED,
            originSourceName = "bach-minuet.musicxml",
            originLicence = "public domain",
            originSeed = null,
            originSpec = null,
        )

        assertEquals(ScoreOrigin.Parsed("bach-minuet.musicxml", "public domain"), row.toStored().origin)
    }

    /** The verdicts, tempo and accuracy are still worth keeping; only the replay is lost. */
    @Test
    fun anUnreadableSpecLosesTheOriginAndSaysWhyRatherThanLeavingABlank() {
        val stored = generated.copy(originSpec = "v=1;bars=nope").toStored()

        assertNull(stored.origin)
        assertEquals(9, stored.correct)
        assertTrue(stored.originDescriptor, stored.originDescriptor.contains("why="))
        assertTrue(stored.originDescriptor, stored.originDescriptor.contains("spec=v=1;bars=nope"))
    }

    @Test
    fun everyWayAnOriginCanBeMissingNamesItself() {
        listOf(
            generated.copy(originSeed = null) to "seed",
            generated.copy(originSpec = null) to "spec",
            generated.copy(originKind = OriginKinds.PARSED, originSourceName = null) to "source name",
            generated.copy(originKind = OriginKinds.UNKNOWN) to "no origin",
            generated.copy(originKind = "fromTheFuture") to "fromTheFuture",
        ).forEach { (row, expected) ->
            val reading = row.readOrigin()

            assertTrue("$row was readable", reading is OriginReading.Unreadable)
            assertTrue(
                "'${(reading as OriginReading.Unreadable).reason}' does not mention '$expected'",
                reading.reason.contains(expected),
            )
        }
    }
}
