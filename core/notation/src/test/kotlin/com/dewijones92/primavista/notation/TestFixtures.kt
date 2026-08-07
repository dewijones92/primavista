package com.dewijones92.primavista.notation

import com.dewijones92.primavista.score.Alter
import com.dewijones92.primavista.score.Clef
import com.dewijones92.primavista.score.Duration
import com.dewijones92.primavista.score.KeySignature
import com.dewijones92.primavista.score.Letter
import com.dewijones92.primavista.score.Measure
import com.dewijones92.primavista.score.MusicalTime
import com.dewijones92.primavista.score.Note
import com.dewijones92.primavista.score.NoteSymbol
import com.dewijones92.primavista.score.Pitch
import com.dewijones92.primavista.score.Rest
import com.dewijones92.primavista.score.Score
import com.dewijones92.primavista.score.ScoreEvent
import com.dewijones92.primavista.score.ScoreId
import com.dewijones92.primavista.score.ScoreOrigin
import com.dewijones92.primavista.score.Staff
import com.dewijones92.primavista.score.Ticks
import com.dewijones92.primavista.score.TimeSignature
import java.io.File

internal const val EPSILON: Double = 1.0e-9

/**
 * The metrics the shipped app will use, read from the real asset files rather than a hand-made stub
 * that could quietly disagree with the font.
 */
internal object RealBravura {
    private val directory: File = listOf(
        File("../../app/src/main/assets/smufl"),
        File("app/src/main/assets/smufl"),
    ).firstOrNull { it.isDirectory } ?: error("shipped SMuFL metadata not found from ${File(".").absolutePath}")

    val metadataJson: String = File(directory, "bravura_metadata.json").readText()
    val glyphNamesJson: String = File(directory, "glyphnames.json").readText()

    val source: GlyphMetricsSource = sourceOf(metadataJson, glyphNamesJson)

    val metrics: GlyphMetrics by lazy { BravuraGlyphMetrics.from(source) }
}

internal fun sourceOf(metadata: String, glyphNames: String): GlyphMetricsSource =
    object : GlyphMetricsSource {
        override fun bravuraMetadataJson(): String = metadata
        override fun glyphNamesJson(): String = glyphNames
    }

internal fun pitchOf(spec: String): Pitch {
    val letter = Letter.valueOf(spec.substring(0, 1))
    val rest = spec.substring(1)
    val alterText = rest.takeWhile { !it.isDigit() && it != '-' }
    val alter = when (alterText) {
        "#" -> Alter.Sharp
        "##" -> Alter.DoubleSharp
        "b" -> Alter.Flat
        "bb" -> Alter.DoubleFlat
        else -> Alter.Natural
    }
    return Pitch(letter, alter, rest.drop(alterText.length).toInt())
}

internal fun ticksOf(quarters: Double): Ticks =
    Ticks((MusicalTime.TICKS_PER_QUARTER * quarters).toLong())

internal fun noteOf(
    onset: Ticks,
    pitch: String,
    symbol: NoteSymbol = NoteSymbol.Quarter,
    dots: Int = 0,
    staff: Staff = Staff.Upper,
    voice: Int = 1,
    tuplet: Pair<Int, Int> = 1 to 1,
    tiedToNext: Boolean = false,
    tiedFromPrevious: Boolean = false,
): Note = Note(
    onset = onset,
    duration = Duration(symbol, dots, tuplet.first, tuplet.second),
    staff = staff,
    voice = voice,
    pitch = pitchOf(pitch),
    tiedFromPrevious = tiedFromPrevious,
    tiedToNext = tiedToNext,
)

internal fun restOf(
    onset: Ticks,
    symbol: NoteSymbol = NoteSymbol.Quarter,
    staff: Staff = Staff.Upper,
    dots: Int = 0,
): Rest = Rest(onset, Duration(symbol, dots), staff, voice = 1)

internal fun measureOf(
    index: Int,
    time: TimeSignature = TimeSignature.FourFour,
    key: KeySignature = KeySignature.C,
    clefs: Map<Staff, Clef> = mapOf(Staff.Upper to Clef.Treble),
): Measure = Measure(index, Ticks(time.measureTicks.value * index), time, key, clefs)

internal fun scoreOf(
    events: List<ScoreEvent>,
    measures: List<Measure> = listOf(measureOf(0)),
    staves: List<Staff> = listOf(Staff.Upper),
): Score = Score(
    id = ScoreId("test"),
    title = "Test",
    composer = null,
    origin = ScoreOrigin.Parsed("test", "public domain"),
    staves = staves,
    measures = measures,
    events = events,
    defaultTempoBpm = 90,
)

internal fun layoutOf(score: Score, style: LayoutStyle = LayoutStyle()): StaffSystem =
    ClassicalStaffLayout().layout(score, RealBravura.metrics, style)

/** y relative to a staff's top line, which is what every engraving claim is actually about. */
internal fun StaffSystem.relativeY(y: StaffSpaces, staff: Staff = Staff.Upper): Double =
    y.value - staffTopY.getValue(staff).value

internal fun StaffSystem.noteAt(onset: Ticks, staff: Staff = Staff.Upper): LaidOutNote =
    notes.first { it.onset == onset && it.staff == staff }
