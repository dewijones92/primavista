package com.dewijones92.primavista.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// The palette lives here and nowhere else. res/values/colors.xml holds two duplicates on purpose
// — the window background before Compose starts — and that is the one recorded duplication.

private val Brass = Color(0xFFE8A13C)
private val BrassBright = Color(0xFFFFC978)
private val BrassDeep = Color(0xFF8A5A00)
private val BrassPale = Color(0xFFFFDDAF)
private val BrassInk = Color(0xFF2A1A00)

private val VioletLight = Color(0xFFA79BFF)
private val VioletDeep = Color(0xFF4B3FA8)
private val VioletPale = Color(0xFFE3DDFF)
private val VioletInk = Color(0xFF1A0B58)

private val Mint = Color(0xFF6FE3B4)
private val MintDeep = Color(0xFF006B52)
private val MintPale = Color(0xFFA6F2D2)

private val Coral = Color(0xFFFF6B6B)
private val CoralDeep = Color(0xFFB3261E)
private val CoralPale = Color(0xFFFFDAD5)

private val Ink = Color(0xFF12101A)
private val InkRaised = Color(0xFF1A1724)
private val InkRaisedMore = Color(0xFF241F31)
private val InkOutline = Color(0xFF3A3547)
private val Chalk = Color(0xFFE9E4F0)
private val ChalkDim = Color(0xFFC9C2D6)

private val Manuscript = Color(0xFFFBF6EC)
private val ManuscriptShade = Color(0xFFF2EADA)
private val ManuscriptOutline = Color(0xFF7C7768)
private val Graphite = Color(0xFF1C1A22)
private val GraphiteDim = Color(0xFF4B4739)

internal val PrimaVistaDarkScheme = darkColorScheme(
    primary = Brass,
    onPrimary = BrassInk,
    primaryContainer = Color(0xFF5C3A00),
    onPrimaryContainer = BrassPale,
    inversePrimary = BrassDeep,
    secondary = VioletLight,
    onSecondary = VioletInk,
    secondaryContainer = VioletDeep,
    onSecondaryContainer = VioletPale,
    tertiary = Mint,
    onTertiary = Color(0xFF00382A),
    tertiaryContainer = MintDeep,
    onTertiaryContainer = MintPale,
    background = Ink,
    onBackground = Chalk,
    surface = Ink,
    onSurface = Chalk,
    surfaceVariant = Color(0xFF2A2635),
    onSurfaceVariant = ChalkDim,
    surfaceContainerLowest = Color(0xFF0D0B14),
    surfaceContainerLow = InkRaised,
    surfaceContainer = Color(0xFF1F1B2B),
    surfaceContainerHigh = InkRaisedMore,
    surfaceContainerHighest = Color(0xFF2A2437),
    outline = Color(0xFF6E6780),
    outlineVariant = InkOutline,
    error = Coral,
    onError = Color(0xFF4A0010),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = CoralPale,
)

internal val PrimaVistaLightScheme = lightColorScheme(
    primary = BrassDeep,
    onPrimary = Color.White,
    primaryContainer = BrassPale,
    onPrimaryContainer = BrassInk,
    inversePrimary = Brass,
    secondary = VioletDeep,
    onSecondary = Color.White,
    secondaryContainer = VioletPale,
    onSecondaryContainer = VioletInk,
    tertiary = MintDeep,
    onTertiary = Color.White,
    tertiaryContainer = MintPale,
    onTertiaryContainer = Color(0xFF00201A),
    background = Manuscript,
    onBackground = Graphite,
    surface = Manuscript,
    onSurface = Graphite,
    surfaceVariant = Color(0xFFE9E2D4),
    onSurfaceVariant = GraphiteDim,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFDF9F2),
    surfaceContainer = ManuscriptShade,
    surfaceContainerHigh = Color(0xFFECE3D2),
    surfaceContainerHighest = Color(0xFFE5DCCA),
    outline = ManuscriptOutline,
    outlineVariant = Color(0xFFCEC8B8),
    error = CoralDeep,
    onError = Color.White,
    errorContainer = CoralPale,
    onErrorContainer = Color(0xFF410002),
)

/**
 * Colours Material 3's scheme has no slot for, because they are about *notation* rather than about
 * chrome.
 *
 * The staff needs its own surface and its own ink: printed music has always been near-black on
 * warm paper, and rendering it in `onSurface` over `surface` gives a washed-out grey staff that
 * reads as a disabled control. In dark mode the roles invert — a raised near-black panel with
 * near-white ink — rather than the staff simply being dimmed, which would be the easy thing and
 * would make the thing Dewi stares at the least legible part of the screen.
 */
@Immutable
public data class NotationColors(
    val paper: Color,
    val ink: Color,
    val staffLine: Color,
    val playhead: Color,
    val playheadGlow: Color,
    val correct: Color,
    val wrongPitch: Color,
    val offTime: Color,
    val missed: Color,
    /** Written but not yet reached. Dimmer than [ink], never a verdict colour — see CODE-NOTES. */
    val upcoming: Color,
    /** The shadow the pinned clef/key strip casts over the music sliding under it. */
    val pinnedEdge: Color,
    /**
     * The keyboard's own two colours, which do **not** invert with the theme: a piano's naturals
     * are pale and its sharps are dark in any light, and swapping them reads as a photographic
     * negative rather than as dark mode.
     */
    val keyNatural: Color,
    val keySharp: Color,
)

internal val DarkNotationColors = NotationColors(
    paper = InkRaised,
    ink = Color(0xFFF4F1FA),
    staffLine = Color(0xFF8A82A0),
    playhead = Brass,
    playheadGlow = BrassBright,
    correct = Mint,
    wrongPitch = Coral,
    offTime = BrassBright,
    missed = Color(0xFF6E6780),
    upcoming = Color(0xFF8B84A3),
    pinnedEdge = Color(0xA6000000),
    keyNatural = Color(0xFFD5D0DE),
    keySharp = Color(0xFF0B0910),
)

internal val LightNotationColors = NotationColors(
    paper = Color(0xFFFFFDF8),
    ink = Color(0xFF14121A),
    staffLine = Color(0xFF5A5648),
    playhead = BrassDeep,
    playheadGlow = Brass,
    correct = MintDeep,
    wrongPitch = CoralDeep,
    offTime = Color(0xFF9A6200),
    missed = Color(0xFF9A9484),
    upcoming = Color(0xFF7C776A),
    pinnedEdge = Color(0x55372A12),
    keyNatural = Color(0xFFFFFDF8),
    keySharp = Color(0xFF17141F),
)

public val LocalNotationColors: androidx.compose.runtime.ProvidableCompositionLocal<NotationColors> =
    staticCompositionLocalOf { LightNotationColors }
