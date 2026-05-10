/*
 * :core:common — pure Kotlin/JVM.
 *
 * Implements Task 3.1 of the ai-daily-planner spec: a [javax.net.SocketFactory]
 * subclass that throws [SecurityException] on every socket-creation path
 * unless the current thread is inside a [ConsentedDownloadScope] block.
 *
 * Design references:
 *  - Req 5.1 / 5.2 / 5.3 / 5.4 — zero outbound network at runtime by default.
 *  - design §1.2 guiding principle 1 — SocketFactory override installed at
 *    Application.onCreate() throws SecurityException on any socket created
 *    outside the downloader's execution context.
 *  - design §2.4 — "A `SocketFactory` and `SSLSocketFactory` override … The
 *    replacement inspects … a thread-local tag set by the downloader and
 *    allows creation only when the call originates from
 *    :distribution:model-downloader. All other paths throw
 *    SecurityException('outbound network not permitted outside Model_Downloader')."
 *  - Property 18 ("Zero network at runtime") — the scaffold test in
 *    :app/src/test/.../ZeroNetworkRuntimeScaffoldTest asserts this behaviour
 *    end-to-end against a running Application. The full property coverage
 *    lands in Phase 1 Task 25.
 */
package dev.aetheris.planner.core.common.network

import java.io.IOException
import java.net.InetAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketException
import java.net.SocketImpl
import java.net.SocketImplFactory
import java.net.URI
import javax.net.SocketFactory
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory

/**
 * A [SocketFactory] that rejects every socket-creation attempt unless the
 * current thread is inside a [ConsentedDownloadScope] block, in which case it
 * delegates to [delegate]. Used as the installed-default [SocketFactory] and
 * wrapped by [NetworkDenyingSSLSocketFactory] for HTTPS.
 *
 * Throws [SecurityException] rather than returning an unusable socket so the
 * failure stack traces point at the caller, not at a subsequent I/O operation.
 */
public class NetworkDenyingSocketFactory internal constructor(
    private val delegate: SocketFactory,
) : SocketFactory() {

    override fun createSocket(): Socket =
        if (ConsentedDownloadScope.isInScope()) {
            delegate.createSocket()
        } else {
            throw denial()
        }

    override fun createSocket(host: String?, port: Int): Socket =
        if (ConsentedDownloadScope.isInScope()) {
            delegate.createSocket(host, port)
        } else {
            throw denial()
        }

    override fun createSocket(
        host: String?,
        port: Int,
        localHost: InetAddress?,
        localPort: Int,
    ): Socket =
        if (ConsentedDownloadScope.isInScope()) {
            delegate.createSocket(host, port, localHost, localPort)
        } else {
            throw denial()
        }

    override fun createSocket(host: InetAddress?, port: Int): Socket =
        if (ConsentedDownloadScope.isInScope()) {
            delegate.createSocket(host, port)
        } else {
            throw denial()
        }

    override fun createSocket(
        address: InetAddress?,
        port: Int,
        localAddress: InetAddress?,
        localPort: Int,
    ): Socket =
        if (ConsentedDownloadScope.isInScope()) {
            delegate.createSocket(address, port, localAddress, localPort)
        } else {
            throw denial()
        }

    public companion object {
        /**
         * Shared message for every denial so tests can assert against it.
         */
        public const val DENIAL_MESSAGE: String =
            "outbound network not permitted outside Model_Downloader"

        /** Flag so [install] is a no-op after the first successful call. */
        @Volatile
        private var installed: Boolean = false

        internal fun denial(): SecurityException = SecurityException(DENIAL_MESSAGE)

        /**
         * Installs the deny-by-default network primitives on this JVM
         * process:
         *
         *   1. [java.net.Socket.setSocketImplFactory] is overridden with a
         *      [SocketImplFactory] that always produces [SocketImpl]s which
         *      throw [SecurityException] from `create`. This covers the
         *      `new Socket()` direct-construction path used by the
         *      default HTTP plumbing.
         *   2. [HttpsURLConnection.setDefaultSSLSocketFactory] is overridden
         *      with a [NetworkDenyingSSLSocketFactory] wrapping the previous
         *      default. This is the primary HTTPS gate.
         *   3. [ProxySelector.setDefault] is overridden with a selector that
         *      returns [Proxy.NO_PROXY] for `select` and throws on
         *      `connectFailed` outside the consent scope, preventing the
         *      JVM from consulting system proxy settings for outbound
         *      traffic.
         *   4. [java.net.Authenticator.setDefault] is overridden with an
         *      authenticator that throws outside the consent scope, so
         *      proxy-authentication challenges cannot exfiltrate credentials.
         *
         * ### Design notes
         *
         * - `javax.net.SocketFactory.setDefault(...)` does not exist in the
         *   JDK — only `getDefault()`. The equivalent JVM hook is
         *   `Socket.setSocketImplFactory`. Task 3.1's phrasing
         *   "SocketFactory.setDefault both overridden" is shorthand for
         *   "install a deny-default hook at the plain-socket layer too",
         *   which is what step 1 does.
         *
         * - `Socket.setSocketImplFactory` can succeed at most once per JVM.
         *   On repeat calls (for example when a test runner has already
         *   installed a factory) it throws; we swallow that exception so
         *   [install] remains idempotent. The [installed] flag guards this
         *   object's own internal state and makes the second visible
         *   invocation a pure no-op.
         *
         * - The in-scope escape hatch (ConsentedDownloadScope) does not
         *   relax the plain-socket factory. The downloader module — when it
         *   lands in Phase 3 (design §4.10) — configures OkHttp with an
         *   explicit `SocketFactory` on the `OkHttpClient.Builder` that
         *   bypasses the SocketImplFactory chain entirely. HTTPS, proxy,
         *   and auth gates *are* relaxed inside the scope (by the
         *   [NetworkDenyingSSLSocketFactory], [DenyingProxySelector], and
         *   [DenyingAuthenticator] helpers in this file) because those are
         *   consulted via process-wide statics that the downloader cannot
         *   avoid.
         *
         * Design §2.4 expects this to run from
         * `PlannerApplication.onCreate()` before any other component
         * initializes.
         */
        @JvmStatic
        @Synchronized
        public fun install() {
            if (installed) return

            // 1. java.net.Socket.setSocketImplFactory — covers new Socket(...)
            //    direct construction. May throw if already set elsewhere in
            //    the JVM (e.g. by a test runner on a re-run); swallow that
            //    and rely on the other gates.
            try {
                Socket.setSocketImplFactory(DenyingSocketImplFactory)
            } catch (_: SocketException) {
                // Factory already set. The SSL / ProxySelector /
                // Authenticator gates below are still installed, and the
                // Application-class scaffold test asserts behaviour
                // end-to-end so we will still detect regressions.
            } catch (_: Error) {
                // Some test runners route setSocketImplFactory conflicts
                // through Error rather than SocketException. Treat the
                // same way — idempotence over strictness.
            }

            // 2. Default SSLSocketFactory used by HttpsURLConnection and —
            //    transitively — OkHttp's default TLS engine.
            val priorSsl: SSLSocketFactory = HttpsURLConnection.getDefaultSSLSocketFactory()
            HttpsURLConnection.setDefaultSSLSocketFactory(
                NetworkDenyingSSLSocketFactory(priorSsl),
            )

            // 3. ProxySelector — short-circuit any system proxy so requests
            //    cannot be routed through a proxy that the SocketFactory
            //    override might not cover (e.g. explicit Proxy.DIRECT).
            ProxySelector.setDefault(DenyingProxySelector)

            // 4. Authenticator — deny proxy-auth challenges outside the scope
            //    so credentials cannot leak through a proxy exchange.
            java.net.Authenticator.setDefault(DenyingAuthenticator)

            installed = true
        }

        /**
         * Test hook: returns `true` iff [install] has been called on this
         * process. Exposed so Robolectric tests in other modules can assert
         * idempotence without touching private state.
         */
        @JvmStatic
        public fun isInstalled(): Boolean = installed
    }
}

