package com.esper.android.ui.theme

import androidx.compose.ui.graphics.Color

data class BookCoverTheme(
    val accentColor: Color,
    val spineColor: Color,
    val tagBgColor: Color,
    val gradientColors: List<Color>
)

object CoverThemeEngine {
    fun getTheme(coverPreset: String?, coverImageUrl: String? = null): BookCoverTheme {
        return when (coverPreset?.lowercase()) {
            "quantum" -> BookCoverTheme(
                accentColor = Color(0xFF34D399), // Emerald 400
                spineColor = Color(0xFF10B981),
                tagBgColor = Color(0x3334D399),
                gradientColors = listOf(Color(0xFF064E3B), Color(0xFF0F172A), Color(0xFF134E4A))
            )
            "solar" -> BookCoverTheme(
                accentColor = Color(0xFFFBBF24), // Amber 400
                spineColor = Color(0xFFF59E0B),
                tagBgColor = Color(0x33FBBF24),
                gradientColors = listOf(Color(0xFF78350F), Color(0xFF451A03), Color(0xFF0F172A))
            )
            "midnight" -> BookCoverTheme(
                accentColor = Color(0xFF60A5FA), // Blue 400
                spineColor = Color(0xFF3B82F6),
                tagBgColor = Color(0x3360A5FA),
                gradientColors = listOf(Color(0xFF1E293B), Color(0xFF1E3A8A), Color(0xFF0F172A))
            )
            "nebula" -> BookCoverTheme(
                accentColor = Color(0xFFC084FC), // Purple 400
                spineColor = Color(0xFFA855F7),
                tagBgColor = Color(0x33C084FC),
                gradientColors = listOf(Color(0xFF581C87), Color(0xFF3B0764), Color(0xFF0F172A))
            )
            "crimson" -> BookCoverTheme(
                accentColor = Color(0xFFFB7185), // Rose 400
                spineColor = Color(0xFFF43F5E),
                tagBgColor = Color(0x33FB7185),
                gradientColors = listOf(Color(0xFF881337), Color(0xFF4C0519), Color(0xFF0F172A))
            )
            "cybergrid" -> BookCoverTheme(
                accentColor = Color(0xFF818CF8),
                spineColor = Color(0xFF6366F1),
                tagBgColor = Color(0x33818CF8),
                gradientColors = listOf(Color(0xFF020617), Color(0xFF1E1B4B))
            )
            "event-horizon" -> BookCoverTheme(
                accentColor = Color(0xFFF97316),
                spineColor = Color(0xFFEA580C),
                tagBgColor = Color(0x33F97316),
                gradientColors = listOf(Color(0xFF000000), Color(0xFFEA580C), Color(0xFF030712))
            )
            "topographic" -> BookCoverTheme(
                accentColor = Color(0xFF38BDF8),
                spineColor = Color(0xFF0E7490),
                tagBgColor = Color(0x3338BDF8),
                gradientColors = listOf(Color(0xFF090D16), Color(0xFF0E7490), Color(0xFF020617))
            )
            "aurora" -> BookCoverTheme(
                accentColor = Color(0xFF34D399),
                spineColor = Color(0xFF10B981),
                tagBgColor = Color(0x3334D399),
                gradientColors = listOf(Color(0xFF022C22), Color(0xFF042F2E), Color(0xFF172554))
            )
            "abyss" -> BookCoverTheme(
                accentColor = Color(0xFF06B6D4),
                spineColor = Color(0xFF0891B2),
                tagBgColor = Color(0x3306B6D4),
                gradientColors = listOf(Color(0xFF0891B2), Color(0xFF164E63), Color(0xFF020617))
            )
            "monolith" -> BookCoverTheme(
                accentColor = Color(0xFF94A3B8),
                spineColor = Color(0xFF64748B),
                tagBgColor = Color(0x3394A3B8),
                gradientColors = listOf(Color(0xFF1E293B), Color(0xFF0F172A), Color(0xFF020617))
            )
            "prismatic" -> BookCoverTheme(
                accentColor = Color(0xFFE879F9),
                spineColor = Color(0xFFA855F7),
                tagBgColor = Color(0x33E879F9),
                gradientColors = listOf(Color(0xFF4338CA), Color(0xFF7E22CE), Color(0xFFBE185D))
            )
            "retrowave" -> BookCoverTheme(
                accentColor = Color(0xFFEC4899),
                spineColor = Color(0xFFDB2777),
                tagBgColor = Color(0x33EC4899),
                gradientColors = listOf(Color(0xFF311042), Color(0xFFEC4899), Color(0xFFF59E0B))
            )
            "supernova" -> BookCoverTheme(
                accentColor = Color(0xFFF59E0B),
                spineColor = Color(0xFFF97316),
                tagBgColor = Color(0x33F59E0B),
                gradientColors = listOf(Color(0xFFFEF08A), Color(0xFFF97316), Color(0xFF020617))
            )
            "matrix" -> BookCoverTheme(
                accentColor = Color(0xFF10B981),
                spineColor = Color(0xFF059669),
                tagBgColor = Color(0x3310B981),
                gradientColors = listOf(Color(0xFF020F08), Color(0xFF064E3B))
            )
            "hyperdrive" -> BookCoverTheme(
                accentColor = Color(0xFF60A5FA),
                spineColor = Color(0xFF3B82F6),
                tagBgColor = Color(0x3360A5FA),
                gradientColors = listOf(Color(0xFF020617), Color(0xFF1E3A8A), Color(0xFF000000))
            )
            "molten" -> BookCoverTheme(
                accentColor = Color(0xFFEF4444),
                spineColor = Color(0xFFDC2626),
                tagBgColor = Color(0x33EF4444),
                gradientColors = listOf(Color(0xFFDC2626), Color(0xFF78350F), Color(0xFF09090B))
            )
            "lunar" -> BookCoverTheme(
                accentColor = Color(0xFFCBD5E1),
                spineColor = Color(0xFF94A3B8),
                tagBgColor = Color(0x33CBD5E1),
                gradientColors = listOf(Color(0xFF475569), Color(0xFF1E293B), Color(0xFF020617))
            )
            "glitch" -> BookCoverTheme(
                accentColor = Color(0xFFF43F5E),
                spineColor = Color(0xFFE11D48),
                tagBgColor = Color(0x33F43F5E),
                gradientColors = listOf(Color(0xFF18181B), Color(0xFF09090B), Color(0xFF030712))
            )
            "acid" -> BookCoverTheme(
                accentColor = Color(0xFFA3E635),
                spineColor = Color(0xFF65A30D),
                tagBgColor = Color(0x33A3E635),
                gradientColors = listOf(Color(0xFF65A30D), Color(0xFF365314), Color(0xFF020617))
            )
            "obsidian-gold" -> BookCoverTheme(
                accentColor = Color(0xFFEAB308),
                spineColor = Color(0xFFCA8A04),
                tagBgColor = Color(0x33EAB308),
                gradientColors = listOf(Color(0xFF18181B), Color(0xFFCA8A04), Color(0xFF000000))
            )
            "glacier" -> BookCoverTheme(
                accentColor = Color(0xFF38BDF8),
                spineColor = Color(0xFF0284C7),
                tagBgColor = Color(0x3338BDF8),
                gradientColors = listOf(Color(0xFF0284C7), Color(0xFF0C4A6E), Color(0xFF020617))
            )
            "parchment" -> BookCoverTheme(
                accentColor = Color(0xFFD6D3D1),
                spineColor = Color(0xFFA8A29E),
                tagBgColor = Color(0x33D6D3D1),
                gradientColors = listOf(Color(0xFF292524), Color(0xFF1C1917), Color(0xFF0C0A09))
            )
            else -> BookCoverTheme(
                accentColor = Color(0xFF818CF8), // Celestial Indigo 400
                spineColor = Color(0xFF6366F1),
                tagBgColor = Color(0x33818CF8),
                gradientColors = listOf(Color(0xFF1E1B4B), Color(0xFF0F172A), Color(0xFF2E1065))
            )
        }
    }
}
