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
import org.junit.Test

class DifficultyCodecTest {
    @Test
    fun aFullSpecRoundTrips() {
        val spec = sampleSpec()

        assertEquals(spec, DifficultyCodec.decode(DifficultyCodec.encode(spec)))
    }

    @Test
    fun aHandsSeparateSingleStaffSpecRoundTrips() {
        val spec = sampleSpec().copy(
            staves = listOf(Staff.Upper),
            clefs = mapOf(Staff.Upper to Clef.Bass),
            range = mapOf(Staff.Upper to Midi(36)..Midi(60)),
            key = KeySignature(7),
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
}
