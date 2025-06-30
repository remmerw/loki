package io.github.remmerw.loki.data

import kotlinx.io.Buffer

internal class HaveHandler : UniqueMessageHandler(Type.Have) {
    override fun doDecode(peer: Peer, buffer: Buffer): Message {
        return decodeHave(buffer)
    }

    override fun doEncode(peer: Peer, message: Message, buffer: Buffer) {
        val have = message as Have

        val payloadLength = Int.SIZE_BYTES
        val size = (payloadLength + MESSAGE_TYPE_SIZE)
        buffer.writeInt(size)
        buffer.writeByte(message.messageId)
        buffer.writeInt(have.piece)
    }


    private fun decodeHave(buffer: Buffer): Message {
        val pieceIndex = checkNotNull(buffer.readInt())
        return Have(pieceIndex)
    }
}