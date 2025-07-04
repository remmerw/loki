package io.github.remmerw.loki.data

import io.ktor.network.sockets.InetSocketAddress
import kotlinx.io.Buffer

internal class ChokeHandler : UniqueMessageHandler(Type.Choke) {
    override fun doDecode(address: InetSocketAddress, buffer: Buffer): Message {
        return choke()
    }

    override fun doEncode(address: InetSocketAddress, message: Message, buffer: Buffer) {
        val choke = message as Choke
        choke.encode(buffer)
    }
}
