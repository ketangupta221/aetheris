package dev.aetheris.planner.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task

/**
 * Root-project plugin that registers the `checkManifests` task and wires it
 * into `./gradlew check` per Task 2.2. Also makes it a dependency of
 * `preBuild` / `assemble` on every subproject so CI can't package a release
 * without passing the gate first.
 *
 * Applied from the root `build.gradle.kts` via:
 * ```
 * plugins {
 *     id("dev.aetheris.planner.manifest-invariant")
 * }
 * ```
 */
class ManifestInvariantPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        require(target == target.rootProject) {
            "The manifest-invariant plugin must be applied to the root project, " +
                "not ${target.path}."
        }

        val checkManifests = target.tasks.register(
            "checkManifests",
            CheckManifestsTask::class.java,
        ) {
            // Receiver `this` is the newly-registered CheckManifestsTask.
            rootDir.set(target.layout.projectDirectory)
            allowedModulePaths.set(ManifestScanner.DEFAULT_ALLOWED)
            // Declare the exact set of source AndroidManifest.xml files as
            // the task's inputs. Using a concrete file collection here —
            // rather than the project root as a whole — prevents Gradle from
            // misidentifying every build output as an implicit input, which
            // previously surfaced as an "implicit dependency" error against
            // `core:domain:processResources` under `./gradlew :app:assembleDebug`.
            val rootFile = target.rootDir
            manifestFiles.from(
                target.provider { ManifestScanner.findSourceManifests(rootFile) },
            )
        }

        // Wire into `./gradlew check` (Task 2.2). Gradle's `base` plugin
        // creates the root `check` task lazily; we create-if-absent and add
        // the dependency so this plugin can be applied before or after any
        // other plugin that brings `check` in.
        val rootCheck: Task = target.tasks.findByName("check")
            ?: target.tasks.register("check").get()
        rootCheck.dependsOn(checkManifests)

        // Belt-and-braces: every subproject's `preBuild` / `assemble` depends
        // on us so the gate fires before any Android module assembles. This
        // covers the case where a developer runs `./gradlew :app:assembleDebug`
        // directly without going through `check`.
        target.allprojects.forEach { sub: Project ->
            sub.tasks.matching { it.name == "preBuild" || it.name == "assemble" }
                .configureEach {
                    // Receiver `this` is each matched Task.
                    dependsOn(checkManifests)
                }
        }
    }
}
