package com.appmine.bankmail.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF163567),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD9E2FF),
    onPrimaryContainer = Color(0xFF001944),
    secondary = Color(0xFF4F5E78),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD7E2F7),
    onSecondaryContainer = Color(0xFF101C2F),
    tertiary = Color(0xFF8C5D00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDFAA),
    onTertiaryContainer = Color(0xFF2D1B00),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF161C24),
    surface = Color(0xFFF7F9FC),
    onSurface = Color(0xFF161C24),
    surfaceVariant = Color(0xFFE3E8F0),
    onSurfaceVariant = Color(0xFF424A57),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F4F9),
    surfaceContainer = Color(0xFFEBEEF4),
    surfaceContainerHigh = Color(0xFFE5E9F0),
    outline = Color(0xFF737C8C),
    outlineVariant = Color(0xFFC5CCD8)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFAEC6FF),
    onPrimary = Color(0xFF002D6F),
    primaryContainer = Color(0xFF00439C),
    onPrimaryContainer = Color(0xFFD9E2FF),
    secondary = Color(0xFFBAC6DF),
    onSecondary = Color(0xFF243146),
    secondaryContainer = Color(0xFF3A475D),
    onSecondaryContainer = Color(0xFFD7E2F7),
    tertiary = Color(0xFFF4BF5E),
    onTertiary = Color(0xFF492E00),
    tertiaryContainer = Color(0xFF694400),
    onTertiaryContainer = Color(0xFFFFDFAA),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF0F141C),
    onBackground = Color(0xFFE3E8F0),
    surface = Color(0xFF0F141C),
    onSurface = Color(0xFFE3E8F0),
    surfaceVariant = Color(0xFF28303E),
    onSurfaceVariant = Color(0xFFC2CAD8),
    surfaceContainerLowest = Color(0xFF0B0F15),
    surfaceContainerLow = Color(0xFF161C26),
    surfaceContainer = Color(0xFF1B222E),
    surfaceContainerHigh = Color(0xFF242C3A),
    outline = Color(0xFF8C94A4),
    outlineVariant = Color(0xFF3F4756)
)

object SemanticColors {
    @Composable
    fun creditContent(dark: Boolean = isSystemInDarkTheme()): Color =
        if (dark) Color(0xFF6EE7A8) else Color(0xFF116D3C)

    @Composable
    fun creditContainer(dark: Boolean = isSystemInDarkTheme()): Color =
        if (dark) Color(0xFF113523) else Color(0xFFDDF5E6)

    @Composable
    fun debitContent(dark: Boolean = isSystemInDarkTheme()): Color =
        if (dark) Color(0xFFFCA5A5) else Color(0xFFB42318)

    @Composable
    fun debitContainer(dark: Boolean = isSystemInDarkTheme()): Color =
        if (dark) Color(0xFF3E1818) else Color(0xFFFEE4E2)

    @Composable
    fun warningContent(dark: Boolean = isSystemInDarkTheme()): Color =
        if (dark) Color(0xFFFCD34D) else Color(0xFF854D0E)

    @Composable
    fun warningContainer(dark: Boolean = isSystemInDarkTheme()): Color =
        if (dark) Color(0xFF3A2C0E) else Color(0xFFFEF3C7)
}

@Composable
fun BankMailTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = BankMailTypography,
        content = content
    )
}
