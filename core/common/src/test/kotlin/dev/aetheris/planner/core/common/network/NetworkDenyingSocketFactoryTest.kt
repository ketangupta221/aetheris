/*
 * :core:common — unit tests for the runtime network-denial primitives.
 *
 * These tests run on the JVM (no Android, no Robolectric). They verify the
 * *factory-level* behaviour of [NetworkDenyingSocketFactory] and
 * [NetworkDenyingSSLSocketFactory] — i.e. that `createSocket(...)` throws
 * [SecurityException] outside the consent scope, and delegates inside it —
 * without ever opening an actual TCP connection (the inner delegate is a
 * harmless recording fake).
 *
 * The Application-level scaffold test (Task 3.3) that exercises
 * [NetworkDenyingSocketFactory.install] under a running Application lives in
 * :app's Robolectric test source; the full Property 18 suite lands in
 * Phase 1 Task 25 once real operations exist.
 */
package dev.aetheris.planner.core.common.network

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.net.InetAddress
import java.net.Socket
import javax.net.SocketFactory
import javax.net.ssl.SSLSocketFactory

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class NetworkDenyingSocketFactoryTest {

    // region ConsentedDownloadScope -----------------------------------------

    @Test
    @Order(10)
    fun `ConsentedDownloadScope is out of scope by default`() {
        assertFalse(
            ConsentedDownloadScope.isInScope(),
            "fresh threads must not observe the consent scope as open",
        )
    }

    @Test
    @Order(20)
    fun `enter exit toggles the thread local`() {
        assertFalse(ConsentedDownloadScope.isInScope())
        val prior = ConsentedDownloadScope.enter()
        try {
            assertTrue(ConsentedDownloadScope.isInScope())
        } finally {
            ConsentedDownloadScope.exit(prior)
        }
        assertFalse(
            ConsentedDownloadScope.isInScope(),
            "exit must restore the prior value (false here)",
        )
    }

    @Test
    @Order(30)
    fun `scope restoration is thread-local`() {
        // A side thread enters the scope; the main thread must NOT observe
        // any change. This property is what makes the scope safe when the
        // downloader uses its own worker thread pool.
        val threadFlag = arrayOf(false)
        val worker = Thread {
            val prior = ConsentedDownloadScope.enter()
            try {
                threadFlag[0] = ConsentedDownloadScope.isInScope()
            } finally {
                ConsentedDownloadScope.exit(prior)
            }
        }
        worker.start()
        worker.join()
        assertTrue(threadFlag[0], "worker thread observed its own scope correctly")
        assertFalse(
            ConsentedDownloadScope.isInScope(),
            "main thread must remain out of scope while the worker toggled",
        )
    }

    // endregion

    // region NetworkDenyingSocketFactory ------------------------------------

    @Test
    @Order(40)
    fun `socket factory throws outside consent scope`() {
        val factory = NetworkDenyingSocketFactory(RecordingSocketFactory())

        assertThrows(SecurityException::class.java) { factory.createSocket() }
        assertThrows(SecurityException::class.java) { factory.createSocket("example.com", 443) }
        assertThrows(SecurityException::class.java) {
            factory.createSocket("example.com", 443, InetAddress.getByName("127.0.0.1"), 0)
        }
        assertThrows(SecurityException::class.java) {
            factory.createSocket(InetAddress.getByName("127.0.0.1"), 443)
        }
        assertThrows(SecurityException::class.java) {
            factory.createSocket(
                InetAddress.getByName("127.0.0.1"),
                443,
                InetAddress.getByName("127.0.0.1"),
                0,
            )
        }
    }

    @Test
    @Order(50)
    fun `socket factory delegates inside consent scope`() {
        val delegate = RecordingSocketFactory()
        val factory = NetworkDenyingSocketFactory(delegate)

        val prior = ConsentedDownloadScope.enter()
        try {
            factory.createSocket()
            factory.createSocket("example.com", 443)
            factory.createSocket(
                "example.com",
                443,
                InetAddress.getByName("127.0.0.1"),
                0,
            )
            factory.createSocket(InetAddress.getByName("127.0.0.1"), 443)
            factory.createSocket(
                InetAddress.getByName("127.0.0.1"),
                443,
                InetAddress.getByName("127.0.0.1"),
                0,
            )
        } finally {
            ConsentedDownloadScope.exit(prior)
        }

        assertSame(5, delegate.createCount, "all five overloads must delegate exactly once")
    }

    @Test
    @Order(60)
    fun `denial message matches the documented constant`() {
        val factory = NetworkDenyingSocketFactory(RecordingSocketFactory())
        val ex = assertThrows(SecurityException::class.java) { factory.createSocket() }
        assertSame(
            NetworkDenyingSocketFactory.DENIAL_MESSAGE,
            ex.message,
            "denial message must match the public constant so tests can assert stably",
        )
    }

    // endregion

    // region NetworkDenyingSSLSocketFactory ---------------------------------

    @Test
    @Order(70)
    fun `ssl socket factory throws outside consent scope`() {
        val factory = NetworkDenyingSSLSocketFactory(RecordingSSLSocketFactory())

        assertThrows(SecurityException::class.java) { factory.createSocket("example.com", 443) }
        assertThrows(SecurityException::class.java) {
            factory.createSocket("example.com", 443, InetAddress.getByName("127.0.0.1"), 0)
        }
        assertThrows(SecurityException::class.java) {
            factory.createSocket(InetAddress.getByName("127.0.0.1"), 443)
        }
        assertThrows(SecurityException::class.java) {
            factory.createSocket(
                InetAddress.getByName("127.0.0.1"),
                443,
                InetAddress.getByName("127.0.0.1"),
                0,
            )
        }
        assertThrows(SecurityException::class.java) {
            factory.createSocket(Socket(), "example.com", 443, false)
        }
    }

    @Test
    @Order(80)
    fun `ssl socket factory delegates inside consent scope`() {
        val delegate = RecordingSSLSocketFactory()
        val factory = NetworkDenyingSSLSocketFactory(delegate)

        val prior = ConsentedDownloadScope.enter()
        try {
            factory.createSocket("example.com", 443)
            factory.createSocket("example.com", 443, InetAddress.getByName("127.0.0.1"), 0)
            factory.createSocket(InetAddress.getByName("127.0.0.1"), 443)
            factory.createSocket(
                InetAddress.getByName("127.0.0.1"),
                443,
                InetAddress.getByName("127.0.0.1"),
                0,
            )
            factory.createSocket(Socket(), "example.com", 443, false)
        } finally {
            ConsentedDownloadScope.exit(prior)
        }

        assertSame(5, delegate.createCount, "all five SSL overloads must delegate exactly once")
    }

    // endregion

    // region Install (idempotent) -------------------------------------------

    @Test
    @Order(1000)
    fun `install is idempotent`() {
        // `install` mutates VM-wide state; calling it twice must be safe.
        // Note this test — and any other test in this JVM — permanently
        // installs the denying factories, so we do the install LAST in a
        // dedicated test. The factory tests above construct their own
        // NetworkDenyingSocketFactory instances without calling `install`
        // and therefore are not affected.
        NetworkDenyingSocketFactory.install()
        assertTrue(NetworkDenyingSocketFactory.isInstalled())
        val sslBefore = javax.net.ssl.HttpsURLConnection.getDefaultSSLSocketFactory()
        val proxyBefore = java.net.ProxySelector.getDefault()
        // Second call must not throw and must not rewrap the existing
        // defaults into doubly-wrapped factories.
        NetworkDenyingSocketFactory.install()
        assertTrue(NetworkDenyingSocketFactory.isInstalled())
        assertSame(
            sslBefore,
            javax.net.ssl.HttpsURLConnection.getDefaultSSLSocketFactory(),
            "install must not rewrap the HTTPS factory on repeat calls",
        )
        assertSame(
            proxyBefore,
            java.net.ProxySelector.getDefault(),
            "install must not rewrap the proxy selector on repeat calls",
        )
    }

    // endregion
}

