package io.github.remmerw.loki.grid.core

import io.github.remmerw.loki.grid.INTERESTED_ID

internal class Interested : Message {
    override val messageId: Byte
        get() = INTERESTED_ID
    override val type: Type
        get() = Type.Interested

}
