// Feature: ai-daily-planner, Property 39: Manifest permission invariant
//
// Covers Task 2.4 + design §9.2 (500 iterations) + design §6 Property 39:
// "For all AndroidManifest.xml files in the project tree, either the file
//  path is under :distribution:model-downloader/, or the file does not
//  contain android.permission.INTERNET. Verified by a static Gradle check
//  :buildSrc:checkManifests wired into ./gradlew check."
//
// Tests the same pure [ManifestScanner.scanManifestContents] surface that the
// production [CheckManifestsTask] calls. That keeps test and prod in lockstep
// without needing to write temporary files on every iteration.
package dev.aetheris.planner.buildlogic

import com.google.common.truth.Truth.assertThat
import net.jqwik.api.Arbitraries
import net.jqwik.api.Arbitrary
import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.Provide

/**
 * Property 39: manifest permission invariant.
 *
 * The test drives [ManifestScanner.scanManifestContents] with a randomly
 * generated list of `(moduleManifestPath, manifestXmlContent)` pairs and
 * asserts that the scanner reports [ScanResult.Clean] iff no forbidden
 * permission appears in any non-allowed manifest.
 *
 * jqwik runs this property at 500 tries by default (see `buildSrc/build.gradle.kts`
 * `jqwik.tries.default = 500`), overridable per-property.
 */
class ManifestPermissionInvariantTest {

