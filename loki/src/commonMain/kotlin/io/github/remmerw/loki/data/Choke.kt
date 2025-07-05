package io.github.remmerw.loki.data

import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeByte
import io.ktor.utils.io.writeInt

internal class Choke : Message {

    override val type: Type
        get() = Type.Choke

    suspend fun encode(channel: ByteWriteChannel) {
        val size = Byte.SIZE_BYTES
        channel.writeInt(size)
        channel.writeByte(CHOKE_ID)
    }
}
