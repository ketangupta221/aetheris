package dev.aetheris.planner.buildlogic

import java.io.File

/**
 * Pure scanner that implements design §2.4 "Manifest lint gate" and the
 * Property 39 "Manifest permission invariant" (design §6):
 *
 *  > For all `AndroidManifest.xml` files in the project tree, either the file
 *  > path is under `:distribution:model-downloader/`, or the file does not
 *  > contain `android.permission.INTERNET`.
 *
 * The scanner is intentionally filesystem-aware but content-logic-free: the
 * "does the content contain a forbidden permission" decision is shared with
 * [scanManifestContents] so the property-based test (Task 2.4) can drive it
 * without touching the filesystem.
 *
 * The allow-list is restricted to the single module path
 * `distribution/model-downloader`, matching requirements 5.8 and design §2.4.
 * Adding or relaxing the allow-list requires updating both the requirements
 * doc and this constant in lockstep.
 */
object ManifestScanner {

    /** Module paths (filesystem-style, `/` separators) that may declare
     *  the forbidden permissions. */
    val DEFAULT_ALLOWED: Set<String> = setOf("distribution/model-downloader")

    /** Permission names policed by the invariant. Covers Task 2.1:
     *  INTERNET plus ACCESS_NETWORK_STATE and ACCESS_WIFI_STATE — all three
     *  must be confined to `:distribution:model-downloader`. */
    val FORBIDDEN_PERMISSIONS: List<String> = listOf(
        "INTERNET",
        "ACCESS_NETWORK_STATE",
        "ACCESS_WIFI_STATE",
    )

    // Matches the raw text `android.permission.<NAME>`. Each hit returns one
    // permission name. The test harness uses the same regex so production and
    // test agree byte-for-byte on what "contains the permission" means.
    private val FORBIDDEN_REGEX: Regex =
        Regex("""android\.permission\.(INTERNET|ACCESS_NETWORK_STATE|ACCESS_WIFI_STATE)""")

    // Strips `<!-- ... -->` comment blocks, including multi-line. The reason:
    // a `<!-- ACCESS_NETWORK_STATE -->` comment is documentation, not a
    // declaration, and should not trip the scanner. This is a deliberate
    // false-negative avoidance; the actual `<uses-permission>` form always
    // survives comment stripping.
    private val XML_COMMENT_REGEX: Regex =
        Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL)

    /**
     * Scan every AndroidManifest.xml under a `src/` subdirectory of [rootDir].
     *
     *  - Skips `build/`, `.gradle/`, and `.idea/` subtrees.
     *  - Returns [ScanResult.Clean] when no forbidden permission appears in a
     *    non-allowed module's manifest.
     *  - Returns [ScanResult.Violations] otherwise, with one [Violation] entry
     *    per (manifest, permission) occurrence.
     *
     * This is the production path used by [CheckManifestsTask]. The test
     * harness calls [scanManifestContents] directly to avoid filesystem I/O.
     */
    fun scanManifests(
        rootDir: File,
        allowedModulePaths: Set<String> = DEFAULT_ALLOWED,
    ): ScanResult {
        val manifests: List<Pair<String, String>> = collectManifestFiles(rootDir).map { file ->
            val relative = file.relativeTo(rootDir).invariantSeparatorsPath
            relative to file.readText(Charsets.UTF_8)
        }
        return scanManifestContents(manifests, allowedModulePaths)
    }

    /**
     * Filesystem-free variant used by the property test (Task 2.4) and by
     * [scanManifests] after it loads files from disk.
     *
     * Each input pair is `(relativePath, content)`. Paths must use `/` as
     * the separator to be cross-platform deterministic. The allow-list match
     * uses a `startsWith("<allowed>/")` check so `distribution/model-downloader`
     * matches `distribution/model-downloader/src/main/AndroidManifest.xml` but
     * not `distribution/model-downloader-lookalike/...`.
     */
    fun scanManifestContents(
        manifests: List<Pair<String, String>>,
        allowedModulePaths: Set<String> = DEFAULT_ALLOWED,
    ): ScanResult {
        val violations: MutableList<Violation> = mutableListOf()
        for ((path, rawContent) in manifests) {
            val normalizedPath = path.replace('\\', '/')
            if (isAllowed(normalizedPath, allowedModulePaths)) continue

            val content = XML_COMMENT_REGEX.replace(rawContent, "")
            for (match in FORBIDDEN_REGEX.findAll(content)) {
                val permission = match.groupValues[1]
                val line = content.take(match.range.first).count { it == '\n' } + 1
                violations += Violation(
                    manifestPath = normalizedPath,
                    permission = permission,
                    line = line,
                )
            }
        }
        return if (violations.isEmpty()) ScanResult.Clean else ScanResult.Violations(violations)
    }

    private fun isAllowed(path: String, allowedModulePaths: Set<String>): Boolean =
        allowedModulePaths.any { allowed ->
            val prefix = if (allowed.endsWith('/')) allowed else "$allowed/"
            path.startsWith(prefix)
        }

    /**
     * Public discovery helper used by [CheckManifestsTask] to populate its
     * declared `@InputFiles` at configuration time. Returns the list of
     * source `AndroidManifest.xml` files under [rootDir]; generated / merged
     * manifests under `build/` subtrees are intentionally excluded, matching
     * the production scan path above.
     */
    fun findSourceManifests(rootDir: File): List<File> = collectManifestFiles(rootDir)

    private fun collectManifestFiles(rootDir: File): List<File> {
        if (!rootDir.isDirectory) return emptyList()
        val results: MutableList<File> = mutableListOf()
        rootDir.walkTopDown()
            .onEnter { dir ->
                // Skip Gradle/IDE build caches. Their manifests are generated
                // and would create false positives that the user can't fix by
                // editing source (and that clear on `clean` anyway).
                val name = dir.name
                name != "build" && name != ".gradle" && name != ".idea" && name != "node_modules"
            }
            .filter { it.isFile && it.name == "AndroidManifest.xml" }
            .filter { candidate ->
                // Only source manifests, not generated or merged outputs. The
                // scanner is the PRE-merge check; the post-merge case is
                // covered by the Android Lint detector in Task 2.3.
                val normalized = candidate.relativeTo(rootDir).invariantSeparatorsPath
                normalized.contains("/src/")
            }
            .forEach(results::add)
        return results.sortedBy { it.relativeTo(rootDir).invariantSeparatorsPath }
    }
}

/** Result of a manifest scan. */
sealed interface ScanResult {
    /** No forbidden permission was found outside the allow-listed modules. */
    object Clean : ScanResult

    /** One or more violations were detected. */
    data class Violations(val violations: List<Violation>) : ScanResult
}

/**
 * A single `(manifest, permission)` violation.
 *
 * @property manifestPath relative, `/`-separated path from the project root.
 * @property permission short name: `INTERNET`, `ACCESS_NETWORK_STATE`, or
 *   `ACCESS_WIFI_STATE`.
 * @property line 1-based line in the original manifest content, or `0` if the
 *   line could not be determined.
 */
data class Violation(
    val manifestPath: String,
    val permission: String,
    val line: Int,
)
