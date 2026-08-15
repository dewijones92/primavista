package com.dewijones92.primavista.score

import kotlin.math.abs

private val inStaffBands = listOf(PitchBand.LowerStaff, PitchBand.MiddleStaff, PitchBand.UpperStaff)

private val defaultMeasure = Measure(
    index = 0,
    start = Ticks.ZERO,
    time = TimeSignature.FourFour,
    key = KeySignature.C,
    clefs = mapOf(Staff.Upper to Clef.Treble, Staff.Lower to Clef.Bass),
)

/**
 * The one derivation of what a note asks you to read.
 */
public class DerivedScoreSkills : ScoreSkills {

    override fun bandOf(clef: Clef, pitch: Pitch): PitchBand {
        val step = StaffGeometry.stepOf(clef, pitch)
        val above = step - StaffGeometry.TOP_STEP
        return when {
            step < 0 -> outsideBand(-step, PitchBand.BelowStaff, PitchBand.FarBelowStaff)
            above > 0 -> outsideBand(above, PitchBand.AboveStaff, PitchBand.FarAboveStaff)
            else -> inStaffBands[step / StaffGeometry.BAND_STEPS]
        }
    }

    override fun legerLines(clef: Clef, pitch: Pitch): Int {
        val step = StaffGeometry.stepOf(clef, pitch)
        val above = (step - StaffGeometry.TOP_STEP).coerceAtLeast(0)
        val below = (-step).coerceAtLeast(0)
        return (above - below) / StaffGeometry.STEPS_PER_LEGER_LINE
    }

    override fun skillsOf(score: Score, attackIndex: Int): Set<SkillTag> {
        val attacks = score.attackedNotes
        require(attackIndex in attacks.indices) {
            "attack $attackIndex is outside the ${attacks.size} attacked notes of ${score.id.value}"
        }
        val note = attacks[attackIndex]
        val part = partKey(note)
        return skillsOf(ScoreContext(score), note, attacks.subList(0, attackIndex).lastOrNull { partKey(it) == part })
    }

    override fun skillsOf(score: Score): Set<SkillTag> {
        val context = ScoreContext(score)
        val previous = mutableMapOf<Int, Note>()
        val skills = mutableSetOf<SkillTag>()
        for (note in score.attackedNotes) {
            val part = partKey(note)
            skills += skillsOf(context, note, previous[part])
            previous[part] = note
        }
        return skills
    }

    private fun skillsOf(context: ScoreContext, note: Note, previous: Note?): Set<SkillTag> {
        val measure = context.measureAt(note.onset) ?: defaultMeasure
        val clef = measure.clefs[note.staff] ?: staffClefDefault(note.staff)
        val skills = mutableSetOf<SkillTag>(
            SkillTag.ClefRegion(clef, bandOf(clef, note.pitch)),
            SkillTag.KeyReading(measure.key.fifths),
            note.duration.figure,
        )
        val legerLines = legerLines(clef, note.pitch)
        if (legerLines != 0) skills += SkillTag.LegerLines(clef, abs(legerLines), above = legerLines > 0)
        if (note.pitch.alter != KeySignatureAlterations.impliedAlter(measure.key, note.pitch.letter)) {
            skills += SkillTag.Accidental(note.pitch.alter)
        }
        if (previous != null) {
            skills += SkillTag.Leap(abs(note.pitch.midi.number - previous.pitch.midi.number))
        }
        if (context.score.isGrandStaff && context.bothHandsSound(measure)) skills += SkillTag.HandIndependence
        return skills
    }

    private fun outsideBand(distance: Int, near: PitchBand, far: PitchBand): PitchBand =
        if (distance <= StaffGeometry.NEAR_OUTSIDE_STEPS) near else far

    private fun partKey(note: Note): Int = note.staff.ordinal * VOICE_KEY_STRIDE + note.voice

    private companion object {
        const val VOICE_KEY_STRIDE = 1000
    }
}

private class ScoreContext(val score: Score) {
    private val starts = score.measures.map { it.start.value }
    private val notes = score.notes
    private val bothHands = mutableMapOf<Int, Boolean>()

    fun measureAt(onset: Ticks): Measure? {
        if (score.measures.isEmpty()) return null
        var low = 0
        var high = starts.lastIndex
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (starts[mid] <= onset.value) low = mid else high = mid - 1
        }
        return score.measures[low]
    }

    fun bothHandsSound(measure: Measure): Boolean = bothHands.getOrPut(measure.index) {
        val from = measure.start.value
        val to = endOf(measure)
        notes.filter { it.onset.value < to && it.endsAt.value > from }
            .mapTo(mutableSetOf()) { it.staff }
            .size > 1
    }

    private fun endOf(measure: Measure): Long {
        val next = starts.firstOrNull { it > measure.start.value }
        return next ?: (measure.start.value + measure.time.measureTicks.value)
    }
}
