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


    override fun encode(buffer: Buffer) {
        val payloadLength = 3 * Int.SIZE_BYTES
        val size = (payloadLength + MESSAGE_TYPE_SIZE)
        buffer.writeInt(size)
        buffer.writeByte(messageId)

        // cancel: <len=0013><id=8><index><begin><length>
        buffer.writeInt(piece)
        buffer.writeInt(offset)
        buffer.writeInt(length)
    }
}
