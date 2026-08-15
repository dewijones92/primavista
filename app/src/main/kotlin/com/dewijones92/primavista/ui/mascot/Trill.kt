package com.dewijones92.primavista.ui.mascot

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dewijones92.primavista.theme.LocalNotationColors
import com.dewijones92.primavista.theme.PrimaVistaTheme
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Trill: one soft ball of feathers wearing a quaver's flag for a crest.
 *
 * One boolean-union silhouette under one outline, drawn in a 0..1 field scaled to the given bounds,
 * so the same code is the 40dp chip and the 200dp hero. She reads as a *music* app's bird with no
 * staff anywhere near her, which is the point. See `.claude/CODE-NOTES.md`.
 *
 * She never contradicts the score: [MascotMood.Wincing] is sympathy, never a frown, and only
 * [MascotMood.Delighted] and [MascotMood.Impressed] sparkle.
 */
@Composable
public fun Trill(mood: MascotMood, modifier: Modifier = Modifier) {
    TrillCanvas(mood, modifier, staffStep = null)
}

/**
 * Trill sitting on a staff at the height a note of that pitch would sit, ledger lines and all.
 *
 * [staffStep] counts half-spaces from the middle line: 0 is the middle line, 2 the line above it,
 * -4 the bottom line. The box must be tall enough for the step. See `.claude/CODE-NOTES.md`.
 */
@Composable
public fun TrillOnStaff(mood: MascotMood, modifier: Modifier = Modifier, staffStep: Int = 0) {
    TrillCanvas(mood, modifier, staffStep)
}

@Composable
private fun TrillCanvas(mood: MascotMood, modifier: Modifier, staffStep: Int?) {
    val target = remember(mood) { poseFor(mood) }
    val spec = tween<Float>(MOOD_CHANGE_MILLIS, easing = FastOutSlowInEasing)
    val tilt by animateFloatAsState(target.tilt, spec, label = "tilt")
    val sink by animateFloatAsState(target.sink, spec, label = "sink")
    val lean by animateFloatAsState(target.lean, spec, label = "lean")
    val eyeOpen by animateFloatAsState(target.eyeOpen, spec, label = "eyeOpen")
    val crest by animateFloatAsState(target.crestAngle, spec, label = "crest")
    val beak by animateFloatAsState(target.beakOpen, spec, label = "beak")
    val pose = target.copy(
        tilt = tilt,
        sink = sink,
        lean = lean,
        eyeOpen = eyeOpen,
        crestAngle = crest,
        beakOpen = beak,
    )
    val motion = rememberMotion(target)
    val staffInk = LocalNotationColors.current.staffLine
    Canvas(modifier) {
        if (staffStep == null) {
            val unit = min(size.width / CONTENT_WIDTH, size.height / CONTENT_HEIGHT) * FIELD
            drawTrill(pose, motion, unit, size.height * HALF - unit * (CONTENT_TOP + CONTENT_HEIGHT * HALF))
        } else {
            val space = size.height / STAFF_BOX_SPACES
            drawStaff(space, staffStep, staffInk)
            val unit = space * BIRD_STAFF_SPACES
            drawTrill(pose, motion, unit, size.height * HALF - staffStep * space * HALF - unit * ANKLE_Y)
        }
    }
}

/** Breathing, blinking, the odd glance at the notation, and the sparkle pulse. */
@Composable
private fun rememberMotion(pose: Pose): Motion {
    val clock = rememberInfiniteTransition(label = "trill")
    val breath by clock.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(pose.breathMillis, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "breath",
    )
    val hop by clock.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(pose.hopMillis, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "hop",
    )
    val shimmer by clock.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SHIMMER_MILLIS, easing = LinearEasing)),
        label = "shimmer",
    )
    val blink = remember { Animatable(0f) }
    val glance = remember { Animatable(0f) }
    LaunchedEffect(pose.blinkGapMin) {
        if (pose.blinkGapMin <= 0L) return@LaunchedEffect
        while (true) {
            delay(Random.nextLong(pose.blinkGapMin, pose.blinkGapMax))
            blink.animateTo(1f, tween(BLINK_SHUT_MILLIS))
            blink.animateTo(0f, tween(BLINK_OPEN_MILLIS))
        }
    }
    LaunchedEffect(pose.glances) {
        if (!pose.glances) return@LaunchedEffect
        while (true) {
            delay(Random.nextLong(GLANCE_GAP_MIN, GLANCE_GAP_MAX))
            glance.animateTo(1f, tween(GLANCE_OUT_MILLIS, easing = FastOutSlowInEasing))
            delay(Random.nextLong(GLANCE_HOLD_MIN, GLANCE_HOLD_MAX))
            glance.animateTo(0f, tween(GLANCE_BACK_MILLIS, easing = FastOutSlowInEasing))
        }
    }
    return Motion(breath, hop, blink.value, glance.value, shimmer)
}

