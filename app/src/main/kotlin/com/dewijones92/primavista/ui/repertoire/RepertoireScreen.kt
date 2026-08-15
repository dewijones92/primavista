package com.dewijones92.primavista.ui.repertoire

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dewijones92.primavista.score.Corpus
import com.dewijones92.primavista.score.CorpusPiece
import com.dewijones92.primavista.score.Polyphony
import com.dewijones92.primavista.theme.LocalNotationColors
import com.dewijones92.primavista.ui.mascot.MascotMood
import com.dewijones92.primavista.ui.mascot.Trill
import com.dewijones92.primavista.ui.mascot.TrillAside
import com.dewijones92.primavista.ui.mascot.TrillPanel
import com.dewijones92.primavista.ui.progress.describe

@Composable
internal fun RepertoireScreen(
    rows: List<RepertoireRow>,
    stillReading: Int,
    onPractise: (CorpusPiece) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(SCREEN_PADDING),
        verticalArrangement = Arrangement.spacedBy(CARD_GAP),
    ) {
        item { RepertoireHeader() }
        items(rows, key = { it.piece.id.value }) { row -> RepertoireCard(row, onPractise) }
        if (stillReading > 0) {
            items(stillReading) { SkeletonCard() }
        } else if (rows.isEmpty()) {
            item { NoPieces() }
        }
    }
}

/** Trill is [MascotMood.Curious] here and nowhere else: this is the screen that waits on a choice. */
@Composable
private fun RepertoireHeader() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Repertoire", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${Corpus.pieces.size} pieces, all public domain, easiest first. Tap one " +
                    "to read what it demands, then practise it.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(GAP))
        Trill(MascotMood.Curious, Modifier.size(HEADER_BIRD))
    }
    Spacer(Modifier.height(CARD_GAP))
}

@Composable
private fun RepertoireCard(row: RepertoireRow, onPractise: (CorpusPiece) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val turn by animateFloatAsState(
        targetValue = if (expanded) HALF_TURN else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "chevron",
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(CARD_CORNER),
        modifier = Modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column(
            Modifier
                .clickable { expanded = !expanded }
                .padding(CARD_PADDING),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = row.piece.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = row.piece.composer,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(CHEVRON).rotate(turn),
                )
            }
            Spacer(Modifier.height(GAP))
            if (row.failure != null) {
                TrillAside(
                    mood = MascotMood.Wincing,
                    text = "Did not parse: ${row.failure}",
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                FactRow(row)
                DroppedBanner(row)
                if (expanded) ExpandedDetail(row, onPractise)
            }
        }
    }
}

@Composable
private fun FactRow(row: RepertoireRow) {
    val notation = LocalNotationColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(CHIP_GAP)) {
        Chip(
            text = row.rung?.let { "stage ${it.number}" } ?: "past the path",
            tint = if (row.rung == null) MaterialTheme.colorScheme.onSurfaceVariant else notation.playhead,
        )
        Chip("${row.bars} bars", MaterialTheme.colorScheme.onSurfaceVariant)
        Chip("${row.tempoBpm} bpm", MaterialTheme.colorScheme.onSurfaceVariant)
        Chip(
            text = if (row.polyphony == Polyphony.Poly) "two hands" else "single line",
            tint = if (row.polyphony == Polyphony.Poly) notation.playhead else notation.correct,
        )
    }
    PassageNote(row)
}

/**
 * A whole song is not a unit of practice. Saying which bars will open is more honest than opening
 * a 197-bar Schubert and letting Dewi discover it while the playhead is already moving.
 */
