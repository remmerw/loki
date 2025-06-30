package io.github.remmerw.loki.data

import kotlinx.io.Buffer


internal data class Have(val piece: Int) : Message {


    override val messageId: Byte
        get() = HAVE_ID
    override val type: Type
        get() = Type.Have

    init {
        require(piece >= 0) { "Illegal argument: piece index ($piece)" }
    }

    override fun encode(buffer: Buffer) {
        val payloadLength = Int.SIZE_BYTES
        val size = (payloadLength + MESSAGE_TYPE_SIZE)
        buffer.writeInt(size)
        buffer.writeByte(messageId)
        buffer.writeInt(piece)
    }
}
