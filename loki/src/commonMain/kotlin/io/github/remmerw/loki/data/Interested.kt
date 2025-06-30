package io.github.remmerw.loki.data

import kotlinx.io.Buffer

internal class Interested : Message {
    override val messageId: Byte
        get() = INTERESTED_ID
    override val type: Type
        get() = Type.Interested

    override fun encode(buffer: Buffer) {
        val size = (MESSAGE_TYPE_SIZE)
        buffer.writeInt(size)
        buffer.writeByte(messageId)
    }

}
