/*
 * :core:common — pure Kotlin/JVM.
 *
 * This file intentionally lives in :core:common (not :distribution:model-downloader)
 * because the runtime SocketFactory override in this package must be able to
 * read the consent flag, and :core:common is the only module that both the
 * downloader and the app's Application class can depend on without introducing
 * a reverse dependency back onto the downloader.
 *
 * The consent flag itself is a process-wide ThreadLocal<Boolean>. The only
 * public helper permitted to flip it is `withConsentedDownload` in
 * :distribution:model-downloader (design §2.4, Req 5.5 / 24.1). This object's
 * `enter`/`exit` primitives are technically reachable from any module that
 * depends on :core:common, but we treat them as module-private by convention —
 * the defence-in-depth caller check lives in the downloader's helper, and the
 * manifest permission invariant (Task 2) plus the dynamic-feature delivery
 * boundary (design §2.4 / Task 41) make misuse impossible to turn into an
 * actual outbound socket at Android runtime.
 */
package dev.aetheris.planner.core.common.network

/**
 * Process-wide consent scope consulted by [NetworkDenyingSocketFactory] and
 * [NetworkDenyingSSLSocketFactory] when deciding whether to allow a socket.
 *
 * Contract (design §2.4, Req 5.5 / 24.1):
 *  - The flag is a [ThreadLocal]<Boolean>. Only the thread currently executing
 *    inside the consented block passes the gate.
 *  - The ONLY permitted caller of [enter]/[exit] is
 *    `dev.aetheris.planner.distribution.modeldownloader.withConsentedDownload`.
 *    No other module may toggle the flag; misuse is a bug and is treated as
 *    such by the caller-class check inside that helper.
 *  - When not inside a consented block, [isInScope] returns `false` and the
 *    socket factories throw [SecurityException] on every socket creation
 *    attempt.
 */
public object ConsentedDownloadScope {
    private val flag: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }

    /**
     * Returns `true` if the current thread is executing inside a consented
     * download block, `false` otherwise.
     */
    public fun isInScope(): Boolean = flag.get()

    /**
     * Enters the consent scope on the current thread, returning the prior
     * value so the caller can restore it in a `finally` block.
     *
     * Only `:distribution:model-downloader`'s `withConsentedDownload` helper
     * may call this. Every other caller is a programming error.
     */
    public fun enter(): Boolean {
        val prior = flag.get()
        flag.set(true)
        return prior
    }

    /**
     * Restores the scope flag to [prior] on the current thread.
     *
     * Only `:distribution:model-downloader`'s `withConsentedDownload` helper
     * may call this.
     */
    public fun exit(prior: Boolean) {
        flag.set(prior)
    }
}
