package com.dewijones92.primavista.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Dynamic colour is deliberately absent, not forgotten.
 *
 * On every modern device it would substitute the wallpaper's palette for the brand, so the brass
 * and violet chosen here would never actually be seen — and beyond taste, this app's verdict
 * colours carry meaning (green right, red wrong, amber off-time). Letting a wallpaper decide them
 * is the difference between a colour scheme and a broken one. Same conclusion Totum reached.
 */
@Composable
public fun PrimaVistaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) PrimaVistaDarkScheme else PrimaVistaLightScheme
    val notation = if (darkTheme) DarkNotationColors else LightNotationColors

    CompositionLocalProvider(LocalNotationColors provides notation) {
        MaterialTheme(
            colorScheme = scheme,
            typography = PrimaVistaTypography,
            content = content,
        )
    }
}
