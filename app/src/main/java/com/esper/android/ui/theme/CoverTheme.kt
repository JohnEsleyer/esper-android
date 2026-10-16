package com.esper.android.ui.theme

import androidx.compose.ui.graphics.Color

data class PresetCoverMeta(
    val id: String,
    val title: String,
    val subtitle: String,
    val theme: BookCoverTheme
)

data class BookCoverTheme(
    val accentColor: Color,
    val spineColor: Color,
    val tagBgColor: Color,
    val gradientColors: List<Color>
)

object CoverThemeEngine {
    val PRESETS = listOf(
        PresetCoverMeta(
            id = "celestial",
            title = "Celestial",
            subtitle = "Orbits",
            theme = BookCoverTheme(
                accentColor = Color(0xFF818CF8),
                spineColor = Color(0xFF6366F1),
                tagBgColor = Color(0x33818CF8),
                gradientColors = listOf(Color(0xFF1E1B4B), Color(0xFF0F172A), Color(0xFF2E1065))
            )
        ),
        PresetCoverMeta(
            id = "quantum",
            title = "Quantum",
            subtitle = "Realm",
            theme = BookCoverTheme(
                accentColor = Color(0xFF34D399),
                spineColor = Color(0xFF10B981),
                tagBgColor = Color(0x3334D399),
                gradientColors = listOf(Color(0xFF064E3B), Color(0xFF0F172A), Color(0xFF134E4A))
            )
        ),
        PresetCoverMeta(
            id = "solar",
            title = "Solar",
            subtitle = "Dynamo",
            theme = BookCoverTheme(
                accentColor = Color(0xFFFBBF24),
                spineColor = Color(0xFFF59E0B),
                tagBgColor = Color(0x33FBBF24),
                gradientColors = listOf(Color(0xFF78350F), Color(0xFF451A03), Color(0xFF0F172A))
            )
        ),
        PresetCoverMeta(
            id = "midnight",
            title = "Midnight",
            subtitle = "Velocity",
            theme = BookCoverTheme(
                accentColor = Color(0xFF60A5FA),
                spineColor = Color(0xFF3B82F6),
                tagBgColor = Color(0x3360A5FA),
                gradientColors = listOf(Color(0xFF1E293B), Color(0xFF1E3A8A), Color(0xFF0F172A))
            )
        ),
        PresetCoverMeta(
            id = "nebula",
            title = "Nebula",
            subtitle = "Cosmic",
            theme = BookCoverTheme(
                accentColor = Color(0xFFC084FC),
                spineColor = Color(0xFFA855F7),
                tagBgColor = Color(0x33C084FC),
                gradientColors = listOf(Color(0xFF581C87), Color(0xFF3B0764), Color(0xFF090314))
            )
        ),
        PresetCoverMeta(
            id = "crimson",
            title = "Crimson",
            subtitle = "Odyssey",
            theme = BookCoverTheme(
                accentColor = Color(0xFFFB7185),
                spineColor = Color(0xFFF43F5E),
                tagBgColor = Color(0x33FB7185),
                gradientColors = listOf(Color(0xFF881337), Color(0xFF4C0519), Color(0xFF020617))
            )
        ),
        PresetCoverMeta(
            id = "cybergrid",
            title = "Cyber Grid",
            subtitle = "Matrix",
            theme = BookCoverTheme(
                accentColor = Color(0xFF818CF8),
                spineColor = Color(0xFF6366F1),
                tagBgColor = Color(0x33818CF8),
                gradientColors = listOf(Color(0xFF020617), Color(0xFF1E1B4B))
            )
        ),
        PresetCoverMeta(
            id = "event-horizon",
            title = "Event",
            subtitle = "Horizon",
            theme = BookCoverTheme(
                accentColor = Color(0xFFF97316),
                spineColor = Color(0xFFEA580C),
                tagBgColor = Color(0x33F97316),
                gradientColors = listOf(Color(0xFF000000), Color(0xFFEA580C), Color(0xFF030712))
            )
        ),
        PresetCoverMeta(
            id = "topographic",
            title = "Topographic",
            subtitle = "Vector",
            theme = BookCoverTheme(
                accentColor = Color(0xFF38BDF8),
                spineColor = Color(0xFF0E7490),
                tagBgColor = Color(0x3338BDF8),
                gradientColors = listOf(Color(0xFF090D16), Color(0xFF0E7490), Color(0xFF020617))
            )
        ),
        PresetCoverMeta(
            id = "aurora",
            title = "Aurora",
            subtitle = "Borealis",
            theme = BookCoverTheme(
                accentColor = Color(0xFF34D399),
                spineColor = Color(0xFF10B981),
                tagBgColor = Color(0x3334D399),
                gradientColors = listOf(Color(0xFF022C22), Color(0xFF042F2E), Color(0xFF172554))
            )
        ),
        PresetCoverMeta(
            id = "abyss",
            title = "Abyss",
            subtitle = "Deep Trench",
            theme = BookCoverTheme(
                accentColor = Color(0xFF06B6D4),
                spineColor = Color(0xFF0891B2),
                tagBgColor = Color(0x3306B6D4),
                gradientColors = listOf(Color(0xFF0891B2), Color(0xFF164E63), Color(0xFF020617))
            )
        ),
        PresetCoverMeta(
            id = "monolith",
            title = "Monolith",
            subtitle = "Brutalist",
            theme = BookCoverTheme(
                accentColor = Color(0xFF94A3B8),
                spineColor = Color(0xFF64748B),
                tagBgColor = Color(0x3394A3B8),
                gradientColors = listOf(Color(0xFF1E293B), Color(0xFF0F172A), Color(0xFF020617))
            )
        ),
        PresetCoverMeta(
            id = "prismatic",
            title = "Prismatic",
            subtitle = "Wave",
            theme = BookCoverTheme(
                accentColor = Color(0xFFE879F9),
                spineColor = Color(0xFFA855F7),
                tagBgColor = Color(0x33E879F9),
                gradientColors = listOf(Color(0xFF4338CA), Color(0xFF7E22CE), Color(0xFFBE185D))
            )
        ),
        PresetCoverMeta(
            id = "retrowave",
            title = "Retro",
            subtitle = "Horizon",
            theme = BookCoverTheme(
                accentColor = Color(0xFFEC4899),
                spineColor = Color(0xFFDB2777),
                tagBgColor = Color(0x33EC4899),
                gradientColors = listOf(Color(0xFF311042), Color(0xFFEC4899), Color(0xFFF59E0B))
            )
        ),
        PresetCoverMeta(
            id = "supernova",
            title = "Supernova",
            subtitle = "Burst",
            theme = BookCoverTheme(
                accentColor = Color(0xFFF59E0B),
                spineColor = Color(0xFFF97316),
                tagBgColor = Color(0x33F59E0B),
                gradientColors = listOf(Color(0xFFFEF08A), Color(0xFFF97316), Color(0xFF020617))
            )
        ),
        PresetCoverMeta(
            id = "matrix",
            title = "Emerald",
            subtitle = "Matrix",
            theme = BookCoverTheme(
                accentColor = Color(0xFF10B981),
                spineColor = Color(0xFF059669),
                tagBgColor = Color(0x3310B981),
                gradientColors = listOf(Color(0xFF020F08), Color(0xFF064E3B))
            )
        ),
        PresetCoverMeta(
            id = "hyperdrive",
            title = "Hyperdrive",
            subtitle = "Velocity",
            theme = BookCoverTheme(
                accentColor = Color(0xFF60A5FA),
                spineColor = Color(0xFF3B82F6),
                tagBgColor = Color(0x3360A5FA),
                gradientColors = listOf(Color(0xFF020617), Color(0xFF1E3A8A), Color(0xFF000000))
            )
        ),
        PresetCoverMeta(
            id = "molten",
            title = "Molten",
            subtitle = "Core",
            theme = BookCoverTheme(
                accentColor = Color(0xFFEF4444),
                spineColor = Color(0xFFDC2626),
                tagBgColor = Color(0x33EF4444),
                gradientColors = listOf(Color(0xFFDC2626), Color(0xFF78350F), Color(0xFF09090B))
            )
        ),
        PresetCoverMeta(
            id = "lunar",
            title = "Lunar",
            subtitle = "Ephemeris",
            theme = BookCoverTheme(
                accentColor = Color(0xFFCBD5E1),
                spineColor = Color(0xFF94A3B8),
                tagBgColor = Color(0x33CBD5E1),
                gradientColors = listOf(Color(0xFF475569), Color(0xFF1E293B), Color(0xFF020617))
            )
        ),
        PresetCoverMeta(
            id = "glitch",
            title = "Glitch",
            subtitle = "Signal",
            theme = BookCoverTheme(
                accentColor = Color(0xFFF43F5E),
                spineColor = Color(0xFFE11D48),
                tagBgColor = Color(0x33F43F5E),
                gradientColors = listOf(Color(0xFF18181B), Color(0xFF09090B), Color(0xFF030712))
            )
        ),
        PresetCoverMeta(
            id = "acid",
            title = "Acid",
            subtitle = "Lime",
            theme = BookCoverTheme(
                accentColor = Color(0xFFA3E635),
                spineColor = Color(0xFF65A30D),
                tagBgColor = Color(0x33A3E635),
                gradientColors = listOf(Color(0xFF65A30D), Color(0xFF365314), Color(0xFF020617))
            )
        ),
        PresetCoverMeta(
            id = "obsidian-gold",
            title = "Obsidian",
            subtitle = "Gold Leaf",
            theme = BookCoverTheme(
                accentColor = Color(0xFFEAB308),
                spineColor = Color(0xFFCA8A04),
                tagBgColor = Color(0x33EAB308),
                gradientColors = listOf(Color(0xFF18181B), Color(0xFFCA8A04), Color(0xFF000000))
            )
        ),
        PresetCoverMeta(
            id = "glacier",
            title = "Glacier",
            subtitle = "Crystalline",
            theme = BookCoverTheme(
                accentColor = Color(0xFF38BDF8),
                spineColor = Color(0xFF0284C7),
                tagBgColor = Color(0x3338BDF8),
                gradientColors = listOf(Color(0xFF0284C7), Color(0xFF0C4A6E), Color(0xFF020617))
            )
        ),
        PresetCoverMeta(
            id = "parchment",
            title = "Parchment",
            subtitle = "Vintage",
            theme = BookCoverTheme(
                accentColor = Color(0xFFD6D3D1),
                spineColor = Color(0xFFA8A29E),
                tagBgColor = Color(0x33D6D3D1),
                gradientColors = listOf(Color(0xFF292524), Color(0xFF1C1917), Color(0xFF0C0A09))
            )
        )
    )

    fun getTheme(coverPreset: String?, coverImageUrl: String? = null): BookCoverTheme {
        return PRESETS.find { it.id.equals(coverPreset, ignoreCase = true) }?.theme
            ?: PRESETS[0].theme
    }
}
