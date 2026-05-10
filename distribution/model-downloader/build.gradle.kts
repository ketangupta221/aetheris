// :distribution:model-downloader — the ONE module permitted to declare
// android.permission.INTERNET per design §2.4. Packaged as a Play Feature
// Delivery dynamic feature so it is only installed after the user accepts the
// Network_Consent_Dialog.
//
// TODO(Task 41.1): Switch this module from `com.android.library` to
// `com.android.dynamic-feature` when Phase 3 lands. We use the library plugin
// during Phase 0 to keep `./gradlew projects` green before the dynamic-feature
// wiring (asset packs, Play Core SplitInstall integration) exists. The
// manifest permission invariant test in Task 2.4 already accepts this path on
// the allowlist, and the INTERNET permission itself is not declared until
// Task 41.2. OkHttp / SplitInstall deps also land alongside Task 41.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "dev.aetheris.planner.distribution.modeldownloader"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        //noinspection TargetSdkVersionLibrary
        targetSdk = 34
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:domain"))

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.bundles.testing.jvm)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
