package com.dewijones92.primavista.ui.progress

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dewijones92.primavista.database.DatabaseOpening
import com.dewijones92.primavista.database.StoredReading
import com.dewijones92.primavista.database.map
import com.dewijones92.primavista.di.AppContainer
import com.dewijones92.primavista.practice.SkillState
import com.dewijones92.primavista.score.Alter
import com.dewijones92.primavista.score.Clef
import com.dewijones92.primavista.score.KeySignature
import com.dewijones92.primavista.score.SkillTag
import com.dewijones92.primavista.ui.UnreadablePanel

@Composable
public fun ProgressRoute(container: AppContainer, modifier: Modifier = Modifier) {
    when (val opening = container.databaseOpening) {
        is DatabaseOpening.Unreadable -> UnreadablePanel(SKILLS, opening.reason, modifier)
        is DatabaseOpening.Opened -> {
            val states by produceState<StoredReading<List<SkillState>>?>(null, container) {
                value = container.skillStore?.storedStates() ?: unopened(SKILLS)
            }
            val sessions by produceState<StoredReading<List<SessionPoint>>?>(null, container) {
                value = container.sessionStore?.recent(TREND_SESSIONS)?.map { stored ->
                    stored.filter { it.isFinished }
                        .sortedBy { it.startedAtEpochMillis }
                        .map { SessionPoint(it.startedAtEpochMillis, it.accuracy, it.scoreTitle) }
                } ?: unopened(SESSIONS)
            }
            when (val reading = states) {
                null -> LoadingProgress(modifier)
                is StoredReading.Unreadable -> UnreadablePanel(reading.what, reading.reason, modifier)
                is StoredReading.Readable -> ProgressScreen(
                    states = reading.value,
                    sessions = sessions,
                    nowEpochMillis = System.currentTimeMillis(),
                    describe = { describe(it.tag) },
                    modifier = modifier,
                )
            }
        }
    }
}

private fun unopened(what: String): StoredReading.Unreadable =
    StoredReading.Unreadable(what, "the practice database could not be opened")

@Composable
private fun LoadingProgress(modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Reading your practice history…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Skill names in the words a musician uses — `ClefRegion(Bass, BelowStaff)` is not one of them. */
internal fun describe(tag: SkillTag): String = when (tag) {
    is SkillTag.ClefRegion -> "${clefName(tag.clef)}, ${bandName(tag.band.name)}"
    is SkillTag.LegerLines ->
        "${clefName(tag.clef)}, ${tag.count} leger line${plural(tag.count)} " +
            if (tag.above) "above" else "below"
    is SkillTag.Accidental -> accidentalName(tag.alter)
    is SkillTag.KeyReading -> "Reading in ${keyName(tag.fifths)}"
    is SkillTag.RhythmFigure -> buildString {
        append(rhythmName(tag.symbol.name))
        if (tag.dots == 1) append(", dotted")
        if (tag.dots == 2) append(", double dotted")
        if (tag.tupletNumerator != 1) append(", ${tag.tupletNumerator}-tuplet")
    }
    is SkillTag.Leap ->
        if (tag.semitones == 0) {
            "Repeated notes"
        } else {
            "Leaps of ${tag.semitones} semitone${plural(tag.semitones)}"
        }
    SkillTag.HandIndependence -> "Both hands at once"
}

private fun accidentalName(alter: Alter): String = when (alter) {
    Alter.DoubleFlat -> "Double flats"
    Alter.Flat -> "Flats"
    Alter.Sharp -> "Sharps"
    Alter.DoubleSharp -> "Double sharps"
    else -> "Naturals"
}

private fun rhythmName(symbol: String): String = when (symbol) {
    "DoubleWhole" -> "Breves"
    "Whole" -> "Semibreves"
    "Half" -> "Minims"
    "Quarter" -> "Crotchets"
    "Eighth" -> "Quavers"
    "Sixteenth" -> "Semiquavers"
    "ThirtySecond" -> "Demisemiquavers"
    else -> symbol
}

private fun clefName(clef: Clef): String = when (clef) {
    Clef.Treble -> "Treble clef"
    Clef.Bass -> "Bass clef"
    Clef.Alto -> "Alto clef"
}

private fun bandName(band: String): String = when (band) {
    "FarBelowStaff" -> "far below the staff"
    "BelowStaff" -> "below the staff"
    "LowerStaff" -> "lower staff"
    "MiddleStaff" -> "middle of the staff"
    "UpperStaff" -> "upper staff"
    "AboveStaff" -> "above the staff"
    "FarAboveStaff" -> "far above the staff"
    else -> band
}

/**
 * Indexed by fifths + 7, so C major sits at the centre. A lookup rather than a fifteen-branch
 * `when`: the number IS the index, and spelling that out as branches hides it behind arithmetic
 * detekt is right to call magic.
 */
private val KeyNames = listOf(
    "C♭ major / A♭ minor", "G♭ major / E♭ minor", "D♭ major / B♭ minor", "A♭ major / F minor",
    "E♭ major / C minor", "B♭ major / G minor", "F major / D minor",
    "C major / A minor",
    "G major / E minor", "D major / B minor", "A major / F♯ minor", "E major / C♯ minor",
    "B major / G♯ minor", "F♯ major / D♯ minor", "C♯ major / A♯ minor",
)

private fun keyName(fifths: Int): String =
    KeyNames.getOrElse(fifths + KeySignature.MAX_FIFTHS) { "$fifths accidentals" }

private fun plural(count: Int): String = if (count == 1) "" else "s"

private const val TREND_SESSIONS = 30

internal const val SKILLS: String = "what you have practised"
internal const val SESSIONS: String = "your recent sessions"
