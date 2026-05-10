// :core:scheduling — Android library per design §2.1. Wraps AlarmManager and
// WorkManager; hosts the Reminder_Scheduler. Requires Android APIs, therefore
// an Android library rather than a pure JVM module.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "dev.aetheris.planner.core.scheduling"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        //noinspection TargetSdkVersionLibrary
        targetSdk = 34

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.android)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler)
    ksp(libs.hilt.work.compiler)

    testImplementation(libs.bundles.testing.jvm)
    testImplementation(libs.androidx.work.testing)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)

    androidTestImplementation(libs.bundles.testing.android)
    androidTestImplementation(libs.androidx.work.testing)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
