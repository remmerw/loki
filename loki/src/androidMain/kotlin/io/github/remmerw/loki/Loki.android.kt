package io.github.remmerw.loki
import io.ktor.network.sockets.InetSocketAddress
import java.net.InetAddress

actual fun createInetSocketAddress(address: ByteArray, port:Int) : InetSocketAddress {
    val inet = InetAddress.getByAddress(address)
    return InetSocketAddress(inet.hostName, port)
}