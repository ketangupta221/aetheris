package dev.aetheris.planner.buildlogic.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue

/**
 * Lint-Registry-v2 entry point for the manifest-internet detector (Task 2.3).
 * Wired by the `Lint-Registry-v2` JAR manifest attribute in
 * `buildlogic/manifest-lint-rules/build.gradle.kts`.
 */
class ManifestInternetIssueRegistry : IssueRegistry() {

    override val issues: List<Issue> = listOf(
        ForbiddenInternetManifestDetector.ISSUE,
    )

    override val api: Int = CURRENT_API

    override val vendor: Vendor = Vendor(
        vendorName = "Aetheris Planner",
        identifier = "dev.aetheris.planner:manifest-lint-rules",
        feedbackUrl = "https://github.com/aetheris/issues",
    )
}
