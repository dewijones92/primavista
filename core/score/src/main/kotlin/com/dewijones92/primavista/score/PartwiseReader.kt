package com.dewijones92.primavista.score

import org.w3c.dom.Element

private const val DEFAULT_TEMPO_BPM = 90

private val ignoredRootChildren = setOf(
    "work",
    "movement-title",
    "movement-number",
    "identification",
    "part-list",
    "defaults",
    "credit",
)

private val ignoredAttributeChildren = setOf("part-symbol", "instruments", "footnote", "level", "directive")

private fun tempoOf(sound: Element?): Int? = sound?.attr("tempo")?.toDoubleOrNull()?.toInt()

/**
 * Reads one `<part>` of a `score-partwise` document, in document order, keeping the running
 * attribute state a MusicXML part implies.
 */
internal class PartwiseReader(
    private val sourceName: String,
    private val licence: String,
    private val part: PartChoice = PartChoice.First,
) {
    private val dropped = mutableListOf<Dropped>()
    private val events = mutableListOf<ScoreEvent>()
    private val measures = mutableListOf<Measure>()
    private val clefs = linkedMapOf<Staff, Clef>(Staff.Upper to Clef.Treble)
    private var divisions = 1
    private var key = KeySignature.C
    private var time = TimeSignature.FourFour
    private var tempo: Int? = null
    private var bar = 1
    private var cursor = Ticks.ZERO
    private var furthest = Ticks.ZERO
    private var chordOnset = Ticks.ZERO

    fun read(root: Element): MusicXmlResult {
        val title = root.first("work")?.textOf("work-title") ?: root.textOf("movement-title") ?: sourceName
        val composer = root.first("identification")
            ?.elements("creator")
            ?.firstOrNull { it.attr("type") == null || it.attr("type") == "composer" }
            ?.textContent
            ?.trim()
        root.elements()
            .filter { it.tagName != "part" && it.tagName !in ignoredRootChildren }
            .forEach { drop(it.tagName, "not read: ${it.summary()}", at = null) }
        val parts = root.elements("part")
        if (parts.isEmpty()) return MusicXmlResult.Failed("no <part> in <score-partwise>")
        val chosen = PartSelection.choose(parts, part)
            ?: return MusicXmlResult.Failed(PartSelection.describeMissing(parts, part))
        if (parts.size > 1) {
            val others = parts.filter { it !== chosen }.joinToString(transform = PartSelection::nameOf)
            drop("part", "read ${PartSelection.nameOf(chosen)}; not read: $others", at = null)
        }
        readPart(chosen)
        return MusicXmlResult.Parsed(buildScore(title, composer), dropped.toList())
    }

    private fun readPart(part: Element) {
        var start = Ticks.ZERO
        part.elements("measure").forEachIndexed { index, element ->
            bar = Measure.numberOf(index)
            cursor = start
            furthest = start
            chordOnset = start
            element.elements().forEach(::readMeasureChild)
            measures += Measure(index, start, time, key, clefs.toMap())
            start = if (furthest > start) furthest else start + time.measureTicks
        }
    }

    private fun readMeasureChild(element: Element) {
        when (element.tagName) {
            "attributes" -> element.elements().forEach(::readAttributeChild)
            "note" -> readNote(element)
            "backup" -> shift(element, forward = false)
            "forward" -> shift(element, forward = true)
            "sound" -> tempo = tempo ?: tempoOf(element)
            "barline" -> dropped += repeatDrops(element, bar)
            "print" -> Unit
            else -> {
                tempo = tempo ?: tempoOf(element.first("sound"))
                drop(element.tagName, "not read: ${element.summary()}")
            }
        }
    }

    private fun readAttributeChild(element: Element) {
        when (element.tagName) {
            "divisions" -> readOrDrop(element, "unreadable divisions", ::readDivisionsElement) { divisions = it }
            "key" -> readOrDrop(element, "non-traditional key is not read", ::readKeyElement) { key = it }
            "time" -> readOrDrop(element, "unreadable time signature", ::readTimeElement) { time = it }
            "clef" -> readOrDrop(element, "unsupported clef", ::readClefElement) { clefs[it.first] = it.second }
            "staves" -> readStaves(element)
            "transpose" -> drop("transpose", "transposing parts are not read")
            else -> if (element.tagName !in ignoredAttributeChildren) {
                drop(element.tagName, "not read: ${element.summary()}")
            }
        }
    }

    private fun <T> readOrDrop(element: Element, reason: String, read: (Element) -> T?, use: (T) -> Unit) {
        val value = read(element)
        if (value == null) drop(element.tagName, "$reason: ${element.summary()}") else use(value)
    }

    private fun readStaves(element: Element) {
        val count = element.textContent.trim().toIntOrNull() ?: return
        if (count > Staff.entries.size) {
            drop("staves", "$count staves; only the first ${Staff.entries.size} are read")
        }
        Staff.entries.take(count).forEach { staff -> clefs.getOrPut(staff) { staffClefDefault(staff) } }
    }

    private fun readNote(element: Element) {
        val read = readNoteElement(element, NoteCursor(bar, divisions, cursor, chordOnset))
        dropped += read.warnings
        read.event?.let { events += it }
        if (!read.isChord && read.length > Ticks.ZERO) {
            chordOnset = cursor
            cursor += read.length
            if (cursor > furthest) furthest = cursor
        }
    }

    private fun shift(element: Element, forward: Boolean) {
        val raw = element.intOf("duration")
        val length = raw?.let { divisionsToTicks(it, divisions) }
        if (length == null) {
            drop(element.tagName, "unreadable duration '$raw' at $divisions divisions")
            return
        }
        cursor = if (forward) cursor + length else cursor - length
        if (cursor < Ticks.ZERO) {
            drop(element.tagName, "moved the cursor before the start of the part; clamped")
            cursor = Ticks.ZERO
        }
        if (cursor > furthest) furthest = cursor
    }

    private fun buildScore(title: String, composer: String?): Score {
        val used = Staff.entries.filter { staff -> events.any { it.staff == staff } }
        return Score(
            id = ScoreId(sourceName),
            title = title,
            composer = composer?.ifEmpty { null },
            origin = ScoreOrigin.Parsed(sourceName, licence),
            staves = used.ifEmpty { listOf(Staff.Upper) },
            measures = measures.toList(),
            events = events.sortedWith(scoreEventOrder),
            defaultTempoBpm = tempo ?: DEFAULT_TEMPO_BPM,
        )
    }

    private fun drop(element: String, detail: String, at: Int? = bar) {
        dropped += Dropped(element, at, detail)
    }
}
