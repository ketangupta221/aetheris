// :buildlogic:manifest-lint-rules — Task 2.3.
//
// Packages a custom Android Lint detector that flags
// `android.permission.INTERNET`, `ACCESS_NETWORK_STATE`, and
// `ACCESS_WIFI_STATE` declared in any post-merge manifest outside
// `:distribution:model-downloader`. This complements the pre-merge
// `:checkManifests` Gradle task (Tasks 2.1 / 2.2): the Gradle task scans
// source manifests, and this detector scans every manifest node that Lint
// visits — including the merged manifest AGP produces for `:app`.
// Together they are the "belt and braces" for Property 39 per design §12.3.
//
// This is a PURE JVM (`java-library`) module. Lint rules are plain JARs with
// a `Lint-Registry-v2` manifest attribute; Android modules consume them via
// the `lintChecks` configuration (wired from root `build.gradle.kts`).
plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Lint API + bundled detectors used as reference while building ours.
    // `compileOnly` because Lint ships the runtime in the IDE / AGP.
    compileOnly(libs.lint.api)
    compileOnly(libs.lint.checks)

    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.truth)
    testImplementation(libs.lint)
    testImplementation(libs.lint.tests)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Advertise this JAR as a Lint rule set so AGP picks it up when a consuming
// module lists it under `lintChecks`. `Lint-Registry-v2` is the modern key;
// `Lint-Registry` is the legacy one and is no longer needed for lint 31+.
tasks.jar {
    manifest {
        attributes(
            "Lint-Registry-v2" to
                "dev.aetheris.planner.buildlogic.lint.ManifestInternetIssueRegistry",
        )
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