private fun DrawScope.drawStaff(space: Float, step: Int, ink: Color) {
    val middle = size.height * HALF
    val weight = space * STAFF_WEIGHT
    for (line in -STAFF_LINES_EACH_SIDE..STAFF_LINES_EACH_SIDE) {
        val y = middle + line * space
        drawLine(ink, Offset(0f, y), Offset(size.width, y), weight)
    }
    val half = space * LEDGER_HALF_SPACES
    val towards = if (step < 0) -1 else 1
    var ledger = LEDGER_FIRST_STEP
    while (ledger <= abs(step)) {
        val y = middle - towards * ledger * space * HALF
        drawLine(ink, Offset(size.width * HALF - half, y), Offset(size.width * HALF + half, y), weight)
        ledger += 2
    }
}

/** Breath swells her sideways more than upwards; a hop squashes her on landing. */
private fun DrawScope.drawTrill(pose: Pose, motion: Motion, unit: Float, originY: Float) {
    val swell = pose.breathAmp * (motion.breath - HALF) * 2f
    val land = pose.hopAmp * (1f - motion.hop)
    translate(size.width * HALF - unit * FIELD_CX, originY) {
        scale(unit, unit, Offset.Zero) {
            scale(
                scaleX = 1f + swell + land,
                scaleY = 1f + swell * BREATH_UPWARD - land,
                pivot = Offset(BODY_CX, ANKLE_Y),
            ) {
                translate(0f, -pose.hopAmp * motion.hop) { drawParts(pose, motion) }
            }
        }
    }
}

private fun DrawScope.drawParts(pose: Pose, motion: Motion) {
    val head = posed(HEAD_SHAPE, pose.tilt, NECK_X, NECK_Y, pose.lean, pose.sink)
    val torso = Path().apply { op(BODY_SHAPE, head, PathOperation.Union) }
    val crest = posed(
        posed(CREST_SHAPE, pose.crestAngle, CREST_X, CREST_Y),
        pose.tilt,
        NECK_X,
        NECK_Y,
        pose.lean,
        pose.sink,
    )
    val beak = beakOf(pose)
    val body = silhouette(torso, crest, beak)
    drawFeet()
    drawPath(body, FEATHER)
    drawPath(Path().apply { op(crest, torso, PathOperation.Difference) }, PLUME)
    clipPath(body) {
        drawOval(CREAM, Offset(BIB_CX - BIB_RX, BIB_CY - BIB_RY), Size(BIB_RX * 2f, BIB_RY * 2f))
        drawPath(CREASES, FEATHER_SHADE, style = Stroke(CREASE_WEIGHT, cap = StrokeCap.Round))
    }
    if (pose.beakOpen > MOUTH_MIN_OPEN) {
        val swung = posed(MOUTH_SHAPE, pose.beakOpen, BEAK_HINGE_X, BEAK_HINGE_Y)
        val gape = Path().apply { op(MOUTH_SHAPE, swung, PathOperation.Difference) }
        drawPath(posed(gape, pose.tilt, NECK_X, NECK_Y, pose.lean, pose.sink), MOUTH)
    }
    beak.forEach { drawPath(it, BEAK) }
    translate(pose.lean, pose.sink) {
        rotate(pose.tilt, Offset(NECK_X, NECK_Y)) {
            drawEye(pose, motion)
            drawBrow(pose)
        }
    }
    drawPath(body, OUTLINE, style = Stroke(OUTLINE_WEIGHT, join = StrokeJoin.Round))
    drawFlourish(pose, motion)
}

