package io.github.remmerw.loki.data

import kotlinx.io.Buffer

internal class NotInterested : Message {
    override val messageId: Byte
        get() = NOT_INTERESTED_ID
    override val type: Type
        get() = Type.NotInterested

    override fun encode(buffer: Buffer) {
        val size = (MESSAGE_TYPE_SIZE)
        buffer.writeInt(size)
        buffer.writeByte(messageId)
    }
}
