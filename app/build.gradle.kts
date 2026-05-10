// :app — the single entry-point module. Sets `applicationId = dev.aetheris.planner`
// per design §2.1 "Android identity"; the Kotlin namespace is the per-module
// suffix `dev.aetheris.planner.app`. This module depends on every feature +
// core + ai + distribution module so that Hilt's `@HiltAndroidApp` can compose
// the full graph. No Activity/Application classes are written yet — those land
// in Task 6.1.
import java.util.Base64

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// ---------------------------------------------------------------------------
// Release signing (Task 1.3, design §9.4).
//
// Release builds are signed from CI secrets. `KEY_ALIAS` is always the literal
// "aetheris-release" per design §2.1; CI just has to surface the keystore
// bytes and passwords. The keystore is delivered base64-encoded in the env
// var `KEYSTORE_BASE64` and materialized into `build/release-keystore.jks`
// (under the gitignored `build/` tree) just before signing runs.
//
// Two failure modes need different behavior:
//   1. Developer runs `./gradlew assembleDebug` locally with no env vars set.
//      We must NOT fail — the debug build has no use for the release keystore.
//   2. CI runs `./gradlew assembleRelease` or `bundleRelease` with missing
//      secrets. We must fail fast and loudly rather than silently producing
//      an unsigned or debug-signed release artifact.
//
// We implement (2) by inspecting `gradle.startParameter.taskNames` at config
// time; when a release-assembly task is requested we require every env var.
// Only explicit release tasks trigger the check — running `./gradlew build`
// or `./gradlew test` on a developer machine with no env vars set continues
// to work and produces a debug-signed artifact (AGP's default fallback).
// ---------------------------------------------------------------------------
val releaseTaskRequested: Boolean = gradle.startParameter.taskNames.any { taskName ->
    val normalized = taskName.substringAfterLast(':').lowercase()
    normalized == "assemblerelease" ||
        normalized == "bundlerelease" ||
        normalized == "installrelease" ||
        normalized == "packagerelease"
}

val keystoreBase64: String? = System.getenv("KEYSTORE_BASE64")
val keystorePassword: String? = System.getenv("KEYSTORE_PASSWORD")
val keyAlias: String? = System.getenv("KEY_ALIAS")
val keyPassword: String? = System.getenv("KEY_PASSWORD")
val releaseSigningEnvComplete: Boolean =
    !keystoreBase64.isNullOrBlank() &&
        !keystorePassword.isNullOrBlank() &&
        !keyAlias.isNullOrBlank() &&
        !keyPassword.isNullOrBlank()

if (releaseTaskRequested && !releaseSigningEnvComplete) {
    throw GradleException(
        "Release signing env vars are missing. Set KEYSTORE_BASE64, KEYSTORE_PASSWORD, " +
            "KEY_ALIAS, KEY_PASSWORD. See docs/ci.md.",
    )
}

// Materialize the release keystore from its base64 representation when the
// env var is present. Using `layout.buildDirectory` keeps the decoded bytes
// inside the gitignored `build/` tree so they never get committed.
val releaseKeystoreFile: File = layout.buildDirectory
    .file("release-keystore.jks")
    .get()
    .asFile

if (releaseSigningEnvComplete) {
    releaseKeystoreFile.parentFile.mkdirs()
    releaseKeystoreFile.writeBytes(Base64.getDecoder().decode(keystoreBase64))
}

android {
    namespace = "dev.aetheris.planner.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.aetheris.planner"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Debug — Android's auto-generated debug keystore at
        // `~/.android/debug.keystore` is used by default; no explicit config
        // is required. We leave this comment to make the expectation
        // explicit to contributors.

        // Release — wired only when CI secrets are present. Local developer
        // builds of `:app` debug or any library module continue to work
        // without these env vars; the fail-fast check above ensures a release
        // build without them errors cleanly rather than silently falling back.
        if (releaseSigningEnvComplete) {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        release {
            if (releaseSigningEnvComplete) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        // Robolectric needs Android resources from the module to resolve
        // @style/Theme.AppCompat.Light.NoActionBar in the manifest when
        // Application is inflated. Without this flag, Robolectric runs
        // against the framework-only resource table and the scaffold test
        // fails at Application inflation with "resource not found".
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Core
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:scheduling"))
    implementation(project(":core:notifications"))

    // Features
    implementation(project(":feature:timeline"))
    implementation(project(":feature:tasks"))
    implementation(project(":feature:chat"))
    implementation(project(":feature:quickcapture"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:habits"))
    implementation(project(":feature:focus"))
    implementation(project(":feature:rituals"))

    // AI
    implementation(project(":ai:router"))
    implementation(project(":ai:nlparser"))
    implementation(project(":ai:entity-resolver"))
    implementation(project(":ai:llm"))
    implementation(project(":ai:speech"))

    // Distribution
    implementation(project(":distribution:model-downloader"))

    // AndroidX foundation
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose.ui)

    // DI + navigation
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler)
    ksp(libs.hilt.work.compiler)

    // Background work + preferences
    implementation(libs.work.runtime.ktx)
    implementation(libs.datastore.preferences)

    // Kotlinx
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.bundles.testing.jvm)
    testImplementation(libs.robolectric)
    // Robolectric-based unit tests in this module (ZeroNetworkRuntimeScaffoldTest)
    // reach into :core:common (NetworkDenyingSocketFactory, ConsentedDownloadScope)
    // and use androidx.test.core's ApplicationProvider. AGP does not expose
    // `main`-sourceset project deps to the unit-test classpath automatically
    // for JVM-only cross-module consumers, and `androidx.test:core` lives
    // only in the `testing-android` instrumented-test bundle. Add both
    // explicitly so `./gradlew :app:testDebugUnitTest` compiles the Task 3.3
    // scaffold test. Wiring landed alongside Task 6 when MainActivity first
    // forced the full test graph to compile.
    testImplementation(project(":core:common"))
    testImplementation(libs.androidx.test.core)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    // Robolectric uses JUnit 4's RunWith/Runner mechanism; surface those
    // tests through the JUnit Platform via the Vintage engine so
    // `useJUnitPlatform()` picks them up alongside Jupiter tests.
    testRuntimeOnly(libs.junit.vintage.engine)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.bundles.testing.android)
    androidTestImplementation(libs.compose.ui.test.junit4)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
