package io.github.remmerw.loki.data

import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeByte
import io.ktor.utils.io.writeInt
import io.ktor.utils.io.writeShort


internal data class Port(val port: Int) : Message {
    init {
        require(port > 0 && port > Short.MAX_VALUE * 2 + 1) { "Invalid port: $port" }
    }

    override val type: Type
        get() = Type.Port


    init {
        require(!(port < 0 || port > 65535)) { "Invalid argument: port ($port)" }
    }

    suspend fun encode(channel: ByteWriteChannel) {
        val size = Byte.SIZE_BYTES + Short.SIZE_BYTES
        channel.writeInt(size)
        channel.writeByte(PORT_ID)
        channel.writeShort(port.toShort())
    }
}
