package com.dewijones92.primavista.score

import org.w3c.dom.Element
import kotlin.math.floor

private val symbolsByTypeName = mapOf(
    "breve" to NoteSymbol.DoubleWhole,
    "whole" to NoteSymbol.Whole,
    "half" to NoteSymbol.Half,
    "quarter" to NoteSymbol.Quarter,
    "eighth" to NoteSymbol.Eighth,
    "16th" to NoteSymbol.Sixteenth,
    "32nd" to NoteSymbol.ThirtySecond,
)

private val handledNoteChildren = setOf(
    "pitch", "rest", "duration", "type", "dot", "chord", "voice", "staff",
    "tie", "notations", "time-modification", "grace", "cue", "unpitched",
)

private val ignoredNoteChildren = setOf(
    "stem",
    "beam",
    "accidental",
    "instrument",
    "footnote",
    "level",
    "play",
    "listen",
)

private val ignoredNotations = setOf("tuplet")

private val unreadableNoteKinds = listOf("grace", "cue", "unpitched")

private const val TIE_START = "start"
private const val TIE_STOP = "stop"

internal class NoteCursor(
    val bar: Int,
    val divisions: Int,
    val cursor: Ticks,
    val chordOnset: Ticks,
)

internal class NoteRead(
    val event: ScoreEvent?,
    val isChord: Boolean,
    val length: Ticks,
    val warnings: List<Dropped>,
)

private class Placement(
    val onset: Ticks,
    val duration: Duration,
    val staff: Staff,
    val voice: Int,
)

internal fun readNoteElement(element: Element, at: NoteCursor): NoteRead {
    val warnings = mutableListOf<Dropped>()
    warnings += unsupportedNoteChildren(element, at.bar)
    val isChord = element.first("chord") != null
    val occupied = element.intOf("duration")?.let { divisionsToTicks(it, at.divisions) } ?: Ticks.ZERO
    val unreadable = unreadableNoteKinds.firstOrNull { element.first(it) != null }
    if (unreadable != null) {
        warnings += Dropped(unreadable, at.bar, "$unreadable notes are not read")
        return NoteRead(null, isChord, occupied, warnings)
    }
    val duration = notatedDuration(element, at, warnings)
    if (duration == null) return NoteRead(null, isChord, occupied, warnings)
    val staffNumber = element.intOf("staff") ?: 1
    val staff = staffOf(staffNumber)
    if (staff == null) {
        warnings += Dropped("staff", at.bar, "staff $staffNumber is beyond the ${Staff.entries.size} this app reads")
        return NoteRead(null, isChord, occupied, warnings)
    }
    val placement = Placement(
        onset = if (isChord) at.chordOnset else at.cursor,
        duration = duration,
        staff = staff,
        voice = element.intOf("voice") ?: 1,
    )
    val event =
        if (element.first("rest") != null) restEvent(placement) else noteEvent(element, at.bar, placement, warnings)
    return NoteRead(event, isChord, duration.ticks, warnings)
}

private fun restEvent(placement: Placement): Rest =
    Rest(placement.onset, placement.duration, placement.staff, placement.voice)

private fun noteEvent(element: Element, bar: Int, placement: Placement, warnings: MutableList<Dropped>): Note? {
    val pitchElement = element.first("pitch")
    if (pitchElement == null) {
        warnings += Dropped("note", bar, "note has neither <pitch> nor <rest>")
        return null
    }
    val pitch = readPitch(pitchElement, bar, warnings) ?: return null
    val ties = tieTypesOf(element)
    return Note(
        onset = placement.onset,
        duration = placement.duration,
        staff = placement.staff,
        voice = placement.voice,
        pitch = pitch,
        tiedFromPrevious = TIE_STOP in ties,
        tiedToNext = TIE_START in ties,
    )
}

