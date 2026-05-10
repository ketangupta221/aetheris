/*
 * :app — Theme mode enum (Task 7.1).
 *
 * Exposed as a top-level public type so that Phase 2 / Phase 3 Settings UIs
 * can render a preference picker without reaching into an internal package.
 * Persisted via [ThemePreferences] (DataStore-backed stub).
 *
 * Requirements:
 *  - Req 22 AC 1 — three modes: light, dark, follow system.
 */
package dev.aetheris.planner.app.ui.theme

/** Theme mode the user has selected; persisted in DataStore. */
public enum class ThemeMode {
    Light,
    Dark,
    FollowSystem,
    ;

    public companion object {
        /**
         * Parses a persisted string back into a [ThemeMode]. Returns
         * [FollowSystem] if [raw] is null or unrecognized so a corrupted
         * DataStore value never bricks the UI.
         */
        public fun fromRawOrDefault(raw: String?): ThemeMode =
            entries.firstOrNull { it.name == raw } ?: FollowSystem
    }
}
