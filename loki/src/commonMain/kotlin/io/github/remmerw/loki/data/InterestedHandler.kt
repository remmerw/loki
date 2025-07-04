package io.github.remmerw.loki.data

import io.ktor.network.sockets.InetSocketAddress
import kotlinx.io.Buffer

internal class InterestedHandler : UniqueMessageHandler(Type.Interested) {
    override fun doDecode(address: InetSocketAddress, buffer: Buffer): Message {
        return interested()
    }

    override fun doEncode(address: InetSocketAddress, message: Message, buffer: Buffer) {
        val interested = message as Interested
        interested.encode(buffer)
    }
}
