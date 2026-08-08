package com.dewijones92.primavista.ui.staff

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.dewijones92.primavista.notation.LaidOutNote
import com.dewijones92.primavista.practice.Verdict
import com.dewijones92.primavista.score.Ticks
import com.dewijones92.primavista.theme.NotationColors
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

public fun tintFor(verdict: Verdict, notation: NotationColors): Color =
    when (verdict) {
        is Verdict.Correct -> notation.correct
        is Verdict.WrongPitch -> notation.wrongPitch
        is Verdict.Early, is Verdict.Late -> notation.offTime
        Verdict.Missed -> notation.missed
        is Verdict.Extra -> notation.wrongPitch
    }

/**
 * How every notehead on the staff should look right now: read-ahead dimming, the verdict colour,
 * and the pop that makes a verdict land rather than blink.
 *
 * The colour only ever travels from [NotationColors.upcoming] to the verdict's own colour, never
 * between two verdict colours — see CODE-NOTES for why that is a correctness rule and not taste.
 */
public class NoteStyling(
    private val verdicts: Map<Int, Verdict>,
    private val landings: Map<Int, Float>,
    private val colors: NotationColors,
    private val position: Ticks,
    private val reveal: Float,
    private val systemWidth: Double,
) {
    public fun of(note: LaidOutNote): NoteAppearance {
        val index = note.attackIndex
        val verdict = index?.let { verdicts[it] }
        val landing = index?.let { landings[it] } ?: 1f
        val settled = lerp(
            colors.upcoming,
            verdict?.let { tintFor(it, colors) } ?: unjudgedTint(note),
            if (verdict == null) 1f else (landing * COLOUR_SETTLE_RATE).coerceAtMost(1f),
        )
        val pop = if (verdict == null) 0f else sin(landing * PI).toFloat()
        return NoteAppearance(
            color = settled.copy(alpha = settled.alpha * revealAlpha(note)),
            scale = 1f + VERDICT_POP * pop,
            halo = pop,
        )
    }

    private fun unjudgedTint(note: LaidOutNote): Color =
        if (note.onset > position) colors.upcoming else colors.ink

    /** A left-to-right wipe when a piece loads: the staff writes itself rather than appearing. */
    private fun revealAlpha(note: LaidOutNote): Float {
        if (reveal >= 1f) return 1f
        val at = if (systemWidth <= 0.0) 0f else (note.notehead.x.value / systemWidth).toFloat()
        return ((reveal - at * REVEAL_STAGGER) / (1f - REVEAL_STAGGER)).coerceIn(0f, 1f)
    }
}

/**
 * Progress 0..1 for each note whose verdict has just arrived.
 *
 * Every note animates on its own timeline, so a fast passage cannot queue verdicts up behind each
 * other — the constraint that rules out one shared animation.
 */
@Composable
public fun rememberVerdictLandings(verdicts: Map<Int, Verdict>): Map<Int, Float> {
    val landings = remember { mutableStateMapOf<Int, Float>() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(verdicts) {
        if (verdicts.isEmpty()) {
            landings.clear()
            return@LaunchedEffect
        }
        verdicts.keys.forEach { index ->
            if (index in landings) return@forEach
            landings[index] = 0f
            scope.launch {
                Animatable(0f).animateTo(1f, tween(VERDICT_LAND_MS, easing = LinearOutSlowInEasing)) {
                    landings[index] = value
                }
            }
        }
    }
    return landings
}

private const val VERDICT_LAND_MS = 260
private const val VERDICT_POP = 0.34f
private const val COLOUR_SETTLE_RATE = 3f
private const val REVEAL_STAGGER = 0.55f
