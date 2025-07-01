package io.github.remmerw.loki.data

import kotlinx.io.Buffer

internal class Unchoke : Message {
    override val messageId: Byte
        get() = UNCHOKE_ID
    override val type: Type
        get() = Type.Unchoke

    fun encode(buffer: Buffer) {
        buffer.writeByte(messageId)
    }
}