    companion object {
        private const val ALLOWED_MODULE_PATH = "distribution/model-downloader"

        /** Every non-allowed module in the settings.gradle.kts layout. */
        private val NON_ALLOWED_MODULE_PATHS: List<String> = listOf(
            "app",
            "core/common",
            "core/domain",
            "core/data",
            "core/scheduling",
            "core/notifications",
            "feature/timeline",
            "feature/tasks",
            "feature/chat",
            "feature/quickcapture",
            "feature/settings",
            "feature/habits",
            "feature/focus",
            "feature/rituals",
            "ai/router",
            "ai/nlparser",
            "ai/entity-resolver",
            "ai/llm",
            "ai/speech",
        )

        private val FORBIDDEN_PERMISSIONS: List<String> = listOf(
            "INTERNET",
            "ACCESS_NETWORK_STATE",
            "ACCESS_WIFI_STATE",
        )

        private val BENIGN_PERMISSIONS: List<String> = listOf(
            "POST_NOTIFICATIONS",
            "SCHEDULE_EXACT_ALARM",
            "USE_EXACT_ALARM",
            "RECEIVE_BOOT_COMPLETED",
            "RECORD_AUDIO",
            "FOREGROUND_SERVICE",
            "FOREGROUND_SERVICE_SPECIAL_USE",
            "READ_MEDIA_AUDIO",
        )

        private val FORBIDDEN_REGEX: Regex =
            Regex("""android\.permission\.(INTERNET|ACCESS_NETWORK_STATE|ACCESS_WIFI_STATE)""")

        // Matches the production regex in ManifestScanner.XML_COMMENT_REGEX.
        private val XML_COMMENT_REGEX: Regex =
            Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL)
    }

    /**
     * Property 39 core. For any randomly generated project-like list of
     * manifests, the scanner reports [ScanResult.Clean] iff no forbidden
     * permission appears in any non-allowed manifest (after XML comments
     * have been stripped, matching production behaviour).
     */
    @Property(tries = 500)
    fun `checkManifests returns clean iff no forbidden permission appears outside allowed module`(
        @ForAll("projectManifests") manifests: List<Pair<String, String>>,
    ) {
        val expectedClean: Boolean = manifests.none { (path, raw) ->
            val normalizedPath = path.replace('\\', '/')
            val underAllowed = normalizedPath.startsWith("$ALLOWED_MODULE_PATH/")
            if (underAllowed) {
                false
            } else {
                val content = XML_COMMENT_REGEX.replace(raw, "")
                FORBIDDEN_REGEX.containsMatchIn(content)
            }
        }

        val actual = ManifestScanner.scanManifestContents(manifests)

        when (actual) {
            ScanResult.Clean -> assertThat(expectedClean).isTrue()
            is ScanResult.Violations -> {
                assertThat(expectedClean).isFalse()
                // Every reported violation must actually reference a forbidden
                // permission in a non-allowed module — no false positives.
                for (violation in actual.violations) {
                    assertThat(FORBIDDEN_PERMISSIONS).contains(violation.permission)
                    assertThat(violation.manifestPath).doesNotContain("$ALLOWED_MODULE_PATH/")
                }
            }
        }
    }

    /**
     * Stronger variant: when a forbidden permission is inserted, the scanner
     * must raise a violation referencing the correct permission. This pins
     * down the detection direction separately from the iff check above.
     */
    @Property(tries = 500)
    fun `checkManifests detects any inserted forbidden permission in a non-allowed module`(
        @ForAll("nonAllowedModulePath") modulePath: String,
        @ForAll("forbiddenPermission") permission: String,
    ) {
        val manifestPath = "$modulePath/src/main/AndroidManifest.xml"
        val content = buildManifestXml(
            permissions = listOf("android.permission.$permission"),
        )

        val result = ManifestScanner.scanManifestContents(listOf(manifestPath to content))

        assertThat(result).isInstanceOf(ScanResult.Violations::class.java)
        val violations = (result as ScanResult.Violations).violations
        assertThat(violations).hasSize(1)
        assertThat(violations[0].manifestPath).isEqualTo(manifestPath)
        assertThat(violations[0].permission).isEqualTo(permission)
    }

    /**
     * Dual direction: the exact same forbidden permission placed under the
     * allowed module path must NOT trigger the scanner. This pins down that
     * the allow-list works and the scanner does not over-flag.
     */
    @Property(tries = 500)
    fun `checkManifests allows forbidden permissions when declared inside the allowed module`(
        @ForAll("forbiddenPermission") permission: String,
    ) {
        val manifestPath = "$ALLOWED_MODULE_PATH/src/main/AndroidManifest.xml"
        val content = buildManifestXml(
            permissions = listOf("android.permission.$permission"),
        )

        val result = ManifestScanner.scanManifestContents(listOf(manifestPath to content))

        assertThat(result).isEqualTo(ScanResult.Clean)
    }

    // ---- Arbitraries -------------------------------------------------------

    /**
     * Generates a list of 1..10 manifests. Each manifest is randomly placed
     * in an allowed or non-allowed module path, with a random mix of benign
     * and (occasionally) forbidden permissions. Roughly 30% of manifests
     * include at least one forbidden permission, enough to give the property
     * plenty of violations to shrink on.
     */
    @Provide
    fun projectManifests(): Arbitrary<List<Pair<String, String>>> {
        val oneManifest: Arbitrary<Pair<String, String>> = Arbitraries.oneOf(
            manifestIn(ALLOWED_MODULE_PATH),
            manifestInRandomNonAllowedModule(),
        )
        return oneManifest.list().ofMinSize(1).ofMaxSize(10)
    }

    @Provide
    fun nonAllowedModulePath(): Arbitrary<String> =
        Arbitraries.of(NON_ALLOWED_MODULE_PATHS)

    @Provide
    fun forbiddenPermission(): Arbitrary<String> =
        Arbitraries.of(FORBIDDEN_PERMISSIONS)

    private fun manifestIn(modulePath: String): Arbitrary<Pair<String, String>> {
        val path = "$modulePath/src/main/AndroidManifest.xml"
        return permissionList().map { permissions ->
            path to buildManifestXml(permissions)
        }
    }

    private fun manifestInRandomNonAllowedModule(): Arbitrary<Pair<String, String>> {
        return Arbitraries.of(NON_ALLOWED_MODULE_PATHS).flatMap { modulePath ->
            manifestIn(modulePath)
        }
    }

    /**
     * Builds a random list of permission strings. Each position
     * independently is either a benign permission or (with ~25% probability)
     * a forbidden permission, implemented by weighting with `Arbitraries.oneOf`
     * and repeating the benign arbitrary three times. Length 0..4 gives a mix
     * of empty and rich manifests. Shrinking drives toward shorter lists and
     * (where available) toward the all-benign case.
     */
    private fun permissionList(): Arbitrary<List<String>> {
        val benign: Arbitrary<String> =
            Arbitraries.of(BENIGN_PERMISSIONS).map { "android.permission.$it" }
        val forbidden: Arbitrary<String> =
            Arbitraries.of(FORBIDDEN_PERMISSIONS).map { "android.permission.$it" }
        val onePermission: Arbitrary<String> =
            Arbitraries.oneOf(benign, benign, benign, forbidden)
        return onePermission.list().ofMinSize(0).ofMaxSize(4)
    }

    private fun buildManifestXml(permissions: List<String>): String {
        val usesPermissionLines = permissions.joinToString(separator = "\n") { permission ->
            """    <uses-permission android:name="$permission" />"""
        }
        return buildString {
            appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
            append("""<manifest xmlns:android="http://schemas.android.com/apk/res/android">""")
            if (usesPermissionLines.isNotEmpty()) {
                append('\n')
                append(usesPermissionLines)
            }
            append('\n')
            append("</manifest>")
            append('\n')
        }
    }
}
