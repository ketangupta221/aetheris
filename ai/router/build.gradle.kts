// :ai:router — Android library per design §2.1. Hosts Task_Router, the
// escalation policy, and routing metrics. Requires Android APIs (WorkManager,
// `RoutingMetricEntity` persistence via :core:data) so it is an Android
// library rather than a pure JVM module.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "dev.aetheris.planner.ai.router"
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
    implementation(project(":core:data"))
    implementation(project(":ai:nlparser"))
    implementation(project(":ai:entity-resolver"))
    implementation(project(":ai:llm"))

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
