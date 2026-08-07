package com.dewijones92.primavista.ui.repertoire

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dewijones92.primavista.di.AppContainer
import com.dewijones92.primavista.score.Corpus
import com.dewijones92.primavista.score.CorpusPiece
import com.dewijones92.primavista.score.MusicXmlResult
import com.dewijones92.primavista.score.Polyphony

private data class RepertoireRow(
    val piece: CorpusPiece,
    val bars: Int,
    val notes: Int,
    val polyphony: Polyphony,
    val dropped: Int,
    val failure: String?,
)

/**
 * What there is to read, with the two facts that decide whether you can read it here: how
 * polyphonic it is, and whether it parsed cleanly.
 *
 * The dropped count is shown rather than hidden because a piece that parses to *nearly* the right
 * thing teaches wrong notes, and a silent approximation is the failure `MusicXmlParser` is written
 * to avoid. If a number appears in that column, the engraving on screen is not the whole piece.
 */
@Composable
public fun RepertoireRoute(container: AppContainer, modifier: Modifier = Modifier) {
    val rows by produceState(initialValue = emptyList<RepertoireRow>(), container) {
        value = Corpus.pieces.map { piece ->
            when (val parsed = Corpus.parse(piece, container.musicXmlParser)) {
                is MusicXmlResult.Parsed -> RepertoireRow(
                    piece = piece,
                    bars = parsed.score.measures.size,
                    notes = parsed.score.attackedNotes.size,
                    polyphony = parsed.score.polyphony,
                    dropped = parsed.dropped.size,
                    failure = null,
                )
                is MusicXmlResult.Failed -> RepertoireRow(piece, 0, 0, Polyphony.Mono, 0, parsed.reason)
            }
        }
    }

    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text("Repertoire", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(2.dp))
        Text(
            text = "${Corpus.pieces.size} pieces, all public domain",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(rows) { row -> RepertoireCard(row) }
        }
    }
}

@Composable
private fun RepertoireCard(row: RepertoireRow) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(14.dp)) {
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
            Spacer(Modifier.height(8.dp))
            if (row.failure != null) {
                Text(
                    text = "Did not parse: ${row.failure}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                Text(
                    text = buildString {
                        append("${row.bars} bars · ${row.notes} notes · ")
                        append(if (row.polyphony == Polyphony.Poly) "two hands" else "single line")
                        if (row.dropped > 0) append(" · ${row.dropped} unsupported markings dropped")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (row.dropped > 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