private fun DrawScope.drawFeet() {
    val stroke = Stroke(FOOT_WEIGHT, cap = StrokeCap.Round, join = StrokeJoin.Round)
    for (x in floatArrayOf(FOOT_BACK_X, FOOT_FRONT_X)) {
        val toes = Path().apply {
            moveTo(x, FOOT_TOP)
            lineTo(x, ANKLE_Y)
            moveTo(x, ANKLE_Y)
            quadraticTo(x + TOE_REACH * HALF, ANKLE_Y + TOE_DROP * TOE_BEND, x + TOE_REACH, ANKLE_Y + TOE_DROP)
            moveTo(x, ANKLE_Y)
            quadraticTo(x - TOE_REACH * HALF, ANKLE_Y + TOE_DROP * TOE_BEND, x - TOE_HEEL, ANKLE_Y + TOE_DROP)
        }
        drawPath(toes, FOOT, style = stroke)
    }
}

/** One enormous eye does the emotional work. See `.claude/CODE-NOTES.md`. */
private fun DrawScope.drawEye(pose: Pose, motion: Motion) {
    val look = motion.glance * EYE_LOOK
    val cx = EYE_CX + look
    drawOval(
        color = BLUSH.copy(alpha = pose.blush),
        topLeft = Offset(CHEEK_CX - CHEEK_RX, CHEEK_CY - CHEEK_RY),
        size = Size(CHEEK_RX * 2f, CHEEK_RY * 2f),
    )
    val open = (pose.eyeOpen * (1f - motion.blink)).coerceIn(0f, 1f)
    if (open < EYE_SHUT) {
        val lash = Path().apply {
            moveTo(cx - EYE_R, EYE_CY + EYE_R * LASH_DROP)
            quadraticTo(cx, EYE_CY + EYE_R * LASH_DROP - pose.eyeCurve * EYE_R, cx + EYE_R, EYE_CY + EYE_R * LASH_DROP)
        }
        drawPath(lash, EYE, style = Stroke(LASH_WEIGHT, cap = StrokeCap.Round))
        return
    }
    val eye = Path().apply { addOval(Rect(Offset(cx, EYE_CY), EYE_R)) }
    if (open >= 1f) {
        drawPath(eye, EYE)
    } else {
        val lidY = EYE_CY - EYE_R + EYE_R * 2f * (1f - open)
        val lid = Path().apply {
            moveTo(cx - EYE_R * LID_SPAN, EYE_CY - EYE_R * LID_SPAN)
            lineTo(cx + EYE_R * LID_SPAN, EYE_CY - EYE_R * LID_SPAN)
            lineTo(cx + EYE_R * LID_SPAN, lidY)
            quadraticTo(cx, lidY + EYE_R * LID_BULGE, cx - EYE_R * LID_SPAN, lidY)
            close()
        }
        drawPath(Path().apply { op(eye, lid, PathOperation.Difference) }, EYE)
    }
    if (open > SPARK_MIN_OPEN) {
        drawCircle(CREAM, EYE_R * SPARK_BIG, Offset(cx - EYE_R * SPARK_X + look, EYE_CY - EYE_R * SPARK_Y))
    }
    drawCircle(CREAM, EYE_R * SPARK_SMALL, Offset(cx + EYE_R * SPARK_X + look, EYE_CY + EYE_R * SPARK_Y))
}

/** A sympathetic brow thickens with its slant, because a hairline vanishes at 40dp. */
private fun DrawScope.drawBrow(pose: Pose) {
    if (pose.browY == 0f && pose.browSlant == 0f) return
    val brow = Path().apply {
        moveTo(BROW_REAR_X, BROW_REAR_Y - pose.browY + pose.browSlant * BROW_REAR_SHARE)
        quadraticTo(
            BROW_MID_X,
            BROW_MID_Y - pose.browY - pose.browSlant * HALF,
            BROW_FRONT_X,
            BROW_FRONT_Y - pose.browY - pose.browSlant,
        )
    }
    val weight = BROW_WEIGHT + abs(pose.browSlant) * BROW_BOLD
    drawPath(brow, OUTLINE.copy(alpha = BROW_ALPHA), style = Stroke(weight, cap = StrokeCap.Round))
}

/** Mint quavers when she is pleased, violet sparks when she is impressed. Nothing otherwise. */
private fun DrawScope.drawFlourish(pose: Pose, motion: Motion) {
    if (pose.flourish == Flourish.None) return
    val notes = pose.flourish == Flourish.Notes
    val tint = if (notes) DELIGHT else WONDER
    for (i in FLOURISH_X.indices) {
        val phase = (motion.shimmer + i.toFloat() / FLOURISH_X.size) % 1f
        val alpha = sin(phase * PI.toFloat())
        val mark = flourishMark(notes, FLOURISH_X[i], FLOURISH_Y[i] - FLOURISH_RISE * phase, FLOURISH_R[i])
        drawPath(mark, tint.copy(alpha = alpha * FLOURISH_ALPHA))
    }
}

