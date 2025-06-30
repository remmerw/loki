package io.github.remmerw.loki.data

import kotlinx.io.Buffer
import kotlinx.io.readByteArray

internal class BitfieldHandler : UniqueMessageHandler(Type.Bitfield) {
    override fun doDecode(peer: Peer, buffer: Buffer): Message {
        return Bitfield(buffer.readByteArray())
    }

    override fun doEncode(peer: Peer, message: Message, buffer: Buffer) {
        val bitfield = message as Bitfield
        val payloadLength = bitfield.bitfield.size
        val size = (payloadLength + MESSAGE_TYPE_SIZE)
        buffer.writeInt(size)
        buffer.writeByte(message.messageId)
        buffer.write(bitfield.bitfield)
    }
}
