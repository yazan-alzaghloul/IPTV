package com.iptv.app

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*

object CinemaTokens {
    val Background = Color(0xFF080B12)
    val Navigation = Color(0xFF151B28)
    val Card = Color(0xFF151B28)
    val Elevated = Color(0xFF242D3E)
    val Accent = Color(0xFF7C3AED)
    val Cyan = Color(0xFF38BDF8)
    val Text = Color(0xFFF8FAFC)
    val Secondary = Color(0xFFA1A1AA)
    val Positive = Color(0xFF22C55E)
    val Live = Color(0xFFEF4444)
    // The saturated action color is a fill. A lighter foreground preserves small-text contrast.
    val AccentText = Color(0xFFC4B5FD)
    val LiveText = Color(0xFFFCA5A5)
    val PosterShape = RoundedCornerShape(12.dp)
    val DialogShape = RoundedCornerShape(24.dp)
}
object CinemaSpacing {
    val Unit = 4.dp
    val Small = 8.dp
    val Compact = 12.dp
    val Medium = 16.dp
    val Page = 24.dp
    val Large = 32.dp
}
val LocalCinemaTv = staticCompositionLocalOf { false }
val LocalCinemaMotion = staticCompositionLocalOf { false }
val Ink: Color @Composable get() = MaterialTheme.colorScheme.background
val Panel: Color @Composable get() = MaterialTheme.colorScheme.surface
val Sky: Color @Composable get() = MaterialTheme.colorScheme.primary
val Muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val AccentInk: Color @Composable get() = MaterialTheme.colorScheme.secondary

private fun cinemaTypography(tv: Boolean): Typography {
    val base = if (tv) 18 else 16
    fun style(size: Int, weight: FontWeight = FontWeight.Normal, line: Int = size * 3 / 2) =
        TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)
    return Typography(
        displayLarge = style(if (tv) 60 else 48, FontWeight.Bold, if (tv) 66 else 54),
        displayMedium = style(if (tv) 48 else 40, FontWeight.Bold),
        displaySmall = style(if (tv) 40 else 32, FontWeight.Bold, if (tv) 46 else 38),
        headlineLarge = style(32, FontWeight.Bold), headlineMedium = style(28, FontWeight.Bold), headlineSmall = style(24, FontWeight.SemiBold),
        titleLarge = style(if (tv) 24 else 22, FontWeight.SemiBold), titleMedium = style(base, FontWeight.SemiBold), titleSmall = style(if (tv) 16 else 14, FontWeight.Medium),
        bodyLarge = style(base), bodyMedium = style(if (tv) 16 else 14), bodySmall = style(if (tv) 14 else 12),
        labelLarge = style(if (tv) 16 else 14, FontWeight.SemiBold), labelMedium = style(if (tv) 14 else 12, FontWeight.Medium), labelSmall = style(12, FontWeight.Medium))
}

@Composable fun IptvTheme(appearance: Appearance = Appearance(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val tv = (context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    val light = appearance.theme == "light"
    val accent = if (light) { if (appearance.accent == "cyan") Color(0xFF006875) else Color(0xFF6D28D9) }
        else if (appearance.accent == "cyan") Color(0xFF006E96) else CinemaTokens.Accent
    val colors = if (light) lightColorScheme(primary = accent, onPrimary = Color.White,
        secondary = Color(0xFF5B21B6), background = Color(0xFFF4F5FA), surface = Color.White, surfaceVariant = Color(0xFFE6E9F0),
        onSurface = Color(0xFF111827), onBackground = Color(0xFF111827), onSurfaceVariant = Color(0xFF455267),
        primaryContainer = Color(0xFFEDE9FE), onPrimaryContainer = Color(0xFF4C1D95),
        secondaryContainer = Color(0xFFEDE9FE), onSecondaryContainer = Color(0xFF4C1D95))
    else darkColorScheme(primary = accent, onPrimary = Color.White,
        background = if (appearance.highContrast) Color.Black else CinemaTokens.Background,
        surface = CinemaTokens.Card, surfaceVariant = CinemaTokens.Elevated,
        onSurface = CinemaTokens.Text, onBackground = CinemaTokens.Text,
        onSurfaceVariant = if (appearance.highContrast) Color.White else CinemaTokens.Secondary,
        secondary = CinemaTokens.AccentText, primaryContainer = Color(0xFF322052), onPrimaryContainer = CinemaTokens.Text,
        secondaryContainer = Color(0xFF322052), onSecondaryContainer = CinemaTokens.Text,
        error = Color(0xFFFFB4AB))
    val activity = LocalActivity.current
    SideEffect {
        activity?.window?.let { window ->
            @Suppress("DEPRECATION")
            window.statusBarColor = colors.background.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = colors.background.toArgb()
            androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = light
                isAppearanceLightNavigationBars = light
            }
        }
    }
    val motion = appearance.motion && Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * appearance.fontScale),
        LocalCinemaTv provides tv, LocalCinemaMotion provides motion) {
        MaterialTheme(colorScheme = colors, typography = cinemaTypography(tv),
            shapes = Shapes(small = RoundedCornerShape(8.dp), medium = CinemaTokens.PosterShape, large = RoundedCornerShape(16.dp), extraLarge = CinemaTokens.DialogShape), content = content)
    }
}

@Composable fun CinemaTextButton(onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    TextButton(onClick, modifier, enabled = enabled, colors = ButtonDefaults.textButtonColors(contentColor = AccentInk), content = content)
}

@Composable fun cinemaFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedLabelColor = AccentInk, focusedBorderColor = AccentInk, cursorColor = AccentInk)
