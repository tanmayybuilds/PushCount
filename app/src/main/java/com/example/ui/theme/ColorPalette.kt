package com.example.ui.theme

import androidx.compose.ui.graphics.Color

enum class ColorPalette(
    val id: String,
    val title: String,
    val subtitle: String,
    val primaryLight: Color,
    val primaryDark: Color,
    val accentGlow: Color
) {
    SOLAR_BLAZE(
        id = "SOLAR_BLAZE",
        title = "Solar Blaze",
        subtitle = "High-energy athletic orange",
        primaryLight = Color(0xFFFF5722),
        primaryDark = Color(0xFFFF7A45),
        accentGlow = Color(0xFFFF5722)
    ),
    VOLT_LIME(
        id = "VOLT_LIME",
        title = "Volt Lime",
        subtitle = "Aggressive cyber neon lime",
        primaryLight = Color(0xFF10B981),
        primaryDark = Color(0xFF34D399),
        accentGlow = Color(0xFF10B981)
    ),
    APEX_CRIMSON(
        id = "APEX_CRIMSON",
        title = "Apex Crimson",
        subtitle = "Pure intensity warrior red",
        primaryLight = Color(0xFFEF4444),
        primaryDark = Color(0xFFF87171),
        accentGlow = Color(0xFFEF4444)
    ),
    CYBER_AZURE(
        id = "CYBER_AZURE",
        title = "Cyber Azure",
        subtitle = "Deep focus electric cyan",
        primaryLight = Color(0xFF0284C7),
        primaryDark = Color(0xFF38BDF8),
        accentGlow = Color(0xFF06B6D4)
    ),
    STEALTH_TITANIUM(
        id = "STEALTH_TITANIUM",
        title = "Stealth White",
        subtitle = "Minimalist monochrome carbon",
        primaryLight = Color(0xFF475569),
        primaryDark = Color(0xFFE2E8F0),
        accentGlow = Color(0xFF94A3B8)
    );

    companion object {
        fun fromId(id: String?): ColorPalette =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: SOLAR_BLAZE
    }
}
