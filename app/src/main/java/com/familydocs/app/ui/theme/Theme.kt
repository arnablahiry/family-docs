package com.familydocs.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.familydocs.app.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Ink navy + marigold, matching the prototype.
private val Light = lightColorScheme(
    primary = Color(0xFF1E2F55), onPrimary = Color.White,
    secondary = Color(0xFFE3962E), onSecondary = Color(0xFF1A1205),
    secondaryContainer = Color(0xFFFBEBD3), onSecondaryContainer = Color(0xFF3A2A10),
    background = Color(0xFFF4F5F9), onBackground = Color(0xFF141B2B),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF141B2B),
    surfaceVariant = Color(0xFFECEEF4), onSurfaceVariant = Color(0xFF667085),
    surfaceContainer = Color(0xFFFFFFFF), surfaceContainerHigh = Color(0xFFECEEF4),
    outline = Color(0xFFD5D9E2), outlineVariant = Color(0xFFE2E5ED),
    error = Color(0xFFC2453D),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFE6E6E9), onPrimary = Color(0xFF121213),
    secondary = Color(0xFFEEB056), onSecondary = Color(0xFF1A1205),
    secondaryContainer = Color(0xFF3A2D16), onSecondaryContainer = Color(0xFFFBE3BC),
    // Neutral dark greys (no navy tint).
    background = Color(0xFF121213), onBackground = Color(0xFFEEEEF0),
    surface = Color(0xFF1C1C1E), onSurface = Color(0xFFEEEEF0),
    surfaceVariant = Color(0xFF2A2A2D), onSurfaceVariant = Color(0xFF9D9DA3),
    surfaceContainer = Color(0xFF1C1C1E), surfaceContainerHigh = Color(0xFF2A2A2D),
    outline = Color(0xFF3C3C40), outlineVariant = Color(0xFF333336),
    error = Color(0xFFE9786F),
)

/** Display serif for names/titles, monospace for card numbers. Drop Young Serif / Sora / JetBrains Mono TTFs into res/font to match the prototype exactly. */

/** Abaddon v1.2 (CC BY 3.0), Albedo metrics. Light covers normal weights, Bold covers medium and up. */
val BrandFont: FontFamily = FontFamily(
    Font(R.font.abaddon_light, FontWeight.Light),
    Font(R.font.abaddon_light, FontWeight.Normal),
    Font(R.font.abaddon_bold, FontWeight.Medium),
    Font(R.font.abaddon_bold, FontWeight.SemiBold),
    Font(R.font.abaddon_bold, FontWeight.Bold),
)

/** Every piece of text in the app uses the brand font. */
val DisplayFont = BrandFont
val MonoFont get() = BrandFont

@Immutable data class Extra(val dark: Boolean, val ok: Color)
val LocalExtra = staticCompositionLocalOf { Extra(false, Color(0xFF2E8B57)) }

private val Type = Typography().let { t ->
    fun TextStyle.brand() = copy(fontFamily = BrandFont)
    Typography(
        displayLarge = t.displayLarge.brand(), displayMedium = t.displayMedium.brand(),
        displaySmall = TextStyle(fontFamily = BrandFont, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp),
        headlineLarge = t.headlineLarge.brand(),
        headlineMedium = TextStyle(fontFamily = BrandFont, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
        headlineSmall = t.headlineSmall.brand(),
        titleLarge = TextStyle(fontFamily = BrandFont, fontWeight = FontWeight.Bold, fontSize = 19.sp),
        titleMedium = TextStyle(fontFamily = BrandFont, fontWeight = FontWeight.Bold, fontSize = 16.sp),
        titleSmall = t.titleSmall.brand(),
        bodyLarge = t.bodyLarge.brand(), bodyMedium = t.bodyMedium.brand(), bodySmall = t.bodySmall.brand(),
        labelLarge = t.labelLarge.brand(), labelMedium = t.labelMedium.brand(),
        labelSmall = TextStyle(fontFamily = BrandFont, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.sp),
    )
}

@Composable
fun FamilyDocsTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalExtra provides Extra(dark, if (dark) Color(0xFF5CC28A) else Color(0xFF2E8B57))) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Type, content = content)
    }
}