/** The whole creature as one closed outline, so a single stroke rims her everywhere. */
private fun silhouette(torso: Path, crest: Path, beak: List<Path>): Path {
    var whole = torso
    for (part in listOf(TAIL_SHAPE, crest) + beak) {
        whole = Path().apply { op(whole, part, PathOperation.Union) }
    }
    return whole
}

private fun beakOf(pose: Pose): List<Path> = listOf(
    posed(BEAK_UPPER, pose.tilt, NECK_X, NECK_Y, pose.lean, pose.sink),
    posed(
        posed(BEAK_LOWER, pose.beakOpen, BEAK_HINGE_X, BEAK_HINGE_Y),
        pose.tilt,
        NECK_X,
        NECK_Y,
        pose.lean,
        pose.sink,
    ),
)

private fun posed(
    shape: Path,
    degrees: Float,
    pivotX: Float,
    pivotY: Float,
    dx: Float = 0f,
    dy: Float = 0f,
): Path {
    val matrix = Matrix()
    matrix.translate(pivotX + dx, pivotY + dy)
    matrix.rotateZ(degrees)
    matrix.translate(-pivotX, -pivotY)
    return Path().apply {
        addPath(shape)
        transform(matrix)
    }
}

private fun flourishMark(notes: Boolean, cx: Float, cy: Float, r: Float): Path = Path().apply {
    if (notes) {
        addOval(Rect(cx - r, cy - r * NOTE_SQUASH, cx + r, cy + r * NOTE_SQUASH))
        addRect(Rect(cx + r * NOTE_STEM_X, cy - r * NOTE_STEM, cx + r, cy))
        moveTo(cx + r, cy - r * NOTE_STEM)
        cubicTo(
            cx + r * NOTE_FLAG,
            cy - r * NOTE_STEM * NOTE_FLAG_DROP,
            cx + r * NOTE_FLAG,
            cy - r * NOTE_STEM * HALF,
            cx + r,
            cy - r * NOTE_STEM * NOTE_FLAG_END,
        )
        close()
    } else {
        val w = r * SPARKLE_WAIST
        moveTo(cx, cy - r)
        quadraticTo(cx + w, cy - w, cx + r, cy)
        quadraticTo(cx + w, cy + w, cx, cy + r)
        quadraticTo(cx - w, cy + w, cx - r, cy)
        quadraticTo(cx - w, cy - w, cx, cy - r)
        close()
    }
}

/** Roots and handles come off the body ellipse, so the join is tangential. See CODE-NOTES. */
private fun tailShape(): Path {
    val top = TAIL_TOP_DEGREES * DEGREES_TO_RADIANS
    val foot = TAIL_FOOT_DEGREES * DEGREES_TO_RADIANS
    val topX = BODY_CX + BODY_RX * cos(top)
    val topY = BODY_CY + BODY_RY * sin(top)
    val footX = BODY_CX + BODY_RX * cos(foot)
    val footY = BODY_CY + BODY_RY * sin(foot)
    val topRun = -BODY_RX * sin(top)
    val topRise = BODY_RY * cos(top)
    val topGrip = TAIL_GRIP / hypot(topRun, topRise)
    val footRun = -BODY_RX * sin(foot)
    val footRise = BODY_RY * cos(foot)
    val footGrip = TAIL_GRIP / hypot(footRun, footRise)
    return Path().apply {
        moveTo(footX, footY)
        cubicTo(
            footX + footRun * footGrip,
            footY + footRise * footGrip,
            TAIL_LOW_CX,
            TAIL_LOW_CY,
            TAIL_END_LOW_X,
            TAIL_END_LOW_Y,
        )
        cubicTo(TAIL_END_CX_A, TAIL_END_CY_A, TAIL_END_CX_B, TAIL_END_CY_B, TAIL_END_HIGH_X, TAIL_END_HIGH_Y)
        cubicTo(TAIL_HIGH_CX, TAIL_HIGH_CY, topX - topRun * topGrip, topY - topRise * topGrip, topX, topY)
        close()
    }
}

