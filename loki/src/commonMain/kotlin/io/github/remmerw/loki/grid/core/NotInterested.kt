package io.github.remmerw.loki.grid.core

import io.github.remmerw.loki.grid.NOT_INTERESTED_ID

internal class NotInterested : Message {
    override val messageId: Byte
        get() = NOT_INTERESTED_ID
    override val type: Type
        get() = Type.NotInterested

}
