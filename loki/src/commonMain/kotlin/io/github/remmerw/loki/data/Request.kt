package io.github.remmerw.loki.data

import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeByte
import io.ktor.utils.io.writeInt

internal data class Request(
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

    override val type: Type
        get() = Type.Request


    suspend fun encode(channel: ByteWriteChannel) {
        val size = Byte.SIZE_BYTES + Int.SIZE_BYTES + Int.SIZE_BYTES + Int.SIZE_BYTES
        channel.writeInt(size)
        channel.writeByte(REQUEST_ID)
        channel.writeInt(piece)
        channel.writeInt(offset)
        channel.writeInt(length)
    }
}
