package io.github.remmerw.loki.grid

internal class Unchoke : Message {
    override val messageId: Byte
        get() = UNCHOKE_ID
    override val type: Type
        get() = Type.Unchoke
}


