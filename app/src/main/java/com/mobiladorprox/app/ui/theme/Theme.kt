package com.mobiladorprox.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MobiladorColorScheme = darkColorScheme(
    primary = NeonPurple,
    onPrimary = TextPrimary,
    secondary = NeonBlue,
    onSecondary = BackgroundBlack,
    background = BackgroundBlack,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    error = StatusRed
)

@Composable
fun MobiladorProXTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // O app usa um tema escuro fixo (identidade "gamer"), independente do
    // tema do sistema, por isso darkTheme não altera o esquema de cores.
    MaterialTheme(
        colorScheme = MobiladorColorScheme,
        typography = MobiladorTypography,
        content = content
    )
}
