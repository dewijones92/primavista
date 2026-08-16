package com.dewijones92.primavista.score

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Naming the key under a finger.
 *
 * The keyboard had no accessibility labels at all, so its keys were unreachable to a screen reader —
 * and the microphone is the only other way in. These name every key, which is separate from naming
 * a *note*: a key has no notation behind it, so nothing can say whether it is F sharp or G flat.
 */
class KeyNamesTest {

    @Test
    fun `middle C is C4`() {
        assertEquals("C4", Midi(Midi.MIDDLE_C).asKey().shortName)
    }

    @Test
    fun `concert A is A4`() {
        assertEquals("A4", Midi(Midi.A4).asKey().shortName)
    }

    @Test
    fun `a black key is named as a sharp`() {
        assertEquals("C#4", Midi(Midi.MIDDLE_C + 1).asKey().shortName)
        assertEquals("A#4", Midi(Midi.A4 + 1).asKey().shortName)
    }

    /** The round trip that matters: naming a key must not move it. */
    @Test
    fun `every key in MIDI names a pitch that sounds as that key`() {
        for (number in Midi.MIN..Midi.MAX) {
            assertEquals("key $number", number, Midi(number).asKey().midi.number)
        }
    }

    @Test
    fun `octaves change at C and not at A`() {
        assertEquals(3, Midi(Midi.MIDDLE_C - 1).asKey().octave)
        assertEquals(4, Midi(Midi.MIDDLE_C).asKey().octave)
        assertEquals(4, Midi(Midi.MIDDLE_C + 11).asKey().octave)
        assertEquals(5, Midi(Midi.MIDDLE_C + 12).asKey().octave)
    }

    /** The very bottom of MIDI is C-1, and negative octaves must not break the arithmetic. */
    @Test
    fun `the lowest key in MIDI is C minus one`() {
        val lowest = Midi(Midi.MIN).asKey()

        assertEquals(Letter.C, lowest.letter)
        assertEquals(-1, lowest.octave)
        assertEquals(0, lowest.midi.number)
    }

    /** A screen reader saying "C hash 4" would be worse than no label. */
    @Test
    fun `the spoken name says sharp in words rather than in punctuation`() {
        val spoken = Midi(Midi.MIDDLE_C + 1).asKey().spokenName

        assertEquals("C sharp 4", spoken)
        assertTrue(spoken, !spoken.contains("#"))
    }

    @Test
    fun `a natural key is spoken as just its letter and octave`() {
        assertEquals("A 4", Midi(Midi.A4).asKey().spokenName)
    }

    /** Written accidentals still spell properly — this names notes as well as keys. */
    @Test
    fun `a flat written in notation keeps its flat in both names`() {
        val gFlat = Pitch(Letter.G, Alter.Flat, 4)

        assertEquals("Gb4", gFlat.shortName)
        assertEquals("G flat 4", gFlat.spokenName)
    }

    /** F sharp and G flat are one key and two notes; naming the key cannot know which. */
    @Test
    fun `one key has one name even where notation has two`() {
        val fSharp = Pitch(Letter.F, Alter.Sharp, 4)
        val gFlat = Pitch(Letter.G, Alter.Flat, 4)

        assertEquals(fSharp.midi, gFlat.midi)
        assertEquals(fSharp.shortName, fSharp.midi.asKey().shortName)
        assertEquals(fSharp.shortName, gFlat.midi.asKey().shortName)
    }
}
