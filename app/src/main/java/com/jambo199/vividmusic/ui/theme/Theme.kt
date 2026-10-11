package com.jambo199.vividmusic.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import com.jambo199.vividmusic.data.Settings
import com.jambo199.vividmusic.data.ThemeMode

/** Accent colours offered when Material You colours are off. */
val AccentColors = listOf(
    "Vivid violet" to Color(0xFF7C4DFF),
    "Ocean blue" to Color(0xFF1E88E5),
    "Teal" to Color(0xFF00897B),
    "Forest green" to Color(0xFF43A047),
    "Sunset orange" to Color(0xFFF4511E),
    "Hot pink" to Color(0xFFD81B60),
    "Crimson" to Color(0xFFE53935),
    "Gold" to Color(0xFFF9A825),
    "Indigo" to Color(0xFF3F51B5),
    "Sky" to Color(0xFF039BE5),
    "Mint" to Color(0xFF26A69A),
    "Lime" to Color(0xFF7CB342),
    "Amber" to Color(0xFFFFB300),
    "Coral" to Color(0xFFFF7043),
    "Rose" to Color(0xFFEC407A),
    "Lavender" to Color(0xFF9575CD),
    "Mocha" to Color(0xFF8D6E63),
    "Slate" to Color(0xFF607D8B),
)

/** Approximates a Material 3 tone: same hue/saturation, chosen lightness (0..100). */
private fun Color.tone(lightness: Int, saturationScale: Float = 1f): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[1] = (hsl[1] * saturationScale).coerceIn(0f, 1f)
    hsl[2] = lightness / 100f
    return Color(ColorUtils.HSLToColor(hsl))
}

private fun Color.rotate(degrees: Float): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[0] = (hsl[0] + degrees + 360f) % 360f
    return Color(ColorUtils.HSLToColor(hsl))
}

fun schemeFromSeed(seed: Color, dark: Boolean): ColorScheme {
    val secondary = seed
    val tertiary = seed.rotate(60f)
    return if (dark) {
        darkColorScheme(
            primary = seed.tone(78),
            onPrimary = seed.tone(18),
            primaryContainer = seed.tone(30),
            onPrimaryContainer = seed.tone(90),
            inversePrimary = seed.tone(40),
            secondary = secondary.tone(78, 0.35f),
            onSecondary = secondary.tone(18, 0.35f),
            secondaryContainer = secondary.tone(28, 0.35f),
            onSecondaryContainer = secondary.tone(90, 0.35f),
            tertiary = tertiary.tone(78, 0.6f),
            onTertiary = tertiary.tone(18, 0.6f),
            tertiaryContainer = tertiary.tone(30, 0.6f),
            onTertiaryContainer = tertiary.tone(90, 0.6f),
            background = seed.tone(7, 0.12f),
            onBackground = seed.tone(90, 0.08f),
            surface = seed.tone(7, 0.12f),
            onSurface = seed.tone(90, 0.08f),
            surfaceVariant = seed.tone(22, 0.15f),
            onSurfaceVariant = seed.tone(80, 0.12f),
            surfaceTint = seed.tone(78),
            outline = seed.tone(58, 0.1f),
            outlineVariant = seed.tone(28, 0.1f),
            surfaceBright = seed.tone(22, 0.12f),
            surfaceDim = seed.tone(7, 0.12f),
            surfaceContainerLowest = seed.tone(4, 0.12f),
            surfaceContainerLow = seed.tone(10, 0.12f),
            surfaceContainer = seed.tone(12, 0.12f),
            surfaceContainerHigh = seed.tone(16, 0.12f),
            surfaceContainerHighest = seed.tone(21, 0.12f),
        )
    } else {
        lightColorScheme(
            primary = seed.tone(42),
            onPrimary = Color.White,
            primaryContainer = seed.tone(90),
            onPrimaryContainer = seed.tone(12),
            inversePrimary = seed.tone(80),
            secondary = secondary.tone(40, 0.35f),
            onSecondary = Color.White,
            secondaryContainer = secondary.tone(90, 0.35f),
            onSecondaryContainer = secondary.tone(12, 0.35f),
            tertiary = tertiary.tone(40, 0.6f),
            onTertiary = Color.White,
            tertiaryContainer = tertiary.tone(90, 0.6f),
            onTertiaryContainer = tertiary.tone(12, 0.6f),
            background = seed.tone(98, 0.3f),
            onBackground = seed.tone(10, 0.1f),
            surface = seed.tone(98, 0.3f),
            onSurface = seed.tone(10, 0.1f),
            surfaceVariant = seed.tone(91, 0.2f),
            onSurfaceVariant = seed.tone(30, 0.12f),
            surfaceTint = seed.tone(42),
            outline = seed.tone(50, 0.1f),
            outlineVariant = seed.tone(80, 0.1f),
            surfaceBright = seed.tone(98, 0.3f),
            surfaceDim = seed.tone(87, 0.2f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = seed.tone(96, 0.3f),
            surfaceContainer = seed.tone(94, 0.3f),
            surfaceContainerHigh = seed.tone(92, 0.3f),
            surfaceContainerHighest = seed.tone(90, 0.3f),
        )
    }
}

private fun ColorScheme.pureBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF101010),
    surfaceContainerHigh = Color(0xFF181818),
    surfaceContainerHighest = Color(0xFF222222),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

val MonoDigits = TextStyle(fontFeatureSettings = "tnum", fontSize = 12.sp)

@Composable
fun isAppInDarkTheme(settings: Settings): Boolean = when (settings.themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** [artworkSeed] is the playing song's artwork colour, used when "Colours from album art" is on. */
@Composable
fun VividTheme(settings: Settings, artworkSeed: Color? = null, content: @Composable () -> Unit) {
    val dark = isAppInDarkTheme(settings)
    val context = LocalContext.current
    val seed = artworkSeed.takeIf { settings.artworkColors }
    val scheme = remember(dark, settings.dynamicColor, settings.accentIndex, settings.pureBlack, seed) {
        val base = when {
            seed != null -> schemeFromSeed(seed, dark)
            settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            else -> schemeFromSeed(AccentColors.getOrElse(settings.accentIndex) { AccentColors[0] }.second, dark)
        }
        if (dark && settings.pureBlack) base.pureBlack() else base
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    MaterialTheme(colorScheme = scheme, typography = AppTypography) {
        // Text and icons default to black unless something says otherwise; make the default
        // follow the theme so nothing turns up black-on-dark in dark mode.
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
    }
}