@Composable
private fun PassageNote(row: RepertoireRow) {
    if (row.isWholePiece) return
    Spacer(Modifier.height(GAP))
    Text(
        text = row.opensAs?.let { "Opens as a $it-bar passage, chosen for where you are." }
            ?: "No part of this is readable at any rung of the path yet.",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Only **material** loss gets the error colour, and it stays visible collapsed because it is the
 * one fact on this screen that changes what the notation on the practice screen actually means.
 * Decoration is stated in the same breath as everything else: the app draws from the parsed score,
 * so a missing slur leaves the page and the expectation agreeing.
 */
@Composable
private fun DroppedBanner(row: RepertoireRow) {
    if (row.material.isNotEmpty()) {
        Spacer(Modifier.height(GAP))
        Text(
            text = "${row.material.size} thing${if (row.material.size == 1) "" else "s"} could not be " +
                "read — there is music missing from this page.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
        )
        return
    }
    if (row.decoration == 0) return
    Spacer(Modifier.height(GAP))
    Text(
        text = "${row.decoration} marking${if (row.decoration == 1) "" else "s"} " +
            "(slurs, dynamics and the like) are not drawn. Every note is.",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Chip(text: String, tint: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = tint,
        modifier = Modifier
            .clip(RoundedCornerShape(CHIP_CORNER))
            .background(tint.copy(alpha = CHIP_ALPHA))
            .padding(horizontal = CHIP_PADDING, vertical = CHIP_INSET),
    )
}

@Composable
private fun ExpandedDetail(row: RepertoireRow, onPractise: (CorpusPiece) -> Unit) {
    Spacer(Modifier.height(SECTION_GAP))
    if (row.skills.isNotEmpty()) {
        Heading("What it makes you read")
        Text(
            text = row.skills.take(SKILLS_SHOWN).joinToString("  ·  ") { describe(it) },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(SECTION_GAP))
    }
    if (row.polyphony == Polyphony.Poly) {
        Heading("On the microphone")
        TrillAside(
            mood = MascotMood.Wincing,
            text = "Refused ${row.firstPolyphonicBar?.let { "from bar $it" } ?: "here"}: a mic " +
                "follows one line, and half-hearing two would score notes it never heard.",
        )
        Spacer(Modifier.height(SECTION_GAP))
    }
    if (row.material.isNotEmpty()) {
        Heading("Music missing from this page")
        row.material.forEach { dropped ->
            Text(
                text = dropped.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(SECTION_GAP))
    }
    if (row.decoration > 0) {
        Heading("Not drawn")
        Text(
            text = row.dropped.filterNot { it in row.material }
                .groupingBy { it.element }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(DROPPED_KINDS_SHOWN)
                .joinToString("  ·  ") { "${it.key} ×${it.value}" },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(SECTION_GAP))
    }
    Heading("Where it comes from")
    Text(
        text = row.piece.source,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(SECTION_GAP))
    Button(onClick = { onPractise(row.piece) }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(CHEVRON))
        Spacer(Modifier.width(CHIP_GAP))
        Text("Practise this")
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(CHIP_INSET))
}

@Composable
private fun SkeletonCard() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(CARD_CORNER),
        modifier = Modifier.fillMaxWidth().padding(bottom = CARD_GAP),
    ) {
        Column(Modifier.padding(CARD_PADDING)) {
            Bone(TITLE_BONE)
            Spacer(Modifier.height(GAP))
            Bone(LINE_BONE)
        }
    }
}

@Composable
private fun Bone(widthFraction: Float) {
    Box(
        Modifier
            .fillMaxWidth(widthFraction)
            .height(BONE_HEIGHT)
            .clip(RoundedCornerShape(BONE_HEIGHT))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
}

@Composable
private fun NoPieces() {
    TrillPanel(
        mood = MascotMood.Curious,
        title = "Nothing to read here",
        body = "The corpus is a compiled-in list and this build's is empty. A piece whose file " +
            "was missing or unreadable would still be listed here, with its reason.",
        modifier = Modifier.padding(vertical = SCREEN_PADDING),
    )
}

private const val HALF_TURN = 180f
private const val CHIP_ALPHA = 0.16f
private const val SKILLS_SHOWN = 8
private const val DROPPED_KINDS_SHOWN = 6
private const val TITLE_BONE = 0.7f
private const val LINE_BONE = 0.45f
private val SCREEN_PADDING = 16.dp
private val CARD_GAP = 10.dp
private val CARD_CORNER = 16.dp
private val CARD_PADDING = 14.dp
private val SECTION_GAP = 12.dp
private val GAP = 8.dp
private val CHIP_GAP = 6.dp
private val CHIP_CORNER = 8.dp
private val CHIP_PADDING = 8.dp
private val CHIP_INSET = 4.dp
private val CHEVRON = 20.dp
private val BONE_HEIGHT = 12.dp
private val HEADER_BIRD = 64.dp
