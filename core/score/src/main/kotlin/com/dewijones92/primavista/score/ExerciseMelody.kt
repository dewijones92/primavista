package com.dewijones92.primavista.score

import kotlin.math.abs
import kotlin.random.Random

private const val STEP_WEIGHT = 8
private const val REPEAT_WEIGHT = 2
private const val LEAP_WEIGHT = 1
private const val ACCIDENTAL_PERCENT = 22
private const val PERCENT = 100
private const val LADDER_SEARCH_OCTAVES = 11

/**
 * The pitches of the key that fit inside a staff's range, low to high. Working in scale steps
 * rather than semitones is what makes "mostly stepwise" mean *musically* stepwise.
 */
internal fun scaleLadder(key: KeySignature, range: ClosedRange<Midi>): List<Pitch> {
    val lowest = range.start.number
    val highest = range.endInclusive.number
    return (0 until LADDER_SEARCH_OCTAVES * Pitch.LETTERS_PER_OCTAVE)
        .map { StaffGeometry.pitchAt(it, key) }
        .filter { StaffGeometry.soundingNumber(it) in lowest..highest }
        .sortedBy { StaffGeometry.soundingNumber(it) }
}

internal fun keyPitchClasses(key: KeySignature): Set<Int> =
    Letter.entries
        .map {
            (it.semitonesFromC + KeySignatureAlterations.impliedAlter(key, it).semitones)
                .mod(Pitch.SEMITONES_PER_OCTAVE)
        }
        .toSet()

/**
 * Walks a readable line: mostly next-door scale steps, occasional leaps, never wider than the
 * spec allows and never outside the staff's range.
 */
internal class MelodyWalker(
    private val ladder: List<Pitch>,
    private val extras: List<Alter>,
    private val maxLeapSemitones: Int,
    private val range: ClosedRange<Midi>,
    private val keyPitchClasses: Set<Int>,
    private val random: Random,
) {
    init {
        require(ladder.isNotEmpty()) { "no pitch of the key fits the range $range" }
    }

    private var index = ladder.size / 2
    private var started = false
    private var lastSounding = StaffGeometry.soundingNumber(ladder[ladder.size / 2])

    fun next(): Pitch {
        if (started) index = nextIndex()
        started = true
        val pitch = withOptionalAccidental(ladder[index])
        lastSounding = StaffGeometry.soundingNumber(pitch)
        return pitch
    }

    private fun nextIndex(): Int {
        val reachable = ladder.indices.filter {
            abs(StaffGeometry.soundingNumber(ladder[it]) - lastSounding) <= maxLeapSemitones
        }
        if (reachable.isEmpty()) return index
        var ticket = random.nextInt(reachable.sumOf { weightOf(it) })
        for (candidate in reachable) {
            ticket -= weightOf(candidate)
            if (ticket < 0) return candidate
        }
        return reachable.last()
    }

    private fun weightOf(candidate: Int): Int = when (abs(candidate - index)) {
        0 -> REPEAT_WEIGHT
        1 -> STEP_WEIGHT
        else -> LEAP_WEIGHT
    }

    private fun withOptionalAccidental(base: Pitch): Pitch {
        if (extras.isEmpty() || random.nextInt(PERCENT) >= ACCIDENTAL_PERCENT) return base
        val altered = base.copy(alter = extras[random.nextInt(extras.size)])
        val sounding = StaffGeometry.soundingNumber(altered)
        val fits = sounding in range.start.number..range.endInclusive.number &&
            abs(sounding - lastSounding) <= maxLeapSemitones &&
            sounding.mod(Pitch.SEMITONES_PER_OCTAVE) !in keyPitchClasses
        return if (fits) altered else base
    }
}
