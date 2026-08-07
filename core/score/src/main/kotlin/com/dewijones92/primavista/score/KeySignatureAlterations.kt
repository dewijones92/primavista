package com.dewijones92.primavista.score

private val sharpOrder = listOf(Letter.F, Letter.C, Letter.G, Letter.D, Letter.A, Letter.E, Letter.B)

/**
 * What a key signature does to each letter. One derivation, because three places need it and
 * they must never disagree: the skill deriver (is this accidental a *reading* skill, or just
 * the key?), the generator (which letters are in the key), and the layout engine (which
 * accidentals to draw at the clef).
 */
public object KeySignatureAlterations {
    public fun impliedAlter(key: KeySignature, letter: Letter): Alter {
        val position = orderFor(key).indexOf(letter)
        if (position < 0 || position >= key.accidentalCount) return Alter.Natural
        return if (key.isSharpKey) Alter.Sharp else Alter.Flat
    }

    public fun alteredLetters(key: KeySignature): List<Letter> = orderFor(key).take(key.accidentalCount)

    private fun orderFor(key: KeySignature): List<Letter> =
        if (key.isSharpKey) sharpOrder else sharpOrder.asReversed()
}
