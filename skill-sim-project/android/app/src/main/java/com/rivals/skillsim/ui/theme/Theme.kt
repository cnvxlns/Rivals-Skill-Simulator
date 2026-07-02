package com.rivals.skillsim.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    background = RivalsDarkBackground,
    onBackground = RivalsDarkOnBackground,
    surface = RivalsDarkSurface,
    onSurface = RivalsDarkOnSurface,
    surfaceVariant = RivalsDarkSurfaceVariant,
    onSurfaceVariant = RivalsDarkSecondaryText,
    primary = RivalsDarkPrimary,
    onPrimary = RivalsDarkOnPrimary,
    primaryContainer = RivalsDarkSurfaceVariant,
    onPrimaryContainer = RivalsDarkOnSurface,
    secondary = RivalsDarkSecondaryText,
    onSecondary = RivalsDarkBackground,
    secondaryContainer = RivalsDarkSurfaceVariant,
    onSecondaryContainer = RivalsDarkSecondaryText,
    tertiary = RivalsOpponentStat,
    onTertiary = RivalsDarkBackground,
    error = RivalsDarkError,
    onError = RivalsDarkBackground,
    outline = RivalsDarkOutline,
    outlineVariant = RivalsDarkOutline,
)

private val LightColorScheme = lightColorScheme(
    background = RivalsLightBackground,
    onBackground = RivalsLightOnBackground,
    surface = RivalsLightSurface,
    onSurface = RivalsLightOnSurface,
    surfaceVariant = RivalsLightBackground,
    onSurfaceVariant = RivalsDarkMuted,
    primary = RivalsLightPrimary,
    onPrimary = RivalsLightOnPrimary,
    primaryContainer = RivalsLightBackground,
    onPrimaryContainer = RivalsLightOnSurface,
    secondary = RivalsLightOnSurface,
    onSecondary = RivalsLightSurface,
    secondaryContainer = RivalsLightBackground,
    onSecondaryContainer = RivalsLightOnSurface,
    tertiary = RivalsOpponentStat,
    onTertiary = RivalsLightSurface,
    error = RivalsDarkError,
    onError = RivalsLightSurface,
    outline = RivalsLightOutline,
    outlineVariant = RivalsLightOutline,
)

@Composable
fun RivalsSkillSimTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = RivalsTypography,
        shapes = RivalsShapes,
        content = content,
    )
}
