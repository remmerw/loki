package io.github.remmerw.loki.data

import io.ktor.network.sockets.InetSocketAddress
import kotlinx.io.Buffer

internal class NotInterestedHandler : UniqueMessageHandler(Type.NotInterested) {
    override fun doDecode(address: InetSocketAddress, buffer: Buffer): Message {
        return notInterested()
    }

    override fun doEncode(address: InetSocketAddress, message: Message, buffer: Buffer) {
        val notInterested = message as NotInterested
        notInterested.encode(buffer)

    }
}
