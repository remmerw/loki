package io.github.remmerw.loki.data

import io.ktor.network.sockets.InetSocketAddress
import kotlinx.io.Buffer

internal class RequestHandler : UniqueMessageHandler(Type.Request) {
    override fun doDecode(address: InetSocketAddress, buffer: Buffer): Message {
        return decodeRequest(buffer)
    }

    override fun doEncode(address: InetSocketAddress, message: Message, buffer: Buffer) {
        val request = message as Request
        request.encode(buffer)
    }

    private fun decodeRequest(buffer: Buffer): Message {
        val pieceIndex = checkNotNull(buffer.readInt())
        val blockOffset = checkNotNull(buffer.readInt())
        val blockLength = checkNotNull(buffer.readInt())

        return Request(pieceIndex, blockOffset, blockLength)
    }
}
