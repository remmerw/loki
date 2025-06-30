package io.github.remmerw.loki.data

import kotlinx.io.Buffer

@Suppress("ArrayInDataClass")
internal data class Piece(
    val piece: Int,
    val offset: Int,
    val data: ByteArray
) : Message {

    init {
        require(!(piece < 0 || offset < 0)) { "Invalid arguments" }
    }

    override val type: Type
        get() = Type.Piece
    override val messageId: Byte
        get() = PIECE_ID

    override fun encode(buffer: Buffer) {
        val payloadLength = data.size + (2 * Int.SIZE_BYTES)
        val size = (payloadLength + MESSAGE_TYPE_SIZE)
        buffer.writeInt(size)
        buffer.writeByte(messageId)

        // piece: <len=0009+X><id=7><index><begin><block>
        buffer.writeInt(piece)
        buffer.writeInt(offset)
        buffer.write(data)
    }
}