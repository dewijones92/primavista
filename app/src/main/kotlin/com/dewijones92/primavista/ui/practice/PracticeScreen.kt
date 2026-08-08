package com.dewijones92.primavista.ui.practice

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.dewijones92.primavista.notation.GlyphMetrics
import com.dewijones92.primavista.notation.StaffSpaces
import com.dewijones92.primavista.notation.StaffSystem
import com.dewijones92.primavista.practice.RefusalReason
import com.dewijones92.primavista.practice.TransportState
import com.dewijones92.primavista.score.Midi
import com.dewijones92.primavista.score.Score
import com.dewijones92.primavista.theme.CountInNumeral
import com.dewijones92.primavista.theme.LocalNotationColors
import com.dewijones92.primavista.theme.TabularNumeral
import com.dewijones92.primavista.ui.staff.NoteStyling
import com.dewijones92.primavista.ui.staff.PinnedFurniture
import com.dewijones92.primavista.ui.staff.StaffCanvas
import com.dewijones92.primavista.ui.staff.rememberVerdictLandings
import kotlin.math.max

/**
 * The app's one screen that matters: music moving past a playhead while you try to keep up.
 */
@Composable
public fun PracticeScreen(
    state: PracticeUiState,
    metrics: GlyphMetrics,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onKeyPressed: (Midi, Long) -> Unit,
    onFrame: () -> Unit,
    modifier: Modifier = Modifier,
    /** Null hides the metronome and echo chips: a control nothing can act on must not be shown. */
    onToggle: ((PracticeToggle) -> Unit)? = null,
) {
    // The frame clock drives the session. It is the UI's job to decide *when to look*; the
    // Conductor remains the only thing that knows what time it is (see .claude/CODE-NOTES.md).
    LaunchedEffect(state.transport) {
        while (state.transport == TransportState.Running || state.transport == TransportState.CountingIn) {
            androidx.compose.runtime.withFrameNanos { }
            onFrame()
        }
    }

    Column(modifier.fillMaxSize()) {
        PracticeHeader(state)
        ProgressRail(state)
        state.notice?.let { NoticeBanner(it) }

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            ScrollingStaff(state, metrics)
            CountInOverlay(state.countInBeatsRemaining)
        }

        onToggle?.let { SessionChips(state, it) }
        TransportBar(state, onStart, onPause, onResume)

        if (state.refusal == null) {
            KeyboardPanel(keyboardRange(state.score), onKeyPressed)
        }
    }

    state.refusal?.let { reason ->
        var dialogOpen by remember(reason) { mutableStateOf(true) }
        if (dialogOpen) RefusalDialog(reason) { dialogOpen = false }
    }
}

/**
 * The staff scrolls; the playhead stays put, far enough right that the pinned clef never covers it.
 *
 * Sight-reading is reading *ahead*, so most of the visible width has to be music not yet played. A
 * playhead that travelled rightwards instead would show the least of what matters at the moment it
 * matters most.
 */
@Composable
private fun ScrollingStaff(state: PracticeUiState, metrics: GlyphMetrics) {
    val notation = LocalNotationColors.current
    val system = state.system
    if (system == null) {
        EmptyStaffCard(state)
        return
    }
    val landings = rememberVerdictLandings(state.verdicts)
    val furniture = remember(system) { PinnedFurniture.of(system) }
    val reveal = remember(system) { Animatable(0f) }
    LaunchedEffect(system) { reveal.animateTo(1f, tween(STAFF_REVEAL_MS, easing = LinearOutSlowInEasing)) }

    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Two limits, and the tighter wins: the staff must fit the height, and the viewport must
        // still hold the pinned furniture plus enough bars ahead to be worth reading.
        val gutter = furniture.gutter.value + PLAYHEAD_GUTTER_SPACES
        val heightFit = maxHeight.value / (system.height.value.toFloat() + STAFF_MARGIN_SPACES)
        val widthFit = (maxWidth.value / (gutter + MIN_READ_AHEAD_SPACES)).toFloat()
        val staffSpace = minOf(heightFit, widthFit).coerceIn(MIN_STAFF_SPACE_DP, MAX_STAFF_SPACE_DP)
        val viewport = (maxWidth.value / staffSpace).toDouble()
        val playhead = playheadOf(state, system)
        val anchored = max(viewport * PLAYHEAD_SCREEN_FRACTION, gutter)
        val scroll = (playhead.value - anchored).coerceAtLeast(0.0)

        val sheet = minOf(maxHeight.value, (system.height.value.toFloat() + SHEET_MARGIN_SPACES) * staffSpace)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(sheet.dp)
                .graphicsLayer {
                    alpha = reveal.value
                    translationY = (1f - reveal.value) * STAFF_RISE_PX
                },
            shape = RoundedCornerShape(STAFF_CORNER),
            colors = CardDefaults.cardColors(containerColor = notation.paper),
            elevation = CardDefaults.cardElevation(defaultElevation = STAFF_ELEVATION),
        ) {
            StaffCanvas(
                system = system,
                metrics = metrics,
                modifier = Modifier.fillMaxSize().testTag("staff"),
                staffSpace = staffSpace.dp,
                scrollX = StaffSpaces(scroll),
                playheadX = playhead,
                pinnedAt = state.position,
                appearance = NoteStyling(
                    verdicts = state.verdicts,
                    landings = landings,
                    colors = notation,
                    position = state.position,
                    reveal = reveal.value,
                    systemWidth = system.width.value,
                )::of,
            )
        }
    }
}

