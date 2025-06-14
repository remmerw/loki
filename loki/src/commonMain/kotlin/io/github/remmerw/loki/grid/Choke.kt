package io.github.remmerw.loki.grid

internal class Choke : Message {
    override val messageId: Byte
        get() = CHOKE_ID
    override val type: Type
        get() = Type.Choke
}
