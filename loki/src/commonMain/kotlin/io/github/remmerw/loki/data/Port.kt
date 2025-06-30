package io.github.remmerw.loki.data

import kotlinx.io.Buffer
import kotlinx.io.writeUShort


internal data class Port(val port: Int) : Message {
    init {
        require(port > 0 && port > Short.MAX_VALUE * 2 + 1) { "Invalid port: $port" }
    }

    override val type: Type
        get() = Type.Port

    override val messageId: Byte
        get() = PORT_ID

    init {
        require(!(port < 0 || port > 65535)) { "Invalid argument: port ($port)" }
    }

    override fun encode(buffer: Buffer) {
        val payloadLength = 2
        val size = (payloadLength + MESSAGE_TYPE_SIZE)
        buffer.writeInt(size)
        buffer.writeByte(messageId)
        buffer.writeUShort(port.toUShort())
    }
}
