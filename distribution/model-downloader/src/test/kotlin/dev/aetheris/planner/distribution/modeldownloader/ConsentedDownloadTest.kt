/*
 * :distribution:model-downloader — unit tests for the consent-scope helper.
 *
 * Pure JVM; no Android, no Robolectric. The tests assert the contract of
 * [withConsentedDownload]: it opens the scope for the duration of [block],
 * restores the prior value on normal return AND on exception, and is
 * thread-local in its effect (a worker thread's scope does not leak to
 * the main thread).
 */
package dev.aetheris.planner.distribution.modeldownloader

import dev.aetheris.planner.core.common.network.ConsentedDownloadScope
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConsentedDownloadTest {

    @Test
    fun `scope is open inside block and closed after`() {
        assertFalse(ConsentedDownloadScope.isInScope(), "precondition")

        val observed = withConsentedDownload { ConsentedDownloadScope.isInScope() }
        assertTrue(observed, "block must observe the scope as open")

        assertFalse(
            ConsentedDownloadScope.isInScope(),
            "scope must be closed after the block returns normally",
        )
    }

    @Test
    fun `scope is closed after block throws`() {
        assertFalse(ConsentedDownloadScope.isInScope(), "precondition")

        assertThrows(IllegalStateException::class.java) {
            withConsentedDownload<Unit> { error("boom") }
        }

        assertFalse(
            ConsentedDownloadScope.isInScope(),
            "scope must be closed even when the block throws",
        )
    }

    @Test
    fun `block receives block-local scope only`() {
        // A worker thread enters the scope; the main thread must continue
        // to observe the scope as closed. This is the contract that makes
        // the escape hatch safe when the downloader uses a worker thread.
        val workerSawScope = arrayOf(false)
        val mainSawScopeAfter = arrayOf(false)

        val worker = Thread {
            withConsentedDownload {
                workerSawScope[0] = ConsentedDownloadScope.isInScope()
            }
        }
        worker.start()
        worker.join()

        mainSawScopeAfter[0] = ConsentedDownloadScope.isInScope()

        assertTrue(workerSawScope[0], "worker saw its own scope open")
        assertFalse(mainSawScopeAfter[0], "main thread must NOT be affected")
    }

    @Test
    fun `block return value is propagated`() {
        val value = withConsentedDownload { 42 }
        assertEquals(42, value)
    }
}
