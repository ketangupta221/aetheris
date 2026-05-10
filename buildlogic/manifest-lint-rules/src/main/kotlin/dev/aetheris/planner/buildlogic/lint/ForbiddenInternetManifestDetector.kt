package dev.aetheris.planner.buildlogic.lint

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_USES_PERMISSION
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import java.io.File
import org.w3c.dom.Element

/**
 * Android Lint detector implementing Task 2.3: flag
 * `android.permission.INTERNET` (and `ACCESS_NETWORK_STATE` /
 * `ACCESS_WIFI_STATE`) declared in any manifest that is not part of the
 * `:distribution:model-downloader` module.
 *
 * This runs at Lint time, which means it sees every manifest Lint visits —
 * including the post-merge manifest AGP produces for `:app` when it merges
 * in feature modules' manifests. That covers the design §12.3 risk: "if
 * in a future Play SDK update, manifest merger rules change to propagate
 * INTERNET to the base, [we want] a post-merge check on
 * `:app:processReleaseMainManifest` output".
 *
 * Pre-merge enforcement lives in `:buildSrc:checkManifests` (Tasks 2.1 +
 * 2.2). This detector is defense in depth at the Lint / IDE layer.
 */
class ForbiddenInternetManifestDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> = listOf(TAG_USES_PERMISSION)

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME) ?: return
        val bareName = name.removePrefix(ANDROID_PERMISSION_PREFIX)
        if (bareName !in FORBIDDEN_PERMISSIONS) return

        val manifestPath = context.file.invariantSeparatorsPath
        val projectDirPath = context.project.dir.invariantSeparatorsPath
        if (isAllowedModule(manifestPath, projectDirPath)) return

        val location = context.getNameLocation(element)
        val message = buildString {
            append("android.permission.").append(bareName)
            append(" is only allowed in :distribution:model-downloader ")
            append("(requirements.md §5.8, design.md §2.4 / §12.3).")
        }
        context.report(ISSUE, element, location, message)
    }

    companion object {
        /** Visible-for-testing constant so the unit test pins the same value. */
        const val ANDROID_PERMISSION_PREFIX: String = "android.permission."

        /** Visible-for-testing constant so the unit test pins the same value. */
        const val ALLOWED_MODULE_PATH: String = "distribution/model-downloader"

        /** Permissions policed by the detector. Exposed for tests. */
        @JvmField
        val FORBIDDEN_PERMISSIONS: Set<String> = setOf(
            "INTERNET",
            "ACCESS_NETWORK_STATE",
            "ACCESS_WIFI_STATE",
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "ForbiddenInternetPermission",
            briefDescription =
                "INTERNET-related permission declared outside :distribution:model-downloader",
            explanation = """
                This app enforces a "no outbound network at runtime" invariant \
                (requirements.md §5, design.md §2.4). The only module permitted \
                to declare `android.permission.INTERNET`, \
                `android.permission.ACCESS_NETWORK_STATE`, or \
                `android.permission.ACCESS_WIFI_STATE` is \
                `:distribution:model-downloader`, which is packaged as a \
                dynamic feature and installed only after explicit user \
                consent via the Network_Consent_Dialog.

                If you are seeing this, either:

                  1. A feature accidentally added one of these permissions \
                     to its manifest. Remove it.
                  2. A transitive dependency added it via manifest merge. \
                     Suppress it in the dependency, or replace the \
                     dependency.

                The pre-merge `:checkManifests` Gradle task (Tasks 2.1 / 2.2) \
                catches the source-manifest case; this Lint detector covers \
                the post-merge case described in design §12.3.
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 10,
            severity = Severity.ERROR,
            implementation = Implementation(
                ForbiddenInternetManifestDetector::class.java,
                Scope.MANIFEST_SCOPE,
            ),
        )

        /**
         * Pure, filesystem-free predicate that decides whether a manifest
         * located at [manifestPath] inside a module rooted at
         * [projectDirPath] is the allow-listed `:distribution:model-downloader`
         * module.
         *
         * Two signals are accepted, in order:
         *  1. The manifest file path (normalized to `/` separators) contains
         *     `/distribution/model-downloader/`. Works on both the source
         *     tree (pre-merge) and AGP's merged-manifest output path, both
         *     of which retain the originating module directory in the URL.
         *  2. The module's project dir ends with `distribution/model-downloader`.
         *     Kept as a fallback for analyzers that normalize the manifest
         *     path away from its module location.
         *
         * Exposed for unit testing (see `ForbiddenInternetManifestDetectorTest`).
         */
        fun isAllowedModule(manifestPath: String, projectDirPath: String): Boolean {
            if (manifestPath.contains("/$ALLOWED_MODULE_PATH/")) return true
            return projectDirPath.endsWith("/$ALLOWED_MODULE_PATH") ||
                projectDirPath == ALLOWED_MODULE_PATH
        }

        /** Convenience overload used by the detector at runtime. */
        fun isAllowedModule(manifestFile: File, projectDir: File): Boolean =
            isAllowedModule(manifestFile.invariantSeparatorsPath, projectDir.invariantSeparatorsPath)
    }
}
