// :core:domain — pure Kotlin JVM module per design §2.1. Holds entities (Task,
// Event, Habit, ...), the Action_Plan schema types, and repository interfaces.
// No Android dependencies; makes unit-testing domain invariants (e.g.
// `Task.start <= Task.end`, Task 10.3) trivial on the JVM.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:common"))

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
