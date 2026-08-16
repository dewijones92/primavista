package com.dewijones92.primavista.database

import com.dewijones92.primavista.score.Alter
import com.dewijones92.primavista.score.Clef
import com.dewijones92.primavista.score.KeySignature
import com.dewijones92.primavista.score.Midi
import com.dewijones92.primavista.score.NoteSymbol
import com.dewijones92.primavista.score.Staff
import com.dewijones92.primavista.score.TimeSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val FOUR_ACCIDENTALS = 4
private const val THREE_SHARPS = 3

class DifficultyCodecTest {
    @Test
    fun aFullSpecRoundTrips() {
        val spec = sampleSpec()

        assertEquals(spec, DifficultyCodec.decode(DifficultyCodec.encode(spec)))
    }

    @Test
    fun theReadingCeilingRoundTrips() {
        val spec = sampleSpec().copy(keys = setOf(KeySignature(1)), maxKeyAccidentals = FOUR_ACCIDENTALS)

        assertEquals(spec, DifficultyCodec.decode(DifficultyCodec.encode(spec)))
    }

    /**
     * Rows written before the reading ceiling was split from the writing key have no such field.
     * Defaulting it to the key's own size is exactly what those rows meant, so an old session stays
     * replayable from a report rather than becoming an unreadable origin.
     */
    @Test
    fun severalKeysRoundTrip() {
        val spec = sampleSpec().copy(keys = setOf(KeySignature(1), KeySignature(-1), KeySignature(2)))

        assertEquals(spec, DifficultyCodec.decode(DifficultyCodec.encode(spec)))
    }

    /**
     * Rows written when a level could only hold one key store a bare `fifths=3`, not a list — and
     * they still decode because a one-key spec encodes to exactly that. Asserted on the *text*
     * rather than on a round trip, because a round trip would agree with itself whatever the
     * format became, and what matters here is that the format did not move.
     */
    @Test
    fun aOneKeySpecStillEncodesAsABareFifthsSoOlderRowsKeepDecoding() {
        val stored = DifficultyCodec.encode(sampleSpec().copy(keys = setOf(KeySignature(THREE_SHARPS))))

        assertTrue(stored, stored.contains(";fifths=$THREE_SHARPS;"))

        val decoded = DifficultyCodec.decode(stored)
        assertEquals(setOf(KeySignature(THREE_SHARPS)), decoded?.keys)
        assertEquals(THREE_SHARPS, decoded?.maxKeyAccidentals)
    }

    /** A build that predates multi-key specs meets a list it cannot read, and says so. */
    @Test
    fun anOlderBuildMeetingSeveralKeysWouldGetAStatedReason() {
        val stored = DifficultyCodec.encode(sampleSpec().copy(keys = setOf(KeySignature(1), KeySignature(-1))))

        assertTrue(stored, stored.contains("fifths=-1,1"))
    }

    @Test
    fun aSpecStoredBeforeTheReadingCeilingExistedStillDecodes() {
        val spec = sampleSpec().copy(keys = setOf(KeySignature(THREE_SHARPS)))
        val withoutTheField = DifficultyCodec.encode(spec)
            .split(";")
            .filterNot { it.startsWith("maxKey=") }
            .joinToString(";")

        val decoded = DifficultyCodec.decode(withoutTheField)

        assertEquals(spec, decoded)
        assertEquals(THREE_SHARPS, decoded?.maxKeyAccidentals)
    }

    @Test
    fun aHandsSeparateSingleStaffSpecRoundTrips() {
        val spec = sampleSpec().copy(
            staves = listOf(Staff.Upper),
            clefs = mapOf(Staff.Upper to Clef.Bass),
            range = mapOf(Staff.Upper to Midi(36)..Midi(60)),
            keys = setOf(KeySignature(7)),
            time = TimeSignature(6, 8),
            symbols = setOf(NoteSymbol.Sixteenth),
            maxDots = 0,
            allowTuplets = false,
            allowedAlterations = setOf(Alter.Natural),
            bothHandsActive = false,
        )

        assertEquals(spec, DifficultyCodec.decode(DifficultyCodec.encode(spec)))
    }

    @Test
    fun encodingIsCanonicalSoTwoEqualSpecsProduceOneString() {
        val ordered = sampleSpec()
        val shuffled = ordered.copy(
            clefs = mapOf(Staff.Lower to Clef.Bass, Staff.Upper to Clef.Treble),
            symbols = setOf(NoteSymbol.Half, NoteSymbol.Eighth, NoteSymbol.Quarter),
            allowedAlterations = setOf(Alter.Sharp, Alter.Natural, Alter.Flat),
        )

        assertEquals(DifficultyCodec.encode(ordered), DifficultyCodec.encode(shuffled))
    }

    @Test
    fun theStoredFormCarriesItsVersion() {
        assertEquals(
            "v=${DifficultyCodec.VERSION}",
            DifficultyCodec.encode(sampleSpec()).substringBefore(';'),
        )
    }

