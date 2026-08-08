package com.dewijones92.primavista.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dewijones92.primavista.ui.repertoire.PracticeRequest
import com.dewijones92.primavista.ui.settings.SettingsRoute

/**
 * [settings] carries a default rather than being supplied by the activity like its siblings, so the
 * Settings destination could be added without editing `MainActivity`. See `.claude/CODE-NOTES.md`.
 */
@Composable
public fun AppShell(
    practise: @Composable () -> Unit,
    repertoire: @Composable () -> Unit,
    progress: @Composable () -> Unit,
    diagnostics: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    settings: @Composable () -> Unit = { SettingsRoute() },
) {
    var current by rememberSaveable { mutableStateOf(Destination.Practise) }

    val requests = PracticeRequest.count
    LaunchedEffect(requests) { if (requests > 0) current = Destination.Practise }

    Scaffold(
        modifier = modifier,
        bottomBar = { DestinationBar(current) { current = it } },
    ) { padding ->
        AnimatedContent(
            targetState = current,
            transitionSpec = { destinationTransition() },
            label = "destination",
        ) { destination ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (destination) {
                    Destination.Practise -> practise()
                    Destination.Repertoire -> repertoire()
                    Destination.Progress -> progress()
                    Destination.Settings -> settings()
                    Destination.Diagnostics -> diagnostics()
                }
            }
        }
    }
}

/**
 * Travel direction follows the tab order, so the motion says where you went rather than being
 * decoration. Short and eased, because this runs between Dewi and the thing he opened the app for.
 */
private fun AnimatedContentTransitionScope<Destination>.destinationTransition(): ContentTransform {
    val forward = targetState.ordinal > initialState.ordinal
    val entering = if (forward) ENTER_OFFSET_FRACTION else -ENTER_OFFSET_FRACTION
    val leaving = if (forward) -EXIT_OFFSET_FRACTION else EXIT_OFFSET_FRACTION
    val enter = slideInHorizontally(tween(TRANSITION_MILLIS, easing = FastOutSlowInEasing)) {
        (it * entering).toInt()
    } + fadeIn(tween(TRANSITION_MILLIS)) + scaleIn(tween(TRANSITION_MILLIS), initialScale = ENTER_SCALE)
    val exit = slideOutHorizontally(tween(EXIT_MILLIS, easing = FastOutSlowInEasing)) {
        (it * leaving).toInt()
    } + fadeOut(tween(EXIT_MILLIS))
    return enter togetherWith exit
}

@Composable
private fun DestinationBar(current: Destination, onSelect: (Destination) -> Unit) {
    val haptics = LocalHapticFeedback.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = BAR_ELEVATION,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .fillMaxWidth()
                .height(BAR_HEIGHT)
                .padding(horizontal = BAR_SIDE_PADDING),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ITEM_GAP),
        ) {
            Destination.entries.forEach { destination ->
                val selected = destination == current
                val expansion by animateFloatAsState(
                    targetValue = if (selected) 1f else 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    label = "expansion-${destination.name}",
                )
                DestinationItem(
                    destination = destination,
                    selected = selected,
                    expansion = expansion,
                    modifier = Modifier.weight(1f + expansion * (SELECTED_WEIGHT - 1f)),
                    onClick = {
                        if (!selected) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelect(destination)
                        }
                    },
                )
            }
        }
    }
}

/**
 * The selected tab grows into a labelled pill and the rest collapse to icons. That is what keeps
 * five destinations legible on a phone — "Diagnostics" does not fit in a fifth of the width — and
 * it is also what says which tab you are on without relying on colour alone.
 */
@Composable
private fun DestinationItem(
    destination: Destination,
    selected: Boolean,
    expansion: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pill = MaterialTheme.colorScheme.primaryContainer
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier
            .fillMaxHeight()
            .padding(vertical = ITEM_VERTICAL_PADDING)
            .clip(RoundedCornerShape(PILL_CORNER))
            .background(pill.copy(alpha = expansion))
            .selectable(
                selected = selected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .testTag("tab-${destination.name}"),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = destination.icon,
                contentDescription = destination.label,
                tint = contentColor,
                modifier = Modifier.size(ICON_SIZE).scale(1f + expansion * ICON_GROWTH),
            )
            if (expansion > LABEL_REVEAL_AT) {
                Text(
                    text = destination.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier
                        .padding(start = LABEL_GAP)
                        .graphicsLayer { alpha = expansion },
                )
            }
        }
    }
}

private const val TRANSITION_MILLIS = 260
private const val EXIT_MILLIS = 180
private const val ENTER_OFFSET_FRACTION = 0.18f
private const val EXIT_OFFSET_FRACTION = 0.10f
private const val ENTER_SCALE = 0.97f
private const val SELECTED_WEIGHT = 2.6f
private const val ICON_GROWTH = 0.08f
private const val LABEL_REVEAL_AT = 0.05f
private val BAR_HEIGHT = 68.dp
private val BAR_ELEVATION = 3.dp
private val BAR_SIDE_PADDING = 10.dp
private val ITEM_GAP = 4.dp
private val ITEM_VERTICAL_PADDING = 10.dp
private val PILL_CORNER = 22.dp
private val ICON_SIZE = 22.dp
private val LABEL_GAP = 6.dp
