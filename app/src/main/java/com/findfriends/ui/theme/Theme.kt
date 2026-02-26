package com.findfriends.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

val SurfaceDark   = Color(0xFF0D0D0D)
val SurfaceCard   = Color(0xFF1A1A1A)
val AccentBlue    = Color(0xFF2979FF)
val AccentGreen   = Color(0xFF4CAF50)
val TextPrimary   = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF9E9E9E)

val AppDarkColorScheme = darkColorScheme(
    primary      = AccentBlue,
    secondary    = AccentGreen,
    background   = SurfaceDark,
    surface      = SurfaceCard,
    onPrimary    = Color.White,
    onSecondary  = Color.Black,
    onBackground = TextPrimary,
    onSurface    = TextPrimary
)