    @Test
    fun anUnknownFieldFromALaterBuildIsIgnoredRatherThanFatal() {
        val encoded = DifficultyCodec.encode(sampleSpec()) + ";fromTheFuture=nonsense"

        assertEquals(sampleSpec(), DifficultyCodec.decode(encoded))
    }

    /**
     * A version bump means the fields no longer mean what they did, so reading one as if it were
     * this version would replay a *different* exercise than the report describes (spec I7).
     */
    @Test
    fun aStoredFormFromAnotherVersionIsRefusedRatherThanReadAsThisOne() {
        val encoded = DifficultyCodec.encode(sampleSpec())
        val version = "v=${DifficultyCodec.VERSION}"

        assertNull(DifficultyCodec.decode(encoded.replace(version, "v=${DifficultyCodec.VERSION + 1}")))
        assertNull(DifficultyCodec.decode(encoded.replace(version, "v=99")))
        assertNull(DifficultyCodec.decode(encoded.removePrefix("$version;")))
    }

    @Test
    fun aSpecWhoseStavesLackAClefOrARangeIsRefused() {
        val encoded = DifficultyCodec.encode(sampleSpec())

        assertNull(DifficultyCodec.decode(encoded.replace("clefs=Upper>Treble,Lower>Bass", "clefs=Upper>Treble")))
        assertNull(
            DifficultyCodec.decode(
                encoded.replace("clefs=Upper>Treble,Lower>Bass", "clefs=Upper>Treble,Upper>Bass"),
            ),
        )
        assertNull(DifficultyCodec.decode(encoded.replace("range=Upper>60-84,Lower>36-60", "range=Lower>36-60")))
        assertNull(DifficultyCodec.decode(encoded.replace("staves=Upper,Lower", "staves=Upper,Upper")))
    }

    /**
     * A corrupt field is not a field from a later build. Ignoring it would replay a *different*
     * exercise than the report names, which is the one thing the codec exists to prevent.
     */
    @Test
    fun aFieldThatIsNotNameEqualsValueIsRefusedRatherThanSkipped() {
        val encoded = DifficultyCodec.encode(sampleSpec())

        assertNull(DifficultyCodec.decode("$encoded;bars"))
        assertNull(DifficultyCodec.decode("$encoded;"))
    }

    @Test
    fun aFieldGivenTwoValuesIsRefusedRatherThanOneOfThemWinning() {
        val encoded = DifficultyCodec.encode(sampleSpec())

        assertNull(DifficultyCodec.decode("$encoded;bars=4"))
        assertNull(DifficultyCodec.decode(encoded.replace("clefs=", "clefs=Upper>Bass;clefs=")))
    }

    /** A dropped origin has to say why, or a report shows a blank where the exercise was. */
    @Test
    fun anUnreadableStoredFormNamesWhatWasWrongWithIt() {
        val reading = DifficultyCodec.read(DifficultyCodec.encode(sampleSpec()).replace("bars=8", "bars=nope"))

        assertTrue("$reading", reading is SpecReading.Unreadable)
        assertTrue((reading as SpecReading.Unreadable).reason.contains("nope"))
    }

    @Test
    fun anUnreadableStoredFormIsNullRatherThanACrash() {
        listOf(
            "",
            "v=1",
            DifficultyCodec.encode(sampleSpec()).replace("staves=Upper,Lower", "staves=Nonsense"),
            DifficultyCodec.encode(sampleSpec()).replace("bars=8", "bars=0"),
            DifficultyCodec.encode(sampleSpec()).replace("beatUnit=4", "beatUnit=3"),
            DifficultyCodec.encode(sampleSpec()).replace("range=", "range=Upper>60"),
            DifficultyCodec.encode(sampleSpec()).replace("tuplets=true", "tuplets=yes"),
            DifficultyCodec.encode(sampleSpec()).replace("fifths=-3", "fifths=-9"),
        ).forEach { encoded ->
            assertNull("expected '$encoded' to be unreadable", DifficultyCodec.decode(encoded))
        }
    }

    /**
     * Every refusal has to carry a reason a human can act on. An empty `fifths=` used to reach
     * `max()` on an empty list, whose `NoSuchElementException` has a null message — so the row came
     * back as `Unreadable(reason = "java.util.NoSuchElementException")`, a blank where docs/spec.md
     * I7 and this type's own contract both demand a sentence.
     */
    @Test
    fun aStoredSpecNamingNoKeyRefusesWithAReasonRatherThanAnExceptionName() {
        val stored = DifficultyCodec.encode(sampleSpec()).replace(";fifths=-3;", ";fifths=;")

        val reading = DifficultyCodec.read(stored)

        assertTrue("$reading", reading is SpecReading.Unreadable)
        val reason = (reading as SpecReading.Unreadable).reason
        assertTrue(reason, reason.contains("no key"))
    }
}
