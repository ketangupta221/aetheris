/*
 * :app — Phase 0 placeholder Hilt module (Task 6.3).
 *
 * Task 6.3 requires "empty modules created per layer" so Hilt's graph is
 * wired and the build is green *before* any real bindings exist. Phase 1
 * Task 27 populates the `DomainModule`, `DataModule`, `SchedulingModule`
 * etc. in their owning feature modules; this file is intentionally empty
 * and serves two purposes:
 *
 *   1. Documents where cross-cutting application-scope bindings go when
 *      they cannot be sensibly placed in a feature / core module (very
 *      few should qualify — we prefer per-layer modules).
 *
 *   2. Keeps the `:app` module's Hilt graph non-empty so the KSP
 *      processor never needs a special case for an empty install target.
 *
 * Note: [dev.aetheris.planner.app.data.settings.ThemePreferences] is bound
 * automatically via its `@Inject constructor(@ApplicationContext)` — it
 * does not require a `@Provides` entry here.
 */
package dev.aetheris.planner.app.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** App-scope Hilt module. Intentionally empty in Phase 0 — see file header. */
@Module
@InstallIn(SingletonComponent::class)
internal object AppModule
