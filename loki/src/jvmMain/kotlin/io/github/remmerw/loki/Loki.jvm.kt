package io.github.remmerw.loki

import io.github.remmerw.loki.mdht.hostname
import io.ktor.network.sockets.InetSocketAddress

actual fun createInetSocketAddress(address: ByteArray, port: Int): InetSocketAddress {
    val host = hostname(address)
    return InetSocketAddress(host, port)
}