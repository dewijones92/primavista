package com.dewijones92.primavista.score

import org.w3c.dom.Element

private val clefsBySignAndLine = mapOf(
    "G2" to Clef.Treble,
    "F4" to Clef.Bass,
    "C3" to Clef.Alto,
)

private val defaultClefLines = mapOf("G" to 2, "F" to 4, "C" to 3)

private val repeatMarkings = setOf("repeat", "ending")

internal fun repeatDrops(element: Element, bar: Int): List<Dropped> =
    element.elements()
        .filter { it.tagName in repeatMarkings }
        .map { Dropped(it.tagName, bar, "repeats and endings are not read; the music is read straight through") }

internal fun staffOf(number: Int): Staff? = Staff.entries.getOrNull(number - 1)

internal fun staffClefDefault(staff: Staff): Clef = if (staff == Staff.Upper) Clef.Treble else Clef.Bass

internal fun readDivisionsElement(element: Element): Int? =
    element.textContent.trim().toIntOrNull()?.takeIf { it > 0 }

internal fun readKeyElement(element: Element): KeySignature? =
    element.intOf("fifths")?.let { fifths ->
        runCatching { KeySignature(fifths) }.getOrNull()
    }

internal fun readTimeElement(element: Element): TimeSignature? {
    val beats = element.intOf("beats") ?: return null
    val beatUnit = element.intOf("beat-type") ?: return null
    return runCatching { TimeSignature(beats, beatUnit) }.getOrNull()
}

internal fun readClefElement(element: Element): Pair<Staff, Clef>? {
    val sign = element.textOf("sign")?.uppercase() ?: return null
    val line = element.intOf("line") ?: defaultClefLines[sign] ?: return null
    val clef = clefsBySignAndLine["$sign$line"] ?: return null
    val staff = staffOf(element.attr("number")?.toIntOrNull() ?: 1) ?: return null
    return staff to clef
}

/** MusicXML `<divisions>` is per quarter note; this app counts in [MusicalTime.TICKS_PER_QUARTER]. */
internal fun divisionsToTicks(duration: Int, divisions: Int): Ticks? {
    if (divisions <= 0) return null
    val scaled = duration.toLong() * MusicalTime.TICKS_PER_QUARTER
    if (scaled % divisions != 0L) return null
    return Ticks(scaled / divisions)
}

/**
 * The written duration that lasts exactly [ticks], when the source gave no `<type>`.
 */
internal fun durationOfTicks(ticks: Ticks): Duration? {
    for (symbol in NoteSymbol.entries) {
        for (dots in 0..Duration.MAX_DOTS) {
            val candidate = Duration(symbol, dots)
            if (candidate.ticksOrNull == ticks) return candidate
        }
    }
    return null
}
