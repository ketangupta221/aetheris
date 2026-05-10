// buildSrc — build logic that runs inside the outer Gradle build (no
// `include(":buildSrc")` required; Gradle auto-discovers this directory).
//
// Hosts:
//   - `ManifestScanner`, a pure Kotlin component that scans AndroidManifest.xml
//     files for forbidden permissions (Task 2.1).
//   - `CheckManifestsTask` + `ManifestInvariantPlugin`, wiring the scanner into
//     the `checkManifests` task and into `./gradlew check` (Tasks 2.1, 2.2).
//   - P39 "Manifest permission invariant" PBT via jqwik (Task 2.4).
//
// The plugin ID `dev.aetheris.planner.manifest-invariant` is consumed from the
// root `build.gradle.kts` via `plugins { id("...") }`.
plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

repositories {
    mavenCentral()
    google()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    // Property-based testing for P39 (Task 2.4).
    // Versions kept in sync with `gradle/libs.versions.toml`. buildSrc cannot
    // use the version catalog directly, so these are pinned inline and should
    // be bumped together with the outer catalog when versions change.
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.3")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.11.3")
    testImplementation("net.jqwik:jqwik:1.9.1")
    testImplementation("com.google.truth:truth:1.4.4")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.3")
}

gradlePlugin {
    plugins {
        create("manifestInvariantPlugin") {
            id = "dev.aetheris.planner.manifest-invariant"
            implementationClass =
                "dev.aetheris.planner.buildlogic.ManifestInvariantPlugin"
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform {
        // jqwik registers its own JUnit Platform engine; leaving the platform
        // default includes both Jupiter and jqwik engines.
    }
    // jqwik default tries via system property, overridable per test class.
    systemProperty("jqwik.tries.default", "500")
}
