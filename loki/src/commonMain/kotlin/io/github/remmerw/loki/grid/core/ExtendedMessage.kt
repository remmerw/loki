package io.github.remmerw.loki.grid.core

import io.github.remmerw.loki.grid.EXTENDED_MESSAGE_ID


internal interface ExtendedMessage : Message {
    override val messageId: Byte
        get() = EXTENDED_MESSAGE_ID
}
