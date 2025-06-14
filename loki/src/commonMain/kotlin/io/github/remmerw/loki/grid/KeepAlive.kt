package io.github.remmerw.loki.grid

internal class KeepAlive : Message {
    override val messageId: Byte
        get() {
            throw UnsupportedOperationException()
        }
    override val type: Type
        get() = Type.KeepAlive

}