private fun poseFor(mood: MascotMood): Pose = when (mood) {
    MascotMood.Idle -> IDLE_POSE
    MascotMood.Listening -> LISTENING_POSE
    MascotMood.Delighted -> DELIGHTED_POSE
    MascotMood.Wincing -> WINCING_POSE
    MascotMood.Sleepy -> SLEEPY_POSE
    MascotMood.Impressed -> IMPRESSED_POSE
    MascotMood.Curious -> CURIOUS_POSE
}

private enum class Flourish { None, Notes, Stars }

private data class Pose(
    val tilt: Float = 0f,
    val sink: Float = 0f,
    val lean: Float = 0f,
    val eyeOpen: Float = 0.94f,
    val eyeCurve: Float = 0f,
    val browY: Float = 0f,
    val browSlant: Float = 0f,
    val crestAngle: Float = 0f,
    val beakOpen: Float = 0f,
    val blush: Float = 0.12f,
    val breathAmp: Float = 0.022f,
    val breathMillis: Int = 2800,
    val hopAmp: Float = 0f,
    val hopMillis: Int = 1000,
    val blinkGapMin: Long = 2400,
    val blinkGapMax: Long = 6200,
    val glances: Boolean = false,
    val flourish: Flourish = Flourish.None,
)

private class Motion(
    val breath: Float,
    val hop: Float,
    val blink: Float,
    val glance: Float,
    val shimmer: Float,
)

