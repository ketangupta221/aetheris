// Top-level build file. Plugins are declared once here with `apply false` and
// then applied by the modules that actually need them. All versions are
// sourced from `gradle/libs.versions.toml` (Task 1.2) — do not duplicate
// version strings across modules.
//
// Cross-cutting configuration applied here (per Tasks 1.4 + 1.5):
//
//   - Detekt is applied to every subproject with a shared `detekt.yml`
//     config at the repo root. Custom rule stubs (forbidden-imports for
//     java.net.*, okhttp3.*, etc.) land in later tasks (design §10.5).
//
//   - Gradle Managed Devices are registered on every Android module so
//     `./gradlew pixel7proApi34GoogleApisDebugAndroidTest` works uniformly.
//     Per design §9.1 / §9.3 the chosen device is Pixel 7 Pro API 34 with
//     Google APIs — a close proxy for the Samsung Galaxy S23.
//
//   - Android Lint severities are elevated per design §9.1 (abortOnError,
//     checkDependencies). `warningsAsErrors` flips to true in Phase 2
//     Task 36 once the baseline issue set is cleaned.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false

    // Firebase App Distribution plugin (Task 5.2). Declared at the root with
    // `apply false` so the plugin lands on the buildscript classpath, then
    // applied by `:app` only. Its configuration block in `app/build.gradle.kts`
    // runs only when `FIREBASE_APP_ID` is present in the environment, so local
    // developer debug builds are unaffected. The uploader itself authenticates
    // via `FIREBASE_CLI_TOKEN` in CI.
    alias(libs.plugins.firebase.appdistribution) apply false

    // Detekt — static analysis (Task 1.5). Declared here with `apply false`
    // so the plugin ends up on the buildscript classpath, then applied to
    // every subproject below. Version pinned inline per the Phase 0 scope:
    // if detekt usage grows we promote it to `gradle/libs.versions.toml`.
    id("io.gitlab.arturbosch.detekt") version "1.23.7" apply false

    // Manifest permission invariant (Task 2.1 + 2.2). Applied at the root so
    // the `checkManifests` task is visible as `./gradlew checkManifests` and
    // hooks into `./gradlew check`. Plugin source lives in `buildSrc`.
    id("dev.aetheris.planner.manifest-invariant")
}

// ---------------------------------------------------------------------------
// Cross-cutting subproject configuration (Tasks 1.4 + 1.5).
// ---------------------------------------------------------------------------
subprojects {
    // --- Detekt (Task 1.5) --------------------------------------------------
    apply(plugin = "io.gitlab.arturbosch.detekt")

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        config.setFrom(rootProject.files("detekt.yml"))
        buildUponDefaultConfig = true
        autoCorrect = false
        parallel = true
    }

    dependencies {
        // Pull in detekt-formatting (ktlint wrappers) so the `formatting`
        // rule set in `detekt.yml` can resolve its rules.
        add("detektPlugins", "io.gitlab.arturbosch.detekt:detekt-formatting:1.23.7")
    }

    // --- Gradle Managed Devices + Lint (Tasks 1.4 + 1.5) -------------------
    plugins.withId("com.android.library") {
        extensions.configure<com.android.build.api.dsl.LibraryExtension>("android") {
            configureManagedDevicesAndLint()
        }
        // Task 2.3 — ship every Android library module with the custom
        // manifest-internet Lint detector so the post-merge manifest case
        // (design §12.3) is caught by ./gradlew lint on any module. Skip
        // self-reference to avoid a circular dependency if the lint-rules
        // module itself ever switches to `com.android.library`.
        if (path != ":buildlogic:manifest-lint-rules") {
            dependencies.add("lintChecks", project(":buildlogic:manifest-lint-rules"))
        }
    }
    plugins.withId("com.android.application") {
        extensions.configure<com.android.build.api.dsl.ApplicationExtension>("android") {
            configureManagedDevicesAndLint()
        }
        dependencies.add("lintChecks", project(":buildlogic:manifest-lint-rules"))
    }
}

/**
 * Shared configuration for every Android module (library + application).
 * Registers the `pixel7proApi34GoogleApis` Gradle Managed Device per design
 * §9.3 and raises Android Lint severity per design §9.1.
 *
 * The function accepts the common ancestor of `ApplicationExtension` and
 * `LibraryExtension` so both module types share the exact same settings.
 */
fun com.android.build.api.dsl.CommonExtension<*, *, *, *, *, *>.configureManagedDevicesAndLint() {
    testOptions {
        managedDevices {
            devices.maybeCreate(
                "pixel7proApi34GoogleApis",
                com.android.build.api.dsl.ManagedVirtualDevice::class.java,
            ).apply {
                device = "Pixel 7 Pro"
                apiLevel = 34
                systemImageSource = "google"
            }
        }
    }
    lint {
        // Design §9.1 — Lint is a gating check in CI; warnings surface as
        // errors once the Phase 2 cleanup pass lands (Task 36).
        abortOnError = true
        warningsAsErrors = false
        checkDependencies = true
        // Dependency freshness is a maintenance concern, not a build blocker.
        disable.add("GradleDependency")
        disable.add("AndroidGradlePluginVersion")
    }
}
