package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DarkSecurityColorScheme = darkColorScheme(
  primary = CyberEmerald,
  onPrimary = Color(0xFF042F2E),
  primaryContainer = CyberEmeraldDark,
  onPrimaryContainer = Color(0xFFA7F3D0),
  secondary = EncryptionCyan,
  onSecondary = Color(0xFF083344),
  secondaryContainer = Color(0xFF164E63),
  onSecondaryContainer = Color(0xFFCFFAFE),
  tertiary = ShieldViolet,
  onTertiary = Color(0xFF1E1B4B),
  background = ObsidianBackground,
  onBackground = TextPrimaryDark,
  surface = CardSurfaceDark,
  onSurface = TextPrimaryDark,
  surfaceVariant = CardSurfaceElevated,
  onSurfaceVariant = TextSecondaryDark,
  outline = BorderDark,
  error = DangerRose,
  onError = Color.White
)

private val LightSecurityColorScheme = lightColorScheme(
  primary = Color(0xFF059669),
  onPrimary = Color.White,
  primaryContainer = Color(0xFFD1FAE5),
  secondary = Color(0xFF0891B2),
  background = Color(0xFFF8FAFC),
  surface = Color.White,
  surfaceVariant = Color(0xFFF1F5F9),
  onBackground = Color(0xFF0F172A),
  onSurface = Color(0xFF0F172A),
  outline = Color(0xFFCBD5E1)
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Default to sleek dark mode as requested
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkSecurityColorScheme else LightSecurityColorScheme
  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
