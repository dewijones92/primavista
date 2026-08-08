package com.dewijones92.primavista.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dewijones92.primavista.database.StoredReading

/** One wording for a refused read. See `.claude/CODE-NOTES.md`. */
internal const val NOTHING_DELETED: String = "It is still on disk and nothing has been deleted."

@Composable
internal fun UnreadablePanel(what: String, reason: String, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxSize().padding(PANEL_PADDING).testTag("unreadable"),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Couldn't read $what",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(GAP))
            Text(
                text = reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(GAP))
            Text(
                text = NOTHING_DELETED,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The same refusal inside a card that has other things to say, so the rest of the screen survives. */
@Composable
internal fun UnreadableNote(refusal: StoredReading.Unreadable) {
    val error = MaterialTheme.colorScheme.error
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).testTag("unreadable")) {
        Box(
            Modifier
                .width(RULE_WIDTH)
                .fillMaxHeight()
                .clip(RoundedCornerShape(RULE_WIDTH))
                .background(error),
        )
        Spacer(Modifier.width(GAP))
        Column {
            Text(
                text = "Couldn't read ${refusal.what}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = error,
            )
            Text(
                text = "${refusal.reason}. $NOTHING_DELETED",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val PANEL_PADDING = 24.dp
private val GAP = 8.dp
private val RULE_WIDTH = 3.dp
