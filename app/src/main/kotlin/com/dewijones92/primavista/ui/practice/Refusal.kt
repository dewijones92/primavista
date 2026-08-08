package com.dewijones92.primavista.ui.practice

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dewijones92.primavista.practice.RefusalReason

internal fun refusalHeadline(reason: RefusalReason): String = when (reason) {
    is RefusalReason.PolyphonicScoreOnMonoInput -> "Can't hear both hands"
    is RefusalReason.EmptyScore -> "Nothing to read"
}

/** docs/spec.md I3's refusal in words: it names the bar and the input, and offers the way out. */
internal fun refusalDetail(reason: RefusalReason): String = when (reason) {
    is RefusalReason.PolyphonicScoreOnMonoInput ->
        "Bar ${reason.firstPolyphonicBar} has more than one note at once, and the " +
            "${reason.inputLabel} input can only follow a single line. Rather than guess and mark " +
            "you wrong, it's stopping here — switch to TAP, or practise one hand at a time."
    is RefusalReason.EmptyScore -> "\"${reason.scoreTitle}\" has no notes to play."
}

/** Stays where the staff would be, so a dismissed dialog cannot leave the screen saying nothing. */
@Composable
internal fun RefusalOnPaper(reason: RefusalReason, ink: Color, mutedAlpha: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = refusalHeadline(reason),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = ink,
        )
        Spacer(Modifier.height(GAP))
        Text(
            text = refusalDetail(reason),
            style = MaterialTheme.typography.bodyMedium,
            color = ink.copy(alpha = mutedAlpha),
        )
    }
}

@Composable
internal fun RefusalCard(reason: RefusalReason) {
    Card(shape = RoundedCornerShape(CARD_CORNER)) {
        Column(Modifier.padding(CARD_PADDING).testTag("refusal")) {
            Text(text = refusalHeadline(reason), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(HEADLINE_GAP))
            Text(
                text = refusalDetail(reason),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val GAP = 8.dp
private val HEADLINE_GAP = 10.dp
private val CARD_CORNER = 20.dp
private val CARD_PADDING = 22.dp
