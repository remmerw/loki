package io.github.remmerw.loki.data

import kotlinx.io.Buffer

internal class Choke : Message {
    override val messageId: Byte
        get() = CHOKE_ID
    override val type: Type
        get() = Type.Choke

    override fun encode(buffer: Buffer) {
        val size = MESSAGE_TYPE_SIZE
        buffer.writeInt(size)
        buffer.writeByte(messageId)
    }
}
