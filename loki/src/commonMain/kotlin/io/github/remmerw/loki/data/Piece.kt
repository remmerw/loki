package io.github.remmerw.loki.data

import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeByte
import io.ktor.utils.io.writeByteArray
import io.ktor.utils.io.writeInt

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


    suspend fun encode(channel: ByteWriteChannel) {
        val size = Byte.SIZE_BYTES + Int.SIZE_BYTES + Int.SIZE_BYTES + data.size
        channel.writeInt(size)
        channel.writeByte(PIECE_ID)
        channel.writeInt(piece)
        channel.writeInt(offset)
        channel.writeByteArray(data)

    }
}