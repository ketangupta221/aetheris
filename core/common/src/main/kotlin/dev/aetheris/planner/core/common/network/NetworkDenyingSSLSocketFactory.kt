/*
 * :core:common — pure Kotlin/JVM.
 *
 * SSL counterpart to [NetworkDenyingSocketFactory]. See that file's header
 * for the full design references. This class is separated out so the HTTPS
 * install path is obvious when reading the code.
 */
package dev.aetheris.planner.core.common.network

import java.net.InetAddress
import java.net.Socket
import javax.net.ssl.SSLSocketFactory

/**
 * An [SSLSocketFactory] that rejects every socket-creation attempt unless
 * the current thread is inside a [ConsentedDownloadScope] block, in which
 * case it delegates to [delegate]. Installed via
 * [javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory] in
 * [NetworkDenyingSocketFactory.install].
 */
public class NetworkDenyingSSLSocketFactory internal constructor(
    private val delegate: SSLSocketFactory,
) : SSLSocketFactory() {

    override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites

    override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites

    override fun createSocket(s: Socket?, host: String?, port: Int, autoClose: Boolean): Socket =
        if (ConsentedDownloadScope.isInScope()) {
            delegate.createSocket(s, host, port, autoClose)
        } else {
            throw NetworkDenyingSocketFactory.denial()
        }

    override fun createSocket(host: String?, port: Int): Socket =
        if (ConsentedDownloadScope.isInScope()) {
            delegate.createSocket(host, port)
        } else {
            throw NetworkDenyingSocketFactory.denial()
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
            throw NetworkDenyingSocketFactory.denial()
        }

    override fun createSocket(host: InetAddress?, port: Int): Socket =
        if (ConsentedDownloadScope.isInScope()) {
            delegate.createSocket(host, port)
        } else {
            throw NetworkDenyingSocketFactory.denial()
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
            throw NetworkDenyingSocketFactory.denial()
        }
}
