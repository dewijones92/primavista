package com.dewijones92.primavista.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.dewijones92.primavista.database.PlacementReading
import com.dewijones92.primavista.database.StoredReading
import com.dewijones92.primavista.di.AppContainer

private const val TAG = "intro"

/** Whether the introduction runs. Three answers, because "we could not tell" is not "yes". */
internal enum class IntroGate { Reading, Show, Skip }

/**
 * The introduction runs exactly once, and what records that is the **placement row** rather than a
 * flag of its own.
 *
 * Every way out of the introduction writes one — taking the read writes a completed placement,
 * declining it writes a skipped one — so a second flag would be a second answer to the same
 * question, and the one that went stale would be the one the app believed.
 *
 * An unreadable journey is deliberately treated as *do not show*: forcing a returning reader back
 * through the introduction because a row could not be parsed is worse than the introduction being
 * missed, and the path carries "Meet Trill again" for exactly that case.
 */
@Composable
internal fun rememberIntroGate(container: AppContainer): IntroGate {
    val gate by produceState(IntroGate.Reading, container) {
        val store = container.journeyStore
        if (store == null) {
            container.diag.event(
                TAG,
                "introduction skipped: the database could not be opened, so nothing could record it"
            )
            value = IntroGate.Skip
            return@produceState
        }
        value = when (val reading = store.journey()) {
            is StoredReading.Unreadable -> {
                container.diag.event(
                    TAG,
                    "introduction skipped: ${reading.what} could not be read (${reading.reason}), and " +
                        "showing it again to someone who has already taken it is the worse mistake",
                )
                IntroGate.Skip
            }
            is StoredReading.Readable -> gateFor(container, reading.value.placement)
        }
    }
    return gate
}

private fun gateFor(container: AppContainer, placement: PlacementReading): IntroGate {
    val show = placement == PlacementReading.NeverTaken
    container.diag.event(
        TAG,
        "introduction ${if (show) "runs" else "skipped"}: placement=${placement.kind}",
    )
    return if (show) IntroGate.Show else IntroGate.Skip
}
