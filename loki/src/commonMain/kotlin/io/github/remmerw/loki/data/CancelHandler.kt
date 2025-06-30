package io.github.remmerw.loki.data

import kotlinx.io.Buffer

internal class CancelHandler : UniqueMessageHandler(Type.Cancel) {
    override fun doDecode(peer: Peer, buffer: Buffer): Message {
        val pieceIndex = checkNotNull(buffer.readInt())
        val blockOffset = checkNotNull(buffer.readInt())
        val blockLength = checkNotNull(buffer.readInt())
        return Cancel(pieceIndex, blockOffset, blockLength)
    }

    override fun doEncode(peer: Peer, message: Message, buffer: Buffer) {
        val cancel = message as Cancel
        cancel.encode(buffer)
    }
}