/**
 * Before the transport starts nothing has sampled the Conductor, so the state's playhead is still
 * zero; the layout's own first note position is the honest answer until it moves.
 */
private fun playheadOf(state: PracticeUiState, system: StaffSystem): StaffSpaces =
    state.playheadX.takeIf { it.value > 0.0 }
        ?: system.measureAnchors.firstOrNull()?.noteAreaX
        ?: StaffSpaces.ZERO

@Composable
private fun EmptyStaffCard(state: PracticeUiState) {
    val notation = LocalNotationColors.current
    Card(
        modifier = Modifier.fillMaxWidth().height(EMPTY_STAFF_HEIGHT),
        shape = RoundedCornerShape(STAFF_CORNER),
        colors = CardDefaults.cardColors(containerColor = notation.paper),
        elevation = CardDefaults.cardElevation(defaultElevation = STAFF_ELEVATION),
    ) {
        Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
            val refusal = state.refusal
            if (refusal == null) {
                Text(
                    text = if (state.loading) "Choosing something to read…" else "Nothing loaded",
                    color = notation.ink.copy(alpha = MUTED_ALPHA),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                RefusalOnPaper(refusal, notation.ink, MUTED_ALPHA)
            }
        }
    }
}

/** Whatever the session needs to say that is not a verdict — a refusal already has its own dialog. */
@Composable
private fun NoticeBanner(notice: String) {
    Text(
        text = notice,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun SessionChips(state: PracticeUiState, onToggle: (PracticeToggle) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ToggleChip("Metronome", state.metronomeOn) { onToggle(PracticeToggle.Metronome) }
        ToggleChip("Echo", state.echoOn) { onToggle(PracticeToggle.Echo) }
        if (state.previewing) {
            Spacer(Modifier.weight(1f))
            Text(
                text = "PLAYING FOR YOU",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun ToggleChip(label: String, on: Boolean, onClick: () -> Unit) {
    val container = if (on) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val content = if (on) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = content,
        modifier = Modifier
            .clip(CircleShape)
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("toggle-$label"),
    )
}

@Composable
private fun PracticeHeader(state: PracticeUiState) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = state.score?.title ?: "PrimaVista",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            listOfNotNull(
                state.score?.composer?.takeIf { it.isNotEmpty() },
                state.choiceSummary.takeIf { it.isNotEmpty() },
            ).forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        InputChip(state.inputLabel)
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${state.tempoBpm} bpm",
            style = TabularNumeral,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun InputChip(label: String) {
    if (label.isEmpty()) return
    Row(
        Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (label == "mic") {
            Icon(
                Icons.Rounded.Mic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/** How far through the piece, taken straight from the position rather than animated separately. */
@Composable
private fun ProgressRail(state: PracticeUiState) {
    val end = state.score?.endsAt?.value ?: 0L
    val fraction = if (end <= 0L) 0f else (state.position.value.toFloat() / end).coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(RAIL_HEIGHT)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.outlineVariant),
    ) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.secondary,
                                MaterialTheme.colorScheme.primary,
                            ),
                        ),
                    ),
            )
        }
    }
}

