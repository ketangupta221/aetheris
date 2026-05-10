package dev.aetheris.planner.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Gradle task that implements Task 2.1:
 *
 *   Fails the build if `android.permission.INTERNET` (or
 *   `ACCESS_NETWORK_STATE` / `ACCESS_WIFI_STATE`) appears in any
 *   source `AndroidManifest.xml` outside `:distribution:model-downloader`.
 *
 * The scan is delegated to [ManifestScanner] so the same logic can be driven
 * from a filesystem-free jqwik property (Task 2.4).
 *
 * Wired into `./gradlew check` by [ManifestInvariantPlugin] (Task 2.2).
 *
 * ### Input declaration strategy
 *
 * The task intentionally declares only the concrete `AndroidManifest.xml`
 * files as its [InputFiles] — not the entire project root. Declaring the
 * project root would make Gradle treat every file under the root as a
 * potential input, which triggers false "implicit dependency" validation
 * errors against sibling tasks (for example `core:domain:processResources`)
 * that write anywhere under `build/`. [rootDir] is still exposed for the
 * task action so it can compute module-relative paths, but it is marked
 * [Internal] so Gradle does not snapshot it.
 */
abstract class CheckManifestsTask : DefaultTask() {

    /**
     * Project root. Used at task-action time to compute relative paths for
     * violation messages. Marked [Internal] so Gradle does not track it as
     * an input — the concrete manifest files below carry the real input
     * fingerprint.
     */
    @get:Internal
    abstract val rootDir: DirectoryProperty

    /**
     * The exact set of source `AndroidManifest.xml` files to scan. Populated
     * by [ManifestInvariantPlugin] at configuration time by walking the
     * project tree once. Relative path sensitivity keeps the cache key stable
     * when the user moves the workspace around on disk.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val manifestFiles: ConfigurableFileCollection

    @get:Input
    abstract val allowedModulePaths: SetProperty<String>

    init {
        group = "verification"
        description =
            "Verifies that android.permission.INTERNET, ACCESS_NETWORK_STATE, and " +
                "ACCESS_WIFI_STATE appear only in :distribution:model-downloader."
    }

    @TaskAction
    fun check() {
        val root = rootDir.asFile.get()
        val allowed = allowedModulePaths.get()
        val manifests: List<Pair<String, String>> = manifestFiles.files
            .filter { it.isFile }
            .map { file ->
                val relative = file.relativeTo(root).invariantSeparatorsPath
                relative to file.readText(Charsets.UTF_8)
            }
        val result = ManifestScanner.scanManifestContents(manifests, allowed)
        when (result) {
            ScanResult.Clean -> {
                logger.lifecycle(
                    "checkManifests: OK — ${manifests.size} manifest(s) scanned, " +
                        "no forbidden permissions found outside allowed modules.",
                )
            }
            is ScanResult.Violations -> {
                val message = buildString {
                    appendLine(
                        "Manifest permission invariant violated " +
                            "(${result.violations.size} violation(s)).",
                    )
                    appendLine("See requirements.md §5.8 and design.md §2.4.")
                    appendLine(
                        "Only :distribution:model-downloader may declare INTERNET, " +
                            "ACCESS_NETWORK_STATE, or ACCESS_WIFI_STATE.",
                    )
                    appendLine()
                    for (violation in result.violations) {
                        appendLine(
                            "  ${violation.manifestPath}:${violation.line}: " +
                                "android.permission.${violation.permission}",
                        )
                    }
                }
                throw GradleException(message)
            }
        }
    }
}
