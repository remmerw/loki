package io.github.remmerw.loki.data

internal class Unchoke : Message {
    override val messageId: Byte
        get() = UNCHOKE_ID
    override val type: Type
        get() = Type.Unchoke
}


