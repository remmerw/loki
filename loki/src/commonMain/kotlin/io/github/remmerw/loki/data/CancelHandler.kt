package io.github.remmerw.loki.data

import io.ktor.network.sockets.InetSocketAddress
import kotlinx.io.Buffer

internal class CancelHandler : UniqueMessageHandler(Type.Cancel) {
    override fun doDecode(address: InetSocketAddress, buffer: Buffer): Message {
        val pieceIndex = checkNotNull(buffer.readInt())
        val blockOffset = checkNotNull(buffer.readInt())
        val blockLength = checkNotNull(buffer.readInt())
        return Cancel(pieceIndex, blockOffset, blockLength)
    }

    override fun doEncode(address: InetSocketAddress, message: Message, buffer: Buffer) {
        val cancel = message as Cancel
        cancel.encode(buffer)
    }
}