/**
 * A [SocketFactory] that records `createSocket` invocations without opening
 * any actual TCP connection. Returning an unconnected [Socket] is safe for
 * the factory-level tests in this file because the tests never call `read`
 * or `write` on the returned object.
 */
private class RecordingSocketFactory : SocketFactory() {
    var createCount: Int = 0
        private set

    override fun createSocket(): Socket {
        createCount++
        return Socket()
    }

    override fun createSocket(host: String?, port: Int): Socket {
        createCount++
        return Socket()
    }

    override fun createSocket(
        host: String?,
        port: Int,
        localHost: InetAddress?,
        localPort: Int,
    ): Socket {
        createCount++
        return Socket()
    }

    override fun createSocket(host: InetAddress?, port: Int): Socket {
        createCount++
        return Socket()
    }

    override fun createSocket(
        address: InetAddress?,
        port: Int,
        localAddress: InetAddress?,
        localPort: Int,
    ): Socket {
        createCount++
        return Socket()
    }
}

/**
 * Same recording pattern as [RecordingSocketFactory] for the SSL overloads.
 * The returned [Socket] is an unconnected [Socket] rather than a real
 * `SSLSocket`; constructing a real SSLSocket requires a TLS context and is
 * unnecessary for the factory-level delegation tests.
 */
private class RecordingSSLSocketFactory : SSLSocketFactory() {
    var createCount: Int = 0
        private set

    override fun getDefaultCipherSuites(): Array<String> = emptyArray()
    override fun getSupportedCipherSuites(): Array<String> = emptyArray()

    override fun createSocket(s: Socket?, host: String?, port: Int, autoClose: Boolean): Socket {
        createCount++
        // Close the wrapping socket per the autoClose contract so the test
        // does not leak a file descriptor.
        if (autoClose) {
            s?.close()
        }
        return Socket()
    }

    override fun createSocket(host: String?, port: Int): Socket {
        createCount++
        return Socket()
    }

    override fun createSocket(
        host: String?,
        port: Int,
        localHost: InetAddress?,
        localPort: Int,
    ): Socket {
        createCount++
        return Socket()
    }

    override fun createSocket(host: InetAddress?, port: Int): Socket {
        createCount++
        return Socket()
    }

    override fun createSocket(
        address: InetAddress?,
        port: Int,
        localAddress: InetAddress?,
        localPort: Int,
    ): Socket {
        createCount++
        return Socket()
    }
}