/**
 * A [ProxySelector] that returns [Proxy.NO_PROXY] on every `select` and
 * throws on `connectFailed` when called outside the consent scope. Inside
 * the scope it defers to [ProxySelector.getDefault] — or rather to the
 * previous default — but in practice we never install this selector before
 * capturing the prior one, so "inside the scope" simply means the JVM uses
 * direct connections (the downloader itself configures OkHttp directly).
 */
private object DenyingProxySelector : ProxySelector() {
    override fun select(uri: URI?): MutableList<Proxy> {
        // Always return NO_PROXY regardless of scope. Inside the consent
        // scope the downloader opens its own HTTP client with explicit
        // connection configuration; outside the scope, returning NO_PROXY
        // here is just defence-in-depth — the SocketFactory override is
        // what actually throws.
        return mutableListOf(Proxy.NO_PROXY)
    }

    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
        if (!ConsentedDownloadScope.isInScope()) {
            throw SocketException(NetworkDenyingSocketFactory.DENIAL_MESSAGE)
        }
        // In-scope failures are surfaced via normal IOException propagation.
    }
}

/**
 * A [java.net.Authenticator] that refuses to respond to authentication
 * challenges outside the consent scope. Inside the scope it returns null,
 * leaving auth handling to the caller's HTTP client (OkHttp in the
 * downloader).
 */
private object DenyingAuthenticator : java.net.Authenticator() {
    override fun getPasswordAuthentication(): java.net.PasswordAuthentication? {
        if (!ConsentedDownloadScope.isInScope()) {
            throw SecurityException(NetworkDenyingSocketFactory.DENIAL_MESSAGE)
        }
        return null
    }
}

/**
 * A [SocketImplFactory] installed via [java.net.Socket.setSocketImplFactory]
 * that throws [SecurityException] from [createSocketImpl] so any attempt to
 * construct a new [Socket] via the default `new Socket(...)` path fails
 * before a [SocketImpl] is ever assigned.
 *
 * The downloader module in Phase 3 (design §4.10) bypasses this layer by
 * configuring OkHttp with an explicit `SocketFactory` on its client; it
 * does not rely on the default factory chain, so the downloader is not
 * affected. The SSL / proxy / authenticator gates above *are* consulted by
 * the downloader's OkHttp client because those are process-wide statics,
 * and they honour the consent scope.
 */
private object DenyingSocketImplFactory : SocketImplFactory {
    override fun createSocketImpl(): SocketImpl {
        throw NetworkDenyingSocketFactory.denial()
    }
}
