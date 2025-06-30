package io.github.remmerw.loki.data

import kotlinx.io.Buffer
import kotlinx.io.writeUShort

internal class PortHandler : UniqueMessageHandler(Type.Port) {
    override fun doDecode(peer: Peer, buffer: Buffer): Message {
        return decodePort(buffer)
    }

    override fun doEncode(peer: Peer, message: Message, buffer: Buffer) {
        val port = message as Port
        val payloadLength = 2
        val size = (payloadLength + MESSAGE_TYPE_SIZE)
        buffer.writeInt(size)
        buffer.writeByte(message.messageId)
        buffer.writeUShort(port.port.toUShort())
    }


    private fun decodePort(buffer: Buffer): Message {
        val port = checkNotNull(buffer.readShort()).toInt() and 0x0000FFFF
        return Port(port)
    }
}
