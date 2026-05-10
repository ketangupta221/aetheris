// :core:common — pure Kotlin JVM module per design §2.1. No Android framework
// dependencies; holds time utilities, result types, hashing, and the JSON
// schema helper. The pure-JVM choice keeps the deepest dependency in the
// module graph cheap to unit-test on the JVM (design §1.2 "Guiding principles,
// item 7 — property-based testing of invariants").
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.bundles.testing.jvm)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
