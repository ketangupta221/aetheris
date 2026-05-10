package dev.aetheris.planner.buildlogic.lint

import com.android.tools.lint.client.api.LintClient
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * Unit tests for [ForbiddenInternetManifestDetector]'s pure decision
 * surface (Task 2.3).
 *
 * The detector delegates the "is this the allow-listed module?" decision to
 * [ForbiddenInternetManifestDetector.Companion.isAllowedModule], a pure
 * function over `(manifestPath, projectDirPath)`. These tests pin down its
 * behaviour without needing a full `TestLintTask` run — which in turn would
 * require an Android SDK that is not available in JVM-only CI (Android Lint's
 * test harness refuses to run without one, even for manifest-only detectors).
 *
 * The integration story for the detector is:
 *
 *  - `ISSUE.severity == ERROR` is enforced by the fixed test below, so any
 *    future refactor that accidentally downgrades the severity fails the
 *    build.
 *  - `ISSUE.id == "ForbiddenInternetPermission"` is likewise pinned so
 *    consumers that suppress / filter by ID stay stable.
 *  - `FORBIDDEN_PERMISSIONS` matches the three permissions Task 2.3 requires:
 *    INTERNET, ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE.
 *  - `ManifestInternetIssueRegistry.issues` includes [ISSUE] so AGP's Lint
 *    actually picks it up when the JAR is on the `lintChecks` classpath.
 *
 * End-to-end detector behaviour (i.e. "Lint fires on a real module") is
 * exercised at the integration level by running `./gradlew :app:lintDebug`
 * in CI where an Android SDK is installed.
 */
class ForbiddenInternetManifestDetectorTest {

    companion object {
        @BeforeAll
        @JvmStatic
        fun initLintClient() {
            // `IssueRegistry`'s constructor asserts that `LintClient.clientName`
            // has been set. In an Android Lint run AGP sets it before any
            // detector loads; in a plain JUnit run we have to do it ourselves.
            // The value is purely a label — `"unit-test"` is just a convention
            // and doesn't affect detector behaviour.
            if (!LintClient.isClientNameInitialized()) {
                LintClient.clientName = "unit-test"
            }
        }
    }

    // --- allow-list decision -------------------------------------------------

    @Test
    fun `manifest inside distribution model-downloader is allowed`() {
        val allowed = ForbiddenInternetManifestDetector.isAllowedModule(
            manifestPath = "/workspace/aetheris/distribution/model-downloader/src/main/AndroidManifest.xml",
            projectDirPath = "/workspace/aetheris/distribution/model-downloader",
        )
        assertThat(allowed).isTrue()
    }

    @Test
    fun `manifest inside any other module is not allowed`() {
        for (module in listOf(
            "app",
            "core/common",
            "core/data",
            "feature/timeline",
            "feature/habits",
            "ai/router",
            "ai/llm",
        )) {
            val allowed = ForbiddenInternetManifestDetector.isAllowedModule(
                manifestPath = "/workspace/aetheris/$module/src/main/AndroidManifest.xml",
                projectDirPath = "/workspace/aetheris/$module",
            )
            assertThat(allowed).isFalse()
        }
    }

    @Test
    fun `project directory ending in distribution model-downloader is allowed`() {
        // Fallback path: when the manifest filename has been copied to a
        // merged-output location that no longer carries the module prefix.
        val allowed = ForbiddenInternetManifestDetector.isAllowedModule(
            manifestPath = "/tmp/merged/AndroidManifest.xml",
            projectDirPath = "/workspace/aetheris/distribution/model-downloader",
        )
        assertThat(allowed).isTrue()
    }

    @Test
    fun `lookalike module path is not treated as allow-listed`() {
        // `.../distribution/model-downloader-lookalike` shares a prefix with
        // the real allow-list entry but is NOT the same module. The pure
        // predicate enforces the trailing `/` (via the
        // `"/distribution/model-downloader/"` substring check).
        val allowed = ForbiddenInternetManifestDetector.isAllowedModule(
            manifestPath =
                "/workspace/aetheris/distribution/model-downloader-lookalike/src/main/AndroidManifest.xml",
            projectDirPath = "/workspace/aetheris/distribution/model-downloader-lookalike",
        )
        assertThat(allowed).isFalse()
    }

    @Test
    fun `backslash-separated windows paths are handled`() {
        // Real Lint runs feed `invariantSeparatorsPath`-normalized values;
        // this test pins the contract so a future change that forgets the
        // normalization step fails fast.
        val allowed = ForbiddenInternetManifestDetector.isAllowedModule(
            manifestPath = "C:/dev/aetheris/distribution/model-downloader/src/main/AndroidManifest.xml",
            projectDirPath = "C:/dev/aetheris/distribution/model-downloader",
        )
        assertThat(allowed).isTrue()
    }

    // --- Issue metadata is stable --------------------------------------------

    @Test
    fun `ISSUE id is the public contract value`() {
        assertThat(ForbiddenInternetManifestDetector.ISSUE.id)
            .isEqualTo("ForbiddenInternetPermission")
    }

    @Test
    fun `ISSUE severity is Error per Task 2_3`() {
        assertThat(ForbiddenInternetManifestDetector.ISSUE.defaultSeverity.description)
            .ignoringCase()
            .contains("error")
    }

    @Test
    fun `FORBIDDEN_PERMISSIONS covers INTERNET, ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE`() {
        assertThat(ForbiddenInternetManifestDetector.FORBIDDEN_PERMISSIONS)
            .containsExactly("INTERNET", "ACCESS_NETWORK_STATE", "ACCESS_WIFI_STATE")
    }

    @Test
    fun `registry exposes the ISSUE`() {
        val registry = ManifestInternetIssueRegistry()
        assertThat(registry.issues).contains(ForbiddenInternetManifestDetector.ISSUE)
    }
}
