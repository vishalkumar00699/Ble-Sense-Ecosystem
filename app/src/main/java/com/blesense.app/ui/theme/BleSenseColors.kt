package com.blesense.app.ui.theme

import androidx.compose.ui.graphics.Color

object BleSenseColors {
    // Primary brand colors
    val PrimaryGreen = Color(0xFF00D4A0)  // oklch(0.696 0.17 162.48)
    val PrimaryGreenDark = Color(0xFF00B889)
    val PrimaryGreenLight = Color(0xFF33E6B3)

    // Background colors
    val BackgroundDark = Color(0xFF0A0A0F)  // oklch(0.141 0.005 285.823)
    val SurfaceDark = Color(0xFF1C1C24)     // oklch(0.21 0.006 285.885)
    val SurfaceLight = Color(0xFF282830)    // oklch(0.274 0.006 286.033)

    // Text colors
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF9F9FA9)
    val TextTertiary = Color(0xFF6B6B76)

    // Status colors
    val BluetoothBlue = Color(0xFF5E9EFF)
    val WarningOrange = Color(0xFFFF8C42)
    val ErrorRed = Color(0xFFFF5E5E)
    
    // New Accents from React prototype styling
    val YellowAccent = Color(0xFFFBBF24) // Data Logger, Sun, Advertising
    val PurpleAccent = Color(0xFFA78BFA) // Robot Control
    val OrangeAccent = Color(0xFFFB923C) // Setting/Analytics
    val RedAccent = Color(0xFFFF5E5E)

    // Glass morphism colors
    val GlassBackground = Color(0x1AFFFFFF)
    val GlassBorder = Color(0x33FFFFFF)
}
