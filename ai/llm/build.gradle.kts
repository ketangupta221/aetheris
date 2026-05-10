// :ai:llm — Android library per design §2.1. Hosts LLM_Agent, the MediaPipe
// adapter, and the llama.cpp JNI adapter. Native library wiring and
// MediaPipe GenAI deps land in Phase 3 (Tasks 42/43); Task 1.2 only wires the
// DI / coroutines / serialization baseline that every :ai:* module needs.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "dev.aetheris.planner.ai.llm"
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
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.bundles.testing.jvm)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
