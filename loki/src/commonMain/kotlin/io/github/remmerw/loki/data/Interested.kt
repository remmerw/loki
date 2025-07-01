package io.github.remmerw.loki.data

import kotlinx.io.Buffer

internal class Interested : Message {
    override val messageId: Byte
        get() = INTERESTED_ID
    override val type: Type
        get() = Type.Interested

    fun encode(buffer: Buffer) {
        buffer.writeByte(messageId)
    }

}
