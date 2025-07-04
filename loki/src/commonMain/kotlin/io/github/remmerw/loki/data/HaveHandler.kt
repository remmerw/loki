package io.github.remmerw.loki.data

import io.ktor.network.sockets.InetSocketAddress
import kotlinx.io.Buffer

internal class HaveHandler : UniqueMessageHandler(Type.Have) {
    override fun doDecode(address: InetSocketAddress, buffer: Buffer): Message {
        val pieceIndex = buffer.readInt()
        return Have(pieceIndex)
    }

    override fun doEncode(address: InetSocketAddress, message: Message, buffer: Buffer) {
        val have = message as Have
        have.encode(buffer)
    }
}