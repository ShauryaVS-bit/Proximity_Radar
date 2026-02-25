package com.findfriends.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

// --- Brand colours ---
val RadarGreen   = Color(0xFF00E676)   // Friend blip
val RadarCyan    = Color(0xFF00E5FF)   // Group blip
val RadarBlue    = Color(0xFF2979FF)   // User centre dot
val SurfaceDark  = Color(0xFF121212)   // Main background
val SurfaceCard  = Color(0xFF1E1E1E)   // Card / panel background
val RingWhite    = Color(0x1AFFFFFF)   // Radar ring lines (10% white)
val TextPrimary  = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF9E9E9E)

val AppDarkColorScheme = darkColorScheme(
    primary          = RadarBlue,
    secondary        = RadarCyan,
    background       = SurfaceDark,
    surface          = SurfaceCard,
    onPrimary        = Color.White,
    onSecondary      = Color.Black,
    onBackground     = TextPrimary,
    onSurface        = TextPrimary
)