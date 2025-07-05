package io.github.remmerw.loki.data

import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeByte
import io.ktor.utils.io.writeByteArray
import io.ktor.utils.io.writeInt

@Suppress("ArrayInDataClass")
internal data class Bitfield(val bitfield: ByteArray) : Message {

    override val type: Type
        get() = Type.Bitfield

    suspend fun encode(channel: ByteWriteChannel) {
        val size = Byte.SIZE_BYTES + bitfield.size
        channel.writeInt(size)
        channel.writeByte(BITFIELD_ID)
        channel.writeByteArray(bitfield)
    }
}
