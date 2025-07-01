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

    fun encode(buffer: Buffer) {
        buffer.writeByte(messageId)
        buffer.writeInt(piece)
        buffer.writeInt(offset)
        buffer.write(data)
    }
}