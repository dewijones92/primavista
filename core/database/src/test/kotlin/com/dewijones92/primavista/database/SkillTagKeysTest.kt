package com.dewijones92.primavista.database

import com.dewijones92.primavista.score.Alter
import com.dewijones92.primavista.score.Clef
import com.dewijones92.primavista.score.NoteSymbol
import com.dewijones92.primavista.score.PitchBand
import com.dewijones92.primavista.score.SkillTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The literal keys are asserted deliberately: a key that changes shape silently resets Dewi's
 * progress, so a rename must break a test rather than a strength.
 */
class SkillTagKeysTest {
    private val cases: List<Pair<SkillTag, String>> = listOf(
        SkillTag.ClefRegion(Clef.Treble, PitchBand.MiddleStaff) to "clefRegion|Treble|MiddleStaff",
        SkillTag.ClefRegion(Clef.Bass, PitchBand.FarBelowStaff) to "clefRegion|Bass|FarBelowStaff",
        SkillTag.ClefRegion(Clef.Alto, PitchBand.UpperStaff) to "clefRegion|Alto|UpperStaff",
        SkillTag.LegerLines(Clef.Bass, 3, above = false) to "legerLines|Bass|3|false",
        SkillTag.LegerLines(Clef.Treble, 2, above = true) to "legerLines|Treble|2|true",
        SkillTag.Accidental(Alter.DoubleFlat) to "accidental|-2",
        SkillTag.Accidental(Alter.Sharp) to "accidental|1",
        SkillTag.KeyReading(-7) to "keyReading|-7",
        SkillTag.KeyReading(0) to "keyReading|0",
        SkillTag.RhythmFigure(NoteSymbol.Eighth, dots = 1, tupletNumerator = 3) to "rhythmFigure|Eighth|1|3",
        SkillTag.RhythmFigure(NoteSymbol.DoubleWhole, dots = 0, tupletNumerator = 1) to
            "rhythmFigure|DoubleWhole|0|1",
        SkillTag.Leap(-12) to "leap|-12",
        SkillTag.HandIndependence to "handIndependence",
    )

    @Test
    fun everySubtypeEncodesToItsAgreedKey() {
        cases.forEach { (tag, key) ->
            assertEquals(key, SkillTagKeys.encode(tag))
        }
    }

    @Test
    fun everySubtypeRoundTripsThroughItsKey() {
        cases.forEach { (tag, key) ->
            assertEquals("decoding $key", tag, SkillTagKeys.decode(key))
        }
    }

    @Test
    fun theKindOfEveryKeyMatchesItsSubtype() {
        cases.forEach { (tag, key) ->
            assertEquals(kindOf(tag), key.substringBefore(SkillTagKeys.FIELD_SEPARATOR))
        }
    }

    @Test
    fun theTableExercisesEverySubtype() {
        assertEquals(7, cases.map { kindOf(it.first) }.toSet().size)
    }

    /** The two sides of the staff are different skills, so they must not share a key. */
    @Test
    fun aLegerLineKeyDistinguishesAboveFromBelow() {
        val above = SkillTag.LegerLines(Clef.Bass, 2, above = true)
        val below = SkillTag.LegerLines(Clef.Bass, 2, above = false)

        assertTrue(SkillTagKeys.encode(above) != SkillTagKeys.encode(below))
        assertEquals(above, SkillTagKeys.decode(SkillTagKeys.encode(above)))
        assertEquals(below, SkillTagKeys.decode(SkillTagKeys.encode(below)))
    }

    /**
     * The v1 key held a count and no side, so no reading of it is honest. Guessing would move
     * Dewi's below-the-staff strength onto an above-the-staff skill he has never read.
     */
    @Test
    fun aFormatV1LegerLineKeyIsDiscardedWithAReasonRatherThanGuessedAt() {
        val reading = SkillTagKeys.read("legerLines|Bass|3")

        assertTrue("expected the v1 key to be refused", reading is SkillKeyReading.Unreadable)
        val reason = (reading as SkillKeyReading.Unreadable).reason
        assertTrue("the reason must name the format: $reason", reason.contains("v1"))
        assertTrue("the reason must say what was missing: $reason", reason.contains("side"))
        assertNull(SkillTagKeys.decode("legerLines|Bass|3"))
    }