@Preview(widthDp = 360, heightDp = 300)
@Composable
private fun TrillPreview() {
    PrimaVistaTheme {
        Column(Modifier.background(Color(0xFF12101A)).padding(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                MascotMoods.forEach { Trill(it, Modifier.size(48.dp)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Trill(MascotMood.Idle, Modifier.size(140.dp))
                TrillOnStaff(MascotMood.Curious, Modifier.width(150.dp).height(150.dp), staffStep = 2)
            }
        }
    }
}

private const val FIELD = 0.97f
private const val HALF = 0.5f
private const val DEGREES_TO_RADIANS = (PI / 180.0).toFloat()

// Her extent across every mood. See .claude/CODE-NOTES.md.
private const val CONTENT_LEFT = 0.010f
private const val CONTENT_WIDTH = 0.935f
private const val CONTENT_TOP = -0.092f
private const val CONTENT_HEIGHT = 0.997f
private const val FIELD_CX = CONTENT_LEFT + CONTENT_WIDTH * HALF

private const val BODY_CX = 0.470f
private const val BODY_CY = 0.530f
private const val BODY_RX = 0.300f
private const val BODY_RY = 0.290f
private const val HEAD_CX = 0.550f
private const val HEAD_CY = 0.315f
private const val HEAD_RX = 0.235f
private const val HEAD_RY = 0.225f
private const val NECK_X = 0.505f
private const val NECK_Y = 0.500f
private const val CREST_X = 0.510f
private const val CREST_Y = 0.115f
private const val BEAK_HINGE_X = 0.714f
private const val BEAK_HINGE_Y = 0.364f
private const val MOUTH_MIN_OPEN = 2f

private const val TAIL_TOP_DEGREES = 207f
private const val TAIL_FOOT_DEGREES = 148f
private const val TAIL_GRIP = 0.055f
private const val TAIL_LOW_CX = 0.115f
private const val TAIL_LOW_CY = 0.632f
private const val TAIL_END_LOW_X = 0.052f
private const val TAIL_END_LOW_Y = 0.606f
private const val TAIL_END_CX_A = 0.020f
private const val TAIL_END_CY_A = 0.586f
private const val TAIL_END_CX_B = 0.016f
private const val TAIL_END_CY_B = 0.500f
private const val TAIL_END_HIGH_X = 0.038f
private const val TAIL_END_HIGH_Y = 0.452f
private const val TAIL_HIGH_CX = 0.098f
private const val TAIL_HIGH_CY = 0.418f

private const val BIB_CX = 0.582f
private const val BIB_CY = 0.605f
private const val BIB_RX = 0.140f
private const val BIB_RY = 0.185f

private const val EYE_CX = 0.615f
private const val EYE_CY = 0.290f
private const val EYE_R = 0.078f
private const val EYE_SHUT = 0.10f
private const val EYE_LOOK = 0.016f
private const val LID_SPAN = 1.4f
private const val LID_BULGE = 0.42f
private const val LASH_DROP = 0.20f
private const val LASH_WEIGHT = 0.026f
private const val SPARK_MIN_OPEN = 0.62f
private const val SPARK_BIG = 0.34f
private const val SPARK_SMALL = 0.16f
private const val SPARK_X = 0.34f
private const val SPARK_Y = 0.30f

private const val CHEEK_CX = 0.520f
private const val CHEEK_CY = 0.404f
private const val CHEEK_RX = 0.068f
private const val CHEEK_RY = 0.046f

private const val BROW_REAR_X = 0.545f
private const val BROW_REAR_Y = 0.190f
private const val BROW_MID_X = 0.617f
private const val BROW_MID_Y = 0.146f
private const val BROW_FRONT_X = 0.688f
private const val BROW_FRONT_Y = 0.184f
private const val BROW_REAR_SHARE = 0.35f
private const val BROW_WEIGHT = 0.018f
private const val BROW_BOLD = 0.55f
private const val BROW_ALPHA = 0.9f

private const val STAFF_LINES_EACH_SIDE = 2
private const val STAFF_BOX_SPACES = 9f
private const val BIRD_STAFF_SPACES = 2.9f
private const val STAFF_WEIGHT = 0.09f
private const val LEDGER_FIRST_STEP = 6
private const val LEDGER_HALF_SPACES = 1.5f

private const val FOOT_FRONT_X = 0.560f
private const val FOOT_BACK_X = 0.450f
private const val FOOT_TOP = 0.770f
private const val ANKLE_Y = 0.856f
private const val FOOT_WEIGHT = 0.019f
private const val TOE_REACH = 0.058f
private const val TOE_HEEL = 0.042f
private const val TOE_DROP = 0.038f
private const val TOE_BEND = 0.20f

private const val OUTLINE_WEIGHT = 0.019f
private const val CREASE_WEIGHT = 0.022f
private const val BREATH_UPWARD = 0.55f
private const val MOOD_CHANGE_MILLIS = 380
private const val SHIMMER_MILLIS = 2200
private const val BLINK_SHUT_MILLIS = 70
private const val BLINK_OPEN_MILLIS = 120
private const val GLANCE_GAP_MIN = 3600L
private const val GLANCE_GAP_MAX = 9000L
private const val GLANCE_HOLD_MIN = 900L
private const val GLANCE_HOLD_MAX = 1800L
private const val GLANCE_OUT_MILLIS = 380
private const val GLANCE_BACK_MILLIS = 520

private const val SPARKLE_WAIST = 0.18f
private const val NOTE_SQUASH = 0.76f
private const val NOTE_STEM_X = 0.70f
private const val NOTE_STEM = 3.1f
private const val NOTE_FLAG = 2.0f
private const val NOTE_FLAG_DROP = 0.92f
private const val NOTE_FLAG_END = 0.52f
private const val FLOURISH_RISE = 0.06f
private const val FLOURISH_ALPHA = 0.95f

private val FLOURISH_X = floatArrayOf(0.820f, 0.900f, 0.745f)
private val FLOURISH_Y = floatArrayOf(0.170f, 0.310f, 0.070f)
private val FLOURISH_R = floatArrayOf(0.050f, 0.034f, 0.026f)

private val FEATHER = Color(0xFFE3A85E)
private val FEATHER_SHADE = Color(0xFFC9873C)
private val OUTLINE = Color(0xFF7A4A12)
private val CREAM = Color(0xFFF9E7C6)
private val BEAK = Color(0xFFD4783A)
private val MOUTH = Color(0xFF7A2E20)
private val FOOT = Color(0xFFC2682C)
private val EYE = Color(0xFF231A2F)
private val BLUSH = Color(0xFFE97F6C)
private val PLUME = Color(0xFF8C7CF5)
private val DELIGHT = Color(0xFF6FE3B4)
private val WONDER = Color(0xFFA79BFF)

private val IDLE_POSE = Pose(glances = true)

private val LISTENING_POSE = Pose(
    tilt = 4f,
    sink = 0.008f,
    lean = 0.012f,
    eyeOpen = 0.95f,
    crestAngle = 5f,
    blush = 0.10f,
    breathAmp = 0.008f,
    breathMillis = 3600,
    blinkGapMin = 5200,
    blinkGapMax = 11_000,
)

private val DELIGHTED_POSE = Pose(
    tilt = -3f,
    sink = -0.010f,
    eyeOpen = 0f,
    eyeCurve = 1f,
    browY = 0.024f,
    crestAngle = 18f,
    beakOpen = 13f,
    blush = 0.30f,
    breathAmp = 0.026f,
    breathMillis = 900,
    hopAmp = 0.045f,
    hopMillis = 620,
    blinkGapMin = 0,
    flourish = Flourish.Notes,
)

private val WINCING_POSE = Pose(
    tilt = 10f,
    sink = 0.034f,
    lean = -0.006f,
    eyeOpen = 0.42f,
    browY = 0.020f,
    browSlant = 0.054f,
    crestAngle = -14f,
    beakOpen = 11f,
    blush = 0.32f,
    breathAmp = 0.016f,
    breathMillis = 2100,
    blinkGapMin = 3000,
    blinkGapMax = 7000,
)

private val SLEEPY_POSE = Pose(
    tilt = 5f,
    sink = 0.046f,
    eyeOpen = 0f,
    eyeCurve = -0.55f,
    crestAngle = -18f,
    blush = 0.12f,
    breathAmp = 0.040f,
    breathMillis = 4600,
    blinkGapMin = 0,
)

private val IMPRESSED_POSE = Pose(
    tilt = -6f,
    sink = -0.018f,
    lean = -0.014f,
    eyeOpen = 1f,
    browY = 0.042f,
    crestAngle = 24f,
    beakOpen = 9f,
    blush = 0.28f,
    breathAmp = 0.020f,
    breathMillis = 1500,
    hopAmp = 0.026f,
    hopMillis = 900,
    blinkGapMin = 3200,
    blinkGapMax = 7000,
    flourish = Flourish.Stars,
)

private val CURIOUS_POSE = Pose(
    tilt = -16f,
    eyeOpen = 0.97f,
    browY = 0.030f,
    browSlant = -0.024f,
    crestAngle = 6f,
    breathAmp = 0.018f,
    breathMillis = 2400,
    glances = true,
)

private val BODY_SHAPE = Path().apply {
    addOval(Rect(BODY_CX - BODY_RX, BODY_CY - BODY_RY, BODY_CX + BODY_RX, BODY_CY + BODY_RY))
}

private val HEAD_SHAPE = Path().apply {
    addOval(Rect(HEAD_CX - HEAD_RX, HEAD_CY - HEAD_RY, HEAD_CX + HEAD_RX, HEAD_CY + HEAD_RY))
}

private val TAIL_SHAPE = tailShape()

// See .claude/CODE-NOTES.md.
private val CREST_SHAPE = Path().apply {
    moveTo(0.552f, 0.130f)
    cubicTo(0.556f, 0.034f, 0.474f, -0.024f, 0.362f, -0.018f)
    cubicTo(0.262f, -0.012f, 0.176f, 0.028f, 0.118f, 0.158f)
    cubicTo(0.196f, 0.088f, 0.292f, 0.086f, 0.390f, 0.082f)
    cubicTo(0.452f, 0.080f, 0.484f, 0.100f, 0.478f, 0.150f)
    close()
}

private val BEAK_UPPER = Path().apply {
    moveTo(0.706f, 0.298f)
    cubicTo(0.790f, 0.310f, 0.838f, 0.330f, 0.868f, 0.356f)
    lineTo(0.714f, 0.364f)
    close()
}

private val BEAK_LOWER = Path().apply {
    moveTo(0.714f, 0.364f)
    lineTo(0.868f, 0.358f)
    cubicTo(0.824f, 0.398f, 0.778f, 0.418f, 0.706f, 0.422f)
    close()
}

// See .claude/CODE-NOTES.md.
private val MOUTH_SHAPE = Path().apply {
    moveTo(BEAK_HINGE_X, BEAK_HINGE_Y)
    lineTo(0.868f, 0.357f)
    lineTo(0.868f, 0.415f)
    close()
}

private val CREASES = Path().apply {
    moveTo(0.335f, 0.425f)
    cubicTo(0.430f, 0.505f, 0.425f, 0.640f, 0.305f, 0.720f)
    moveTo(0.185f, 0.472f)
    quadraticTo(0.120f, 0.482f, 0.060f, 0.498f)
    moveTo(0.190f, 0.548f)
    quadraticTo(0.125f, 0.552f, 0.058f, 0.556f)
}
