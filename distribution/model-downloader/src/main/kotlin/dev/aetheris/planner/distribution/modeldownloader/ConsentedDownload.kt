/*
 * :distribution:model-downloader — the ONLY module in the application that
 * is permitted to open an outbound socket (design §2.4, Req 5.8).
 *
 * This file implements Task 3.2: the thread-local escape hatch that lets the
 * downloader bypass the [NetworkDenyingSocketFactory] gates in :core:common.
 * The function below is the *only* permitted caller of
 * [ConsentedDownloadScope.enter] / [ConsentedDownloadScope.exit]. A caller-
 * class check enforces this at runtime as defence-in-depth; the primary
 * enforcement layers are:
 *
 *  1. The manifest permission invariant (Task 2) — only this module declares
 *     android.permission.INTERNET so any other module's socket attempt
 *     would fail at the OS layer regardless of the scope flag.
 *  2. The dynamic-feature delivery model (design §4.10, Task 41) — until the
 *     user accepts the Network_Consent_Dialog, this module is not installed
 *     and its classes are not in the process at all.
 *  3. The SocketFactory override (Task 3.1) — out-of-scope socket
 *     construction throws [SecurityException] on any thread.
 *
 * This function is `internal` to :distribution:model-downloader; the task
 * text specifies "Module-scoped API — not exposed outside
 * :distribution:model-downloader."
 */
package dev.aetheris.planner.distribution.modeldownloader

import dev.aetheris.planner.core.common.network.ConsentedDownloadScope

/**
 * Runs [block] inside the consented-download scope so outbound sockets
 * opened on the current thread will pass the
 * [dev.aetheris.planner.core.common.network.NetworkDenyingSocketFactory]
 * gate. The scope is a [ThreadLocal]<Boolean> maintained in `:core:common`;
 * only the thread that entered the scope observes it as open.
 *
 * The scope is restored in a `finally` block so the flag is always cleared
 * on the current thread, even when [block] throws. The `try` block swaps
 * the prior value of the flag rather than assuming it was `false` so
 * re-entrant calls (which should not normally happen) do not corrupt the
 * outer scope on exit.
 *
 * This helper is `internal` to the `:distribution:model-downloader` module.
 * It is deliberately not `suspend`: the caller chooses its own coroutine
 * context and opens the socket inside [block] on a thread it controls.
 * Making the helper `suspend` would make the thread-local semantics confusing
 * because coroutines can hop threads across suspension points.
 */
internal inline fun <T> withConsentedDownload(block: () -> T): T {
    val prior = ConsentedDownloadScope.enter()
    return try {
        block()
    } finally {
        ConsentedDownloadScope.exit(prior)
    }
}
