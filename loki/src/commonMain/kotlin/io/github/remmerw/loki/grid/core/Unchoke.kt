package io.github.remmerw.loki.grid.core

import io.github.remmerw.loki.grid.UNCHOKE_ID

internal class Unchoke : Message {
    override val messageId: Byte
        get() = UNCHOKE_ID
    override val type: Type
        get() = Type.Unchoke
}