/**
 * The count-in is shown, not just heard, and it pulses per beat.
 *
 * A purely audible count-in is useless with the volume down, and a static number gives no sense of
 * pace — starting to read without knowing when bar 1 arrives makes the first note late every time,
 * which the judge would faithfully record as Dewi's mistake. The last beat is violet and filled so
 * bar 1 is never a surprise; violet because no verdict colour may mean "ready".
 */
@Composable
private fun CountInOverlay(beatsRemaining: Int) {
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(beatsRemaining) {
        if (beatsRemaining > 0) {
            pulse.snapTo(0f)
            pulse.animateTo(1f, tween(COUNT_IN_PULSE_MS, easing = LinearOutSlowInEasing))
        }
    }
    if (beatsRemaining <= 0) return

    val last = beatsRemaining == 1
    val accent = if (last) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
    val scrim = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = COUNT_IN_SCRIM_ALPHA)
    val progress = pulse.value

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(COUNT_IN_SIZE)
                .graphicsLayer {
                    val scale = COUNT_IN_MAX_SCALE - (COUNT_IN_MAX_SCALE - COUNT_IN_MIN_SCALE) * progress
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - COUNT_IN_FADE * progress * progress
                }
                .drawBehind { drawCountInBadge(scrim, accent, 1f - progress, last) },
            contentAlignment = Alignment.Center,
        ) {
            Text(text = beatsRemaining.toString(), style = CountInNumeral, color = accent)
        }
    }
}

private fun DrawScope.drawCountInBadge(scrim: Color, accent: Color, sweep: Float, last: Boolean) {
    val stroke = COUNT_IN_STROKE_FRACTION * size.minDimension
    val radius = (size.minDimension - stroke) / 2f
    drawCircle(color = scrim, radius = radius)
    if (last) drawCircle(color = accent.copy(alpha = COUNT_IN_DISC_ALPHA), radius = radius)
    drawArc(
        color = accent,
        startAngle = COUNT_IN_ARC_START,
        sweepAngle = if (last) FULL_TURN_DEGREES else FULL_TURN_DEGREES * sweep,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2, radius * 2),
        style = Stroke(width = stroke, cap = StrokeCap.Round),
    )
}