private fun readPitch(element: Element, bar: Int, warnings: MutableList<Dropped>): Pitch? {
    val step = element.textOf("step")?.uppercase()
    val letter = Letter.entries.firstOrNull { it.name == step }
    val octave = element.intOf("octave")
    if (letter == null || octave == null) {
        warnings += Dropped("pitch", bar, "unreadable step/octave '$step'/'${element.textOf("octave")}'")
        return null
    }
    val alterText = element.textOf("alter")
    val alterValue = alterText?.toDoubleOrNull()
    if (alterText != null && (alterValue == null || alterValue != floor(alterValue))) {
        warnings += Dropped("alter", bar, "microtonal alteration '$alterText' is not read")
        return null
    }
    val alter = runCatching { Alter(alterValue?.toInt() ?: 0) }.getOrNull()
    if (alter == null) {
        warnings += Dropped("alter", bar, "alteration '$alterText' is beyond a double sharp or flat")
        return null
    }
    val pitch = Pitch(letter, alter, octave)
    if (runCatching { pitch.midi }.isFailure) {
        warnings += Dropped("pitch", bar, "$letter$octave is outside the MIDI range")
        return null
    }
    return pitch
}

private fun notatedDuration(element: Element, at: NoteCursor, warnings: MutableList<Dropped>): Duration? {
    val fileTicks = element.intOf("duration")?.let { divisionsToTicks(it, at.divisions) }
    if (element.intOf("duration") != null && fileTicks == null) {
        warnings += Dropped(
            "duration",
            at.bar,
            "${element.intOf("duration")} of ${at.divisions} divisions is not a whole tick",
        )
        return null
    }
    val typeName = element.textOf("type")
    val duration = if (typeName != null) {
        durationOfType(
            element,
            typeName,
            at,
            warnings
        )
    } else {
        fileTicks?.let(::durationOfTicks)
    }
    if (duration == null) {
        if (typeName == null) {
            warnings += Dropped("duration", at.bar, "no <type> and ${fileTicks?.value} ticks is not a written value")
        }
        return null
    }
    if (fileTicks != null && fileTicks != duration.ticks) {
        warnings += Dropped(
            "duration",
            at.bar,
            "<type> $typeName is ${duration.ticks.value} ticks but <duration> is ${fileTicks.value}",
        )
    }
    return duration
}

private fun durationOfType(
    element: Element,
    typeName: String,
    at: NoteCursor,
    warnings: MutableList<Dropped>,
): Duration? {
    val symbol = symbolsByTypeName[typeName]
    if (symbol == null) {
        warnings += Dropped("type", at.bar, "note value '$typeName' is not read")
        return null
    }
    val dots = element.elements("dot").size
    val modification = element.first("time-modification")
    val actual = modification?.intOf("actual-notes") ?: 1
    val normal = modification?.intOf("normal-notes") ?: 1
    val duration = runCatching { Duration(symbol, dots, actual, normal) }.getOrNull()
    if (duration?.ticksOrNull == null) {
        warnings += Dropped("type", at.bar, "$typeName with $dots dots at $actual:$normal is not representable")
        return null
    }
    return duration
}

private fun tieTypesOf(element: Element): Set<String> {
    val fromTies = element.elements("tie").mapNotNull { it.attr("type") }
    val fromNotations = element.first("notations")?.elements("tied")?.mapNotNull { it.attr("type") } ?: emptyList()
    return (fromTies + fromNotations).toSet()
}

private fun unsupportedNoteChildren(element: Element, bar: Int): List<Dropped> {
    val fromNote = element.elements()
        .filter { it.tagName !in handledNoteChildren && it.tagName !in ignoredNoteChildren }
        .map { Dropped(it.tagName, bar, "not read: ${it.summary()}") }
    val fromNotations = element.first("notations")?.elements()
        .orEmpty()
        .filter { it.tagName != "tied" && it.tagName !in ignoredNotations }
        .map { Dropped(it.tagName, bar, "not read: ${it.summary()}") }
    return fromNote + fromNotations
}
