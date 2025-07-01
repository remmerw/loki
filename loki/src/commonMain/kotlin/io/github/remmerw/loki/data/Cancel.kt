package io.github.remmerw.loki.data

import kotlinx.io.Buffer

internal data class Cancel(
    val piece: Int,
    val offset: Int,
    val length: Int
) : Message {
    init {
        require(!(piece < 0 || offset < 0 || length <= 0)) {
            ("Invalid arguments: pieceIndex (" + piece
                    + "), offset (" + offset + "), length (" + length + ")")
        }
    }

    override val messageId: Byte
        get() = CANCEL_ID

    override val type: Type
        get() = Type.Cancel


    fun encode(buffer: Buffer) {
        buffer.writeByte(messageId)
        buffer.writeInt(piece)
        buffer.writeInt(offset)
        buffer.writeInt(length)
    }
}
