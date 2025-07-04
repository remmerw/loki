package io.github.remmerw.loki.data

import io.ktor.network.sockets.InetSocketAddress
import kotlinx.io.Buffer

internal class UnchokeHandler : UniqueMessageHandler(Type.Unchoke) {
    override fun doDecode(address: InetSocketAddress, buffer: Buffer): Message {
        return unchoke()
    }

    override fun doEncode(address: InetSocketAddress, message: Message, buffer: Buffer) {
        val unchoke = message as Unchoke
        unchoke.encode(buffer)

    }
}
