package io.github.remmerw.loki.data

import kotlinx.io.Buffer

internal class RequestHandler : UniqueMessageHandler(Type.Request) {
    override fun doDecode(peer: Peer, buffer: Buffer): Message {
        return decodeRequest(buffer)
    }

    override fun doEncode(peer: Peer, message: Message, buffer: Buffer) {
        val request = message as Request
        val payloadLength = 3 * Int.SIZE_BYTES
        val size = (payloadLength + MESSAGE_TYPE_SIZE)
        buffer.writeInt(size)
        buffer.writeByte(message.messageId)

        buffer.writeInt(request.piece)
        buffer.writeInt(request.offset)
        buffer.writeInt(request.length)
    }

    private fun decodeRequest(buffer: Buffer): Message {
        val pieceIndex = checkNotNull(buffer.readInt())
        val blockOffset = checkNotNull(buffer.readInt())
        val blockLength = checkNotNull(buffer.readInt())

        return Request(pieceIndex, blockOffset, blockLength)
    }
}
