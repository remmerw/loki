package io.github.remmerw.loki.data

import kotlinx.io.Buffer

internal class Choke : Message {
    override val messageId: Byte
        get() = CHOKE_ID
    override val type: Type
        get() = Type.Choke

    fun encode(buffer: Buffer) {
        buffer.writeByte(messageId)
    }
}
