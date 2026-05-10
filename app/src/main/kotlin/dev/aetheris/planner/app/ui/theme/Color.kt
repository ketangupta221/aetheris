/*
 * :app — Color palette for the Aetheris theme (Task 7.1).
 *
 * The brand is "Aetheris" (aether + intelligent system), so we ground the
 * palette on a deep indigo/violet primary that reads well against both white
 * and near-black surfaces. These ColorSchemes are the Phase 0 fallback used
 * when Material You dynamic color is unavailable or disabled (Task 7.2). The
 * full Material You polish pass lands in Phase 2; these values are
 * intentionally simple and conservative so they survive a dynamic-color
 * redesign without churn.
 *
 * Design references:
 *  - Req 22 AC 1: light / dark / follow-system modes.
 *  - Req 22 AC 2: Material You dynamic color path (handled in Theme.kt).
 *  - design §1 — Compose / Material 3.
 */
package dev.aetheris.planner.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Brand anchors — `Aetheris` indigo. Chosen to hit WCAG AA contrast against
// both the light onPrimary (white) and the dark onPrimary (near-black)
// Material 3 defaults.
private val AetherisIndigo40 = Color(0xFF4B4FE9)
private val AetherisIndigo80 = Color(0xFFBFC2FF)
private val AetherisIndigo90 = Color(0xFFDFE0FF)
private val AetherisIndigo10 = Color(0xFF00105C)
private val AetherisIndigo20 = Color(0xFF1C1F82)
private val AetherisIndigo30 = Color(0xFF3538A9)

// Secondary — a muted cyan that complements the indigo primary without
// fighting it on the Timeline_View's dense task cards.
private val AetherisCyan40 = Color(0xFF0D6B78)
private val AetherisCyan80 = Color(0xFF82D4E3)
private val AetherisCyan90 = Color(0xFFAEF0FF)
private val AetherisCyan10 = Color(0xFF001F25)

// Tertiary — a warm coral accent for status / highlights.
private val AetherisCoral40 = Color(0xFFB34266)
private val AetherisCoral80 = Color(0xFFFFB1C3)
private val AetherisCoral90 = Color(0xFFFFD9E1)
private val AetherisCoral10 = Color(0xFF3F0018)

// Error — Material 3 defaults are good enough; we reuse the official tones.
private val ErrorLight = Color(0xFFBA1A1A)
private val ErrorDark = Color(0xFFFFB4AB)

/**
 * Light ColorScheme used when the theme mode resolves to light AND either the
 * device lacks Material You support or the user has disabled dynamic color.
 */
internal val AetherisLightColors = lightColorScheme(
    primary = AetherisIndigo40,
    onPrimary = Color.White,
    primaryContainer = AetherisIndigo90,
    onPrimaryContainer = AetherisIndigo10,
    secondary = AetherisCyan40,
    onSecondary = Color.White,
    secondaryContainer = AetherisCyan90,
    onSecondaryContainer = AetherisCyan10,
    tertiary = AetherisCoral40,
    onTertiary = Color.White,
    tertiaryContainer = AetherisCoral90,
    onTertiaryContainer = AetherisCoral10,
    error = ErrorLight,
    onError = Color.White,
    background = Color(0xFFFEFBFF),
    onBackground = Color(0xFF1B1B1F),
    surface = Color(0xFFFEFBFF),
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFE3E1EC),
    onSurfaceVariant = Color(0xFF46464F),
    outline = Color(0xFF777680),
)

/**
 * Dark ColorScheme used when the theme mode resolves to dark AND either the
 * device lacks Material You support or the user has disabled dynamic color.
 */
internal val AetherisDarkColors = darkColorScheme(
    primary = AetherisIndigo80,
    onPrimary = AetherisIndigo20,
    primaryContainer = AetherisIndigo30,
    onPrimaryContainer = AetherisIndigo90,
    secondary = AetherisCyan80,
    onSecondary = AetherisCyan10,
    secondaryContainer = Color(0xFF00515E),
    onSecondaryContainer = AetherisCyan90,
    tertiary = AetherisCoral80,
    onTertiary = AetherisCoral10,
    tertiaryContainer = Color(0xFF82274C),
    onTertiaryContainer = AetherisCoral90,
    error = ErrorDark,
    onError = Color(0xFF690005),
    background = Color(0xFF1B1B1F),
    onBackground = Color(0xFFE4E1E9),
    surface = Color(0xFF1B1B1F),
    onSurface = Color(0xFFE4E1E9),
    surfaceVariant = Color(0xFF46464F),
    onSurfaceVariant = Color(0xFFC7C5D0),
    outline = Color(0xFF91909A),
)
