package io.github.remmerw.loki.data

import kotlinx.io.Buffer

internal class KeepAlive : Message {
    override val messageId: Byte
        get() {
            throw UnsupportedOperationException()
        }
    override val type: Type
        get() = Type.KeepAlive

    // keep-alive: <len=0000>
    fun encode(buffer: Buffer) {
        buffer.write(KEEPALIVE)
    }
}
