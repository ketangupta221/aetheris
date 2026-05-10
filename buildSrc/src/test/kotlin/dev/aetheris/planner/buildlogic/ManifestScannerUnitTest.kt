package dev.aetheris.planner.buildlogic

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Deterministic example-based tests for [ManifestScanner]. These complement
 * the [ManifestPermissionInvariantTest] jqwik property by pinning down a
 * small set of specific scenarios — useful when a future refactor would
 * shrink something subtle under the property.
 */
class ManifestScannerUnitTest {

    @Test
    fun `empty project returns Clean`() {
        val result = ManifestScanner.scanManifestContents(manifests = emptyList())
        assertThat(result).isEqualTo(ScanResult.Clean)
    }

    @Test
    fun `single allowed manifest with INTERNET returns Clean`() {
        val content = """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <uses-permission android:name="android.permission.INTERNET" />
            </manifest>
        """.trimIndent()

        val result = ManifestScanner.scanManifestContents(
            manifests = listOf(
                "distribution/model-downloader/src/main/AndroidManifest.xml" to content,
            ),
        )

        assertThat(result).isEqualTo(ScanResult.Clean)
    }

    @Test
    fun `single non-allowed manifest with INTERNET produces one violation`() {
        val content = """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <uses-permission android:name="android.permission.INTERNET" />
            </manifest>
        """.trimIndent()

        val result = ManifestScanner.scanManifestContents(
            manifests = listOf(
                "app/src/main/AndroidManifest.xml" to content,
            ),
        )

        assertThat(result).isInstanceOf(ScanResult.Violations::class.java)
        val violations = (result as ScanResult.Violations).violations
        assertThat(violations).hasSize(1)
        assertThat(violations[0].manifestPath).isEqualTo("app/src/main/AndroidManifest.xml")
        assertThat(violations[0].permission).isEqualTo("INTERNET")
    }

    @Test
    fun `multiple non-allowed manifests surface every forbidden permission`() {
        val withNetworkState = """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
            </manifest>
        """.trimIndent()
        val withWifiState = """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
            </manifest>
        """.trimIndent()
        val clean = """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
            </manifest>
        """.trimIndent()

        val result = ManifestScanner.scanManifestContents(
            manifests = listOf(
                "core/data/src/main/AndroidManifest.xml" to withNetworkState,
                "ai/router/src/main/AndroidManifest.xml" to withWifiState,
                "feature/timeline/src/main/AndroidManifest.xml" to clean,
            ),
        )

        assertThat(result).isInstanceOf(ScanResult.Violations::class.java)
        val violations = (result as ScanResult.Violations).violations
        assertThat(violations).hasSize(2)

        assertThat(violations.map { it.permission })
            .containsExactly("ACCESS_NETWORK_STATE", "ACCESS_WIFI_STATE")
    }

    @Test
    fun `INTERNET inside XML comment is not a violation`() {
        // Scanner strips <!-- ... --> before regex match so documentation
        // referencing the forbidden permission never trips the gate.
        val content = """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <!-- Do not add android.permission.INTERNET here -->
            </manifest>
        """.trimIndent()

        val result = ManifestScanner.scanManifestContents(
            manifests = listOf("app/src/main/AndroidManifest.xml" to content),
        )

        assertThat(result).isEqualTo(ScanResult.Clean)
    }

    @Test
    fun `lookalike module path is not treated as allow-listed`() {
        // `distribution/model-downloader-lookalike` happens to share the
        // prefix string "distribution/model-downloader"; the allow-list must
        // still reject it because the real allow-list entry ends with `/`.
        val content = """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <uses-permission android:name="android.permission.INTERNET" />
            </manifest>
        """.trimIndent()

        val result = ManifestScanner.scanManifestContents(
            manifests = listOf(
                "distribution/model-downloader-lookalike/src/main/AndroidManifest.xml" to content,
            ),
        )

        assertThat(result).isInstanceOf(ScanResult.Violations::class.java)
    }

    @Test
    fun `line numbers point at the offending uses-permission line`() {
        val content = buildString {
            append("""<?xml version="1.0" encoding="utf-8"?>""")
            append("\n<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n")
            append("    <uses-permission android:name=\"android.permission.POST_NOTIFICATIONS\" />\n")
            append("    <uses-permission android:name=\"android.permission.INTERNET\" />\n")
            append("</manifest>\n")
        }

        val result = ManifestScanner.scanManifestContents(
            manifests = listOf("app/src/main/AndroidManifest.xml" to content),
        )

        val violations = (result as ScanResult.Violations).violations
        assertThat(violations).hasSize(1)
        // The INTERNET declaration is on line 4 in the source string above.
        assertThat(violations[0].line).isEqualTo(4)
    }
}
