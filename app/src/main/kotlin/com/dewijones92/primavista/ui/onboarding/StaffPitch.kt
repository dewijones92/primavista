package com.dewijones92.primavista.ui.onboarding

import com.dewijones92.primavista.score.Clef
import com.dewijones92.primavista.score.KeySignature
import com.dewijones92.primavista.score.Pitch
import com.dewijones92.primavista.score.StaffGeometry

/**
 * The one idea the whole app rests on, as arithmetic: a perch height **is** a pitch.
 *
 * `TrillOnStaff` counts half-spaces from the middle line and `StaffGeometry` counts diatonic steps
 * from the bottom line, and both are the same unit — a note per staff position. The offset between
 * their origins is the four steps from the bottom line to the middle one, named once here so no
 * screen invents its own conversion. See `.claude/CODE-NOTES.md`.
 */
internal const val MIDDLE_LINE_STEP: Int = 4

/** Bottom line to top line inclusive, which is the whole staff and no leger lines. */
internal val PERCH_RANGE: IntRange = -MIDDLE_LINE_STEP..MIDDLE_LINE_STEP

internal fun perchPitch(perch: Int, clef: Clef = Clef.Treble): Pitch =
    StaffGeometry.pitchAt(StaffGeometry.diatonicIndexAt(clef, perch + MIDDLE_LINE_STEP), KeySignature.C)

internal fun perchName(perch: Int, clef: Clef = Clef.Treble): String = perchPitch(perch, clef).let {
    "${it.letter.name}${it.octave}"
}

/** Line or space, said in the words a beginner is actually taught. */
internal fun perchPlace(perch: Int): String {
    val fromBottom = perch + MIDDLE_LINE_STEP
    return if (fromBottom % 2 == 0) {
        "on the ${LINES.getOrElse(fromBottom / 2) { "" }} line"
    } else {
        "in the ${SPACES.getOrElse(fromBottom / 2) { "" }} space"
    }
}

private val LINES = listOf("bottom", "second", "middle", "fourth", "top")
private val SPACES = listOf("first", "second", "third", "fourth")
