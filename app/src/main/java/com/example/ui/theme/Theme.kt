package com.example.ui.theme

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

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldLight,
    onPrimary = Color(0xFF003825),
    primaryContainer = Color(0xFF005238),
    onPrimaryContainer = Color(0xFF75F8C6),
    secondary = AmberAccent,
    onSecondary = Color(0xFF422C00),
    tertiary = SkyTransfer,
    onTertiary = Color(0xFF003548),
    error = CoralExpense,
    onError = Color.White,
    background = SlateDarkBackground,
    onBackground = TextDarkPrimary,
    surface = SlateDarkSurface,
    onSurface = TextDarkPrimary,
    surfaceVariant = SlateDarkSurfaceVariant,
    onSurfaceVariant = TextDarkSecondary,
    outline = SlateDarkCardBorder
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8F5DC),
    onPrimaryContainer = Color(0xFF002115),
    secondary = AmberAccent,
    onSecondary = Color.Black,
    tertiary = SkyTransfer,
    onTertiary = Color.White,
    error = CoralExpense,
    onError = Color.White,
    background = SlateLightBackground,
    onBackground = TextLightPrimary,
    surface = SlateLightSurface,
    onSurface = TextLightPrimary,
    surfaceVariant = SlateLightSurfaceVariant,
    onSurfaceVariant = TextLightSecondary,
    outline = SlateLightCardBorder
)

@Composable
fun MoneyLensTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Use our handcrafted financial color scheme for distinct MoneyLens brand identity
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Backwards compatibility alias
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) = MoneyLensTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
