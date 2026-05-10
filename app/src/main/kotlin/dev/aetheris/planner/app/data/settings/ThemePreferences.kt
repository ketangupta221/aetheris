/*
 * :app — DataStore-backed settings stub for theme mode + dynamic color
 * (Tasks 7.1 and 7.2).
 *
 * Phase 0 scope: persist the two values the theme system needs (ThemeMode,
 * useDynamicColor) so the selected theme survives process death. A real
 * Settings UI that lets the user flip these lands later (Phase 2 / Phase 3
 * polish); for Phase 0 the defaults are:
 *
 *   - ThemeMode.FollowSystem
 *   - useDynamicColor = true
 *
 * These two defaults together match the Material 3 "just do the right thing
 * on a modern Android device" experience: on a Samsung S23 running Android
 * 13+, the UI inherits the user's wallpaper palette and light/dark toggle
 * without any in-app configuration.
 *
 * A deliberately small API surface keeps this file easy to replace in
 * Phase 2 when the Settings feature module takes over. The two `setX`
 * suspenders exist so future UI code (or test code) can mutate the
 * preferences without round-tripping through a ViewModel.
 *
 * Design references:
 *  - Req 22.1 — theme mode persistence.
 *  - Req 22.2 — Material You dynamic color opt-in.
 *  - design §3 — settings values held in DataStore rather than the
 *    encrypted Room DB (no PII; no need for SQLCipher overhead).
 */
package dev.aetheris.planner.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.aetheris.planner.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore delegate for theme preferences.
 *
 * Kept as a top-level property (delegate) per the official DataStore recipe:
 * the singleton lifecycle is guaranteed by the Android process, and
 * `ThemePreferences` is annotated `@Singleton` so only one Hilt instance
 * ever holds a reference to it.
 */
private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "theme_prefs",
)

/**
 * Phase 0 settings store for the theme system.
 *
 * Injected into [dev.aetheris.planner.app.MainActivity] via Hilt's field
 * injection. The activity collects both flows as [androidx.compose.runtime.State]
 * and passes them to [dev.aetheris.planner.app.ui.theme.AetherisTheme].
 */
@Singleton
public class ThemePreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Observable theme-mode preference. Defaults to [ThemeMode.FollowSystem]. */
    public val themeMode: Flow<ThemeMode> = context.themeDataStore.data.map { prefs ->
        ThemeMode.fromRawOrDefault(prefs[Keys.MODE])
    }

    /** Observable dynamic-color preference. Defaults to `true`. */
    public val useDynamicColor: Flow<Boolean> = context.themeDataStore.data.map { prefs ->
        prefs[Keys.DYNAMIC] ?: DEFAULT_USE_DYNAMIC_COLOR
    }

    /** Persists a new [ThemeMode] selection. */
    public suspend fun setThemeMode(mode: ThemeMode) {
        context.themeDataStore.edit { prefs ->
            prefs[Keys.MODE] = mode.name
        }
    }

    /** Persists the user's dynamic-color preference. */
    public suspend fun setUseDynamicColor(enabled: Boolean) {
        context.themeDataStore.edit { prefs ->
            prefs[Keys.DYNAMIC] = enabled
        }
    }

    private object Keys {
        val MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC = booleanPreferencesKey("use_dynamic_color")
    }

    public companion object {
        /** Default for first-run and uninitialized reads. */
        public const val DEFAULT_USE_DYNAMIC_COLOR: Boolean = true
    }
}
