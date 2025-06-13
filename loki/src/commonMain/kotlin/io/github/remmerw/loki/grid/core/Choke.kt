package io.github.remmerw.loki.grid.core

import io.github.remmerw.loki.grid.CHOKE_ID

internal class Choke : Message {
    override val messageId: Byte
        get() = CHOKE_ID
    override val type: Type
        get() = Type.Choke
}
