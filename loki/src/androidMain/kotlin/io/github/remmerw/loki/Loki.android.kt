package io.github.remmerw.loki

import io.github.remmerw.loki.mdht.hostname
import io.ktor.network.sockets.InetSocketAddress
import java.net.InetAddress

actual fun createInetSocketAddress(address: ByteArray, port: Int): InetSocketAddress {
    if (address.size == 16) {
        val inet = InetAddress.getByAddress(address)
        return InetSocketAddress(inet.hostName, port)
    } else {
        val host = hostname(address)
        return InetSocketAddress(host, port)
    }
}