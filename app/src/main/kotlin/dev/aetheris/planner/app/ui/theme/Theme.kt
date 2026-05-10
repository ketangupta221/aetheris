/*
 * :app — Root Material 3 theme for Aetheris Planner (Tasks 7.1 + 7.2).
 *
 * Responsibilities per the tasks file:
 *
 *   Task 7.1 — three theme modes (light / dark / follow-system) backed by
 *   the `ColorScheme` values in [AetherisLightColors] / [AetherisDarkColors].
 *   The selected mode is persisted in DataStore via [ThemePreferences] and
 *   pushed into this composable from [MainActivity].
 *
 *   Task 7.2 — Material You dynamic color stub. When the device is on
 *   Android 12+ (API 31, [Build.VERSION_CODES.S]) AND the user has the
 *   dynamic-color preference enabled, we source the color scheme from
 *   [dynamicLightColorScheme] / [dynamicDarkColorScheme]. Otherwise we fall
 *   back to the bundled Aetheris palette. Full Material You polish
 *   (tonal palettes, elevation overlays, brand guardrails when the system
 *   palette is exotic) is deferred to Phase 2 per the task description.
 *
 * Requirements:
 *  - Req 22 AC 1 — light / dark / follow-system modes.
 *  - Req 22 AC 2 — Material You dynamic color on API 31+.
 *  - Req 22 AC 3 — theme changes apply within one render frame. Recomposition
 *    from a StateFlow collected in MainActivity handles this automatically;
 *    the MaterialTheme composable below has no hidden caching.
 */
package dev.aetheris.planner.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Root theme wrapper. Resolves the effective [androidx.compose.material3.ColorScheme]
 * from [themeMode] + [useDynamicColor] + the system dark-mode flag, then
 * applies it via [MaterialTheme] together with [AetherisTypography].
 *
 * @param themeMode the user's explicit theme preference.
 * @param useDynamicColor when true AND API 31+, derive the scheme from the
 *   system wallpaper via Material You; when false, use the bundled Aetheris
 *   palette regardless of API level.
 * @param content the composable tree that should receive the theme.
 */
@Composable
public fun AetherisTheme(
    themeMode: ThemeMode = ThemeMode.FollowSystem,
    useDynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val darkTheme: Boolean = when (themeMode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.FollowSystem -> isSystemInDarkTheme()
    }

    val dynamicColorAvailable: Boolean =
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        dynamicColorAvailable -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> AetherisDarkColors
        else -> AetherisLightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AetherisTypography,
        content = content,
    )
}
