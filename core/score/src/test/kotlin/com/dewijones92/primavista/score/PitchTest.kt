package com.dewijones92.primavista.score

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PitchTest {

    @Test
    fun `enharmonics sound the same and are written differently`() {
        val fSharp4 = Pitch(Letter.F, Alter.Sharp, 4)
        val gFlat4 = Pitch(Letter.G, Alter.Flat, 4)
        assertEquals(fSharp4.midi, gFlat4.midi)
        assertNotEquals(fSharp4.diatonicIndex, gFlat4.diatonicIndex)
        assertEquals(1, gFlat4.diatonicIndex - fSharp4.diatonicIndex)
    }

    @Test
    fun `C flat 4 sounds as B3 but sits on the C line`() {
        val cFlat4 = Pitch(Letter.C, Alter.Flat, 4)
        val b3 = Pitch(Letter.B, Alter.Natural, 3)
        assertEquals(b3.midi, cFlat4.midi)
        assertEquals(Pitch(Letter.C, Alter.Natural, 4).diatonicIndex, cFlat4.diatonicIndex)
        assertTrue(cFlat4.diatonicIndex > b3.diatonicIndex)
    }

    @Test
    fun `middle C is MIDI 60 and A4 is 69`() {
        assertEquals(Midi.MIDDLE_C, Pitch(Letter.C, Alter.Natural, 4).midi.number)
        assertEquals(Midi.A4, Pitch(Letter.A, Alter.Natural, 4).midi.number)
    }

    @Test
    fun `a pitch outside the MIDI range refuses to sound`() {
        assertThrows(IllegalArgumentException::class.java) { Pitch(Letter.C, Alter.Natural, -3).midi }
        assertThrows(IllegalArgumentException::class.java) { Alter(3) }
    }

    @Test
    fun `a key signature says which letters it alters`() {
        assertEquals(emptyList<Letter>(), KeySignatureAlterations.alteredLetters(KeySignature.C))
        assertEquals(
            listOf(Letter.F, Letter.C, Letter.G),
            KeySignatureAlterations.alteredLetters(KeySignature(3)),
        )
        assertEquals(
            listOf(Letter.B, Letter.E, Letter.A, Letter.D),
            KeySignatureAlterations.alteredLetters(KeySignature(-4)),
        )
        assertEquals(Alter.Sharp, KeySignatureAlterations.impliedAlter(KeySignature(1), Letter.F))
        assertEquals(Alter.Natural, KeySignatureAlterations.impliedAlter(KeySignature(1), Letter.C))
        assertEquals(Alter.Flat, KeySignatureAlterations.impliedAlter(KeySignature(-2), Letter.E))
        assertEquals(Alter.Natural, KeySignatureAlterations.impliedAlter(KeySignature(0), Letter.B))
    }

    @Test
    fun `the diatonic ladder of a key holds only that key's pitches`() {
        val ladder = scaleLadder(KeySignature(1), Midi(60)..Midi(72))
        assertEquals(
            listOf("C4", "D4", "E4", "F#4", "G4", "A4", "B4", "C5"),
            ladder.map { "${it.letter}${if (it.alter == Alter.Sharp) "#" else ""}${it.octave}" },
        )
    }
}
