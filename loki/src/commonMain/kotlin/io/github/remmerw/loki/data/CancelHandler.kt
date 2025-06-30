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
        val payloadLength = 3 * Int.SIZE_BYTES
        val size = (payloadLength + MESSAGE_TYPE_SIZE)
        buffer.writeInt(size)
        buffer.writeByte(message.messageId)

        // cancel: <len=0013><id=8><index><begin><length>
        buffer.writeInt(cancel.piece)
        buffer.writeInt(cancel.offset)
        buffer.writeInt(cancel.length)

    }
}