    @Test
    fun anUnreadableKeyIsNullRatherThanACrash() {
        listOf(
            "",
            "clefRegion",
            "clefRegion|Treble",
            "clefRegion|Tenor|MiddleStaff",
            "legerLines|Bass|many|true",
            "legerLines|Bass|3|maybe",
            "accidental|9",
            "rhythmFigure|Eighth|1",
            "somethingFromTheFuture|1|2",
        ).forEach { key ->
            assertNull("expected '$key' to be unreadable", SkillTagKeys.decode(key))
        }
    }

    /** A trailing field means a later build added one, so the key names a skill this one has not got. */
    @Test
    fun aKeyCarryingAFieldThisBuildDoesNotKnowIsUnreadable() {
        cases.forEach { (_, key) ->
            val extended = "$key${SkillTagKeys.FIELD_SEPARATOR}fromTheFuture"
            assertNull("expected '$extended' to be unreadable", SkillTagKeys.decode(extended))
        }
    }

    /** Every refusal carries a reason, because a dropped row with no log is an invisible loss. */
    @Test
    fun everyUnreadableKeySaysWhy() {
        listOf("", "clefRegion|Tenor|MiddleStaff", "legerLines|Bass|3", "leap|007", "handIndependence|1")
            .forEach { key ->
                val reading = SkillTagKeys.read(key)
                assertTrue("'$key' should be unreadable", reading is SkillKeyReading.Unreadable)
                assertTrue(
                    "'$key' was refused with an empty reason",
                    (reading as SkillKeyReading.Unreadable).reason.isNotBlank(),
                )
            }
    }

    @Test
    fun aKeyThatIsNotSpelledTheWayThisBuildWritesItIsUnreadable() {
        listOf("accidental|+1", "leap|007", "keyReading|-0", "legerLines|Bass|3|TRUE").forEach { key ->
            assertNull("expected '$key' to be unreadable", SkillTagKeys.decode(key))
        }
    }

    @Test
    fun aSetOfTagsRoundTripsAndIsOrderIndependent() {
        val tags = cases.map { it.first }.toSet()
        val encoded = SkillTagKeys.encodeSet(tags)

        assertEquals(tags, SkillTagKeys.decodeSet(encoded))
        assertEquals(encoded, SkillTagKeys.encodeSet(tags.reversed().toSet()))
        assertEquals(emptySet<SkillTag>(), SkillTagKeys.decodeSet(SkillTagKeys.encodeSet(emptySet())))
    }

    @Test
    fun readSetReportsTheKeysItDroppedRatherThanShrinkingSilently() {
        val encoded = listOf("legerLines|Bass|3", SkillTagKeys.encode(SkillTag.HandIndependence))
            .joinToString(SkillTagKeys.SET_SEPARATOR)

        val readings = SkillTagKeys.readSet(encoded)

        assertEquals(2, readings.size)
        assertEquals(setOf(SkillTag.HandIndependence), SkillTagKeys.decodeSet(encoded))
        assertEquals("legerLines|Bass|3", readings.filterIsInstance<SkillKeyReading.Unreadable>().single().key)
    }

    /** Exhaustive by construction. */
    private fun kindOf(tag: SkillTag): String = when (tag) {
        is SkillTag.ClefRegion -> SkillTagKeys.CLEF_REGION
        is SkillTag.LegerLines -> SkillTagKeys.LEGER_LINES
        is SkillTag.Accidental -> SkillTagKeys.ACCIDENTAL
        is SkillTag.KeyReading -> SkillTagKeys.KEY_READING
        is SkillTag.RhythmFigure -> SkillTagKeys.RHYTHM_FIGURE
        is SkillTag.Leap -> SkillTagKeys.LEAP
        SkillTag.HandIndependence -> SkillTagKeys.HAND_INDEPENDENCE
    }
}
