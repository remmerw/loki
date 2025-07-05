package io.github.remmerw.loki.data

import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeByteArray

internal class KeepAlive : Message {

    override val type: Type
        get() = Type.KeepAlive

    // keep-alive: <len=0000>
    suspend fun encode(channel: ByteWriteChannel) {
        channel.writeByteArray(KEEPALIVE)
    }
}
