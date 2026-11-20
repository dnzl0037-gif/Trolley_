package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class TrolleyExtraColors(
    val lime: Color,
    val limeDeep: Color,
    val limeAccent: Color,
    val limeSoft: Color,
    val orange: Color,
    val orangeSoft: Color,
    val hairline: Color,
    val mutedInk: Color,
    val bannerDark: Color,
    val bannerDarkInk: Color,
    val bannerDarkMuted: Color,
)

private val LightExtraColors = TrolleyExtraColors(
    lime = LightLime,
    limeDeep = LightLimeDeep,
    limeAccent = LightLimeAccent,
    limeSoft = LightLimeSoft,
    orange = LightOrange,
    orangeSoft = LightOrangeSoft,
    hairline = LightLine,
    mutedInk = LightMuted,
    bannerDark = LightBannerDark,
    bannerDarkInk = Color(0xFFFFFFFF),
    bannerDarkMuted = Color(0xFFB7BDB0),
)

private val DarkExtraColors = TrolleyExtraColors(
    lime = DarkLime,
    limeDeep = DarkLimeDeep,
    limeAccent = DarkLimeAccent,
    limeSoft = DarkLimeSoft,
    orange = DarkOrange,
    orangeSoft = DarkOrangeSoft,
    hairline = DarkLine,
    mutedInk = DarkMuted,
    bannerDark = DarkSurface,
    bannerDarkInk = DarkInk,
    bannerDarkMuted = DarkMuted,
)

val LocalTrolleyColors = staticCompositionLocalOf { LightExtraColors }

private val TrolleyLightColorScheme = lightColorScheme(
    primary = LightCharcoal,
    onPrimary = Color.White,
    primaryContainer = LightLime,
    onPrimaryContainer = LightLimeDeep,
    secondary = LightLimeAccent,
    onSecondary = Color.White,
    secondaryContainer = LightLimeSoft,
    onSecondaryContainer = LightLimeDeep,
    tertiary = LightOrange,
    onTertiary = Color.White,
    tertiaryContainer = LightOrangeSoft,
    onTertiaryContainer = LightOrange,
    background = LightBg,
    onBackground = LightInk,
    surface = LightSurface,
    onSurface = LightInk,
    surfaceVariant = LightSurfaceAlt,
    onSurfaceVariant = LightMuted,
    outline = LightLine,
    outlineVariant = LightLine,
    error = ErrorRed,
    onError = Color.White,
)

private val TrolleyDarkColorScheme = darkColorScheme(
    primary = DarkPrimaryButton,
    onPrimary = DarkPrimaryButtonInk,
    primaryContainer = DarkLime,
    onPrimaryContainer = DarkLimeDeep,
    secondary = DarkLimeAccent,
    onSecondary = DarkLimeDeep,
    secondaryContainer = DarkLimeSoft,
    onSecondaryContainer = DarkLime,
    tertiary = DarkOrange,
    onTertiary = Color.White,
    tertiaryContainer = DarkOrangeSoft,
    onTertiaryContainer = DarkOrange,
    background = DarkBg,
    onBackground = DarkInk,
    surface = DarkSurface,
    onSurface = DarkInk,
    surfaceVariant = DarkSurfaceAlt,
    onSurfaceVariant = DarkMuted,
    outline = DarkLine,
    outlineVariant = DarkLine,
    error = DarkErrorRed,
    onError = Color(0xFF601410),
)

val TrolleyShapes = Shapes(
    small = RoundedCornerShape(9.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(19.dp),
)

object TrolleyTheme {
    val colors: TrolleyExtraColors
        @Composable
        get() = LocalTrolleyColors.current
}

@Composable
fun TrolleyTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) TrolleyDarkColorScheme else TrolleyLightColorScheme
    val extraColors = if (darkTheme) DarkExtraColors else LightExtraColors

    CompositionLocalProvider(LocalTrolleyColors provides extraColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = TrolleyTypography,
            shapes = TrolleyShapes,
            content = content,
        )
    }
}