@Composable
private fun TransportBar(
    state: PracticeUiState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
) {
    val running = state.transport == TransportState.Running ||
        state.transport == TransportState.CountingIn
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ScoreReadout(state)
        Box(contentAlignment = Alignment.Center) {
            Spacer(
                Modifier
                    .size(TRANSPORT_GLOW_SIZE)
                    .drawBehind { drawTransportGlow(size.minDimension) },
            )
            FilledIconButton(
                onClick = {
                    when (state.transport) {
                        TransportState.Idle, TransportState.Finished -> onStart()
                        TransportState.Running, TransportState.CountingIn -> onPause()
                        TransportState.Paused -> onResume()
                    }
                },
                enabled = state.refusal == null && state.system != null,
                modifier = Modifier.size(TRANSPORT_SIZE).testTag("transport"),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(
                    imageVector = if (running) Icons.Rounded.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (running) "Pause" else "Start",
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

private fun DrawScope.drawTransportGlow(diameter: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(BRASS_GLOW, Color.Transparent),
            center = center,
            radius = diameter * TRANSPORT_GLOW_SCALE,
        ),
        radius = diameter * TRANSPORT_GLOW_SCALE,
    )
}

@Composable
private fun ScoreReadout(state: PracticeUiState) {
    val judged = state.verdicts.size
    val expected = state.score?.attackedNotes?.size ?: 0
    val clean = state.verdicts.values.count { it.isClean }
    val notation = LocalNotationColors.current
    // Coloured by how it is actually going, using the same verdict palette as the noteheads. A
    // running total that is always mint would congratulate a bad performance.
    val tone = when {
        judged == 0 -> MaterialTheme.colorScheme.onBackground
        clean >= judged * GOOD_ACCURACY -> notation.correct
        clean >= judged * FAIR_ACCURACY -> notation.offTime
        else -> notation.wrongPitch
    }
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = if (judged == 0) "—" else "$clean",
                style = MaterialTheme.typography.headlineMedium,
                color = tone,
            )
            if (judged > 0) {
                Text(
                    text = " / $judged",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
        Text(
            text = when {
                expected == 0 -> ""
                state.extras > 0 -> "of $expected notes  ·  ${state.extras} extra"
                else -> "of $expected notes"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The keyboard covers the piece, not a fixed three octaves.
 *
 * A note the keyboard cannot reach is a note Dewi is guaranteed to be marked `Missed` on, which is
 * the app inventing a fault — and a bass-clef drill sitting below the old floor of C3 did exactly
 * that. Whole octaves so the pattern of black keys still reads as a keyboard, and a floor on the
 * span so a two-note exercise does not produce four enormous keys.
 */
private fun keyboardRange(score: Score?): ClosedRange<Int> {
    val sounding = score?.attackedNotes?.map { it.pitch.midi.number }.orEmpty()
    val low = (sounding.minOrNull() ?: KEYBOARD_DEFAULT_LOWEST).floorDiv(SEMITONES) * SEMITONES
    val high = ((sounding.maxOrNull() ?: KEYBOARD_DEFAULT_HIGHEST) / SEMITONES + 1) * SEMITONES - 1
    val short = KEYBOARD_MINIMUM_SEMITONES - (high - low + 1)
    val padded = if (short > 0) ((short + SEMITONES - 1) / SEMITONES) * SEMITONES else 0
    return (low - padded).coerceAtLeast(Midi.MIN)..(high).coerceAtMost(Midi.MAX)
}

/** The keyboard sits on a felt strip, the way it does inside a piano lid. */
@Composable
private fun KeyboardPanel(range: ClosedRange<Int>, onKeyPressed: (Midi, Long) -> Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Box(Modifier.fillMaxWidth().height(FELT_HEIGHT).background(MaterialTheme.colorScheme.primary))
        Box(
            Modifier
                .fillMaxWidth()
                .height(FALLBOARD_SHADOW)
                .background(Brush.verticalGradient(listOf(FALLBOARD_SHADOW_COLOR, Color.Transparent))),
        )
        PianoKeyboard(
            lowest = Midi(range.start),
            highest = Midi(range.endInclusive),
            onKeyPressed = onKeyPressed,
            modifier = Modifier.fillMaxWidth().height(KEYBOARD_HEIGHT),
        )
    }
}

/**
 * The honest refusal, made useful. It names the bar and offers the thing a pianist would actually
 * do about it — practise one hand at a time (docs/spec.md I3).
 */
@Composable
private fun RefusalDialog(reason: RefusalReason, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) { RefusalCard(reason) }
}

private const val PLAYHEAD_SCREEN_FRACTION = 0.30
private const val PLAYHEAD_GUTTER_SPACES = 2.4
private const val STAFF_MARGIN_SPACES = 3f
private const val MIN_STAFF_SPACE_DP = 4f
private const val MAX_STAFF_SPACE_DP = 20f
private const val MIN_READ_AHEAD_SPACES = 20.0
private const val SHEET_MARGIN_SPACES = 9f
private const val GOOD_ACCURACY = 0.8
private const val FAIR_ACCURACY = 0.5
private const val STAFF_REVEAL_MS = 700
private const val STAFF_RISE_PX = 42f
private const val MUTED_ALPHA = 0.5f
private const val SEMITONES = 12
private const val KEYBOARD_DEFAULT_LOWEST = 48
private const val KEYBOARD_DEFAULT_HIGHEST = 83
private const val KEYBOARD_MINIMUM_SEMITONES = 36
private const val COUNT_IN_PULSE_MS = 380
private const val COUNT_IN_MAX_SCALE = 1.22f
private const val COUNT_IN_MIN_SCALE = 0.88f
private const val COUNT_IN_FADE = 0.45f
private const val COUNT_IN_STROKE_FRACTION = 0.07f
private const val COUNT_IN_DISC_ALPHA = 0.22f
private const val COUNT_IN_SCRIM_ALPHA = 0.96f
private const val COUNT_IN_ARC_START = -90f
private const val FULL_TURN_DEGREES = 360f
private const val TRANSPORT_GLOW_SCALE = 0.95f

private val BRASS_GLOW = Color(0x26E8A13C)
private val STAFF_CORNER = 20.dp
private val STAFF_ELEVATION = 6.dp
private val EMPTY_STAFF_HEIGHT = 220.dp
private val RAIL_HEIGHT = 4.dp
private val FELT_HEIGHT = 2.dp
private val FALLBOARD_SHADOW = 7.dp
private val FALLBOARD_SHADOW_COLOR = Color(0x40000000)
private val COUNT_IN_SIZE = 152.dp
private val TRANSPORT_SIZE = 62.dp
private val TRANSPORT_GLOW_SIZE = 92.dp
private val KEYBOARD_HEIGHT = 150.dp
