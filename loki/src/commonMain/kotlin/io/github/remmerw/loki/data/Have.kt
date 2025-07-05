package io.github.remmerw.loki.data

import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeByte
import io.ktor.utils.io.writeInt


internal data class Have(val piece: Int) : Message {

    override val type: Type
        get() = Type.Have

    init {
        require(piece >= 0) { "Illegal argument: piece index ($piece)" }
    }

    suspend fun encode(channel: ByteWriteChannel) {
        val size = Byte.SIZE_BYTES + Int.SIZE_BYTES
        channel.writeInt(size)
        channel.writeByte(HAVE_ID)
        channel.writeInt(piece)
    }
}
